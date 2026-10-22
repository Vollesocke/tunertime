/* pitch.c - finds the note being played, for auto note detection.
 *
 * Runs on the strobe thread alongside the strobes, so it has to be cheap
 * and must not touch the FFT-mode state in fft.c.  The input is decimated
 * 2:1 (the partials we care about are below 9kHz) behind a half-band
 * low-pass, and every HOP new
 * samples an N-point FFT is taken over the last N.  At 44.1kHz that is a
 * 0.37s window with 2.7Hz bins, analysed about 11 times a second.
 *
 * Peaks are picked from the magnitude spectrum.  Candidate fundamentals
 * are every peak divided by 1..MAX_DIV, plus the spacing between strong
 * neighbouring peaks (a missing or weak fundamental: the low organ pipe
 * through a phone mic, a reed whose 3rd-5th partials dominate).  Each is
 * scored by how much peak energy lies on its harmonics 1..MAX_HARM.  Lower
 * harmonics weigh more, which keeps the octave below from winning on even
 * harmonics alone.  A spacing candidate alone would be an octave high on a
 * stopped pipe (Gedackt, Subbass: odd partials 1,3,5.. only, spaced 2*f0),
 * but there it matches nothing at odd multiples, while peak/3 or peak/5
 * does, so the score sorts that out.  The per-partial tolerance is wide
 * enough for piano inharmonicity.
 *
 * Confidence is the share of all peak energy that lies on harmonics of the
 * winner (up to MAX_EXPL), so reeds with 20 partials and mixtures count as
 * clear tones, while noise, whose peaks fall anywhere, does not.
 *
 * The place's steady noise lines (a blower motor in the organ case, mains
 * hum) are learnt from the quiet windows between keys and taken off the
 * spectrum below NOISE_HZ before the peaks are found; the gate on the
 * input level drops in a quiet place (LOW_RMS).  Measured on Onno's runs
 * of 21 Oct (tools/field/eval.sh): keys agreed 186 -> 191 of 206 (the
 * Gedackt in the Becker organ, its motor 12 dB under the soft pipes, 44 ->
 * 48 of 56), warped runs 367 -> 398 of 465, the 19 Oct recordings 40 -> 44
 * of 81, sim_organ's Field scenarios 16% and 2% empty instead of 33% and
 * 16%; the synthetic bench unchanged.
 *
 * pitch_hint() passes the partial the user focused on (organ mode): then
 * the note is that partial's peak / focus ratio, the peak being looked for
 * within half an octave of where the shown note puts it (see focus() for
 * which of the partials in reach is taken).
 */
#include <math.h>
#include <string.h>
#include "pitch.h"

#define N		8192
#define LGN		13
#define HOP		(N/4)
#define MAX_PK		32
#define MAX_DIV		6
#define MAX_GAP		8	/* strongest peaks whose spacings are tried */
#define MAX_HARM	12
#define MAX_EXPL	32	/* harmonics counted for the confidence */
#define TOL_CENTS	40.0
#define TOL_SPACING	0.2	/* ... but never more than this * f0 */
#define MIN_F0		15.0	/* a bit under C0, the 32' organ C */
#define MAX_F0		4300.0	/* a bit over C8 */
#define MAX_SINE_F0	8000.0	/* a lone partial: the top of a 1' rank */
#define MAX_PARTIAL	9000.0
#define MIN_RMS		60.0	/* 16-bit units; quieter input is ignored.. */
#define LOW_RMS		20.0	/* ..unless the place is quiet: then down
				 * to FLOOR_GAIN over its noise floor, but
				 * not under this.  Onno's runs of 21 Oct:
				 * at Hmth 204 (floor 18) B4 and Cs5 came
				 * in at 47 and 30 and were never read; at
				 * Hmth 202 (blower hum, floor 42) a lower
				 * fixed gate let the hum through as notes
				 * (sim_organ Field: 18 more wrong) */
#define FLOOR_GAIN	1.6
#define NOISE_HZ	1000.0	/* the steady lines of the place (blower motor,
				 * mains hum), learnt below this.. */
#define QUIET		1.4	/* ..from windows this close to the floor,
				 * under MIN_RMS (no pipe heard from the
				 * start is taken for noise), with nothing
				 * detected: between keys... */
#define NOISE_AVG	0.1	/* ...averaged so.. */
#define NOISE_OVER	1.5	/* ..and taken off the spectrum this many
				 * times over before the peaks are found */
#define FLOOR_RISE	1.0005	/* per analysis: ~0.5%/s.  pitch_floor(),
				 * the floor the follow logic ends the hold
				 * of a correction on (TunerTime KEY_QUIET),
				 * rises so only after FLOOR_UNHEARD with no
				 * note heard: it follows a noise that grows
				 * (a blower started), not the organ.
				 * Rising under the notes too, it crept up
				 * to the pipes in a pass with no quiet
				 * between keys (Onno's run of 22 Oct,
				 * Gedackt 8 + Rohrfloete 4: 65 to 280 in
				 * 270 s, F6 and G6 sounding at 250-400)
				 * and soft keys were taken for released:
				 * tools/field/noise_score.py "quiet" 16
				 * times in the runs of 21/22 Oct, now 6
				 * (keys at the floor, never read, and a
				 * 4' heard alone); sim_organ "Field work
				 * noise, Quinte": corrections undone 3 ->
				 * 1.  The gate and the noise lines keep
				 * the floor rising always: held, the
				 * Field scenarios of sim_organ lost 11
				 * and 10 keys */
#define FLOOR_UNHEARD	8	/* analyses in a row (0.7 s): not the few a
				 * knock on the pipe drops, which, a knock
				 * every 3 s (sim_organ "Field work
				 * noise"), lifted the floor 12% in six
				 * minutes */
#define MIN_CONF	0.7	/* share of peak energy on the winner's
				 * harmonics; the synthetic tones of
				 * tools/test_pitch.c score 0.84-1,
				 * the lowest with mains hum */
#define WEAK_CONF	0.55	/* weaker series are considered only after
				 * repeated, stable estimates in pitch_feed().
				 * tools/field (Onno's church recordings):
				 * 0.60 -> 0.55 reached the corrected note
				 * after 40 corrections of 81 instead of 34,
				 * no more wrong jumps, sim_organ unchanged;
				 * 0.50 let speech through (sim_organ) */
#define WEAK_AGREE	3	/* consecutive FFT windows */
#define WEAK_TOL_CENTS	25.0	/* maximum change between weak estimates */
#define SINE_CONF	0.8	/* share a lone partial needs to stand alone */
#define SUB_SCORE	0.85	/* f0/2 or f0/3 replaces f0 if it scores at
				 * least this share of f0's score.. */
#define SUB_GAIN	0.1	/* ..and explains this much more */
#define SUB_MIN_F0	30.0	/* ..and is not below this: under 30 Hz the
				 * harmonics are so dense that mains hum
				 * (50, 100, 150 Hz) falls on them */
#define FOCUS_SPAN	550.0	/* cents either side of the expected partial,
				 * under half an octave so that only one
				 * octave of it can be in reach */

static double fs;		/* sample rate after decimation */
static float ring[N];		/* decimated input, circular */
static int ring_pos;
static int fresh;		/* decimated samples since the last analysis */
/* the 2:1 decimation's low-pass: a half-band FIR, 2*HB_M+1 taps of a
 * Kaiser-windowed sinc, every other one zero.  The partials of a top pipe
 * (a 2' or 1' rank at 5-8 kHz) lie at 10-24 kHz; a plain average of two
 * samples let them fold back at -7 dB and fake a harmonic series below
 * (E8 heard as C6), this keeps them 50 dB down above 13 kHz */
#define HB_M		19
#define HB_BETA		5.0
static double hb_c[HB_M+1];	/* by distance from the centre tap */
static float hb_x[2*HB_M+1];	/* input history, circular */
static int hb_pos;
static int odd;			/* the next input sample makes no output */
static double focus_ratio, expect_hz;	/* pitch_hint() */
static int last_at;		/* pitch_last_at() */
static double nmag[N/2];	/* the learnt noise lines, by bin */
static int nbins;
static double level, floor_rms;	/* pitch_level(): the last window's rms,
				 * and its slowly rising minimum */
static double key_floor;	/* pitch_floor(): the same rising only
				 * while no note is heard (FLOOR_RISE),
				 * from the first full window (the ring
				 * starts with zeros: half the level) */
static int windows;		/* analyses since pitch_init(), to N/HOP */
static int unheard;		/* analyses in a row with no note heard */
static double weak_hz, weak_conf;
static int weak_count;
#if 2*HOP != PITCH_STEP
#error PITCH_STEP in pitch.h must be 2*HOP
#endif

static double re[N], im[N], win[N], mag[N/2];
static double tw_r[N/2], tw_i[N/2];
static int bitrev[N];

/* modified Bessel function of order 0, for the Kaiser window */
static double
bessel_i0(double x)
{
	double s = 1.0, t = 1.0;
	int k;
	for (k = 1; k < 30; k++) {
		t *= (x/(2.0*k))*(x/(2.0*k));
		s += t;
	}
	return s;
}

void
pitch_init(int sample_rate)
{
	int i, j, b;

	fs = sample_rate / 2.0;
	memset(ring, 0, sizeof ring);
	memset(hb_x, 0, sizeof hb_x);
	ring_pos = fresh = odd = hb_pos = 0;
	weak_hz = weak_conf = 0.0;
	weak_count = 0;
	level = floor_rms = key_floor = 0.0;
	windows = unheard = 0;
	memset(nmag, 0, sizeof nmag);
	nbins = (int)(NOISE_HZ*N/fs);
	{
		double sum = 0.0;
		for (i = 0; i <= HB_M; i++) {
			double x = (double)i/HB_M;
			hb_c[i] = (i == 0) ? 0.5 : (i % 2) ?
				sin(M_PI*i/2.0)/(M_PI*i) *
				bessel_i0(HB_BETA*sqrt(1.0 - x*x)) /
				bessel_i0(HB_BETA) : 0.0;
			sum += (i == 0) ? hb_c[i] : 2.0*hb_c[i];
		}
		for (i = 0; i <= HB_M; i++) {
			hb_c[i] /= sum;		/* unity gain at DC */
		}
	}
	for (i = 0; i < N; i++) {
		win[i] = 0.5 - 0.5*cos(2.0*M_PI*i/N);	/* Hann */
		for (j = 0, b = 0; b < LGN; b++) {
			j |= ((i >> b) & 1) << (LGN-1-b);
		}
		bitrev[i] = j;
	}
	for (i = 0; i < N/2; i++) {
		tw_r[i] = cos(2.0*M_PI*i/N);
		tw_i[i] = -sin(2.0*M_PI*i/N);
	}
}

void
pitch_hint(double focus, double expect)
{
	double new_focus = (focus > 0.0) ? focus : 0.0;
	double new_expect = (expect > 0.0) ? expect : 0.0;
	if ((new_focus != focus_ratio) || (new_expect != expect_hz)) {
		weak_count = 0;
		weak_hz = weak_conf = 0.0;
	}
	focus_ratio = new_focus;
	expect_hz = new_expect;
}

static void
fft(void)
{
	int len, i, j;
	for (i = 0; i < N; i++) {
		j = bitrev[i];
		if (i < j) {
			double t = re[i]; re[i] = re[j]; re[j] = t;
			t = im[i]; im[i] = im[j]; im[j] = t;
		}
	}
	for (len = 2; len <= N; len <<= 1) {
		int half = len/2, step = N/len;
		for (i = 0; i < N; i += len) {
			for (j = 0; j < half; j++) {
				double wr = tw_r[j*step], wi = tw_i[j*step];
				int a = i+j, b = a+half;
				double xr = re[b]*wr - im[b]*wi;
				double xi = re[b]*wi + im[b]*wr;
				re[b] = re[a]-xr; im[b] = im[a]-xi;
				re[a] += xr; im[a] += xi;
			}
		}
	}
}

struct peak { double f, m; };

/* the magnitude with the place's noise lines taken off */
static double
clean(int k)
{
	double m;
	if (k >= nbins) return mag[k];
	m = mag[k] - NOISE_OVER*nmag[k];
	return (m > 0.0) ? m : 0.0;
}

/* collects the MAX_PK strongest local maxima that stand well above the
 * average level, with parabolic interpolation of the frequency */
static int
find_peaks(struct peak *pk)
{
	int k, n = 0, kmin = (int)(MIN_F0*N/fs), kmax = (int)(MAX_PARTIAL*N/fs);
	double level = 0.0;

	if (kmin < 2) kmin = 2;
	if (kmax > N/2-2) kmax = N/2-2;
	for (k = kmin; k <= kmax; k++) {
		level += mag[k];
	}
	level = 4.0 * level / (kmax-kmin+1);

	for (k = kmin; k <= kmax; k++) {
		double a = clean(k-1), b = clean(k), c = clean(k+1);
		if ((b <= level) || (b <= a) || (b < c)) {
			continue;
		}
		double la = log(a+1e-9), lb = log(b), lc = log(c+1e-9);
		double den = la - 2.0*lb + lc;
		double d = (den < 0.0) ? 0.5*(la-lc)/den : 0.0;
		struct peak p = { (k+d)*fs/N, b };
		/* insert, keeping pk[] sorted by magnitude, strongest first */
		int i = (n < MAX_PK) ? n++ : MAX_PK;
		while ((i > 0) && (pk[i-1].m < p.m)) {
			if (i < MAX_PK) pk[i] = pk[i-1];
			i--;
		}
		if (i < MAX_PK) pk[i] = p;
	}
	return n;
}

/* how far (Hz) partial h of f0 may lie from h*f0 */
static double
tolerance(int h, double f0)
{
	static double lim = 0.0;
	if (lim == 0.0) lim = pow(2.0, TOL_CENTS/1200.0) - 1.0;
	double t = h*f0*lim;
	return (t < TOL_SPACING*f0) ? t : TOL_SPACING*f0;
}

/* scores f0 against the peaks; *f0 is refined from the matched partials */
static double
score(struct peak *pk, int n, double *f0)
{
	double s = 0.0, w = 1.0, fsum = 0.0, fwt = 0.0;
	int h, i, matched = 0, lowest = 0;

	for (h = 1; h <= MAX_HARM; h++, w *= 0.85) {
		double t = h * *f0, tol = tolerance(h, *f0);
		double best = 0.0, bestf = 0.0;
		if (t > MAX_PARTIAL) break;
		for (i = 0; i < n; i++) {
			if ((fabs(pk[i].f - t) < tol) && (pk[i].m > best)) {
				best = pk[i].m;
				bestf = pk[i].f;
			}
		}
		if (best > 0.0) {
			s += w*best;
			if (!matched++) lowest = h;
			fsum += h*best * (bestf/h);	/* high partials resolve */
			fwt += h*best;			/* f0 more finely */
		}
	}
	/* two partials alone, the lowest of them the 3rd or above, are no
	 * evidence of a tone: any two partials are the 4th and 5th of
	 * something, like the tail of C8 and a new E8 (C6) */
	if ((matched < 2) || ((matched == 2) && (lowest > 2))) {
		return 0.0;
	}
	*f0 = fsum/fwt;
	return s;
}

/* peak energy on harmonics 1..MAX_EXPL of f0 */
static double
explained(struct peak *pk, int n, double f0)
{
	double e = 0.0;
	int i;
	for (i = 0; i < n; i++) {
		int h = (int)floor(pk[i].f/f0 + 0.5);
		if ((h >= 1) && (h <= MAX_EXPL) &&
		    (fabs(pk[i].f - h*f0) < tolerance(h, f0))) {
			e += pk[i].m;
		}
	}
	return e;
}

static void
try_f0(struct peak *pk, int n, double f0, double *best, double *best_f)
{
	if ((f0 < MIN_F0) || (f0 > MAX_F0)) return;
	double s = score(pk, n, &f0);
	if (s > *best*1.0001) {
		*best = s;
		*best_f = f0;
	}
}

/* is f within tolerance of a harmonic 1..MAX_EXPL of f0 */
static int
harmonic(double f, double f0)
{
	int h = (int)floor(f/f0 + 0.5);
	return (h >= 1) && (h <= MAX_EXPL) &&
		(fabs(f - h*f0) < tolerance(h, f0));
}

/* with a focus hint: the partial of the heard tone near where the shown
 * note puts the focused partial, divided back down; or, with the note
 * unknown or the partial not in reach, the heard fundamental is taken as
 * the focused partial.
 *
 * The span reaches the neighbouring ranks of a mixture (the 1 1/3' is 6
 * times the 8', the 1' 8 times, 498 cents apart), and the loudest partial
 * in it need not be the focused one.  A mixture's ranks are all harmonics
 * of the key, so the note a candidate stands for (its frequency / focus
 * ratio) should be
 *  - plausible: the heard fundamental f0, the ranks' common divisor, is a
 *    whole multiple or fraction of it (2', 1 1/3', 1' = 4, 6, 8 times the
 *    8': f0 = 2 times), and
 *  - explain the partials up to the candidate: the 1' as "1 1/3'" stands
 *    for F3 on a C3 key, and leaves the real 1 1/3' (G5) unexplained.
 * Ties go to the stronger partial.  Candidates are harmonics of f0, f0/2
 * or f0/3, as f0 may be one rank when the common divisor is weak */
static double
focus(struct peak *pk, int n, double f0)
{
	if (expect_hz > 0.0) {
		double t = focus_ratio*expect_hz, lim, bestf = 0.0;
		double best_m = 0.0, best_x = -1.0;
		int best_pl = -1, i, j;
		lim = pow(2.0, FOCUS_SPAN/1200.0);
		for (i = 0; i < n; i++) {
			double note = pk[i].f/focus_ratio, r, x, e = 0.0, all = 0.0;
			int pl;
			if ((pk[i].f >= t*lim) || (pk[i].f <= t/lim) ||
			    !(harmonic(pk[i].f, f0) || harmonic(pk[i].f, f0/2.0) ||
			      harmonic(pk[i].f, f0/3.0))) {
				continue;
			}
			r = (f0 > note) ? f0/note : note/f0;
			pl = (fabs(r - floor(r + 0.5)) < 0.02*r);
			for (j = 0; j < n; j++) {
				if (pk[j].f > pk[i].f*1.01) continue;
				all += pk[j].m;
				if (harmonic(pk[j].f, note)) e += pk[j].m;
			}
			x = e/all;
			if ((pl > best_pl) ||
			    ((pl == best_pl) && ((x > best_x + 0.05) ||
			      ((x > best_x - 0.05) && (pk[i].m > best_m))))) {
				best_pl = pl;
				best_x = x;
				best_m = pk[i].m;
				bestf = pk[i].f;
			}
		}
		if (bestf > 0.0) {
			return bestf/focus_ratio;
		}
	}
	return f0/focus_ratio;
}

static double
analyse(double *conf)
{
	int i, j, n, d;
	double mean = 0.0, rms = 0.0, total = 0.0, best = 0.0, best_f = 0.0;
	struct peak pk[MAX_PK];

	for (i = 0; i < N; i++) {
		re[i] = ring[(ring_pos+i) % N];
		mean += re[i];
	}
	mean /= N;
	for (i = 0; i < N; i++) {
		double v = re[i]-mean;
		rms += v*v;
		re[i] = v*win[i];
		im[i] = 0.0;
	}
	*conf = 0.0;
	level = sqrt(rms/N);
	if ((floor_rms <= 0.0) || (level < floor_rms)) {
		floor_rms = level;
	} else {
		floor_rms *= FLOOR_RISE;
	}
	if (windows < N/HOP) {
		windows++;
	} else if ((key_floor <= 0.0) || (level < key_floor)) {
		key_floor = level;
	}
	fft();
	for (i = 0; i < N/2; i++) {
		mag[i] = sqrt(re[i]*re[i] + im[i]*im[i]);
	}
	/* under the gate too: the quiet windows teach the noise lines */
	if (level < pitch_gate()) {
		return 0.0;
	}
	n = find_peaks(pk);
	for (i = 0; i < n; i++) {
		total += pk[i].m;
	}
	if (total <= 0.0) {
		return 0.0;
	}
	for (i = 0; i < n; i++) {
		for (d = 1; d <= MAX_DIV; d++) {
			try_f0(pk, n, pk[i].f/d, &best, &best_f);
		}
	}
	/* spacings: of the strongest few, the gap from each to the next
	 * one above it in frequency */
	for (i = 0; (i < n) && (i < MAX_GAP); i++) {
		double next = 0.0;
		for (j = 0; (j < n) && (j < MAX_GAP); j++) {
			if ((pk[j].f > pk[i].f*1.02) &&
			    ((next == 0.0) || (pk[j].f < next))) {
				next = pk[j].f;
			}
		}
		if (next > 0.0) {
			try_f0(pk, n, next-pk[i].f, &best, &best_f);
		}
	}
	if ((best == 0.0) && (pk[0].f >= MIN_F0) && (pk[0].f <= MAX_SINE_F0)) {
		/* no harmonic series: a lone strong partial (flute stop,
		 * tuning fork, top octave) is the fundamental itself */
		*conf = pk[0].m/total;
		best_f = pk[0].f;
		if (*conf < SINE_CONF) return 0.0;
	} else if (best > 0.0) {
		*conf = explained(pk, n, best_f)/total;
		/* the octave or twelfth below, if it scores nearly as well
		 * and explains clearly more: the winner was a strong upper
		 * partial (Krummhorn through a phone mic: the 3rd beats the
		 * weak 1st on score, but leaves the 5th, 7th.. unexplained)
		 * or one rank of a mixture (its 2' rather than the 4', the
		 * common divisor of its 2', 1 1/3' and 1' ranks) */
		double f = best_f, c = *conf;
		for (d = 2; d <= 3; d++) {
			double fd = best_f/d, sd, cd;
			if (fd < SUB_MIN_F0) break;
			sd = score(pk, n, &fd);
			cd = explained(pk, n, fd)/total;
			if ((sd >= SUB_SCORE*best) && (cd >= *conf + SUB_GAIN) &&
			    (cd > c)) {
				f = fd;
				c = cd;
			}
		}
		best_f = f;
		*conf = c;
		if (*conf < WEAK_CONF) return 0.0;
	} else {
		return 0.0;
	}
	return (focus_ratio > 0.0) ? focus(pk, n, best_f) : best_f;
}

int
pitch_feed(const short *s, int len, double *hz, double *conf)
{
	int i, ret = 0;
	for (i = 0; i < len; i++) {
		int k, c;
		double y;
		hb_x[hb_pos] = s[i];
		hb_pos = (hb_pos+1) % (2*HB_M+1);
		odd = !odd;
		if (odd) {
			continue;
		}
		/* hb_pos is now the oldest sample; the centre is HB_M on */
		c = (hb_pos + HB_M) % (2*HB_M+1);
		y = hb_c[0]*hb_x[c];
		for (k = 1; k <= HB_M; k += 2) {
			y += hb_c[k]*(hb_x[(c+k) % (2*HB_M+1)] +
				hb_x[(c+2*HB_M+1-k) % (2*HB_M+1)]);
		}
		ring[ring_pos] = (float)y;
		ring_pos = (ring_pos+1) % N;
		if (++fresh >= HOP) {
			double candidate;
			fresh = 0;
			candidate = analyse(conf);
			if ((candidate > 0.0) && (*conf >= MIN_CONF)) {
				/* Keep the original high-confidence path exactly as is. */
				*hz = candidate;
				weak_count = 0;
				weak_hz = weak_conf = 0.0;
			} else if ((candidate > 0.0) && (*conf >= WEAK_CONF)) {
				double cents = (weak_hz > 0.0) ?
					1200.0*log(candidate/weak_hz)/log(2.0) : 0.0;
				if ((weak_hz <= 0.0) ||
				    (fabs(cents) > WEAK_TOL_CENTS)) {
					weak_hz = candidate;
					weak_conf = *conf;
					weak_count = 1;
				} else {
					if (weak_count < WEAK_AGREE) {
						weak_hz = exp((weak_count*log(weak_hz) +
							log(candidate))/(weak_count+1));
						weak_conf = (weak_conf*weak_count + *conf) /
							(weak_count+1);
						weak_count++;
					} else {
						weak_hz = sqrt(weak_hz*candidate);
						weak_conf = (weak_conf + *conf)/2.0;
					}
				}
				if (weak_count >= WEAK_AGREE) {
					*hz = weak_hz;
					*conf = weak_conf;
				} else {
					*hz = 0.0;
				}
			} else {
				*hz = 0.0;
				weak_count = 0;
				weak_hz = weak_conf = 0.0;
			}
			if ((*hz <= 0.0) && (level < QUIET*floor_rms) &&
			    (level < MIN_RMS)) {
				for (k = 0; k < nbins; k++)
					nmag[k] += NOISE_AVG*(mag[k] - nmag[k]);
			}
			unheard = (*hz > 0.0) ? 0 : unheard + 1;
			if ((unheard >= FLOOR_UNHEARD) && (key_floor > 0.0) &&
			    (level > key_floor)) {
				/* rises only while no note is heard */
				key_floor *= FLOOR_RISE;
			}
			last_at = i+1;
			ret = 1;
		}
	}
	return ret;
}

double
pitch_level(void)
{
	return level;
}

double
pitch_floor(void)
{
	return key_floor;
}

double
pitch_gate(void)
{
	double g = FLOOR_GAIN*floor_rms;
	return (g > MIN_RMS) ? MIN_RMS : (g < LOW_RMS) ? LOW_RMS : g;
}

int
pitch_last_at(void)
{
	return last_at;
}
