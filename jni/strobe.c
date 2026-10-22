#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <assert.h>
#include "interface.h"

struct strobe_rec {
		/* location within bitmap */
	int height;
	int y;

		/* center frequency */
	double freq;
		/* filter coefficients: */
	double a0,a1,a2;
	double b1,b2;
		/* filter history: */
	double x1,x2;
	double y1,y2;
		/* filter output: */
	signed short filtered[SND_BUFSAMPS];

	unsigned xsamps_per_col;	/* 100,000th samples per column */
	unsigned prev_pos;		/* pos within column of last point */
	unsigned prev_x, prev_y;	/* display location of last point */
	unsigned prev_color;		/* display color of last point */
	int samp_max;			/* highest amplitude seen so far */
	int pre_max;			/* samp_max before a jump, 0 if none
					 * pending (strobe_gain) */
	int jump_samps;			/* samples since that jump */
	unsigned multiplier;		/* horizontal scale */
	int last_cleared_col;
};


int base_cents;
int num_strobes = 5;
int harm_mult[MAX_STROBES] = { 1, 2, 4, 6, 8, 1, 1, 1 };
	/* organ mode: harm_mult[] holds multiples of the 32' pitch
	 * (ORGAN_DIV = 8'), rows are labelled by footage, and rows the
	 * sample rate cannot carry (ORGAN_MAX_FREQ) are left blank */
int organ_mode = 0;

static struct strobe_rec strobes[MAX_STROBES];


void
strobe_init(void)
{
	memset(strobes, 0, sizeof strobes);
}


static unsigned color_red(int x) {
	return COLOR_VALUE(x, x/2, x/2);
}
static unsigned color_green(int x) {
	return COLOR_VALUE(x/2, x, x/2);
}
static unsigned color_white(int x) {
	x = 0x80 + (x/2);
	return COLOR_VALUE(x, x, x);
}
static unsigned color_black(int x) {
	x = 0x80 - (x/2);
	return COLOR_VALUE(x, x, x);
}
static unsigned color_blue(int x) {
	return COLOR_VALUE(x/2, x/2, x);
}
static unsigned color_yellow(int x) {
	return COLOR_VALUE(x, x, x/2);
}
static unsigned color_slate_dark(int x) {
	return COLOR_VALUE(x/6, (x*2)/9, x/3);
}
static unsigned color_slate_light(int x) {
	return COLOR_VALUE((x*29)/32, (x*29)/32, (x*15)/16);
}

	/* batlow: the positive half of the wave sweeps the whole map from
	 * deep blue to pale pink as it gets louder, the negative half stays
	 * deep blue, so quiet input is dark like the other schemes.  x comes
	 * through exptab[], whose quietest value is about 101 */
#define BATLOW_X0 101
static unsigned color_batlow_hi(int x) {
	return batlow((x-BATLOW_X0)/(double)(254-BATLOW_X0));
}
static unsigned color_batlow_lo(int x) {
	return batlow(0.0);
}

static unsigned (*color1)(int x) = color_batlow_hi;
static unsigned (*color2)(int x) = color_batlow_lo;

void
strobe_color(const char *s)
{
	if (!s || !strcmp(s, "batlow")) {
		color1 = color_batlow_hi;
		color2 = color_batlow_lo;
	} else if (!strcmp(s, "slate")) {
		color1 = color_slate_dark;
		color2 = color_slate_light;
	} else if (!strcmp(s, "redgreen")) {
		color1 = color_red;
		color2 = color_green;
	} else if (!strcmp(s, "blackwhite")) {
		color1 = color_black;
		color2 = color_white;
	} else if (!strcmp(s, "blueyellow")) {
		color1 = color_blue;
		color2 = color_yellow;
	} else if (!strcmp(s, "redblue")) {
		color1 = color_red;
		color2 = color_blue;
	} else {
		color1 = color_batlow_hi;
		color2 = color_batlow_lo;
	}
}


static void
filter_recalc(struct strobe_rec *s)
{
	double R,K;
	double R2;
	double c;
	double freq = s->freq;
	double bandwidth = freq/20.0;

	freq /= ((double)sample_rate);
	bandwidth /= ((double)sample_rate);

	c = cos(2.0*M_PI*freq);
	R = 1.0-(3.0*bandwidth);
	R2 = R*R;
	K = ((1.0-(2.0*R*c))+R2)/(2.0-(2.0*c));

	s->a0 = 1.0-K;
	s->a1 = 2.0*(K-R)*c;
	s->a2 = R2-K;
	s->b1 = 2.0*R*c;
	s->b2 = -R2;

	s->x1 = s->x2 = s->y1 = s->y2 = 0.0;
}


static void
strobe_recalc_single(struct strobe_rec *s)
{
	if (s->freq <= 0.0) {
		/* blank row (organ mode, above ORGAN_MAX_FREQ) */
		return;
	}
	filter_recalc(s);

	double t = ((double)sample_rate) / (double)s->freq;
	s->xsamps_per_col = t*100000.0+0.5;

	s->multiplier = (s->freq + width) / width;
	if (s->multiplier < 1) {
		s->multiplier = 1;
	}
}

static void
strobe_filter(struct strobe_rec *s)
{
	double x1,x2,y0,y1,y2;
	double a0,a1,a2,b1,b2;
	int i;

	a0 = s->a0; a1 = s->a1; a2 = s->a2; b1 = s->b1; b2 = s->b2;
	x1 = s->x1; x2 = s->x2; y1 = s->y1; y2 = s->y2;

	for (i = 0; i < snd_buflen; i++) {
		double in = snd_buf[i];
		y0 = ((a0*in) + (a1*x1) + (a2*x2) + (b1*y1) + (b2*y2));
		x2 = x1;
		x1 = in;
		y2 = y1;
		y1 = y0;
		s->filtered[i] = y0;
	}

	s->x1 = x1; s->x2 = x2; s->y1 = y1; s->y2 = y2;
}


#define NUM_CLEAR 25	/* number of columns to clear at once */
static void
clear_col(jint *bitmap_ptr, struct strobe_rec *s, int col)
{
	int start_x, stop_x;
	if (col < s->last_cleared_col) {
		if (col+(2*NUM_CLEAR) < s->last_cleared_col) {
			/* must have looped to zero */
			col += width;
		} else {
			/* nothing to do, already cleared */
			return;
		}
	} else if (col+(2*NUM_CLEAR) >= s->last_cleared_col+width) {
		/* this column has already been cleared, just last_cleared_col
		 * has looped to zero */
		return;
	}

	start_x = s->last_cleared_col;
	stop_x = col+NUM_CLEAR;
	s->last_cleared_col = (stop_x % width);

	if (stop_x > width) {
		if (stop_x >= width*2) {
			start_x = 0;
			stop_x = 0;
		}
		filled_rect(bitmap_ptr,
			start_x, s->y, width, s->y+s->height-1, 0xFF000000);
		start_x = 0;
		stop_x -= width;
	}

	if (start_x == stop_x) {
		return;
	}
	filled_rect(bitmap_ptr,
		start_x, s->y, stop_x+1, s->y+s->height-1, 0xFF000000);
}

	/* the gain of a row follows its loudest sample at once and falls
	 * back by a tenth per buffer, so a pipe fills the row however loud.
	 * A knock of the tuning tool on the pipe, the cone, the slide: a
	 * burst, often far above the pipe in its band, and gone again in
	 * 0.05-0.3 s.  Followed like a new note, it would leave the pipe's
	 * pattern dim for up to a second (the gain falls 39 dB/s), on the
	 * weak upper rows above all.  So a jump of the buffer's peak to
	 * GAIN_JUMP times the gain is remembered, and when the peak is back
	 * within GAIN_BACK times the gain before inside GAIN_JUMP_SECS, the
	 * gain goes straight back to it.  A key played (or louder) stays up
	 * longer, and its jump stands: it looks as before, and so do a
	 * release and a soft pipe after silence (the gain only falls there) */
#define GAIN_JUMP 4
#define GAIN_BACK 2
#define GAIN_JUMP_SECS 0.5
static void
strobe_gain(struct strobe_rec *s, int start_max, int buf_peak)
{
	if (s->pre_max > 0) {
		s->jump_samps += snd_buflen;
		if (buf_peak <= GAIN_BACK*s->pre_max) {
			/* a transient: back to the gain before it */
			s->samp_max = s->pre_max;
			s->pre_max = 0;
		} else if (s->jump_samps > GAIN_JUMP_SECS*sample_rate) {
			s->pre_max = 0;		/* a new level */
		}
	} else if ((start_max > 0) && (buf_peak > GAIN_JUMP*start_max)) {
		s->pre_max = start_max;
		s->jump_samps = 0;
	}
}

static void
strobe_drawnew(jint *bitmap_ptr, struct strobe_rec *s)
{
	static int exptab[256] = { -1, };
	unsigned pos = s->prev_pos;
	unsigned color, last_color = s->prev_color;
	int samp_max = (s->samp_max*9)/10;
	int start_max = samp_max, buf_peak = 0;
	int samp_divider = 1+(samp_max/256);
	unsigned last_x = s->prev_x, last_y = s->prev_y;
	unsigned h = s->height-1;
	unsigned x = last_x, y;
	int i;
	unsigned multiplier = s->multiplier;
	unsigned xsamps_per_pos = s->xsamps_per_col * multiplier;
	unsigned xsamps_per_col = s->xsamps_per_col;
	unsigned msamps_per_col = s->xsamps_per_col/100;

	if (!width) {				/* prevent divide by zero */
		return;
	}

	if (exptab[0] == -1) {
#define LOG_STEEP 40.0
		int i;
		double scale = 254.0/log(LOG_STEEP*256.0);
		for (i = 0; i < 256; i++) {
			exptab[i] = log((i+1.0)*LOG_STEEP)*scale;
		}
	}

	for (i = 0; i < snd_buflen; i++) {
		int samp = s->filtered[i];

		if (abs(samp) > buf_peak) {
			buf_peak = abs(samp);
		}
		if (abs(samp) > samp_max) {
			samp_max = abs(samp);
			samp_divider = 1+(samp_max/256);
		}
		samp /= samp_divider;

		pos += 100000;
		while (pos > xsamps_per_pos) {
			pos -= xsamps_per_pos;
			x++;
			x %= width;
		}
		if (pos > xsamps_per_col) {
			/* multiplier must be greater than 1, skipping this
			 * column.. */
			continue;
		}
		y = ((pos/100) * h) / msamps_per_col;
		if (x != last_x) {
			clear_col(bitmap_ptr, s, x);
			last_x = x;
		} else if (y > last_y+1) {
			/* if there are more pixels than samples, then fill in
			 * the in-betweens */
			int ty;
			for (ty = last_y+1; ty < y; ty++) {
				DRAW_PIXEL(last_x, ty+s->y, last_color);
			}
		}

		if (samp >= 0) {
			color = color1(exptab[samp]);
		} else {
			color = color2(exptab[-samp]);
		}
		DRAW_PIXEL(x, y+s->y, color);

		last_y = y;
		last_color = color;
	}
	s->prev_pos = pos;
	s->prev_x = last_x;
	s->prev_y = last_y;
	s->prev_color = last_color;
	s->samp_max = samp_max;
	strobe_gain(s, start_max, buf_peak);
}

/* draw a vertical tic mark at 1 second into the buffer */
static void
strobe_draw_htic(jint *bitmap_ptr, struct strobe_rec *s)
{
	int x;
	int step;
	step = s->freq / s->multiplier;
	for (x = step; x < width; x += step) {
		int h = (s->height*3)/10;
		vline(bitmap_ptr, x, s->y, s->y+h, 0xFFFFB300);
		vline(bitmap_ptr, x-1, s->y, s->y+h, 0xFFFFB300);
		vline(bitmap_ptr, x-2, s->y, s->y+h, 0xFFFFB300);
	}
}


	/* drift of the focused row (organ mode): a row's pattern moves one
	 * stripe for each cycle the partial gains on the row frequency.
	 * the band-passed signal of that row is demodulated at the row
	 * frequency over blocks of a few whole periods, and the phase step
	 * from one block to the next, unwrapped, is that drift.  the
	 * band-pass has already removed the other partials, so this follows
	 * just the chosen one, and over a couple of seconds it resolves far
	 * below a cent.  steps up to half a cycle per block are unambiguous,
	 * i.e. up to freq/(2*DRIFT_MAX_PERIODS) Hz off, wider than the
	 * band-pass anyway */
#define DRIFT_MAX_PERIODS 8	/* whole periods per block, at most */
#define DRIFT_MAX_SECS 0.1	/* block length, at most (low rows) */
#define DRIFT_MIN_AMP 8.0	/* quieter blocks are ignored (16-bit units) */
#define DRIFT_REL_AMP 0.1	/* ...as are blocks this far below the peak */
#define DRIFT_PEAK_HALFLIFE 1.0	/* seconds */
	/* after a reset the band-pass starts from rest and rings at the row
	 * frequency, which pulls the phase towards "in tune"; blocks are
	 * ignored for this many filter time constants (filter_recalc: the
	 * pole radius is 1-3*bw, so tau = 20/(3*freq) seconds).  The same
	 * after an onset: a block more than DRIFT_ONSET times louder than
	 * the one before, or, after a block below the gate, the first one
	 * DRIFT_ONSET times above it (the key played after the row was set,
	 * the usual order with mixture mode's Measure; a weak row merely
	 * flickering about the gate is no onset).  The pipe's attack rings
	 * the filter just the same, and the pipe's own speech (chiff, its
	 * pitch settling) is off for some 10-50 ms in the treble, a few
	 * dozen periods in the bass: so at least DRIFT_ONSET_SECS */
#define DRIFT_SETTLE_TAUS 4.0
#define DRIFT_ONSET 2.0
#define DRIFT_ONSET_SECS 0.2
	/* a knock on the pipe or the tuning cone is an onset too, often far
	 * above the pipe in the row's band, and its blocks are skipped as
	 * above.  But it raised the peak, and with it the gate: after a
	 * knock more than 20 dB over the pipe the pipe stayed below the gate
	 * until the peak had decayed (up to 1.5 s in tools/strobe_replay.c),
	 * and a mixture measurement could take that for the key released
	 * (mixture.java RELEASE_GAP).
	 * So when, within DRIFT_TRANSIENT_SECS of an onset, a block is back
	 * within DRIFT_ONSET times the peak before it, the peak goes back to
	 * that (a key played stays up, and keeps its peak) */
#define DRIFT_TRANSIENT_SECS 0.5

static int focus_row = -1;
static struct {
	double w;		/* reference step, radians per sample */
	double ph;		/* reference phase */
	double re, im;		/* sum over the current block */
	int n, len;		/* samples in the current block, block length */
	double prev_re, prev_im;	/* sum over the previous block */
	int prev_ok;		/* previous block was loud enough */
	double peak;		/* recent block amplitude, decaying */
	double cycles, secs;	/* drift found in the last buffer */
	double amp_sum;		/* ... and its blocks' amplitudes, summed */
	int amp_n;
	int settle;		/* samples still to ignore after a reset */
	int settle_len;		/* ... and after an onset */
	double prev_amp;	/* the previous block's amplitude */
	int quiet;		/* a block was below the gate since the
				 * last onset */
	double tr_peak;		/* the peak before the last onset (decaying) */
	int tr_left;		/* samples left in which a block back near it
				 * makes that onset a transient */
} drift;

static void
drift_reset(void)
{
	struct strobe_rec *s;
	int k;

	memset(&drift, 0, sizeof drift);
	if ((focus_row < 0) || (focus_row >= num_strobes) ||
	    (sample_rate <= 0)) {
		return;
	}
	s = &strobes[focus_row];
	if (s->freq <= 0.0) {
		return;
	}
	drift.w = 2.0*M_PI*s->freq/(double)sample_rate;
	k = s->freq*DRIFT_MAX_SECS;
	if (k > DRIFT_MAX_PERIODS) k = DRIFT_MAX_PERIODS;
	if (k < 1) k = 1;
	drift.len = k*(double)sample_rate/s->freq + 0.5;
	drift.settle = DRIFT_SETTLE_TAUS*20.0*sample_rate/(3.0*s->freq);
	drift.settle_len = drift.settle;
	if (drift.settle_len < DRIFT_ONSET_SECS*sample_rate) {
		drift.settle_len = DRIFT_ONSET_SECS*sample_rate;
	}
}

/* ratio is parms.focus_ratio: the focused partial's frequency over the
 * note's 8', 0 for none.  picks the row showing it */
void
strobe_focus(double ratio)
{
	double div = organ_mode ? (double)ORGAN_DIV : 1.0;
	int row = -1;
	int i;

	for (i = 0; (i < num_strobes) && (ratio > 0.0); i++) {
		if (fabs(harm_mult[i]/div - ratio) < 1e-6) {
			row = i;
			break;
		}
	}
	if (row != focus_row) {
		focus_row = row;
		drift_reset();
	}
}

static void
drift_block(void)
{
	double amp = 2.0*hypot(drift.re, drift.im)/drift.n;
	double secs = drift.n/(double)sample_rate, gate, before;
	double decay = pow(0.5, secs/DRIFT_PEAK_HALFLIFE);
	int ok;

	drift.peak *= decay;
	if (drift.tr_left > 0) {
		/* within DRIFT_TRANSIENT_SECS of an onset: back to the level
		 * before it is a transient (a knock), so its peak goes.  Not
		 * ended there, as a knock often rises in bumps */
		drift.tr_peak *= decay;
		drift.tr_left -= drift.n;
		if (amp <= DRIFT_ONSET*drift.tr_peak) {
			drift.peak = drift.tr_peak;
			drift.quiet = 0;
		}
	}
	before = drift.peak;
	if (amp > drift.peak) {
		drift.peak = amp;
	}
	gate = fmax(DRIFT_MIN_AMP, DRIFT_REL_AMP*drift.peak);
	ok = (amp >= gate);
	if (!ok) {
		drift.quiet = 1;
	}
	if (ok && ((amp > DRIFT_ONSET*drift.prev_amp) ||
		   (drift.quiet && (amp > DRIFT_ONSET*gate)))) {
		drift.settle = drift.settle_len;
		drift.quiet = 0;
		if (drift.tr_left <= 0) {
			drift.tr_peak = before;
			drift.tr_left = (int)fmax(drift.settle_len,
					DRIFT_TRANSIENT_SECS*sample_rate);
		}
	}
	drift.prev_amp = amp;
	if (drift.settle > 0) {
		drift.settle -= drift.n;
		ok = 0;
	}
	if (ok && drift.prev_ok) {
		/* angle of this block's sum against the previous one's,
		 * positive when the partial is sharp of the row */
		double dph = atan2(drift.im*drift.prev_re -
					drift.re*drift.prev_im,
				   drift.re*drift.prev_re +
					drift.im*drift.prev_im);
		drift.cycles += dph/(2.0*M_PI);
		drift.secs += secs;
		drift.amp_sum += amp;
		drift.amp_n++;
	}
	drift.prev_re = drift.re;
	drift.prev_im = drift.im;
	drift.prev_ok = ok;
	drift.re = drift.im = 0.0;
	drift.n = 0;
}

static void
drift_feed(struct strobe_rec *s)
{
	int i;

	drift.cycles = drift.secs = 0.0;
	drift.amp_sum = 0.0;
	drift.amp_n = 0;
	if (drift.len <= 0) {
		return;
	}
	for (i = 0; i < snd_buflen; i++) {
		double y = s->filtered[i];
		drift.re += y*cos(drift.ph);
		drift.im -= y*sin(drift.ph);
		drift.ph += drift.w;
		if (drift.ph >= 2.0*M_PI) {
			drift.ph -= 2.0*M_PI;
		}
		if (++drift.n >= drift.len) {
			drift_block();
		}
	}
}

/* the focused row's drift over the last buffer: the partial gained
 * *cycles on the row frequency *freq during *secs of usable signal.
 * returns 0 if no row is focused */
int
strobe_drift(double *freq, double *cycles, double *secs)
{
	if ((focus_row < 0) || (drift.len <= 0)) {
		return 0;
	}
	*freq = strobes[focus_row].freq;
	*cycles = drift.cycles;
	*secs = drift.secs;
	return 1;
}


/* the mean amplitude (16-bit units) of the blocks that went into the last
 * buffer's drift, 0 if none.  over a measurement, the swing of these,
 * (hi-lo)/(hi+lo), shows the partial beating: a second pipe sounds on it
 * (celeste, a rank not muted), and the drift then follows the louder one
 * or lands between them.  in tools/sim_organ.c two pipes of equal level
 * swing 0.7-0.85 (the measurement up to 8 c off), a second one at half
 * level 0.3-0.6 (up to 1.3 c off); a single pipe stays below 0.3 up to
 * C6, but in the reverberant treble the room alone swings it up to 0.5,
 * rarely 0.8, and a tremulant 0.2-0.65.  returns 0 if no row is focused */
int
strobe_drift_amp(double *amp)
{
	if ((focus_row < 0) || (drift.len <= 0)) {
		return 0;
	}
	*amp = (drift.amp_n > 0) ? drift.amp_sum/drift.amp_n : 0.0;
	return 1;
}


/* just draws the separators...everything else is redrawn by strobe_newdata */
void
strobe_redraw(jint *bitmap_ptr)
{
	int h = strobes[0].height;
	int y = h-1;
	int i;

	for (i = 0; i < num_strobes-1; i++) {
		hline(bitmap_ptr, 0, y, width, 0xFF2E343C);
		y += h;
	}
}

void
strobe_newdata(jint *bitmap_ptr)
{
	int i;
	for (i = 0; i < num_strobes; i++) {
		struct strobe_rec *s = &strobes[i];
		if ((snd_buflen == 0) || (s->freq <= 0.0)) {
			/* not really any new data, or a blank row */
			continue;
		}
		strobe_filter(s);
		strobe_drawnew(bitmap_ptr, s);
		strobe_draw_htic(bitmap_ptr, s);
		if (i == focus_row) {
			drift_feed(s);
		}
	}
}


/* organ footage of a row holding m times the 32' pitch, like "10 2/3'" */
static void
footage_str(char *buf, int m)
{
	int num = 8*ORGAN_DIV;		/* 32' */
	int whole, rem, a, b;

	if (m < 1) m = 1;
	whole = num/m;
	rem = num%m;
	a = rem; b = m;			/* gcd, to reduce rem/m */
	while (b) {
		int t = a%b;
		a = b;
		b = t;
	}
	if (rem == 0) {
		sprintf(buf, "%d'", whole);
	} else if (whole == 0) {
		sprintf(buf, "%d/%d'", rem/a, m/a);
	} else {
		sprintf(buf, "%d %d/%d'", whole, rem/a, m/a);
	}
}

static void
update_labels(jobject this)
{
	char labs[MAX_STROBES][100];
	char *labels[MAX_STROBES];
	int i;
	char pianoname[10];
	static const char notes[12][3] =
	    { "A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#" };
	const int semi = 100*UNITS_PER_CENT;
	int note = ((base_cents+semi/2)/semi)%12;

	for (i = 0; i < num_strobes; i++) {
		int mult = harm_mult[i];
		char *ext = ((mult==1) ? "st" : ((mult==2) ? "nd" :
				((mult==3) ? "rd" : "th")));
		labels[i] = labs[i];
		if (organ_mode) {
			char foot[20];
			footage_str(foot, mult);
			if (strobes[i].freq > 0.0) {
				sprintf(labels[i], "%7s %7.1f", foot,
					strobes[i].freq);
			} else {
				sprintf(labels[i], "%7s too high", foot);
			}
			continue;
		}
		sprintf(labels[i], "%2d%s %7.1f", mult, ext, strobes[i].freq);
	}
	set_labels(this, labels);
	i = ((base_cents+semi/2)/semi)+1;
	if ((i < 1) || (i > 88)) {
		sprintf(pianoname, "%s__", notes[note]);
	} else {
		sprintf(pianoname, "%s%d", notes[note], i);
	}
	set_pianoname(this, pianoname);
}


void
strobe_recalculate(jobject this)
{
	int i;
	int cents = base_cents;
	int sheight = height/num_strobes;
	int y = 0;

	for (i = 0; i < num_strobes; i++) {
		int mult = harm_mult[i];
		strobes[i].prev_pos = 0;
		strobes[i].y = y;
		strobes[i].height = sheight;
		if (organ_mode) {
			/* no stretch in organ mode, so the partials are
			 * exact multiples of the 32' pitch */
			strobes[i].freq = cents_to_freq(this, cents, 1) *
						mult / ORGAN_DIV;
			if (strobes[i].freq > ORGAN_MAX_FREQ*sample_rate) {
				strobes[i].freq = 0.0;
			}
		} else {
			strobes[i].freq = cents_to_freq(this, cents, mult);
		}
		strobe_recalc_single(&strobes[i]);

		y += sheight;
	}
	drift_reset();
	update_labels(this);
}
