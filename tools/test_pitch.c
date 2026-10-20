/* bench test for jni/pitch.c: synthetic tones in, detected note out.
 *
 *   test_pitch                   runs the synthetic cases, prints a score
 *   test_pitch [-t secs] file.wav [focus_ratio [expect_hz]]
 *               [--hints recording.txt]
 *                                replays a recording (16-bit PCM WAV, e.g.
 *                                one saved by the app's debug recording)
 *                                and prints every estimate over time,
 *                                in seconds and samples from its start
 *                                like the timeline in the app's .txt;
 *                                -t secs: analyse at the same samples
 *                                as the app's estimate at secs; --hints
 *                                applies the changing hints from the .txt
 *
 * Build with any C compiler, e.g.
 *   gcc -O2 -Ijni -o test_pitch tools/test_pitch.c jni/pitch.c -lm
 */
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "pitch.h"

#define SR 44100
#define MAX_H 32

static unsigned rng = 12345;
static double noise(void) {
	rng = rng*1103515245u + 12345u;
	return ((rng >> 8) & 0xffff) / 32768.0 - 1.0;
}

/* amplitude response of a 2nd-order high-pass at fc, like the low end of
 * a phone microphone; fc = 0 is flat */
static double hp_gain(double f, double fc) {
	if (fc <= 0.0) return 1.0;
	double r = (f/fc)*(f/fc);
	return r / sqrt(1.0 + r*r);
}

/* amps[h-1] for harmonics 1..nh, all with fundamental f0, plus an optional
 * second series (a mixture rank: amps2 on harmonics of f0*ratio2);
 * B = inharmonicity, hp = microphone high-pass corner */
struct tone {
	const double *a; int nh; double B;
	const double *a2; int nh2; double ratio2;
	double hp;
	double hum;		/* 50 Hz mains hum, relative level */
};

static short *synth(double f0, const struct tone *t, double level,
			double nlevel, int n)
{
	short *buf = malloc(n*sizeof(short));
	double ph[2*MAX_H] = { 0 }, fr[2*MAX_H], am[2*MAX_H];
	int i, h, np = 0;
	for (h = 1; h <= t->nh; h++) {
		double fh = h*f0*sqrt(1.0 + t->B*h*h);
		if (fh > SR/2.2) break;
		fr[np] = fh; am[np] = t->a[h-1]*hp_gain(fh, t->hp); np++;
	}
	for (h = 1; t->a2 && (h <= t->nh2); h++) {
		double fh = h*f0*t->ratio2;
		if (fh > SR/2.2) break;
		fr[np] = fh; am[np] = t->a2[h-1]*hp_gain(fh, t->hp); np++;
	}
	if (t->hum > 0.0) {
		fr[np] = 50.0; am[np] = t->hum; np++;
	}
	for (h = 0; h < np; h++) {
		ph[h] = 2.0*M_PI*h*0.37;	/* not all in phase */
	}
	for (i = 0; i < n; i++) {
		double v = 0.0;
		for (h = 0; h < np; h++) {
			v += am[h]*sin(ph[h]);
			ph[h] += 2.0*M_PI*fr[h]/SR;
		}
		v = level*v + nlevel*noise();
		if (v > 32767) v = 32767;
		if (v < -32767) v = -32767;
		buf[i] = (short)v;
	}
	return buf;
}

static double run(const short *buf, int n, double *conf)
{
	double hz = 0.0, c = 0.0, last = 0.0, lastc = 0.0;
	int i;
	for (i = 0; i + 1024 <= n; i += 1024) {
		if (pitch_feed(buf+i, 1024, &hz, &c)) {
			last = hz; lastc = c;
		}
	}
	*conf = lastc;
	return last;
}

static double detect(double f0, const struct tone *t, double level,
			double nlevel, double secs, double focus,
			double expect, double *conf)
{
	int n = (int)(secs*SR);
	short *buf = synth(f0, t, level, nlevel, n);
	pitch_init(SR);
	pitch_hint(focus, expect);
	double hz = run(buf, n, conf);
	free(buf);
	return hz;
}

static const char *names[12] = { "A","A#","B","C","C#","D","D#","E","F","F#","G","G#" };

static int semis_from_a4(double hz) { return (int)lround(12.0*log2(hz/440.0)); }

static void name(int s, char *out) {
	int k = s + 48;			/* A0 = 0 */
	sprintf(out, "%s%d", names[((k%12)+12)%12], (int)floor((k+9)/12.0));
}

struct hint_event {
	long sample;
	double focus, expect;
};

/* Reads `hint focus_ratio expect_hz` events from a DebugRecorder .txt. */
static struct hint_event *load_hints(const char *path, int *count)
{
	FILE *f = fopen(path, "r");
	struct hint_event *hints = NULL;
	char line[512];
	int n = 0, cap = 0;
	if (!f) {
		perror(path);
		return NULL;
	}
	while (fgets(line, sizeof line, f)) {
		double secs, focus, expect;
		long sample;
		if (sscanf(line, "%lf %ld hint %lf %lf", &secs, &sample,
				&focus, &expect) != 4) {
			continue;
		}
		if (n == cap) {
			int new_cap = cap ? cap*2 : 32;
			struct hint_event *p = realloc(hints,
					new_cap*sizeof(*hints));
			if (!p) {
				free(hints);
				fclose(f);
				return NULL;
			}
			hints = p;
			cap = new_cap;
		}
		hints[n].sample = sample;
		hints[n].focus = focus;
		hints[n].expect = expect;
		n++;
	}
	fclose(f);
	if (n == 0) {
		/* recordings before v18.3 often have none: the hints from
		 * the command line then hold throughout */
		fprintf(stderr, "%s: no pitch-hint events found, using the "
			"command line's\n", path);
		free(hints);
		*count = 0;
		return NULL;
	}
	*count = n;
	return hints;
}

static int replay_options(int argc, char **argv, int first,
		double *focus, double *expect, const char **hint_path)
{
	int have_focus = 0, have_expect = 0, i;
	for (i = first; i < argc; i++) {
		if (!strcmp(argv[i], "--hints")) {
			if (++i >= argc) {
				fprintf(stderr, "--hints needs a .txt path\n");
				return 0;
			}
			*hint_path = argv[i];
		} else {
			char *end;
			double v = strtod(argv[i], &end);
			if ((end == argv[i]) || (*end != '\0')) {
				fprintf(stderr, "unexpected replay argument: %s\n",
					argv[i]);
				return 0;
			}
			if (!have_focus) {
				*focus = v;
				have_focus = 1;
			} else if (!have_expect) {
				*expect = v;
				have_expect = 1;
			} else {
				fprintf(stderr, "too many numeric replay arguments\n");
				return 0;
			}
		}
	}
	return 1;
}

/* replays a WAV file through the detector, one line per estimate: the
 * time in seconds from the start of the file and the sample at which the
 * analysed window ended, as the timeline in a debug recording's .txt
 * (EventLog.java) has them.  align >= 0: the time of one of the app's
 * estimates in that timeline; the replay's analyses are then made at the
 * same samples as the app's (every PITCH_STEP samples), by feeding a few
 * samples of silence first, so that both lists line up */
static int replay(const char *path, double focus, double expect,
		double align, const char *hint_path)
{
	FILE *f = fopen(path, "rb");
	unsigned char hdr[12], ck[8];
	int rate = 0, chans = 0, bits = 0, fmt = 0;
	long data_len = -1;
	int nhints = 0, ihint = 0;
	struct hint_event *hints = NULL;

	if (!f) { perror(path); return 1; }
	if ((fread(hdr, 1, 12, f) != 12) || memcmp(hdr, "RIFF", 4) ||
	    memcmp(hdr+8, "WAVE", 4)) {
		fprintf(stderr, "%s: not a RIFF/WAVE file\n", path);
		return 1;
	}
	while (fread(ck, 1, 8, f) == 8) {
		long len = ck[4] | (ck[5]<<8) | (ck[6]<<16) | ((long)ck[7]<<24);
		if (!memcmp(ck, "fmt ", 4)) {
			unsigned char b[16];
			if ((len < 16) || (fread(b, 1, 16, f) != 16)) break;
			fmt = b[0] | (b[1]<<8);
			chans = b[2] | (b[3]<<8);
			rate = b[4] | (b[5]<<8) | (b[6]<<16) | (b[7]<<24);
			bits = b[14] | (b[15]<<8);
			fseek(f, len - 16 + (len & 1), SEEK_CUR);
		} else if (!memcmp(ck, "data", 4)) {
			data_len = len;
			break;
		} else {
			fseek(f, len + (len & 1), SEEK_CUR);
		}
	}
	if ((data_len < 0) || (fmt != 1) || (bits != 16) || (chans < 1)) {
		fprintf(stderr, "%s: need 16-bit PCM (fmt %d, %d bits, %d ch)\n",
			path, fmt, bits, chans);
		return 1;
	}
	long frames = data_len / (2*chans), i;
	short *buf = malloc(frames*sizeof(short));
	for (i = 0; i < frames; i++) {
		unsigned char b[2];
		if (fread(b, 1, 2, f) != 2) break;
		buf[i] = (short)(b[0] | (b[1]<<8));	/* first channel */
		fseek(f, 2*(chans-1), SEEK_CUR);
	}
	frames = i;
	fclose(f);

	if (hint_path) {
		nhints = -1;
		hints = load_hints(hint_path, &nhints);
		if (!hints && (nhints != 0)) {
			free(buf);
			return 1;
		}
	}
	printf("# %s: %d Hz, %ld samples (%.2f s), focus %.3f, expect %.2f Hz\n",
		path, rate, frames, (double)frames/rate, focus, expect);
	if (hints) {
		printf("# using %d pitch-hint changes from %s\n", nhints,
				hint_path);
		while ((ihint < nhints) && (hints[ihint].sample <= 0)) {
			focus = hints[ihint].focus;
			expect = hints[ihint].expect;
			ihint++;
		}
	}
	printf("#     time   sample        Hz  note    cents  conf\n");
	pitch_init(rate);
	pitch_hint(focus, expect);
	if (align >= 0.0) {
		/* analyses at samples = align (mod PITCH_STEP) */
		static const short zero[PITCH_STEP];
		long a = lround(align*rate) % PITCH_STEP;
		long pad = (PITCH_STEP - a) % PITCH_STEP;
		double hz, c;
		pitch_feed(zero, (int)pad, &hz, &c);	/* < PITCH_STEP: no
							 * estimate */
		printf("# aligned with the app's estimate at %.5f s: %ld "
			"samples of silence first\n", align, pad);
	}
	for (i = 0; i + 1024 <= frames; i += 1024) {
		double hz, c;
		while (hints && (ihint < nhints) &&
		       (hints[ihint].sample <= i)) {
			focus = hints[ihint].focus;
			expect = hints[ihint].expect;
			ihint++;
			pitch_hint(focus, expect);
		}
		if (pitch_feed(buf+i, 1024, &hz, &c)) {
			long at = i + pitch_last_at();
			double t = (double)at/rate;
			if (hz > 0.0) {
				char nm[8];
				int s = semis_from_a4(hz);
				name(s, nm);
				printf("%9.5f %8ld  est   %8.2f  %-5s %+6.1f  "
					"%.2f\n", t, at, hz, nm,
					1200.0*log2(hz/440.0) - 100.0*s, c);
			} else {
				printf("%9.5f %8ld  est          -  -              "
					"%.2f\n", t, at, c);
			}
		}
	}
	free(hints);
	free(buf);
	return 0;
}

int main(int argc, char **argv)
{
	double focus = 0.0, expect = 0.0;
	const char *hint_path = NULL;
	if ((argc > 2) && !strcmp(argv[1], "-t")) {
		double align = atof(argv[2]);
		if (argc < 4) {
			fprintf(stderr, "usage: test_pitch [-t secs] file.wav "
				"[focus_ratio [expect_hz]] [--hints file.txt]\n");
			return 1;
		}
		if (!replay_options(argc, argv, 4, &focus, &expect,
				&hint_path)) return 1;
		return replay(argv[3], focus, expect, align, hint_path);
	}
	if (argc > 1) {
		if (!replay_options(argc, argv, 2, &focus, &expect,
				&hint_path)) return 1;
		return replay(argv[1], focus, expect, -1.0, hint_path);
	}

	static const double full[8]   = { 1, .6, .4, .3, .2, .15, .1, .08 };
	static const double nofund[8] = { 0, 1, .8, .6, .4, .3, .2, .1 };
	static const double weak1[8]  = { .15, 1, .5, .4, .3, .2, .1, .05 };
	static const double pure[1]   = { 1 };
	/* organ pipes.  stopped pipes (Gedackt, Subbass) have only the odd
	 * partials; a Quintadena's twelfth (3rd) is louder than its
	 * fundamental */
	static const double gedackt[9] = { 1, 0, .25, 0, .08, 0, .03, 0, .015 };
	static const double gedackt_w[9] = { .2, 0, 1, 0, .5, 0, .25, 0, .1 };
	static const double quintad[9] = { .6, .02, 1, .02, .35, 0, .12, 0, .05 };
	static const double principal[10] = { 1, 1.3, .6, .45, .3, .2, .15, .1, .08, .05 };
	static const double prin_weak[10] = { .1, 1, .7, .5, .35, .25, .15, .1, .08, .05 };
	static const double flute[4] = { 1, .15, .05, .02 };
	static double trumpet[24], krumm[16];
	static const double mixrank[4] = { 1, .4, .2, .1 };
	int i;
	for (i = 0; i < 24; i++) {	/* rises to the 5th, then slowly falls */
		double h = (i+1)/5.0;
		trumpet[i] = h*exp(1.0-h);
	}
	for (i = 0; i < 16; i++) {	/* cylindrical reed: odd partials */
		krumm[i] = (i % 2) ? .04 : 1.0/(1.0 + 0.15*i);
	}
	trumpet[0] = .25;		/* weak fundamental */

	struct {
		const char *what; struct tone t; double lvl, nl;
		int lo, hi;		/* semitones from A4, step 3 */
		double focus;		/* pitch_hint() focus ratio */
		double sound;		/* sounding pitch / note (4' = 2) */
		double exp_off;		/* expected note off by semitones,
					 * 99 = no note known */
		int pc_only;		/* any octave counts */
	} cases[] = {
		{ "harmonic",          { full,   8, 0.0 },  3000, 30,  -48, 39 },
		{ "no fundamental",    { nofund, 8, 0.0 },  3000, 30,  -48, 24 },
		{ "weak fundamental",  { weak1,  8, 0.0 },  3000, 30,  -48, 39 },
		{ "piano B=4e-4",      { full,   8, 4e-4 }, 3000, 30,  -48, 39 },
		{ "sine",              { pure,   1, 0.0 },  3000, 30,  -48, 39 },
		{ "noisy (SNR ~10dB)", { full,   8, 0.0 },  1500, 500, -48, 39 },
		/* organ: manual range C2..C7 (65 Hz..2 kHz) at 8' */
		{ "Gedackt (odd)",     { gedackt, 9, 0.0 }, 3000, 30,  -33, 27 },
		{ "Gedackt weak fund", { gedackt_w, 9, 0.0 }, 3000, 30, -33, 15 },
		{ "Quintadena",        { quintad, 9, 0.0 }, 3000, 30,  -33, 15 },
		{ "Principal 2nd>1st", { principal, 10, 0.0 }, 3000, 30, -33, 27 },
		{ "Principal weak 1st",{ prin_weak, 10, 0.0 }, 3000, 30, -33, 15 },
		{ "Flute (near sine)", { flute, 4, 0.0 },   3000, 30,  -33, 39 },
		{ "Trumpet reed",      { trumpet, 24, 0.0 }, 3000, 30, -33, 15 },
		{ "Krummhorn (odd)",   { krumm, 16, 0.0 },  3000, 30,  -33, 15 },
		/* pedal: Subbass 16' and Principal 32'/16' sounding C0..C3
		 * through a phone mic that loses the bottom (80 Hz HP) */
		{ "Subbass 16' +mic",  { gedackt, 9, 0.0, 0,0,0, 80 }, 3000, 30, -45, -21 },
		{ "Principal 32' +mic",{ principal, 10, 0.0, 0,0,0, 80 }, 3000, 30, -57, -33 },
		{ "Posaune 16' +mic",  { trumpet, 24, 0.0, 0,0,0, 80 }, 3000, 30, -45, -21 },
		/* a church is not a lab */
		{ "Gedackt noisy",     { gedackt, 9, 0.0 }, 1500, 500, -33, 27 },
		{ "Trumpet noisy",     { trumpet, 24, 0.0 }, 1500, 500, -33, 15 },
		{ "Principal +50Hz hum", { principal, 10, 0.0, 0,0,0, 0, .3 }, 3000, 30, -33, 27 },
		{ "Subbass +mic+hum",  { gedackt, 9, 0.0, 0,0,0, 80, .1 }, 3000, 30, -45, -21 },
		/* mixture: ranks 2' + 1 1/3' (4th and 6th partial of the key);
		 * the sound's own fundamental is the 4' octave, so only the
		 * pitch class is asked for */
		{ "Mixture 2'+1 1/3'", { mixrank, 4, 0.0, mixrank, 4, 1.5 }, 3000, 30, -33, 3, 0, 4, 0, 1 },
		/* focused partial: the user tapped a row (focus ratio), the
		 * tuner's note is the key; the expected note is off a bit as
		 * when following a melody */
		{ "focus 4' pipe=2",   { principal, 10, 0.0 }, 3000, 30, -33, 21, 2.0, 2, 2 },
		{ "focus 4', no note", { principal, 10, 0.0 }, 3000, 30, -33, 21, 2.0, 2, 99 },
		{ "focus 2 2/3' =3",  { full, 8, 0.0 },    3000, 30,  -33, 15, 3.0, 3, -3 },
		{ "focus 8' on 2nd",   { full, 8, 0.0 },    3000, 30,  -33, 27, 2.0, 1, -1 },
		{ "focus 16' =0.5",    { gedackt, 9, 0.0, 0,0,0, 80 }, 3000, 30, -33, 3, 0.5, 0.5, 1 },
		{ "focus mixture =4",  { mixrank, 4, 0.0, mixrank, 4, 1.5 }, 3000, 30, -33, 3, 4.0, 4, -2 },
	};
	int nc = sizeof cases / sizeof cases[0], c, s, ok = 0, tot = 0;
	char want[8], got[8];

	for (c = 0; c < nc; c++) {
		int good = 0, cnt = 0;
		double cmin = 1.0;
		double sound = (cases[c].sound > 0.0) ? cases[c].sound : 1.0;
		printf("%-19s", cases[c].what);
		for (s = cases[c].lo; s <= cases[c].hi; s += 3) {
			double f0 = 440.0*pow(2.0, s/12.0), conf;
			double expect = ((cases[c].focus > 0.0) &&
					  (cases[c].exp_off != 99)) ?
				f0*pow(2.0, cases[c].exp_off/12.0) : 0.0;
			struct tone t = cases[c].t;
			/* the note is f0; the pipe sounds at f0*sound */
			double hz = detect(f0*sound, &t, cases[c].lvl,
					cases[c].nl, 1.0, cases[c].focus,
					expect, &conf);
			int g = (hz > 0) ? semis_from_a4(hz) : -999;
			int hit = cases[c].pc_only ?
				((g != -999) && ((((g-s)%12)+12)%12 == 0)) :
				(g == s);
			cnt++;
			if (conf < cmin) cmin = conf;
			if (hit) {
				good++;
			} else {
				name(s, want);
				if (g == -999) sprintf(got, "none");
				else name(g, got);
				printf(" [%s->%s]", want, got);
			}
		}
		printf("  %d/%d  (conf >= %.2f)\n", good, cnt, cmin);
		ok += good; tot += cnt;
	}
	double conf;
	struct tone tf = { full, 8, 0.0 };
	double hz = detect(440, &tf, 0, 30, 1.0, 0, 0, &conf);
	printf("silence+hiss       -> %s (conf %.2f)\n", hz > 0 ? "DETECTED (bad)" : "none (good)", conf);
	hz = detect(440, &tf, 0, 3000, 1.0, 0, 0, &conf);
	printf("loud white noise   -> %s (conf %.2f)\n", hz > 0 ? "DETECTED (bad)" : "none (good)", conf);
	hz = detect(440, &tf, 0, 3000, 1.0, 2.0, 220, &conf);
	printf("noise, focus set   -> %s (conf %.2f)\n", hz > 0 ? "DETECTED (bad)" : "none (good)", conf);

	/* cost: analyses per second of CPU */
	{
		int n = SR*10, i;
		struct tone tr = { trumpet, 24, 0.0 };
		short *buf = synth(110.0, &tr, 3000, 30, n);
		pitch_init(SR);
		pitch_hint(0.0, 0.0);
		struct timespec t0, t1;
		clock_gettime(CLOCK_MONOTONIC, &t0);
		double h2, c2;
		for (i = 0; i + 1024 <= n; i += 1024) pitch_feed(buf+i, 1024, &h2, &c2);
		clock_gettime(CLOCK_MONOTONIC, &t1);
		double ms = (t1.tv_sec-t0.tv_sec)*1e3 + (t1.tv_nsec-t0.tv_nsec)/1e6;
		printf("cost: %.1f ms CPU per 10 s of audio (%.2f%% of one core)\n", ms, ms/100.0);
		free(buf);
	}
	printf("TOTAL %d/%d\n", ok, tot);
	return 0;
}
