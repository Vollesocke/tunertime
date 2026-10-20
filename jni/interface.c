#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <assert.h>
#include <jni.h>
#include "interface.h"
#include "pitch.h"

int width;
int height;

int sample_rate;
short snd_buf[SND_BUFSAMPS];
int snd_buflen;			/* new data in snd_buf goes [0..snd_buflen) */

static JNIEnv *env;

static jclass cl_system;
static jclass cl_printstream;
static jclass cl_string;
static jclass cl_strobe;
static jclass cl_tunertime;
static jclass cl_listener;
static jclass cl_parms;
static jclass cl_eventlog;

static jmethodID mid_println;
static jmethodID mid_st_do_draw;
static jmethodID mid_st_time_to_die;
static jmethodID mid_st_check_surface;
static jmethodID mid_st_pitch_detected;
static jmethodID mid_st_focus_drift;
static jmethodID mid_tt_cents_to_freq;
static jmethodID mid_li_read;
static jmethodID mid_li_get_samplerate;
static jmethodID mid_eventlog_hint;

static jfieldID fid_out;
static jfieldID fid_st_labels;
static jfieldID fid_st_pianoname;
static jfieldID fid_st_ui;
static jfieldID fid_st_paused;
static jfieldID fid_p_strobe_cents;
static jfieldID fid_p_num_strobes;
static jfieldID fid_p_auto_note;
static jfieldID fid_p_mixture_mode;
static jfieldID fid_p_mixture_focus_follow;
static jfieldID fid_p_length_mode;
static jfieldID fid_p_run_active;
static jfieldID fid_p_harm_mult;
static jfieldID fid_p_organ_mode;
static jfieldID fid_p_focus_ratio;
static jfieldID fid_p_strobe_color;
static jfieldID fid_p_fft_mode;
static jfieldID fid_p_fft_size;
static jfieldID fid_p_fft_width;
static jfieldID fid_p_fft_height;
static jfieldID fid_p_fft_xoffset;
static jfieldID fid_p_fft_yoffset;
static jfieldID fid_p_fft_grid_freq;
static jfieldID fid_p_changed;
static jfieldID fid_p_focus_ratio;

static double pitch_expect;	/* Hz of the note shown, for pitch_hint() */

static jintArray bitmap;



void
filled_rect(jint *bitmap_ptr, int x1, int y1, int x2, int y2, unsigned color)
{
	int x,y;
	for (y = y1; y < y2; y++) {
		for (x = x1; x < x2; x++) {
			DRAW_PIXEL(x, y, color);
		}
	}
}

void
hline(jint *bitmap_ptr, int x1, int y, int x2, unsigned color)
{
	int x;
	for (x = x1; x < x2; x++) {
		DRAW_PIXEL(x, y, color);
	}
}

void
vline(jint *bitmap_ptr, int x, int y1, int y2, unsigned color)
{
	int y;
	for (y = y1; y < y2; y++) {
		DRAW_PIXEL(x, y, color);
	}
}

void
xor_hline(jint *bitmap_ptr, int x1, int x2, int y)
{
	int x;
	for (x = x1; x < x2; x++) {
		jint *p = ADDR_PIXEL(x, y);
		*p ^= 0x303438;
	}
}

void
xor_vline(jint *bitmap_ptr, int x, int y1, int y2)
{
	int y;
	for (y = y1; y < y2; y++) {
		jint *p = ADDR_PIXEL(x, y);
		*p ^= 0x303438;
	}
}
/* batlow (F. Crameri, Scientific colour maps): perceptually uniform and
 * readable with colour-vision deficiency.  t in [0, 1], deep blue to pale
 * pink; 10 stops, linearly interpolated */
unsigned
batlow(double t)
{
#define BATLOW_RANGES 9
	static const unsigned stops[BATLOW_RANGES+1][3] =
			{ {   1,  25,  89 },	/* deep blue */
			  {  16,  63,  96 },
			  {  28,  90,  98 },
			  {  60, 109,  86 },
			  { 104, 123,  62 },	/* olive */
			  { 157, 137,  43 },
			  { 210, 147,  67 },	/* orange */
			  { 248, 161, 123 },
			  { 253, 183, 188 },
			  { 250, 204, 250 } };	/* pale pink */
	int k;
	unsigned a, b;

	if (!(t > 0.0)) t = 0.0;	/* also catches NaN */
	if (t > 1.0) t = 1.0;
	t *= BATLOW_RANGES;
	k = (int)t;
	if (k > BATLOW_RANGES-1) k = BATLOW_RANGES-1;
	b = (unsigned)((t-k)*255.0);
	a = 255-b;
	return COLOR_VALUE((a*stops[k][0] + b*stops[k+1][0]) / 255,
			   (a*stops[k][1] + b*stops[k+1][1]) / 255,
			   (a*stops[k][2] + b*stops[k+1][2]) / 255);
}




/* seven segment display:
 *      0x01
 *  0x02    0x04
 *      0x08
 *  0x10    0x20
 *      0x40
 */
static unsigned
char_to_bits(char c)
{
	switch (c) {
		case '0': return 0x77;
		case '1': return 0x24;
		case '2': return 0x5D;
		case '3': return 0x6D;
		case '4': return 0x2E;
		case '5': return 0x6B;
		case '6': return 0x7B;
		case '7': return 0x25;
		case '8': return 0x7F;
		case '9': return 0x6F;
		case 'A': return 0x3F;
		case 'B': return 0x7A;
		case 'C': return 0x53;
		case 'D': return 0x7C;
		case 'E': return 0x5B;
		case 'F': return 0x1B;
		case 'G': return 0x73;
	}
	return 0x28;
}

void
seven_seg(jint *bitmap_ptr, int x, int y, char c, unsigned color)
{
	unsigned bits = char_to_bits(c);
	int l1,l2;
	int x1,x2,x3,x4;
	int y1,y2,y3,y4,y5,y6;

	l1 = SEVEN_WID/5;
	if (l1 < 1) l1 = 1;
	l2 = SEVEN_WID-(3*l1);
	if (l2 < 1) l2 = 1;

	x1 = x;
	x2 = x1+l1;
	x3 = x2+l2;
	x4 = x3+l1;
	if ((x1 < 0) || (x4 > width)) return;

	y1 = y;
	y2 = y1+l1;
	y3 = y2+l2;
	y4 = y3+l1;
	y5 = y4+l2;
	y6 = y5+l1;
	if ((y1 < 0) || (y6 > height)) return;

	if (bits & 0x01) filled_rect(bitmap_ptr, x2,y1,x3,y2, color);
	if (bits & 0x02) filled_rect(bitmap_ptr, x1,y2,x2,y3, color);
	if (bits & 0x04) filled_rect(bitmap_ptr, x3,y2,x4,y3, color);
	if (bits & 0x08) filled_rect(bitmap_ptr, x2,y3,x3,y4, color);
	if (bits & 0x10) filled_rect(bitmap_ptr, x1,y4,x2,y5, color);
	if (bits & 0x20) filled_rect(bitmap_ptr, x3,y4,x4,y5, color);
	if (bits & 0x40) filled_rect(bitmap_ptr, x2,y5,x3,y6, color);
}


void
newdata(jint *bitmap_ptr)
{
	if (fft_mode) {
		fft_newdata(bitmap_ptr);
	} else {
		strobe_newdata(bitmap_ptr);
	}
}


static jshortArray snd_readbuf;

static void
sound_init(jobject this)
{
	sample_rate = (*env)->CallStaticIntMethod(env, cl_listener,
				mid_li_get_samplerate);

	snd_readbuf = (*env)->NewShortArray(env, SND_BUFSAMPS);
}

static int
sound_input(jobject this)
{
	int num_read = (*env)->CallStaticIntMethod(env,
			cl_listener, mid_li_read, snd_readbuf, SND_BUFSAMPS);
	if (num_read < 0) {
		return 0;
	} else if (num_read > 0) {
		(*env)->GetShortArrayRegion(env, snd_readbuf, 0, num_read,
				snd_buf);
		record_feed(snd_buf, num_read);
	}
	snd_buflen = num_read;
	return 1;
}


/* sets up the tokens needed to reference Java things */
static int
jni_init(JNIEnv *env_)
{
	env = env_;
#define CLASS(var, id) \
	cl_##var = (*env)->FindClass(env, id); \
	if (!cl_##var) return 0;
#define METHOD(var, mycl, id, sig) \
	mid_##var = (*env)->GetMethodID(env, cl_##mycl, id, sig); \
	if (!mid_##var) return 0;
#define STMETHOD(var, mycl, id, sig) \
	mid_##var = (*env)->GetStaticMethodID(env, cl_##mycl, id, sig); \
	if (!mid_##var) return 0;
#define FIELD(var, mycl, id, sig) \
	fid_##var = (*env)->GetFieldID(env, cl_##mycl, id, sig); \
	if (!fid_##var) return 0;
#define STFIELD(var, mycl, id, sig) \
	fid_##var = (*env)->GetStaticFieldID(env, cl_##mycl, id, sig); \
	if (!fid_##var) return 0;


	CLASS(system, "java/lang/System")
	CLASS(printstream, "java/io/PrintStream")
	CLASS(string, "java/lang/String")
	CLASS(strobe, "org/galexander/tunertime/Strobe")
	CLASS(tunertime, "org/galexander/tunertime/TunerTime")
	CLASS(listener, "org/galexander/tunertime/Listener")
	CLASS(parms, "org/galexander/tunertime/parms")
	CLASS(eventlog, "org/galexander/tunertime/EventLog")

	METHOD(println, printstream, "println", "(Ljava/lang/String;)V")
	METHOD(st_do_draw, strobe, "do_draw", "([III)Z")
	METHOD(st_time_to_die, strobe, "time_to_die", "()Z")
	METHOD(st_check_surface, strobe, "check_surface", "()[I")
	METHOD(st_pitch_detected, strobe, "pitch_detected", "(DDJ)V")
	METHOD(st_focus_drift, strobe, "focus_drift", "(DDDDDJ)V")
	METHOD(tt_cents_to_freq, tunertime, "cents_to_freq", "(II)D")
	STMETHOD(li_read, listener, "read", "([SI)I")
	STMETHOD(li_get_samplerate, listener, "get_samplerate", "()I")
	STMETHOD(eventlog_hint, eventlog, "hint", "(JDD)V")

	STFIELD(out, system, "out", "Ljava/io/PrintStream;")
	FIELD(st_labels, strobe, "labels", "[Ljava/lang/String;")
	FIELD(st_pianoname, strobe, "pianoname", "Ljava/lang/String;")
	FIELD(st_ui, strobe, "ui", "Lorg/galexander/tunertime/TunerTime;")
	FIELD(st_paused, strobe, "paused", "Z");

	STFIELD(p_strobe_cents, parms, "strobe_cents", "I")
	STFIELD(p_auto_note, parms, "auto_note", "Z")
	STFIELD(p_mixture_mode, parms, "mixture_mode", "Z")
	STFIELD(p_mixture_focus_follow, parms, "mixture_focus_follow", "Z")
	STFIELD(p_length_mode, parms, "length_mode", "Z")
	STFIELD(p_run_active, parms, "run_active", "Z")
	STFIELD(p_num_strobes, parms, "num_strobes", "I")
	STFIELD(p_harm_mult, parms, "harm_mult", "[I")
	STFIELD(p_organ_mode, parms, "organ_mode", "Z")
	STFIELD(p_focus_ratio, parms, "focus_ratio", "D")
	STFIELD(p_strobe_color, parms, "strobe_color", "Ljava/lang/String;")
	STFIELD(p_fft_mode, parms, "fft_mode", "Z")
	STFIELD(p_fft_size, parms, "fft_size", "I")
	STFIELD(p_fft_width, parms, "fft_width", "I")
	STFIELD(p_fft_height, parms, "fft_height", "I")
	STFIELD(p_fft_xoffset, parms, "fft_xoffset", "I")
	STFIELD(p_fft_yoffset, parms, "fft_yoffset", "I")
	STFIELD(p_fft_grid_freq, parms, "fft_grid_freq", "Z")
	STFIELD(p_changed, parms, "changed", "Z")
	STFIELD(p_focus_ratio, parms, "focus_ratio", "D")

	return 1;
}

void
println(const char *fmt, ...)
{
	char buf[1000];
	va_list ap;
	va_start(ap, fmt);
	vsprintf(buf,fmt,ap);
	va_end(ap);
	jstring *x = (*env)->NewStringUTF(env, buf);
	if (!x) return;
	jobject out = (*env)->GetStaticObjectField(env, cl_system, fid_out);
	if (!out) return;
	(*env)->CallVoidMethod(env, out, mid_println, x);
	(*env)->DeleteLocalRef(env, out);
	(*env)->DeleteLocalRef(env, x);
}


double
cents_to_freq(jobject this, jint cents, jint mult)
{
	jobject ui = (*env)->GetObjectField(env, this, fid_st_ui);
	double ret = (*env)->CallDoubleMethod(env, ui, mid_tt_cents_to_freq,
			cents, mult);
	(*env)->DeleteLocalRef(env, ui);
	return ret;
}


void
set_labels(jobject this, char *labels[MAX_STROBES])
{
	jobjectArray j_labels = (*env)->NewObjectArray(env, num_strobes, cl_string, NULL);
	int i;
	for (i = 0; i < num_strobes; i++) {
		jstring lab = (*env)->NewStringUTF(env, labels[i]);
		(*env)->SetObjectArrayElement(env, j_labels, i, lab);
		(*env)->DeleteLocalRef(env, lab);
	}
	(*env)->SetObjectField(env, this, fid_st_labels, j_labels);
	(*env)->DeleteLocalRef(env, j_labels);
}

void
set_pianoname(jobject this, char *pianoname)
{
	jstring j_pianoname = (*env)->NewStringUTF(env, pianoname);
	(*env)->SetObjectField(env, this, fid_st_pianoname, j_pianoname);
	(*env)->DeleteLocalRef(env, j_pianoname);
}

/* hands the drift of the focused row in the last buffer to the UI, with
 * the mean amplitude of its usable blocks (strobe_drift_amp) */
void
focus_drift(jobject this, double freq, double cycles, double secs,
		double buf_secs, double amp)
{
	(*env)->CallVoidMethod(env, this, mid_st_focus_drift,
			freq, cycles, secs, buf_secs, amp,
			(jlong)record_total());
}


static void
resize_bitmap(void)
{
	if (bitmap) {
		(*env)->DeleteLocalRef(env, bitmap);
	}
	bitmap = (*env)->NewIntArray(env, width*height);
}

static void
check_surface(jobject this)
{
	jintArray arr;
	jint c_arr[2] = { 0 };
	arr = (*env)->CallObjectMethod(env, this, mid_st_check_surface);
	if ((*env)->IsSameObject(env, arr, NULL)) {
		/* null means surface did not change */
		return;
	}
	(*env)->GetIntArrayRegion(env, arr, 0, 2, c_arr);
	width = c_arr[0];
	height = c_arr[1];
	(*env)->DeleteLocalRef(env, arr);
	resize_bitmap();

	/* force full update: */
	(*env)->SetStaticBooleanField(env, cl_parms, fid_p_changed, JNI_TRUE);
}

static int
check_parms(jobject this)
{
	if (!(*env)->GetStaticBooleanField(env, cl_parms, fid_p_changed)) {
		return 0;
	}

	(*env)->SetStaticBooleanField(env, cl_parms, fid_p_changed, JNI_FALSE);

	base_cents = (*env)->GetStaticIntField(env, cl_parms, fid_p_strobe_cents);
	num_strobes = (*env)->GetStaticIntField(env, cl_parms, fid_p_num_strobes);
	if (num_strobes < 1) num_strobes = 1;
	if (num_strobes > MAX_STROBES) num_strobes = MAX_STROBES;
	pitch_expect = cents_to_freq(this, base_cents, 1);
	{
		jintArray a = (*env)->GetStaticObjectField(env, cl_parms, fid_p_harm_mult);
		(*env)->GetIntArrayRegion(env, a, 0, MAX_STROBES, harm_mult);
		(*env)->DeleteLocalRef(env, a);
	}
	organ_mode = (*env)->GetStaticBooleanField(env, cl_parms, fid_p_organ_mode);

	{
		jstring t = (*env)->GetStaticObjectField(env, cl_parms, fid_p_strobe_color);
		const char *s = (*env)->GetStringUTFChars(env, t, NULL);
		strobe_color(s);
		(*env)->ReleaseStringUTFChars(env, t, s);
		(*env)->DeleteLocalRef(env, t);
	}

	fft_mode = (*env)->GetStaticBooleanField(env, cl_parms, fid_p_fft_mode);
	fft_size = (*env)->GetStaticIntField(env, cl_parms, fid_p_fft_size);
	fft_width = (*env)->GetStaticIntField(env, cl_parms, fid_p_fft_width);
	fft_height = (*env)->GetStaticIntField(env, cl_parms, fid_p_fft_height);
	fft_xoffset = (*env)->GetStaticIntField(env, cl_parms, fid_p_fft_xoffset);
	fft_yoffset = (*env)->GetStaticIntField(env, cl_parms, fid_p_fft_yoffset);
	fft_grid_freq = (*env)->GetStaticBooleanField(env, cl_parms, fid_p_fft_grid_freq);

	if (fft_mode) {
		fft_recalculate(this);
	} else {
		strobe_recalculate(this);
	}
	return 1;
}

/* Strobe.organ_max_freq(): rows above this fraction of the sample rate are
 * left blank in organ mode, and so can't be focused */
JNIEXPORT jdouble JNICALL
Java_org_galexander_tunertime_Strobe_organ_1max_1freq(JNIEnv *env_, jclass cl)
{
	return ORGAN_MAX_FREQ;
}

/* This is the entry point from the Java code...it is a loop that checks for
 * thread state changes, reads audio, then writes to screen. */
JNIEXPORT void JNICALL
Java_org_galexander_tunertime_Strobe_runner(JNIEnv *env_, jobject this)
{
	double logged_focus = -1.0, logged_expect = -1.0;
	if (!jni_init(env_)) {
		return;
	}

	bitmap = NULL;

	sound_init(this);
	strobe_init();
	fft_init();
	pitch_init(sample_rate);
	record_init(sample_rate);
	pitch_expect = 0.0;

	while (1) {
		check_surface(this);

		if ((*env)->CallBooleanMethod(env, this, mid_st_time_to_die)) {
			println("thread interrupted");
			return;
		}

		if (!sound_input(this)) {
			return;
		}

		/* auto note detection (and length mode, which measures the
		 * pitch): analysed here, outside the critical region below,
		 * and handed to the UI as a plain call */
		if ((snd_buflen > 0) && !fft_mode &&
		    ((*env)->GetStaticBooleanField(env, cl_parms, fid_p_auto_note) ||
		     (*env)->GetStaticBooleanField(env, cl_parms, fid_p_mixture_mode) ||
		     (*env)->GetStaticBooleanField(env, cl_parms, fid_p_length_mode) ||
		     (*env)->GetStaticBooleanField(env, cl_parms, fid_p_run_active)) &&
		    !(*env)->GetBooleanField(env, this, fid_st_paused)) {
			double hz, conf;
			/* the partial the user focused on (organ mode),
			 * looked for near where the shown note puts it.  In
			 * mixture mode the focus is the reference rank
			 * measured, and the key is detected independent of
			 * it (Onno), unless Settings asks to follow the
			 * reference rank (then TunerTime.on_pitch() keeps the
			 * key from jumping when another rank sounds alone) */
			jboolean unfocused = (*env)->GetStaticBooleanField(env,
					cl_parms, fid_p_mixture_mode) &&
				!(*env)->GetStaticBooleanField(env, cl_parms,
					fid_p_mixture_focus_follow);
			double detector_focus = unfocused ? 0.0 :
				(*env)->GetStaticDoubleField(env, cl_parms,
					fid_p_focus_ratio);
			double detector_expect = unfocused ? 0.0 : pitch_expect;
			long long at = record_total() - snd_buflen;
			pitch_hint(detector_focus, detector_expect);
			if ((detector_focus != logged_focus) ||
			    (detector_expect != logged_expect)) {
				(*env)->CallStaticVoidMethod(env, cl_eventlog,
					mid_eventlog_hint, (jlong)at, detector_focus,
					detector_expect);
				logged_focus = detector_focus;
				logged_expect = detector_expect;
			}
			if (pitch_feed(snd_buf, snd_buflen, &hz, &conf)) {
				/* the sample the analysed window ended
				 * on, for the debug recordings' timeline */
				at += pitch_last_at();
				(*env)->CallVoidMethod(env, this,
					mid_st_pitch_detected, hz, conf,
					(jlong)at);
			}
		}

		/* process sound into bitmap */
		if (bitmap) {
			int changed = check_parms(this);
			int paused = (*env)->GetBooleanField(env, this,
							fid_st_paused);
			double freq, cycles, secs;

			strobe_focus((*env)->GetStaticDoubleField(env,
						cl_parms, fid_p_focus_ratio));

			jint *bitmap_ptr = (*env)->GetPrimitiveArrayCritical(env, bitmap, 0);
			if (changed) {
				filled_rect(bitmap_ptr, 0, 0, width, height, 0xFF000000);
				if (fft_mode) {
					fft_redraw(bitmap_ptr);
				} else {
					strobe_redraw(bitmap_ptr);
				}
			}
			if (!paused) {
				newdata(bitmap_ptr);
			}
			(*env)->ReleasePrimitiveArrayCritical(env, bitmap, bitmap_ptr, 0);
			/* no JNI calls inside the critical region, so the
			 * focused row's drift is reported only now */
			if (!fft_mode && !paused && (snd_buflen > 0) &&
			    strobe_drift(&freq, &cycles, &secs)) {
				double amp = 0.0;
				strobe_drift_amp(&amp);
				focus_drift(this, freq, cycles, secs,
					snd_buflen/(double)sample_rate, amp);
			}
			/* send bitmap to surface */
			if ((*env)->CallBooleanMethod(env, this, mid_st_do_draw, bitmap, width, height)) {
				/* thread was interrupted, must be time for it to die */
				println("thread interrupted in do_draw()");
				return;
			}
		}
	}
}
