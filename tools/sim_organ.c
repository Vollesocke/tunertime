/* sim_organ.c - realistic organ signals, and a bench that runs them through
 * jni/pitch.c (auto note, length mode) and jni/strobe.c (drift of the
 * focused partial: mixture mode, length mode) the way the app does.
 *
 *   sim_organ                 all scenarios, one line each, and a summary
 *   sim_organ -v [name]       ... with every note's details
 *   sim_organ -w dir [name]   also writes each scenario as dir/<name>.wav
 *                             (16-bit mono, replay with test_pitch file.wav)
 *   sim_organ -a N            N estimates in a row move the note (4)
 *   sim_organ -f old|new      follow mode with the user's corrections:
 *                             the note shown should be the key's (8')
 *                             note; when it is not 1.5 s into the key,
 *                             the user corrects it by hand.  old: the
 *                             correction's interval is added to every
 *                             later detection (follow_shift, v18.1);
 *                             new: later detections are taken in the
 *                             octave nearest the note shown, within a
 *                             fourth (TunerTime.on_pitch, continuity)
 *   name                      only scenarios whose name contains it
 *
 * Build (plain C, any compiler):
 *   gcc -O2 -Ijni -Itools/hostjni -o sim_organ tools/sim_organ.c \
 *	jni/pitch.c jni/strobe.c -lm
 *
 * The signal, per pipe: harmonic partials with the spectrum of its kind
 * (open/stopped flue, flute, string, trumpet, cylindrical reed), each with
 * its own attack (big pipes speak slowly, high partials first), an octave
 * overshoot, noise burst ("chiff") and pitch glide at the speech of flues,
 * a fast buzzy onset and pitch slide on reeds, a release, slow random pitch
 * wander (wind), and the tremulant's AM and FM.  Around it: the room (a
 * feedback delay network with RT60 and high damping, so the previous note
 * still rings when the next starts), blower hum (50 Hz and harmonics),
 * rumble and wind hiss, background speech (glottal pulses through vowel
 * formants, in syllables and phrases) and footsteps.  Then the phone: a
 * high-pass microphone (2nd or 4th order, 100-200 Hz), optional AGC with
 * attack/release, gain and hard clipping, 16 bits.
 *
 * What is measured, per scenario and per key held:
 *  - detection, emulating TunerTime.on_pitch (DET_AGREE estimates in a row
 *    move the shown note): latency from the key to the right note, keys
 *    never shown, the shown note moving to a wrong one (octave or other)
 *    during the key or its tail, wrong single estimates in the sustain,
 *    and in noise-only scenarios any note shown at all (false trigger);
 *  - length mode from the detector (pipelength: median over 1 s, stable
 *    when MAD < 1 c and >= 5 estimates): time to stable, share of the
 *    sustain that is stable, error of the reading, worst stable error;
 *  - the strobe drift of the row of the key's pipe (strobe.c, as fed to
 *    pipelength.drift_reading: 1 s window, stable when the halves agree
 *    within 1 c) and the mixture measurement (mixture.java: 2 s of usable
 *    drift, 3 s on a row below 8', started MSTART after the key): error against the true mean
 *    pitch of the focused partial over the time it took.
 */
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "interface.h"
#include "pitch.h"

/* ---- stand-ins for jni/interface.c ---- */
int width = 400, height = 700, sample_rate = 44100;
short snd_buf[SND_BUFSAMPS];
int snd_buflen;
void filled_rect(jint *b, int x1, int y1, int x2, int y2, unsigned c) {}
void hline(jint *b, int x1, int y, int x2, unsigned c) {}
void vline(jint *b, int x, int y1, int y2, unsigned c) {}
void xor_hline(jint *b, int x1, int x2, int y) {}
void xor_vline(jint *b, int x, int y1, int y2) {}
unsigned batlow(double t) { return 0; }
void seven_seg(jint *b, int x, int y, char c, unsigned color) {}
void println(const char *fmt, ...) {}
double cents_to_freq(jobject t, jint cents, jint mult) {
	return 440.0*pow(2.0, (cents-48000)/12000.0)*mult;
}
void set_labels(jobject t, char *l[MAX_STROBES]) {}
void set_pianoname(jobject t, char *p) {}
void focus_drift(jobject t, double f, double c, double s, double b,
		double a) {}
static jint bitmap[400*700];

/* ---- app constants emulated (Java side) ---- */
#define BUFLEN		1024	/* samples per audio buffer */
static int det_agree = 4;	/* TunerTime.DET_AGREE; -a N */
#define LEN_WINDOW	1.0	/* pipelength.WINDOW */
#define LEN_MIN_EST	5	/* pipelength.MIN_EST */
#define STABLE_CENTS	1.0	/* pipelength.STABLE_CENTS */
#define DRIFT_MIN_SECS	0.3	/* pipelength.DRIFT_MIN_SECS */
#define MEASURE_SECONDS	2.0	/* mixture.MEASURE_SECONDS */
#ifndef MEASURE_SECONDS_LOW
#define MEASURE_SECONDS_LOW 3.0	/* mixture.MEASURE_SECONDS_LOW, rows
				 * below 8' (focus ratio < 1) */
#endif
#define MSTART		0.5	/* measure pressed this long after the key */
/* how long 16' and 32' keys are held: long enough for MSTART, the
 * settling after the onset and MEASURE_SECONDS_LOW, as the tuner holds
 * the key until the measurement is done */
#ifndef PEDAL_HOLD
#define PEDAL_HOLD	4.5
#endif
#ifndef HOLD_32
#define HOLD_32		5.0
#endif
#define BEAT_SWING	0.7	/* strobe_drift_amp(): (hi-lo)/(hi+lo) over a
				 * measurement that means a second pipe of
				 * about the same level */
#define RECALC_BEFORE	0.4	/* target set this long before the key */
/* follow mode with corrections (-f): TunerTime constants */
#define FOLLOW_NONE	0
#define FOLLOW_OLD	1	/* follow_shift, v18.1 */
#define FOLLOW_NEW	2	/* continuity */
static int follow_mode = FOLLOW_NONE;
#define CORR_RECENT	4.0	/* TunerTime.CORR_RECENT */
#define NEAR_SILENCE	10.0	/* TunerTime.NEAR_SILENCE */
#define NEAR_SEMIS	5	/* TunerTime.NEAR_SEMIS */
#define MIX_SEMIS	2	/* TunerTime.MIX_SEMIS */
#define CORR_AFTER	1.5	/* the user corrects a wrong note this long
				 * into the key */
/* organ rows, in multiples of the 32' pitch: 32' 16' 8' 4' 2 2/3' 2' 1 1/3' 1' */
static const int rows[] = { 1, 2, 4, 8, 12, 16, 24, 32 };
#define NROWS 8

/* ---- random numbers ---- */
static unsigned long long rs;
static double urand(void) {
	rs ^= rs >> 12; rs ^= rs << 25; rs ^= rs >> 27;
	return ((rs * 2685821657736338717ULL) >> 11) * (1.0/9007199254740992.0);
}
static double grand(void) {
	double u = urand(), v = urand();
	return sqrt(-2.0*log(u + 1e-300)) * cos(2.0*M_PI*v);
}

/* ---- filters ---- */
struct bq { double b0, b1, b2, a1, a2, z1, z2; };
enum { LP, HP, BP };
static void bq_set(struct bq *q, int type, double f, double Q, double sr) {
	double w = 2.0*M_PI*f/sr, c = cos(w), a = sin(w)/(2.0*Q), a0 = 1.0+a;
	if (type == LP) {
		q->b0 = (1.0-c)/2.0; q->b1 = 1.0-c; q->b2 = (1.0-c)/2.0;
	} else if (type == HP) {
		q->b0 = (1.0+c)/2.0; q->b1 = -(1.0+c); q->b2 = (1.0+c)/2.0;
	} else {		/* band-pass, 0 dB peak */
		q->b0 = a; q->b1 = 0.0; q->b2 = -a;
	}
	q->a1 = -2.0*c; q->a2 = 1.0-a;
	q->b0 /= a0; q->b1 /= a0; q->b2 /= a0; q->a1 /= a0; q->a2 /= a0;
	q->z1 = q->z2 = 0.0;
}
static double bq_run(struct bq *q, double x) {
	double y = q->b0*x + q->z1;
	q->z1 = q->b1*x - q->a1*y + q->z2;
	q->z2 = q->b2*x - q->a2*y;
	return y;
}

/* ---- the scene ---- */
enum { OPEN, STOPPED, FLUTE, STRING, REED, KRUMM };
static const char *kind_name[] = { "open", "stopped", "flute", "string",
					"reed", "krumm" };
#define MAXH	40
#define MAXK	16
#define MAXP	48

struct pipe {
	int key;		/* keys[] index */
	double ratio;		/* nominal pitch / the key's 8' pitch */
	double cents;		/* mistuning */
	int kind;
	double level;		/* amplitude, 16-bit units before the room */
	double chiff;		/* speech transient 0..1 (flues) */
	/* rendering state */
	double f, on, off, tau1, taur;
	int nh;
	double ha[MAXH], tau[MAXH], over[MAXH];
	double zr[MAXH], zi[MAXH];	/* partial phasors */
	double q[MAXH], qd[MAXH];	/* exp(-dt/tau[h]), per-sample step */
	double ov, ovd;			/* exp(-dt/(0.7*tau1)), step */
	double wob;
	struct bq nbp;
	double *cyc;		/* fundamental cycles at each buffer start,
				 * without the speech transient's glide (the
				 * truth: the steady pitch, with wind and
				 * tremulant) */
};

struct key {
	int semi;		/* 8' note, semitones from A4 */
	double on, off;
	double focus;		/* focused partial (ratio to 8'), 0 = the
				 * truth pipe's own */
	int truth;		/* pipe whose pitch is measured */
	int pc_only;		/* detection: any octave counts */
};

struct scen {
	const char *name;
	int sr;
	double secs;
	double rt60, wet;	/* room; wet 0.3 = reverberant field -10 dB */
	double mic_hp;		/* phone mic high-pass corner, 0 = flat */
	int mic_ord;		/* 2 or 4 */
	int agc;
	double gain;		/* before AGC and clipping */
	double hum, rumble, wind;	/* blower: 50 Hz, < 100 Hz, hiss */
	double trem_rate, trem_am, trem_fm;	/* Hz, depth, cents */
	double wobble;		/* wind: slow pitch wander, cents rms */
	double speech, speech_on, speech_off;
	double steps, steps_on, steps_off;
	int hint;		/* pitch_hint(focus, key) like the app with
				 * a row focused */
	int mixmode;		/* mixture mode: the shown key folds octaves
				 * and moves by MIX_SEMIS at most; bigger
				 * steps between keys are made by hand */
	double mstart;
	int nk, np;
	struct key k[MAXK];
	struct pipe p[MAXP];
	unsigned seed;
};

static double key_hz(int semi) { return 440.0*pow(2.0, semi/12.0); }

/* the spectrum of a kind, before the mic */
static double spectrum(int kind, int h) {
	double x;
	switch (kind) {
	case OPEN: {		/* Principal: 2nd close to the 1st */
		static const double a[] = { 1, .8, .45, .35, .22, .18, .12,
			.1, .07, .05, .04, .03, .02, .015, .01 };
		return (h <= 15) ? a[h-1] : 0.0;
	}
	case STOPPED:		/* Gedackt: odd partials, faint evens */
		if (h % 2) return (h == 1) ? 1.0 : 0.6*pow(h, -1.8);
		return (h <= 6) ? 0.025 : 0.0;
	case FLUTE: {
		static const double a[] = { 1, .2, .08, .03, .02 };
		return (h <= 5) ? a[h-1] : 0.0;
	}
	case STRING:		/* Gamba: many slowly falling partials */
		return (h <= 24) ? pow(h, -0.6) : 0.0;
	case REED:		/* Trompete: weak 1st, peak at the 5th */
		x = h/5.0;
		return (h == 1) ? 0.25 : x*exp(1.0-x);
	case KRUMM:		/* cylindrical: odd strong, even weak */
		return (h % 2) ? 1.0/(1.0+0.15*h) : 0.05/(1.0+0.15*h);
	}
	return 0.0;
}

static void pipe_setup(struct scen *S, struct pipe *p) {
	struct key *k = &S->k[p->key];
	int h;
	p->f = key_hz(k->semi)*p->ratio*pow(2.0, p->cents/1200.0);
	p->on = k->on;
	p->off = k->off;
	if ((p->kind == REED) || (p->kind == KRUMM)) {
		p->tau1 = 3.0/p->f + 0.006;
		p->taur = 0.012;
	} else {
		/* big flue pipes speak slowly: 16' C ~ 0.3 s */
		p->tau1 = 10.0/p->f + 0.01;
		if (p->tau1 > 0.35) p->tau1 = 0.35;
		p->taur = 3.0/p->f + 0.02;
	}
	p->nh = 0;
	for (h = 1; h <= MAXH; h++) {
		double fh = h*p->f;
		if (fh > 0.45*S->sr) break;
		/* +-20% per partial: no two pipes are voiced alike */
		p->ha[h-1] = spectrum(p->kind, h) * (0.8 + 0.4*urand());
		{
			double ph = 2.0*M_PI*urand();
			p->zr[h-1] = cos(ph);
			p->zi[h-1] = sin(ph);
		}
		if ((p->kind == REED) || (p->kind == KRUMM)) {
			p->tau[h-1] = p->tau1;
			p->over[h-1] = 0.0;
		} else {
			p->tau[h-1] = p->tau1/sqrt(h);
			/* chiff: the octave and twelfth speak first and
			 * overshoot */
			p->over[h-1] = ((h == 2) || (h == 3)) ?
					2.0*p->chiff : 0.0;
		}
		p->nh = h;
	}
	for (h = 0; h < p->nh; h++) {
		p->q[h] = 1.0;
		p->qd[h] = exp(-1.0/(p->tau[h]*S->sr));
	}
	p->ov = 1.0;
	p->ovd = exp(-1.0/(0.7*p->tau1*S->sr));
	p->wob = 0.0;
	/* chiff noise around the 4th-8th partial */
	{
		double fc = p->f*(4.0 + 4.0*urand());
		if (fc > 0.4*S->sr) fc = 0.4*S->sr;
		bq_set(&p->nbp, BP, fc, 4.0, S->sr);
	}
}

/* ---- room: 8-line feedback delay network ---- */
#define FDN 8
struct fdn {
	float *buf[FDN];
	int len[FDN], pos[FDN];
	double g[FDN], lp[FDN], damp;
	double norm;
};
static void fdn_init(struct fdn *r, double rt60, int sr) {
	static const int base[FDN] = { 1433, 1601, 1867, 2053, 2251, 2399,
					2617, 2797 };
	int i;
	for (i = 0; i < FDN; i++) {
		r->len[i] = base[i]*sr/44100;
		r->buf[i] = calloc(r->len[i], sizeof(float));
		r->pos[i] = 0;
		r->g[i] = pow(10.0, -3.0*r->len[i]/(sr*rt60));
		r->lp[i] = 0.0;
	}
	r->damp = 0.25;		/* highs die sooner */
	r->norm = 1.0;
}
static double fdn_run(struct fdn *r, double x) {
	double o[FDN], s = 0.0, y = 0.0;
	int i;
	for (i = 0; i < FDN; i++) {
		o[i] = r->buf[i][r->pos[i]];
		s += o[i];
		y += o[i];
	}
	s *= 2.0/FDN;
	for (i = 0; i < FDN; i++) {
		double v = (o[i] - s)*r->g[i];	/* Householder */
		r->lp[i] += (1.0 - r->damp)*(v - r->lp[i]);
		r->buf[i][r->pos[i]] = (float)(r->lp[i] + x);
		r->pos[i] = (r->pos[i]+1) % r->len[i];
	}
	return y*r->norm;
}
static void fdn_free(struct fdn *r) {
	int i;
	for (i = 0; i < FDN; i++) free(r->buf[i]);
}
/* scales the impulse response to unit energy */
static void fdn_normalise(struct fdn *r, double rt60, int sr) {
	double e = 0.0;
	int i, n = (int)(1.5*rt60*sr);
	for (i = 0; i < n; i++) {
		double y = fdn_run(r, (i == 0) ? 1.0 : 0.0);
		e += y*y;
	}
	fdn_free(r);
	fdn_init(r, rt60, sr);
	r->norm = 1.0/sqrt(e);
}

/* ---- background: speech and footsteps ---- */
struct speech {
	double ph, f0, base, t_syl, t_end, env, envt, glide;
	int on;
	struct bq gl, f[3], fric;
	double fa[3], fric_lv;
	int sr;
};
static void speech_syllable(struct speech *s, double t) {
	static const double v[5][3] = { {730,1090,2440}, {270,2290,3010},
		{300,870,2240}, {530,1840,2480}, {570,840,2410} };
	int k = (int)(urand()*5), i;
	if (urand() < 0.15) {	/* phrase break */
		s->t_syl = t + 0.4 + 0.8*urand();
		s->on = 0;
		s->base = 100.0 + 60.0*urand();
		return;
	}
	for (i = 0; i < 3; i++) {
		bq_set(&s->f[i], BP, v[k][i]*(0.9+0.2*urand()), 6.0, s->sr);
	}
	s->fric_lv = (urand() < 0.3) ? 0.3 : 0.0;
	s->on = 1;
	s->envt = t;
	s->t_end = t + 0.12 + 0.18*urand();
	s->t_syl = s->t_end + 0.03 + 0.12*urand();
	s->f0 = s->base*(0.9 + 0.25*urand());
	/* intonation: -15..+10 semitones/s through the syllable, a third of
	 * them nearly level (sustained vowels) */
	s->glide = (urand() < 0.33) ? 2.0*(urand() - 0.5)
				   : -15.0 + 25.0*urand();
}
static void speech_init(struct speech *s, int sr) {
	memset(s, 0, sizeof *s);
	s->sr = sr;
	s->base = 120.0;
	bq_set(&s->gl, LP, 700.0, 0.7, sr);
	bq_set(&s->fric, HP, 3500.0, 0.7, sr);
	s->fa[0] = 1.0; s->fa[1] = 0.5; s->fa[2] = 0.25;
}
static double speech_run(struct speech *s, double t) {
	double x = 0.0, y = 0.0, e;
	int i;
	if (t >= s->t_syl) speech_syllable(s, t);
	if (!s->on) return 0.0;
	s->f0 *= pow(2.0, s->glide/(12.0*s->sr));
	s->ph += s->f0/s->sr;
	if (s->ph >= 1.0) {
		s->ph -= 1.0 - 0.01*grand();	/* jitter */
		x = 30.0;
	}
	x = bq_run(&s->gl, x + 0.02*grand());
	for (i = 0; i < 3; i++) y += s->fa[i]*bq_run(&s->f[i], x);
	y += s->fric_lv*bq_run(&s->fric, grand());
	/* syllable envelope: 20 ms rise and fall */
	e = fmin(1.0, (t - s->envt)/0.02) * fmin(1.0, (s->t_end - t)/0.02);
	return (e > 0.0) ? e*y : 0.0;
}

struct steps {
	double next, t0;
	struct bq lp, hp;
	int sr;
};
static double steps_run(struct steps *s, double t) {
	double d;
	if (t >= s->next) {
		s->t0 = t;
		s->next = t + 0.5 + 0.12*urand();
	}
	d = t - s->t0;
	if (d > 0.3) return 0.0;
	return 4.0*bq_run(&s->lp, grand())*exp(-d/0.04) +
		0.5*bq_run(&s->hp, grand())*exp(-d/0.004);
}

/* ---- rendering ---- */
static short *render(struct scen *S, int *n_out) {
	int sr = S->sr, n = (int)(S->secs*sr), nbuf = n/BUFLEN + 1;
	short *out = malloc(n*sizeof(short));
	struct fdn room;
	struct bq mic[2], hiss, rumb;
	struct speech sp;
	struct steps st;
	double agc_env = 0.0, agc_gain = 1.0;
	double wob_a = exp(-2.0*M_PI*0.7/sr), wob_b = sqrt(1.0 - wob_a*wob_a);
	int i, j, h;

	rs = 0x9E3779B97F4A7C15ULL ^ (S->seed*0x100000001B3ULL + 1);
	for (j = 0; j < S->np; j++) {
		pipe_setup(S, &S->p[j]);
		S->p[j].cyc = calloc(nbuf+1, sizeof(double));
	}
	if (S->rt60 > 0.0) {
		fdn_init(&room, S->rt60, sr);
		fdn_normalise(&room, S->rt60, sr);
	}
	if (S->mic_ord >= 4) {		/* 4th-order Butterworth */
		bq_set(&mic[0], HP, S->mic_hp, 0.5412, sr);
		bq_set(&mic[1], HP, S->mic_hp, 1.3066, sr);
	} else {
		bq_set(&mic[0], HP, (S->mic_hp > 0.0) ? S->mic_hp : 10.0,
			0.7071, sr);
	}
	bq_set(&hiss, LP, 5000.0, 0.7, sr);
	bq_set(&rumb, LP, 90.0, 0.7, sr);
	speech_init(&sp, sr);
	memset(&st, 0, sizeof st);
	bq_set(&st.lp, LP, 140.0, 1.0, sr);
	bq_set(&st.hp, HP, 2500.0, 0.7, sr);

	for (i = 0; i < n; i++) {
		double t = (double)i/sr, dry = 0.0, x;
		double tr = 2.0*M_PI*S->trem_rate*t;
		double am = 1.0 + S->trem_am*sin(tr);
		double fm = S->trem_fm*sin(tr + 1.2);

		for (j = 0; j < S->np; j++) {
			struct pipe *p = &S->p[j];
			double dt = t - p->on, er = 1.0, dev, mult, v = 0.0;
			if ((i % BUFLEN) == 0) p->cyc[i/BUFLEN] = p->cyc[nbuf];
			if (dt < 0.0) continue;
			if (t > p->off) {
				er = exp(-(t - p->off)/p->taur);
				if (er < 1e-4) continue;
			}
			/* pitch: wind wander, tremulant, speech glide */
			p->wob = wob_a*p->wob + wob_b*grand();
			dev = S->wobble*p->wob + fm;
			if ((p->kind == REED) || (p->kind == KRUMM)) {
				dev -= 25.0*exp(-dt/0.02);
			} else {
				dev += 15.0*p->chiff*exp(-dt/p->tau1);
			}
			mult = p->f*pow(2.0, dev/1200.0);
			p->cyc[nbuf] += p->f*pow(2.0, (S->wobble*p->wob + fm)/1200.0)/sr;
			{
				/* partial h turns by h times the fundamental's
				 * step: phasors, rotated by powers of it */
				double w = 2.0*M_PI*mult/sr;
				double cr = cos(w), ci = sin(w), rr = cr, ri = ci;
				for (h = 0; h < p->nh; h++) {
					double e = (1.0 - p->q[h]) *
						(1.0 + p->over[h]*p->ov);
					double zr = p->zr[h]*rr - p->zi[h]*ri;
					double zi = p->zr[h]*ri + p->zi[h]*rr;
					double t2 = rr*cr - ri*ci;
					ri = rr*ci + ri*cr;
					rr = t2;
					p->zr[h] = zr;
					p->zi[h] = zi;
					p->q[h] *= p->qd[h];
					v += p->ha[h]*e*zi;
				}
				p->ov *= p->ovd;
				if ((i % 4096) == 0) {
					for (h = 0; h < p->nh; h++) {
						double m = 1.0/hypot(p->zr[h],
								p->zi[h]);
						p->zr[h] *= m;
						p->zi[h] *= m;
					}
				}
			}
			/* breath noise, and the chiff burst */
			v += 0.004*grand();
			if (dt > 0.3) {
				/* transients over */
			} else if ((p->kind != REED) && (p->kind != KRUMM)) {
				v += p->chiff*1.5*exp(-dt/0.03)*
					bq_run(&p->nbp, grand());
			} else {
				v += 0.6*exp(-dt/0.01)*grand();	/* rattle */
			}
			dry += p->level*er*am*v;
		}
		if ((S->speech > 0.0) && (t >= S->speech_on) &&
		    (t < S->speech_off)) {
			dry += 12.0*S->speech*speech_run(&sp, t);
		}
		if ((S->steps > 0.0) && (t >= S->steps_on) &&
		    (t < S->steps_off)) {
			dry += S->steps*steps_run(&st, t);
		}
		x = dry;
		if (S->rt60 > 0.0) {
			x += S->wet*fdn_run(&room, dry);
		}
		x += S->hum*(sin(2.0*M_PI*50.0*t) +
			0.6*sin(2.0*M_PI*100.0*t + 1.0) +
			0.4*sin(2.0*M_PI*150.0*t + 2.0) +
			0.2*sin(2.0*M_PI*200.0*t + 0.5) +
			0.1*sin(2.0*M_PI*250.0*t + 1.7));
		x += S->rumble*5.0*bq_run(&rumb, grand());
		x += S->wind*bq_run(&hiss, grand());
		if (S->mic_hp > 0.0) {
			x = bq_run(&mic[0], x);
			if (S->mic_ord >= 4) x = bq_run(&mic[1], x);
		}
		x *= S->gain;
		if (S->agc) {
			/* phone AGC: rms follower, 10 ms up, 1 s down, aims
			 * at -12 dBFS, at most +24 dB */
			double a2 = x*x;
			double c = (a2 > agc_env) ? 1.0/(0.01*sr)
						  : 1.0/(1.0*sr);
			double g;
			agc_env += c*(a2 - agc_env);
			g = 8000.0/(sqrt(agc_env) + 1.0);
			if (g > 16.0) g = 16.0;
			agc_gain += (g - agc_gain)*(1.0/(0.005*sr));
			x *= agc_gain;
		}
		x += urand() - 0.5;			/* dither */
		if (x > 32767.0) x = 32767.0;		/* clipping */
		if (x < -32767.0) x = -32767.0;
		out[i] = (short)lrint(x);
	}
	if (S->rt60 > 0.0) fdn_free(&room);
	*n_out = n;
	return out;
}

/* true mean frequency of pipe j between two buffer indices */
static double true_hz(struct scen *S, int j, int b0, int b1) {
	/* only while it sounds */
	int on = (int)ceil(S->p[j].on*S->sr/BUFLEN);
	if (b0 < on) b0 = on;
	if (b1 <= b0) b1 = b0 + 1;
	return (S->p[j].cyc[b1] - S->p[j].cyc[b0]) /
		((double)(b1 - b0)*BUFLEN/S->sr);
}

/* ---- evaluation ---- */
#define MAXEST 4096
struct est { double t, hz; int buf, shown; };

/* what length mode shows over a key, pipelength.update() */
struct lenres {
	double tstable;		/* key to the first stable reading */
	double frac;		/* share of the sustain from 1.5 s on that
				 * is stable */
	double bad;		/* ... that is stable but > 1 c wrong */
	double unclear;		/* ... that is "unclear" */
	double worst;		/* worst stable reading's error, cents */
	double err;		/* the reading at the key's release */
	int n;
};

static double median(double *v, int n);

static double fold_oct(double c) {
	return c - 1200.0*floor(c/1200.0 + 0.5);
}

/* c[m]: detector estimates in the window, cents against the target;
 * dr, drs: the drift reading and its spread; tc: the truth, cents */
static void len_score(struct lenres *L, const double *c, int m, int stopped,
			int have_dr, double dr, double drs, double tc,
			double dt)
{
	static const int parts[3] = { 1, 3, 5 };
	double det = NAN, mad = 0.0, cents, v[64], err;
	int stable, unclear, i, use_drift;
	if (m > 0) {
		double med;
		for (i = 0; i < m; i++) v[i] = c[i];
		med = median(v, m);
		for (i = 0; i < m; i++) v[i] = fabs(c[i] - med);
		mad = median(v, m);
		for (i = 0; i < (stopped ? 3 : 1); i++) {
			double x = med - 1200.0*log2(parts[i]);
			double r = x - 1200.0*floor(x/1200.0 + 0.5);
			if (isnan(det) || (fabs(r) < fabs(det))) det = r;
		}
	}
	use_drift = have_dr && (fabs(dr) < 90.0) &&
		(isnan(det) || (fabs(det) < 90.0) || (fabs(det - dr) < 45.0));
	if (use_drift) {
		cents = dr; stable = (drs < STABLE_CENTS); unclear = 0;
	} else if (!isnan(det)) {
		cents = det;
		stable = (m >= LEN_MIN_EST) && (mad < STABLE_CENTS);
		unclear = (fabs(det) > 150.0);
	} else if (have_dr) {
		cents = dr; stable = 0; unclear = 1;
	} else {
		return;			/* listening */
	}
	if (unclear) stable = 0;
	err = cents - fold_oct(tc);
	if (stable) {
		if (L->tstable < 0.0) L->tstable = dt;
		if (fabs(err) > L->worst) L->worst = fabs(err);
	}
	if (dt >= 1.5) {
		L->n++;
		if (stable) L->frac += 1.0;
		if (stable && (fabs(err) > 1.0)) L->bad += 1.0;
		if (unclear) L->unclear += 1.0;
	}
	L->err = unclear ? NAN : err;
}

struct keyres {
	double lat;		/* key to right note shown, <0 never (with
				 * -f: shown by the detector, before any
				 * correction) */
	int corrected;		/* -f: the user corrected the note */
	double corr_t;
	int wrong_oct, wrong_other;	/* shown note moved to a wrong one */
	int est_n, est_none, est_oct, est_other;	/* sustain estimates */
	struct lenres ld;	/* length mode, no row focused */
	struct lenres lr;	/* length mode, the pipe's row focused */
	/* mixture */
	double mx_err, mx_time;
	double mx_swing;	/* strobe_drift_amp() over the measurement */
	double focus;
};

struct scenres {
	int keys, shown, missed;
	double lat_med, lat_max;
	int wrong_oct, wrong_other;
	int est_n, est_bad, est_oct, est_none;
	int false_trig;		/* noise-only: notes shown */
	int corrections;	/* -f: by hand */
	double false_est;	/* noise-only: share of estimates */
	struct lensum {
		double frac, bad, unclear;	/* mean shares */
		double err, worst;		/* max |error| */
		double tst;			/* max time to stable */
		int n, never;			/* keys, never stable */
	} ld, lr;
	double mx_mean, mx_max, mx_time;
	int mx_n, mx_fail;
	int beat;		/* measurements with a swing > BEAT_SWING */
	double swing;		/* the largest swing */
};

static int semis(double hz) { return (int)lround(12.0*log2(hz/440.0)); }

static int is_ok(struct key *k, int expect, int got) {
	return k->pc_only ? ((((got - expect)%12)+12)%12 == 0)
			  : (got == expect);
}

static double median(double *v, int n) {
	int i, j;
	for (i = 1; i < n; i++) {
		double x = v[i];
		for (j = i; (j > 0) && (v[j-1] > x); j--) v[j] = v[j-1];
		v[j] = x;
	}
	return (n % 2) ? v[n/2] : 0.5*(v[n/2-1] + v[n/2]);
}

static const char *names12[12] = { "A","A#","B","C","C#","D","D#","E","F",
					"F#","G","G#" };
static void note_name(int s, char *out) {
	int k = s + 48;
	if (s == -999) { strcpy(out, "-"); return; }
	sprintf(out, "%s%d", names12[((k%12)+12)%12], (int)floor((k+9)/12.0));
}

static int verbose;

/* the pipe the key's row measures, and its row ratio */
static double key_focus(struct scen *S, struct key *k) {
	return (k->focus > 0.0) ? k->focus : S->p[k->truth].ratio;
}
static int focus_pipe(struct scen *S, int ki) {
	struct key *k = &S->k[ki];
	double r = key_focus(S, k);
	int j;
	for (j = 0; j < S->np; j++) {
		if ((S->p[j].key == ki) && (fabs(S->p[j].ratio - r) < 1e-6))
			return j;
	}
	return k->truth;
}
static int row_for(double ratio) {
	int i;
	for (i = 0; i < NROWS; i++) {
		if (fabs(rows[i]/(double)ORGAN_DIV - ratio) < 1e-6) return i;
	}
	return -1;
}

static void evaluate(struct scen *S, short *buf, int n, struct scenres *R)
{
	static struct est est[MAXEST];
	static struct keyres kr[MAXK];
	static double dcyc[MAXEST*4], dsecs[MAXEST*4];
	static int dbuf[MAXEST*4];
	int ne = 0, nd = 0, nbuf = n/BUFLEN, b, ki = -1, i;
	int det_semi = -999, det_count = 0, shown = -999;
	/* -f: follow_shift (old), continuity (new), the last agreed note */
	int shift = 0, cont = 0, det_stable = -999;
	double det_stable_t = -1e9, last_heard = 0.0;
	double hz, conf;
	double lat[MAXK];
	int nl = 0;
	/* mixture */
	int mx_on = 0, mx_b0 = 0;
	double mx_c = 0.0, mx_s = 0.0, mx_lo = 0.0, mx_hi = 0.0;
	int tgt_key = -1;

	memset(kr, 0, sizeof kr);
	memset(R, 0, sizeof *R);
	for (i = 0; i < S->nk; i++) {
		kr[i].lat = -1.0;
		kr[i].ld.tstable = kr[i].lr.tstable = -1.0;
		kr[i].mx_err = NAN;
		kr[i].ld.err = kr[i].lr.err = NAN;
	}
	sample_rate = S->sr;
	pitch_init(S->sr);
	pitch_hint(0.0, 0.0);
	organ_mode = 1;
	num_strobes = NROWS;
	for (i = 0; i < NROWS; i++) harm_mult[i] = rows[i];
	strobe_init();
	base_cents = 48000;
	strobe_recalculate(0);
	strobe_focus(0.0);
	if (follow_mode && (S->nk == 0)) {
		/* noise alone: following at A4, corrected there (new:
		 * continuity on), to see what the noise moves it to */
		shown = 0;
		cont = (follow_mode == FOLLOW_NEW);
	}

	for (b = 0; b < nbuf; b++) {
		double t0 = (double)b*BUFLEN/S->sr;
		double t = (double)(b+1)*BUFLEN/S->sr;	/* after this one */
		int nk;
		/* the key whose window this is: from RECALC_BEFORE before
		 * its onset to the next one's */
		nk = ki;
		while ((nk+1 < S->nk) && (t0 >= S->k[nk+1].on - RECALC_BEFORE))
			nk++;
		if ((nk != ki) && (nk >= 0)) {
			struct key *k = &S->k[nk];
			double r = key_focus(S, k);
			ki = nk;
			/* the target goes to the key's 8' note: strobes
			 * recalculated (drift reset), focus on its row */
			base_cents = 48000 + 1000*k->semi;
			strobe_recalculate(0);
			strobe_focus(row_for(r) >= 0 ? r : 0.0);
			/* mixture mode: the tuner sets the key by hand
			 * where it jumps (Ton+/-, octave buttons) */
			if (S->mixmode && ((shown == -999) ||
			    (abs(k->semi - shown) > MIX_SEMIS)))
				shown = k->semi;
			/* the app hints the note shown; without -f that is
			 * taken to be the key's */
			if (S->hint) pitch_hint(r, key_hz((follow_mode &&
					(shown != -999)) ? shown : k->semi));
			nd = 0;
			mx_on = 0;
			mx_c = mx_s = mx_lo = mx_hi = 0.0;
			tgt_key = ki;
		}
		memcpy(snd_buf, buf + b*BUFLEN, BUFLEN*sizeof(short));
		snd_buflen = BUFLEN;
		if (pitch_feed(snd_buf, BUFLEN, &hz, &conf)) {
			int g = (hz > 0.0) ? semis(hz) : -999;
			if (hz > 0.0) {
				last_heard = t;
			} else if (t - last_heard > NEAR_SILENCE) {
				shift = 0;
				cont = 0;
			}
			if (hz > 0.0) {
				if (g == det_semi) det_count++;
				else { det_semi = g; det_count = 1; }
			} else {
				det_count = 0;
			}
			if ((hz > 0.0) && (det_count >= det_agree)) {
				/* where TunerTime.on_pitch moves the note */
				int to = g;
				det_stable = g;
				det_stable_t = t;
				if (S->mixmode && (shown != -999)) {
					to = g + 12*(int)floor((shown - g)/12.0
								+ 0.5);
					if (abs(to - shown) > MIX_SEMIS)
						to = shown;
				} else if (follow_mode == FOLLOW_OLD) {
					to = g + shift;
				} else if (cont && (shown != -999)) {
					to = g + 12*(int)floor((shown - g)/12.0
								+ 0.5);
					if (abs(to - shown) > NEAR_SEMIS)
						to = shown;
				}
				if (to != shown) {
					shown = to;
					if (S->hint && follow_mode && (ki >= 0))
						pitch_hint(key_focus(S,
							&S->k[ki]), key_hz(to));
					if (verbose > 1) {
						char nm[8];
						note_name(to, nm);
						printf("    %6.2fs shown %s\n", t, nm);
					}
					if (S->nk == 0) {
						R->false_trig++;
					} else if ((ki >= 0) &&
						   (t >= S->k[ki].on)) {
						struct key *k = &S->k[ki];
						int ex = semis(key_hz(k->semi) *
							S->p[k->truth].ratio);
						if (S->hint || follow_mode)
							ex = k->semi;
						if (!is_ok(k, ex, to)) {
							if (((to-ex)%12) == 0)
								kr[ki].wrong_oct++;
							else
								kr[ki].wrong_other++;
						}
					}
				}
			}
			/* -f: the user sees a wrong note while holding the
			 * key and corrects it to the key's note by hand
			 * (TunerTime.user_moved_note) */
			if (follow_mode && (ki >= 0) &&
			    (t >= S->k[ki].on + CORR_AFTER) &&
			    (t <= S->k[ki].off) && !kr[ki].corrected &&
			    !is_ok(&S->k[ki], S->k[ki].semi, shown)) {
				int to = S->k[ki].semi;
				kr[ki].corrected = 1;
				kr[ki].corr_t = t;
				R->corrections++;
				if (verbose > 1) {
					char nm[8];
					note_name(to, nm);
					printf("    %6.2fs corrected to %s\n", t,
						nm);
				}
				if (follow_mode == FOLLOW_OLD) {
					if ((det_stable != -999) &&
					    (t - det_stable_t < CORR_RECENT))
						shift = to - det_stable;
				} else {
					cont = 1;
				}
				shown = to;
				if (S->hint)
					pitch_hint(key_focus(S, &S->k[ki]),
						key_hz(to));
			}
			if (ne < MAXEST) {
				est[ne].t = t; est[ne].hz = hz; est[ne].buf = b;
				est[ne].shown = shown;
				ne++;
			}
		}
		strobe_newdata(bitmap);
		{
			double fr, c, s;
			if ((ki >= 0) && strobe_drift(&fr, &c, &s) &&
			    (s > 0.0) && (nd < MAXEST*4)) {
				dcyc[nd] = c; dsecs[nd] = s; dbuf[nd] = b;
				nd++;
			}
			/* mixture measurement */
			if ((ki >= 0) && (tgt_key == ki) &&
			    isnan(kr[ki].mx_err) &&
			    (t >= S->k[ki].on + S->mstart) &&
			    strobe_drift(&fr, &c, &s)) {
				if (!mx_on) {
					mx_on = 1;
					mx_b0 = b;
				}
				mx_c += c;
				mx_s += s;
				{
					double a;
					if (strobe_drift_amp(&a) && (a > 0.0)) {
						if ((mx_lo == 0.0) || (a < mx_lo))
							mx_lo = a;
						if (a > mx_hi) mx_hi = a;
					}
				}
				if (mx_s >= ((key_focus(S, &S->k[ki]) <
					      1.0 - 1e-6) ? MEASURE_SECONDS_LOW
							  : MEASURE_SECONDS)) {
					struct key *k = &S->k[ki];
					int j = focus_pipe(S, ki);
					double f_row = cents_to_freq(0,
						base_cents, 1) *
						key_focus(S, k);
					double got = 1200.0*log2(
						(fr + mx_c/mx_s)/fr);
					double tru = 1200.0*log2(
						true_hz(S, j, mx_b0, b+1) *
						key_focus(S, k) /
						S->p[j].ratio / f_row);
					kr[ki].mx_err = got - tru;
					kr[ki].mx_time = t - S->k[ki].on;
					kr[ki].mx_swing = (mx_hi > 0.0) ?
						(mx_hi - mx_lo)/(mx_hi + mx_lo) : 0.0;
					tgt_key = -1;
				}
			}
		}
		/* length mode, once per buffer during the key */
		if ((ki >= 0) && (t >= S->k[ki].on) && (t <= S->k[ki].off)) {
			struct key *k = &S->k[ki];
			int jf = focus_pipe(S, ki), e, m, b0;
			int stopped = (S->p[k->truth].kind == STOPPED);
			double dt = t - k->on, c[64], f_base, f_nom, f_true;
			double dr = 0.0, drs = 1e9;
			int have_dr = 0;
			/* no row focused: the detector against the pipe's
			 * nominal pitch (the 8' note with a hint, as the
			 * hinted detector divides the partial out) */
			f_nom = key_hz(k->semi) *
				(S->hint ? 1.0 : S->p[k->truth].ratio);
			b0 = (int)((t - LEN_WINDOW)*S->sr/BUFLEN);
			if (b0 < 0) b0 = 0;
			f_true = true_hz(S, k->truth, b0, b+1) *
				(S->hint ? 1.0/S->p[k->truth].ratio : 1.0);
			m = 0;
			for (e = ne-1; (e >= 0) && (m < 64); e--) {
				if (est[e].t < k->on - RECALC_BEFORE) break;
				if (est[ne-1].t - est[e].t > LEN_WINDOW) break;
				if (est[e].hz <= 0.0) continue;
				c[m++] = 1200.0*log2(est[e].hz/f_nom);
			}
			len_score(&kr[ki].ld, c, m, stopped, 0, 0.0, 0.0,
				  1200.0*log2(f_true/f_nom), dt);
			/* the pipe's row focused, as pipelength: the row's
			 * drift against note*focus, and the detector's
			 * estimates against the same f_base */
			if (row_for(key_focus(S, k)) < 0) continue;
			f_base = key_hz(k->semi)*key_focus(S, k);
			for (e = 0; e < m; e++) c[e] += 1200.0*log2(f_nom/f_base);
			if (nd > 0) {
				double c0 = 0, s0 = 0, c1 = 0, s1 = 0, latest;
				latest = (double)(dbuf[nd-1]+1)*BUFLEN/S->sr;
				for (e = nd-1; e >= 0; e--) {
					double age = latest -
						(double)(dbuf[e]+1)*BUFLEN/S->sr;
					if (age > LEN_WINDOW) break;
					if (age > LEN_WINDOW/2) {
						c0 += dcyc[e]; s0 += dsecs[e];
					} else {
						c1 += dcyc[e]; s1 += dsecs[e];
					}
				}
				if (s0 + s1 >= DRIFT_MIN_SECS) {
					have_dr = 1;
					dr = 1200.0*log2((f_base +
						(c0+c1)/(s0+s1))/f_base);
					if ((s0 > 0) && (s1 > 0)) {
						drs = 0.5*fabs(1200.0*log2(
						    (f_base+c0/s0)/f_base) -
						    1200.0*log2((f_base+c1/s1)/
						    f_base));
					}
				}
			}
			f_true = true_hz(S, jf, b0, b+1) *
					key_focus(S, k)/S->p[jf].ratio;
			len_score(&kr[ki].lr, c, m, stopped, have_dr, dr, drs,
				  1200.0*log2(f_true/f_base), dt);
		}
	}

	/* per key: latency, and the sustain's single estimates */
	for (i = 0; i < S->nk; i++) {
		struct key *k = &S->k[i];
		double end = (i+1 < S->nk) ? S->k[i+1].on : S->secs;
		int ex = semis(key_hz(k->semi)*S->p[k->truth].ratio);
		int e, sh;
		if (S->hint) ex = k->semi;
		kr[i].focus = key_focus(S, k);
		/* the shown note, as it went */
		for (e = 0; e < ne; e++) {
			int g = (est[e].hz > 0.0) ? semis(est[e].hz) : -999;
			sh = est[e].shown;
			if (est[e].t < k->on) continue;
			if (est[e].t >= end) break;
			if ((kr[i].lat < 0.0) && (sh != -999) &&
			    is_ok(k, follow_mode ? k->semi : ex, sh) &&
			    (est[e].t <= k->off + 0.5) &&
			    (!kr[i].corrected || (est[e].t < kr[i].corr_t))) {
				kr[i].lat = est[e].t - k->on;
			}
			if ((est[e].t >= k->on + 0.6) && (est[e].t <= k->off)) {
				kr[i].est_n++;
				if (g == -999) kr[i].est_none++;
				else if (!is_ok(k, ex, g)) {
					if (((g - ex)%12) == 0) kr[i].est_oct++;
					else kr[i].est_other++;
				}
			}
		}
		if (verbose) {
			char nm[8];
			note_name(ex, nm);
			struct lenres *L[2];
			int q;
			note_name(ex, nm);
			printf("  key %-4s %-7s r%.2f: lat %5.2f wrong %d/%d "
				"est %2d none %2d oct %2d oth %2d",
				nm, kind_name[S->p[k->truth].kind], kr[i].focus,
				kr[i].lat, kr[i].wrong_oct, kr[i].wrong_other,
				kr[i].est_n, kr[i].est_none, kr[i].est_oct,
				kr[i].est_other);
			L[0] = &kr[i].ld; L[1] = &kr[i].lr;
			for (q = 0; q < 2; q++) {
				double n = L[q]->n ? L[q]->n : 1;
				printf(" | %s st %5.2fs %3.0f%% bad %3.0f%% "
					"uncl %3.0f%% err %+6.2f worst %5.2f",
					q ? "lenDrf" : "lenDet", L[q]->tstable,
					100*L[q]->frac/n, 100*L[q]->bad/n,
					100*L[q]->unclear/n, L[q]->err,
					L[q]->worst);
			}
			printf(" | mix %+6.2f (%.1fs) swing %.2f\n", kr[i].mx_err,
				kr[i].mx_time, kr[i].mx_swing);
		}
		R->keys++;
		if (kr[i].lat >= 0.0) { R->shown++; lat[nl++] = kr[i].lat; }
		else R->missed++;
		R->wrong_oct += kr[i].wrong_oct;
		R->wrong_other += kr[i].wrong_other;
		R->est_n += kr[i].est_n;
		R->est_none += kr[i].est_none;
		R->est_oct += kr[i].est_oct;
		R->est_bad += kr[i].est_oct + kr[i].est_other;
		{
			struct lenres *L[2] = { &kr[i].ld, &kr[i].lr };
			struct lensum *M[2] = { &R->ld, &R->lr };
			int q;
			for (q = 0; q < 2; q++) {
				if (L[q]->n == 0) continue;
				M[q]->n++;
				M[q]->frac += L[q]->frac/L[q]->n;
				M[q]->bad += L[q]->bad/L[q]->n;
				M[q]->unclear += L[q]->unclear/L[q]->n;
				if (!isnan(L[q]->err))
					M[q]->err = fmax(M[q]->err, fabs(L[q]->err));
				M[q]->worst = fmax(M[q]->worst, L[q]->worst);
				if (L[q]->tstable < 0.0) M[q]->never++;
				else M[q]->tst = fmax(M[q]->tst, L[q]->tstable);
			}
		}
		if (row_for(kr[i].focus) >= 0) {
			if (isnan(kr[i].mx_err)) R->mx_fail++;
			else {
				R->mx_n++;
				R->mx_mean += fabs(kr[i].mx_err);
				R->mx_max = fmax(R->mx_max, fabs(kr[i].mx_err));
				R->mx_time = fmax(R->mx_time, kr[i].mx_time);
				R->swing = fmax(R->swing, kr[i].mx_swing);
				if (kr[i].mx_swing > BEAT_SWING) R->beat++;
			}
		}
	}
	if (R->ld.n) {
		R->ld.frac /= R->ld.n; R->ld.bad /= R->ld.n;
		R->ld.unclear /= R->ld.n;
	}
	if (R->lr.n) {
		R->lr.frac /= R->lr.n; R->lr.bad /= R->lr.n;
		R->lr.unclear /= R->lr.n;
	}
	if (R->mx_n) R->mx_mean /= R->mx_n;
	if (nl) {
		R->lat_max = 0;
		for (i = 0; i < nl; i++) R->lat_max = fmax(R->lat_max, lat[i]);
		R->lat_med = median(lat, nl);
	}
	if (S->nk == 0) {
		int nz = 0;
		for (i = 0; i < ne; i++) if (est[i].hz > 0.0) nz++;
		R->false_est = ne ? (double)nz/ne : 0.0;
	}
}

static void write_wav(const char *path, const short *buf, int n, int sr) {
	FILE *f = fopen(path, "wb");
	unsigned char h[44];
	int len = 2*n;
	if (!f) { perror(path); return; }
	memcpy(h, "RIFF", 4);
	h[4] = (36+len) & 255; h[5] = ((36+len)>>8) & 255;
	h[6] = ((36+len)>>16) & 255; h[7] = ((36+len)>>24) & 255;
	memcpy(h+8, "WAVEfmt ", 8);
	h[16] = 16; h[17] = h[18] = h[19] = 0;
	h[20] = 1; h[21] = 0; h[22] = 1; h[23] = 0;
	h[24] = sr & 255; h[25] = (sr>>8) & 255; h[26] = (sr>>16) & 255; h[27] = 0;
	h[28] = (2*sr) & 255; h[29] = ((2*sr)>>8) & 255;
	h[30] = ((2*sr)>>16) & 255; h[31] = 0;
	h[32] = 2; h[33] = 0; h[34] = 16; h[35] = 0;
	memcpy(h+36, "data", 4);
	h[40] = len & 255; h[41] = (len>>8) & 255; h[42] = (len>>16) & 255;
	h[43] = (len>>24) & 255;
	fwrite(h, 1, 44, f);
	fwrite(buf, 2, n, f);		/* little-endian hosts */
	fclose(f);
}

/* ---- scenario building ---- */
static struct scen *S;

static void church(const char *name) {
	memset(S, 0, sizeof *S);
	S->name = name;
	S->sr = 44100;
	S->rt60 = 3.0; S->wet = 0.3;
	S->mic_hp = 150.0; S->mic_ord = 2;
	S->gain = 1.0;
	S->hum = 15.0; S->rumble = 15.0; S->wind = 20.0;
	S->wobble = 0.7;
	S->mstart = MSTART;
	{	/* from the name, so that each scenario stays the same
		 * whatever else is added */
		const char *c;
		S->seed = 5381;
		for (c = name; *c; c++) S->seed = S->seed*33 + (unsigned char)*c;
	}
}
static void lab(const char *name) {
	church(name);
	S->rt60 = 0.0; S->wet = 0.0; S->mic_hp = 0.0;
	S->hum = S->rumble = 0.0; S->wind = 3.0; S->wobble = 0.0;
}
static int key(int semi, double on, double dur) {
	struct key *k = &S->k[S->nk];
	k->semi = semi; k->on = on; k->off = on + dur;
	k->truth = S->np;	/* the next pipe added */
	if (k->off + 2.0 > S->secs) S->secs = k->off + 2.0;
	return S->nk++;
}
static int pipe(int k, double ratio, double cents, int kind, double level,
		double chiff) {
	struct pipe *p = &S->p[S->np];
	memset(p, 0, sizeof *p);
	p->key = k; p->ratio = ratio; p->cents = cents; p->kind = kind;
	p->level = level; p->chiff = chiff;
	return S->np++;
}
/* a run of keys, one pipe each */
static void run_keys(const int *semis_, int nk, double ratio, int kind,
			double level, double chiff, double hold, double gap) {
	int i;
	double t = 0.5;
	for (i = 0; i < nk; i++) {
		/* mistuned by a few cents, as found */
		int k = key(semis_[i], t, hold);
		pipe(k, ratio, ((i*37) % 11) - 5.0, kind, level, chiff);
		t += hold + gap;
	}
}

/* semitones from A4 of the 8' notes */
#define C1 (-45)
#define C2 (-33)
#define C3 (-21)
#define C4 (-9)
#define C5 3
#define C6 15
#define C7 27

typedef void (*builder)(void);

static void sc_clean(void) {
	static const int n[] = { C2, C2+7, C3+4, C4, 0, C5+4, C6, C6+9 };
	lab("clean Principal 8'");
	run_keys(n, 8, 1.0, OPEN, 3000, 0.0, 2.5, 0.5);
}
static const int manual[] = { C2, C2+7, C3+4, C4, 0, C5+4, C6, C6+9 };
static const int pedal[] = { C2, C2+3, C2+5, C2+7, C2+10, C3, C3+5 };
static void sc_principal(void) {
	church("Principal 8' church");
	run_keys(manual, 8, 1.0, OPEN, 3000, 0.5, 3.0, 0.7);
}
static void sc_principal48(void) {
	church("Principal 8' 48k");
	S->sr = 48000;
	run_keys(manual, 8, 1.0, OPEN, 3000, 0.5, 3.0, 0.7);
}
/* mixture measure pressed before the key: the start of the measurement
 * holds the previous note's tail and this one's speech */
static void sc_early(void) {
	church("Principal measure early");
	S->mstart = -0.3;
	run_keys(manual, 8, 1.0, OPEN, 3000, 0.5, 3.0, 0.7);
}
static void sc_early16(void) {
	church("Subbass measure early");
	S->mstart = -0.3;
	run_keys(pedal, 7, 0.5, STOPPED, 4000, 0.3, PEDAL_HOLD, 0.8);
}
static void sc_hot(void) {
	church("Principal hot+AGC+clip");
	S->agc = 1; S->gain = 4.0;
	run_keys(manual, 8, 1.0, OPEN, 9000, 0.5, 3.0, 0.7);
}
static void sc_quiet(void) {
	church("Principal far/quiet");
	S->wet = 0.8;
	run_keys(manual, 8, 1.0, OPEN, 300, 0.5, 3.0, 0.7);
}
static void sc_gedackt(void) {
	church("Gedackt 8'");
	run_keys(manual, 8, 1.0, STOPPED, 3000, 0.4, 3.0, 0.7);
}
static void sc_flute4(void) {
	church("Flute 4'");
	run_keys(manual, 8, 2.0, FLUTE, 3000, 0.3, 3.0, 0.7);
}
static void sc_gamba(void) {
	church("Gamba 8'");
	run_keys(manual, 8, 1.0, STRING, 2000, 0.6, 3.0, 0.7);
}
static void sc_trumpet(void) {
	church("Trompete 8'");
	run_keys(manual, 8, 1.0, REED, 3000, 0.0, 3.0, 0.7);
}
static void sc_krumm(void) {
	church("Krummhorn 8'");
	run_keys(manual, 8, 1.0, KRUMM, 3000, 0.0, 3.0, 0.7);
}
static void sc_subbass(void) {
	church("Subbass 16' phone");
	run_keys(pedal, 7, 0.5, STOPPED, 4000, 0.3, PEDAL_HOLD, 0.8);
}
static void sc_subbass4(void) {
	church("Subbass 16' 4th-ord 200Hz");
	S->mic_hp = 200.0; S->mic_ord = 4;
	run_keys(pedal, 7, 0.5, STOPPED, 4000, 0.3, PEDAL_HOLD, 0.8);
}
static void sc_subhum(void) {
	church("Subbass 16' strong hum");
	S->hum = 150.0; S->rumble = 40.0;
	run_keys(pedal, 7, 0.5, STOPPED, 4000, 0.3, PEDAL_HOLD, 0.8);
}
static void sc_prin16(void) {
	church("Principal 16' phone");
	run_keys(pedal, 7, 0.5, OPEN, 4000, 0.3, PEDAL_HOLD, 0.8);
}
static void sc_32(void) {
	static const int n[] = { C2, C2+2, C2+4, C2+7, C2+9 };
	church("Untersatz 32' phone");
	run_keys(n, 5, 0.25, STOPPED, 6000, 0.2, HOLD_32, 1.0);
}
static void sc_prin32(void) {
	static const int n[] = { C2, C2+2, C2+4, C2+7, C2+9 };
	church("Principal 32' phone");
	run_keys(n, 5, 0.25, OPEN, 6000, 0.2, HOLD_32, 1.0);
}
static void sc_posaune(void) {
	church("Posaune 16' phone");
	run_keys(pedal, 7, 0.5, REED, 4000, 0.0, PEDAL_HOLD, 0.8);
}
static void sc_high2(void) {
	static const int n[] = { C5, C5+5, C6, C6+4, C6+7, C7 };
	church("Octave 2' top");
	run_keys(n, 6, 4.0, OPEN, 2000, 0.4, 3.0, 0.7);
}
static void sc_high1(void) {
	static const int n[] = { C4, C4+7, C5, C5+4, C5+7, C6 };
	church("Sifflote 1' top");
	run_keys(n, 6, 8.0, OPEN, 2000, 0.4, 3.0, 0.7);
}
static void sc_trem_flute(void) {
	church("Flute 8' tremulant");
	S->trem_rate = 5.8; S->trem_am = 0.3; S->trem_fm = 10.0;
	run_keys(manual, 8, 1.0, FLUTE, 3000, 0.3, 3.0, 0.7);
}
static void sc_trem_prin(void) {
	church("Principal 8' tremulant");
	S->trem_rate = 6.5; S->trem_am = 0.2; S->trem_fm = 4.0;
	run_keys(manual, 8, 1.0, OPEN, 3000, 0.5, 3.0, 0.7);
}
static void sc_wobble(void) {
	church("Principal unsteady wind");
	S->wobble = 3.0;
	run_keys(manual, 8, 1.0, OPEN, 3000, 0.5, 3.0, 0.7);
}
static void celeste(const char *nm, int kind, double det, double lv2) {
	int i;
	double t = 0.5;
	church(nm);
	for (i = 0; i < 6; i++) {
		int k = key(manual[i+1], t, 3.5);
		pipe(k, 1.0, 0.0, kind, 2000, 0.5);
		pipe(k, 1.0, det, kind, 2000*lv2, 0.5);
		t += 4.2;
	}
}
static void sc_celeste(void) { celeste("Voix celeste +8c", STRING, 8.0, 1.0); }
static void sc_unda(void) { celeste("Unda maris -4c 50%", FLUTE, -4.0, 0.5); }
static void sc_leak(void) {
	/* the tuned rank and a unison neighbour (a mixture rank not fully
	 * muted) 6 cents off at -20 dB */
	celeste("Unison leak -20dB +6c", OPEN, 6.0, 0.1);
}
static void sc_neighbour(void) {
	/* a pipe a semitone up speaks faintly (leaky pallet) */
	int i;
	double t = 0.5;
	church("Neighbour semitone -20dB");
	for (i = 0; i < 8; i++) {
		int k = key(manual[i], t, 3.0);
		pipe(k, 1.0, 2.0, OPEN, 3000, 0.5);
		pipe(k, pow(2.0, 1.0/12.0), 0.0, OPEN, 300, 0.5);
		t += 3.7;
	}
}
/* mixture: 2' + 1 1/3' + 1' on one key, each rank focused in turn, at
 * level 1 and the other ranks at 'other' (not fully muted).  with all
 * ranks open the 1' coincides with the 2' rank's octave partial, so only
 * the 2' and 1 1/3' are asked for there */
static void mixture(const char *nm, double other, int nr) {
	static const int n[] = { C3, C3+5, C4, C4+7 };
	static const double f[3] = { 4.0, 6.0, 8.0 };
	int i, r;
	double t = 0.5;
	church(nm);
	S->hint = 1;
	S->mixmode = 1;
	for (i = 0; i < 4; i++) {
		for (r = 0; r < nr; r++) {
			int k = key(n[i], t, 3.5);
			int a = pipe(k, 4.0, 3.0, OPEN,
					2000*((r == 0) ? 1 : other), 0.4);
			int b = pipe(k, 6.0, -5.0, OPEN,
					2000*((r == 1) ? 1 : other), 0.4);
			int c = pipe(k, 8.0, 6.0, OPEN,
					2000*((r == 2) ? 1 : other), 0.4);
			S->k[k].focus = f[r];
			S->k[k].truth = (r == 0) ? a : (r == 1) ? b : c;
			S->k[k].pc_only = 1;
			t += 4.2;
		}
	}
}
static void sc_mix_all(void) { mixture("Mixture III open", 1.0, 2); }
/* mixture mode played chromatically, the 2' rank focused as the
 * reference, the ranks sounding one at a time as when they are tuned
 * against it (the others muted): 2', then 2 2/3', 1 3/5' and 1' alone.
 * The key shown has to stay on each key while the other ranks sound */
static void sc_mix_chrom(void) {
	static const double f[4] = { 4.0, 3.0, 5.0, 8.0 };
	int i, r;
	double t = 0.5;
	church("Mixture chromatic, ranks alone");
	S->hint = 1;
	S->mixmode = 1;
	for (i = 0; i < 4; i++) {	/* MAXK keys */
		for (r = 0; r < 4; r++) {
			int k = key(C3 + i, t, 2.5);
			int a = pipe(k, f[r], 2.0, OPEN, 2000, 0.4);
			S->k[k].focus = 4.0;
			S->k[k].truth = a;
			S->k[k].pc_only = 1;
			t += 3.0;
		}
	}
}
static void sc_mix_muted(void) { mixture("Mixture III muted -20dB", 0.1, 3); }
/* Sesquialtera 2 2/3' + 1 3/5', the 2 2/3' focused, with a Gedackt 8' */
static void sc_sesq(void) {
	static const int n[] = { C3, C3+4, C4, C4+5, C5 };
	int i;
	double t = 0.5;
	church("Sesquialtera + Gedackt 8'");
	S->hint = 1;
	for (i = 0; i < 5; i++) {
		int k = key(n[i], t, 3.5);
		pipe(k, 3.0, 4.0, OPEN, 1500, 0.4);
		pipe(k, 5.0, -3.0, OPEN, 1200, 0.4);
		pipe(k, 1.0, 0.0, STOPPED, 2000, 0.4);
		S->k[k].focus = 3.0;
		S->k[k].pc_only = 1;
		t += 4.2;
	}
}
static void sc_octaves(void) {
	/* tuning by octaves: the tail of one holds the other's partials */
	static const int n[] = { C3, C4, C3+2, C4+2, C5+2, C4+4, C3+4 };
	church("Octave steps, reverb");
	S->rt60 = 4.0; S->wet = 0.5;
	run_keys(n, 7, 1.0, OPEN, 3000, 0.5, 3.3, 0.3);
}
static void sc_fifths(void) {
	static const int n[] = { C3, C3+7, C3+2, C3+9, C3+4, C3+11, C4+6 };
	church("Fifth steps, reverb");
	S->rt60 = 4.0; S->wet = 0.5;
	run_keys(n, 7, 1.0, OPEN, 3000, 0.5, 3.3, 0.3);
}
static void sc_longtail(void) {
	church("Gedackt, 6 s reverb");
	S->rt60 = 6.0; S->wet = 0.7;
	run_keys(manual, 8, 1.0, STOPPED, 3000, 0.4, 3.3, 0.3);
}
static void sc_speech_over(void) {
	church("Principal + speech");
	S->speech = 600.0; S->speech_on = 0.0; S->speech_off = 1e9;
	run_keys(manual, 8, 1.0, OPEN, 3000, 0.5, 3.0, 0.7);
}
static void sc_steps_over(void) {
	church("Subbass + footsteps");
	S->steps = 600.0; S->steps_on = 0.0; S->steps_off = 1e9;
	run_keys(pedal, 7, 0.5, STOPPED, 4000, 0.3, PEDAL_HOLD, 0.8);
}
/* chromatic runs, the way a stop is tuned key by key: what follow mode's
 * continuity (-f new) is for */
static void chromatic(const char *nm, int from, int dir, double ratio,
			int kind, double level, double chiff) {
	int n[16], i;
	church(nm);
	for (i = 0; i < 16; i++) n[i] = from + dir*i;
	/* held long enough for a mixture measurement */
	run_keys(n, 16, ratio, kind, level, chiff,
		(ratio < 1.0) ? PEDAL_HOLD : 3.0, 0.3);
}
static void sc_chr_prin(void) {
	chromatic("Chromatic Principal 8'", C3, 1, 1.0, OPEN, 3000, 0.5);
}
static void sc_chr_ged(void) {
	chromatic("Chromatic Gedackt 8'", C4, 1, 1.0, STOPPED, 3000, 0.4);
}
static void sc_chr_flute4(void) {
	chromatic("Chromatic Flute 4'", C3, 1, 2.0, FLUTE, 3000, 0.3);
}
static void sc_chr_quint(void) {
	chromatic("Chromatic Quinte 2 2/3'", C3, 1, 3.0, OPEN, 2000, 0.4);
}
/* the same with its row focused: the detector divides the partial out
 * and reports the key (pitch_hint), as the app does in organ mode */
static void sc_chr_quint_row(void) {
	int i;
	chromatic("Chromatic Quinte, row", C3, 1, 3.0, OPEN, 2000, 0.4);
	S->hint = 1;
	for (i = 0; i < S->nk; i++) S->k[i].focus = 3.0;
}
static void sc_chr_sub(void) {
	chromatic("Chromatic Subbass 16'", C2, 1, 0.5, STOPPED, 4000, 0.3);
}
static void sc_chr_down(void) {
	chromatic("Chromatic down, 6 s reverb", C5, -1, 1.0, STOPPED, 3000,
		0.4);
	S->rt60 = 6.0; S->wet = 0.7;
}
static void sc_chr_speech(void) {
	chromatic("Chromatic + speech", C3, 1, 1.0, OPEN, 3000, 0.5);
	S->speech = 600.0; S->speech_on = 0.0; S->speech_off = 1e9;
}
static void sc_noise(void) {
	church("noise: blower+wind");
	S->hum = 60.0; S->rumble = 60.0; S->wind = 80.0;
	S->secs = 12.0;
}
static void sc_speech(void) {
	church("noise: speech");
	S->speech = 1500.0; S->speech_on = 0.0; S->speech_off = 1e9;
	S->secs = 40.0;
}
static void sc_steps(void) {
	church("noise: footsteps");
	S->steps = 3000.0; S->steps_on = 0.0; S->steps_off = 1e9;
	S->secs = 12.0;
}

static const builder builders[] = {
	sc_clean, sc_principal, sc_principal48, sc_early, sc_early16, sc_hot,
	sc_quiet, sc_gedackt,
	sc_flute4, sc_gamba, sc_trumpet, sc_krumm, sc_subbass, sc_subbass4,
	sc_subhum,
	sc_prin16, sc_32, sc_prin32, sc_posaune, sc_high2, sc_high1,
	sc_trem_flute, sc_trem_prin, sc_wobble, sc_celeste, sc_unda,
	sc_leak, sc_neighbour, sc_mix_all, sc_mix_muted, sc_mix_chrom, sc_sesq, sc_octaves,
	sc_fifths, sc_longtail, sc_speech_over, sc_steps_over,
	sc_chr_prin, sc_chr_ged, sc_chr_flute4, sc_chr_quint,
	sc_chr_quint_row, sc_chr_sub,
	sc_chr_down, sc_chr_speech, sc_noise, sc_speech, sc_steps,
};

int main(int argc, char **argv) {
	const char *only = NULL, *wavdir = NULL;
	int a, c, nb = sizeof builders / sizeof builders[0];
	int t_keys = 0, t_shown = 0, t_woct = 0, t_woth = 0, t_false = 0;
	int t_est = 0, t_bad = 0, t_mxn = 0, t_mxfail = 0, t_lk = 0;
	int t_corr = 0;
	double t_ldst = 0, t_ldbad = 0, t_lrst = 0, t_lrbad = 0, t_mxsum = 0;

	for (a = 1; a < argc; a++) {
		if (!strcmp(argv[a], "-v")) verbose++;
		else if (!strcmp(argv[a], "-w") && (a+1 < argc)) wavdir = argv[++a];
		else if (!strcmp(argv[a], "-a") && (a+1 < argc))
			det_agree = atoi(argv[++a]);
		else if (!strcmp(argv[a], "-f") && (a+1 < argc)) {
			a++;
			follow_mode = !strcmp(argv[a], "old") ? FOLLOW_OLD :
				!strcmp(argv[a], "new") ? FOLLOW_NEW :
							  FOLLOW_NONE;
		}
		else only = argv[a];
	}
	S = calloc(1, sizeof *S);
	printf("%-26s %5s %9s %5s %8s | %-26s | %-21s | %s\n",
		"", "", "latency", "wrong", "bad est", "length, no row: stable",
		"length, row: stable", "mixture |err|");
	printf("%-26s %5s %9s %5s %8s | %4s %4s %4s %4s %5s | %4s %4s %4s %5s | "
		"%s\n", "scenario", "shown", "med/max s", "oct/x", "/sustain",
		"%", "bad%", "uncl", "tmax", "err", "%", "bad%", "tmax", "err",
		"mean/max c, beat warnings, max swing");
	for (c = 0; c < nb; c++) {
		short *buf;
		int n, j;
		struct scenres R;
		builders[c]();
		if (only && !strstr(S->name, only)) continue;
		buf = render(S, &n);
		if (verbose) {
			double e = 0.0;
			int peak = 0;
			for (j = 0; j < n; j++) {
				e += (double)buf[j]*buf[j];
				if (abs(buf[j]) > peak) peak = abs(buf[j]);
			}
			printf("%s (rms %.0f, peak %d)\n", S->name, sqrt(e/n),
				peak);
		}
		evaluate(S, buf, n, &R);
		if (wavdir) {
			char path[512], nm[128], *q;
			strncpy(nm, S->name, sizeof nm - 1);
			nm[sizeof nm - 1] = 0;
			for (q = nm; *q; q++) {
				if (!((*q >= 'a' && *q <= 'z') ||
				      (*q >= 'A' && *q <= 'Z') ||
				      (*q >= '0' && *q <= '9'))) *q = '_';
			}
			snprintf(path, sizeof path, "%s/%s.wav", wavdir, nm);
			write_wav(path, buf, n, S->sr);
		}
		if (S->nk == 0) {
			printf("%-26s false triggers %d, estimates in %.0f%% of "
				"analyses\n", S->name, R.false_trig,
				100*R.false_est);
			t_false += R.false_trig;
		} else {
			printf("%-26s %2d/%-2d %4.2f/%4.2f %2d/%-2d %3d/%-4d | "
				"%3.0f%% %3.0f%% %3.0f%% %4.1f %5.1f | "
				"%3.0f%% %3.0f%% %4.1f %5.1f | %4.2f/%5.2f",
				S->name, R.shown, R.keys, R.lat_med, R.lat_max,
				R.wrong_oct, R.wrong_other, R.est_bad, R.est_n,
				100*R.ld.frac, 100*R.ld.bad, 100*R.ld.unclear,
				R.ld.never ? 99.9 : R.ld.tst, R.ld.err,
				100*R.lr.frac, 100*R.lr.bad,
				R.lr.never ? 99.9 : R.lr.tst, R.lr.err,
				R.mx_mean, R.mx_max);
			printf(" beat %d/%d %.2f", R.beat, R.mx_n, R.swing);
			if (R.mx_fail) printf(" (%d unfinished)", R.mx_fail);
			if (follow_mode) printf(" | corrected %d", R.corrections);
			printf("\n");
			t_corr += R.corrections;
			t_keys += R.keys; t_shown += R.shown;
			t_woct += R.wrong_oct; t_woth += R.wrong_other;
			t_est += R.est_n; t_bad += R.est_bad;
			t_ldst += R.ld.frac*R.ld.n; t_ldbad += R.ld.bad*R.ld.n;
			t_lrst += R.lr.frac*R.lr.n; t_lrbad += R.lr.bad*R.lr.n;
			t_lk += R.ld.n;
			t_mxn += R.mx_n;
			t_mxsum += R.mx_mean*R.mx_n;
			t_mxfail += R.mx_fail;
		}
		for (j = 0; j < S->np; j++) free(S->p[j].cyc);
		free(buf);
	}
	printf("SUMMARY shown %d/%d, wrong shown %d oct + %d other, false "
		"triggers %d, bad estimates %d/%d; length stable %.0f%% "
		"(stable but wrong %.1f%%), with row %.0f%% (%.1f%%); mixture "
		"mean |err| %.2f c, unfinished %d/%d\n",
		t_shown, t_keys, t_woct, t_woth, t_false, t_bad, t_est,
		t_lk ? 100*t_ldst/t_lk : 0, t_lk ? 100*t_ldbad/t_lk : 0,
		t_lk ? 100*t_lrst/t_lk : 0, t_lk ? 100*t_lrbad/t_lk : 0,
		t_mxn ? t_mxsum/t_mxn : 0, t_mxfail, t_mxn + t_mxfail);
	if (follow_mode) {
		printf("FOLLOW %s: corrected by hand %d of %d keys (shown "
			"counts keys shown right before any correction)\n",
			(follow_mode == FOLLOW_OLD) ? "old (follow_shift)" :
			"new (continuity)", t_corr, t_keys);
	}
	return 0;
}
