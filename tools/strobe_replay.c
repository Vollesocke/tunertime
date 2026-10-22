/* strobe_replay.c - plays a recording through the real jni/strobe.c, buffer
 * by buffer as the app does, and prints what each strobe row draws and the
 * focused row's drift.  For measuring the display against noise (knocks
 * of the tuning tool, handling) and onsets on real runs.
 *
 *   strobe_replay [-b N] [-F ratio] run.wav run.txt > out.txt
 *   strobe_replay [-b N] [-F ratio] -c HZ file.wav > out.txt
 *
 *   run.txt   a chromatic run's .txt: the rows (its "rows" header) and
 *             each key's 8' Hz from its start (the strobe is set to it
 *             there, as by the tap, strobe_recalculate)
 *   -b N      samples per audio buffer (Listener.BUFSZ, 1024)
 *   -F ratio  the focused partial over the 8' (parms.focus_ratio: 1 for
 *             the 8' row, 2 for the 4'), its drift is printed; 0 for none
 *   -c HZ     one key of 8' HZ for the whole file (rows 8' 4' 2 2/3'
 *             2' 1 1/3' 1')
 *
 * One line per buffer, after it:
 *   t key  [vmean vmax pk re im] per row  dcyc dsecs damp
 * t: seconds at the buffer's end; key: the key's index (0 before the
 * first).  vmean, vmax: what the row draws on the positive half wave, as
 * the strobe's own brightness (exptab, 101 = dark .. 254 = full), mean and
 * highest over the buffer; pk: the band-passed signal's peak; re, im:
 * the band-passed signal demodulated at the row frequency over the
 * buffer (a reference phase running on from buffer to buffer, so the
 * angle's step per buffer is how the pattern moves: zero is a pattern
 * standing still).  dcyc, dsecs, damp: strobe_drift() and
 * strobe_drift_amp() of the focused row.
 *
 * The brightness comes from the pixels drawn (DRAW_PIXEL is caught here,
 * with the redgreen colours: red is the positive half, green the
 * negative), so it is what the app shows, whatever strobe.c does.
 *
 * Build:
 *   gcc -O2 -Ijni -Itools/hostjni -o strobe_replay tools/strobe_replay.c -lm
 * (strobe.c is included here, for its rows)
 */
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "interface.h"

/* every pixel strobe.c draws comes here */
static void pixel(int x, int y, unsigned c);
#undef DRAW_PIXEL
#define DRAW_PIXEL(x,y,c) pixel((x),(y),(c))

#include "../jni/strobe.c"

/* ---- stand-ins for jni/interface.c ---- */
#define W 400
#define H 600
int width = W, height = H, sample_rate = 44100;
short snd_buf[SND_BUFSAMPS];
int snd_buflen;
void filled_rect(jint *b, int x1, int y1, int x2, int y2, unsigned c) {}
void hline(jint *b, int x1, int y, int x2, unsigned c) {}
void vline(jint *b, int x, int y1, int y2, unsigned c) {}
void xor_hline(jint *b, int x1, int x2, int y) {}
void xor_vline(jint *b, int x, int y1, int y2) {}
unsigned batlow(double t) { return 0; }
void println(const char *fmt, ...) {}
static double key_hz = 440.0;
double cents_to_freq(jobject t, jint cents, jint mult) {
	return key_hz*mult;
}
void set_labels(jobject t, char *l[MAX_STROBES]) {}
void set_pianoname(jobject t, char *p) {}

static double vsum[MAX_STROBES];
static int vmax[MAX_STROBES], vn[MAX_STROBES];

static void
pixel(int x, int y, unsigned c)
{
	int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff;
	int row = y/(H/num_strobes);
	(void)x;
	if ((row < 0) || (row >= num_strobes) || (c == 0xFF000000)) return;
	if (r >= g) {	/* positive half: color_red */
		vsum[row] += r;
		vn[row]++;
		if (r > vmax[row]) vmax[row] = r;
	}
}

#define MAXKEYS 512
static double kstart[MAXKEYS], khz[MAXKEYS];
static int nkeys;

static int
footage_mult(const char *s)
{
	/* "8'", "2 2/3'", "1/2'" -> multiple of 32' (8' = ORGAN_DIV) */
	int a = 0, b = 0, c = 1;
	double ft;
	if (sscanf(s, "%d %d/%d", &a, &b, &c) == 3) ft = a + b/(double)c;
	else if (sscanf(s, "%d/%d", &b, &c) == 2) ft = b/(double)c;
	else if (sscanf(s, "%d", &a) == 1) ft = a;
	else return 0;
	return (int)(8.0*ORGAN_DIV/ft + 0.5);
}

static int
load_txt(const char *path)
{
	FILE *f = fopen(path, "r");
	char line[1000];
	if (!f) { perror(path); return 0; }
	while (fgets(line, sizeof line, f)) {
		int i; double s, e, hz;
		char name[32];
		if (!strncmp(line, "rows ", 5)) {
			char *p = line+5, *q;
			num_strobes = 0;
			while (*p && (num_strobes < MAX_STROBES)) {
				q = strchr(p, ',');
				if (q) *q = 0;
				harm_mult[num_strobes++] = footage_mult(p);
				if (!q) break;
				p = q+1;
			}
		} else if ((sscanf(line, "key %d %lf %lf %31s %*d %lf",
				&i, &s, &e, name, &hz) == 5) &&
			   (nkeys < MAXKEYS)) {
			kstart[nkeys] = s;
			khz[nkeys] = hz;
			nkeys++;
		}
	}
	fclose(f);
	return 1;
}

static short *
load_wav(const char *path, long *nframes)
{
	FILE *f = fopen(path, "rb");
	unsigned char hdr[12], ck[8];
	int rate = 0, chans = 0, bits = 0, fmt = 0;
	long data_len = -1, frames, i;
	short *buf;

	if (!f) { perror(path); return NULL; }
	if ((fread(hdr, 1, 12, f) != 12) || memcmp(hdr, "RIFF", 4) ||
	    memcmp(hdr+8, "WAVE", 4)) {
		fprintf(stderr, "%s: not a RIFF/WAVE file\n", path);
		return NULL;
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
		fprintf(stderr, "%s: need 16-bit PCM\n", path);
		return NULL;
	}
	frames = data_len / (2*chans);
	buf = malloc(frames*sizeof(short));
	for (i = 0; i < frames; i++) {
		unsigned char b[2];
		if (fread(b, 1, 2, f) != 2) break;
		buf[i] = (short)(b[0] | (b[1]<<8));
		fseek(f, 2*(chans-1), SEEK_CUR);
	}
	fclose(f);
	sample_rate = rate;
	*nframes = i;
	return buf;
}

static jint bitmap[1];

int
main(int argc, char **argv)
{
	int buflen = 1024, ai = 1, i, key = -1;
	double focus = 1.0, const_hz = 0.0;
	double ph[MAX_STROBES] = { 0 };
	long n, pos;
	short *wav;
	static const int def_rows[] = { 4, 8, 12, 16, 24, 32 };

	while ((ai < argc) && (argv[ai][0] == '-')) {
		if (!strcmp(argv[ai], "-b") && (ai+1 < argc)) {
			buflen = atoi(argv[ai+1]); ai += 2;
		} else if (!strcmp(argv[ai], "-F") && (ai+1 < argc)) {
			focus = atof(argv[ai+1]); ai += 2;
		} else if (!strcmp(argv[ai], "-c") && (ai+1 < argc)) {
			const_hz = atof(argv[ai+1]); ai += 2;
		} else {
			break;
		}
	}
	if ((ai >= argc) || ((const_hz <= 0.0) && (ai+1 >= argc))) {
		fprintf(stderr, "usage: strobe_replay [-b N] [-F ratio] "
			"run.wav run.txt | -c HZ run.wav\n");
		return 2;
	}
	organ_mode = 1;
	num_strobes = 6;
	memcpy(harm_mult, def_rows, sizeof def_rows);
	if (const_hz > 0.0) {
		kstart[0] = 0.0; khz[0] = const_hz; nkeys = 1;
	} else if (!load_txt(argv[ai+1])) {
		return 1;
	}
	if (!(wav = load_wav(argv[ai], &n))) return 1;
	if (buflen < 1) buflen = 1;
	if (buflen > SND_BUFSAMPS) buflen = SND_BUFSAMPS;

	strobe_init();
	strobe_color("redgreen");
	strobe_recalculate(0);
	strobe_focus(0.0);
	printf("# rows");
	for (i = 0; i < num_strobes; i++) printf(" %d", harm_mult[i]);
	printf("\n# sample_rate %d buffer %d focus %g\n", sample_rate, buflen,
		focus);
	for (pos = 0; pos + buflen <= n; pos += buflen) {
		double t0 = pos/(double)sample_rate;
		double t = (pos+buflen)/(double)sample_rate;
		double f, c = 0.0, s = 0.0, a = 0.0;
		int k = key;
		while ((k+1 < nkeys) && (t0 >= kstart[k+1])) k++;
		if (k != key) {
			key = k;
			key_hz = khz[key];
			/* the tap: Ton+ sets the target, the strobe is
			 * recalculated (filters and drift start over) */
			strobe_focus(focus);
			strobe_recalculate(0);
			for (i = 0; i < num_strobes; i++) {
				printf("# key %d %.3f %.3f row %d %.3f\n",
					key+1, t0, key_hz, i, strobes[i].freq);
			}
		}
		memcpy(snd_buf, wav+pos, buflen*sizeof(short));
		snd_buflen = buflen;
		for (i = 0; i < num_strobes; i++) {
			vsum[i] = 0.0; vmax[i] = 0; vn[i] = 0;
		}
		strobe_newdata(bitmap);
		printf("%.4f %d", t, key+1);
		for (i = 0; i < num_strobes; i++) {
			struct strobe_rec *sr = &strobes[i];
			double re = 0.0, im = 0.0, w;
			int j, pk = 0;
			if (sr->freq <= 0.0) {
				printf(" 0 0 0 0 0");
				continue;
			}
			w = 2.0*M_PI*sr->freq/sample_rate;
			for (j = 0; j < buflen; j++) {
				re += sr->filtered[j]*cos(ph[i]);
				im -= sr->filtered[j]*sin(ph[i]);
				ph[i] = fmod(ph[i] + w, 2.0*M_PI);
				if (abs(sr->filtered[j]) > pk)
					pk = abs(sr->filtered[j]);
			}
			printf(" %.1f %d %d %.0f %.0f",
				vn[i] ? vsum[i]/vn[i] : 0.0, vmax[i], pk,
				2.0*re/buflen, 2.0*im/buflen);
		}
		if (strobe_drift(&f, &c, &s)) strobe_drift_amp(&a);
		printf(" %.6f %.5f %.1f\n", c, s, a);
	}
	free(wav);
	return 0;
}
