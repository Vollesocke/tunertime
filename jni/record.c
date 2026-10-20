/* record.c - keeps the last few seconds of microphone input, so that the
 * Java side (DebugRecorder) can save what the detector heard when the user
 * had to correct it.  Fed from the strobe thread with every buffer read;
 * DebugRecorder.grab() copies it out on another thread, hence the lock.
 *
 * Every sample fed is counted (rec_total, never reset), which gives the
 * position in the input that EventLog stamps its events with: the
 * detector's estimates and the strobe drift from the strobe thread
 * (record_total()), UI events from the UI thread (EventLog.sample_pos(),
 * interpolated from the time of the last feed). */
#include <pthread.h>
#include <string.h>
#include <time.h>
#include <jni.h>
#include "interface.h"

#define REC_SECS	8
#define REC_MAX		(48000*REC_SECS)

static short rec[REC_MAX];
static int rec_len;		/* capacity in use, REC_SECS of sample_rate */
static int rec_pos;		/* next write */
static int rec_filled;
static int rec_rate;
static long long rec_total;	/* samples fed since the app started */
static struct timespec rec_time;	/* when the last ones were fed */
static pthread_mutex_t rec_lock = PTHREAD_MUTEX_INITIALIZER;

void
record_init(int rate)
{
	pthread_mutex_lock(&rec_lock);
	rec_len = rate*REC_SECS;
	if ((rec_len <= 0) || (rec_len > REC_MAX)) rec_len = REC_MAX;
	rec_pos = rec_filled = 0;
	rec_rate = rate;
	pthread_mutex_unlock(&rec_lock);
}

void
record_feed(const short *s, int n)
{
	pthread_mutex_lock(&rec_lock);
	rec_total += n;
	clock_gettime(CLOCK_MONOTONIC, &rec_time);
	while ((n > 0) && (rec_len > 0)) {
		int k = rec_len - rec_pos;
		if (k > n) k = n;
		memcpy(rec+rec_pos, s, k*sizeof(short));
		rec_pos = (rec_pos+k) % rec_len;
		rec_filled += k;
		if (rec_filled > rec_len) rec_filled = rec_len;
		s += k;
		n -= k;
	}
	pthread_mutex_unlock(&rec_lock);
}

/* the number of samples fed so far (strobe thread) */
long long
record_total(void)
{
	long long t;
	pthread_mutex_lock(&rec_lock);
	t = rec_total;
	pthread_mutex_unlock(&rec_lock);
	return t;
}

/* EventLog.sample_pos(): where in the input now is, for an event on
 * another thread: the samples fed so far, plus the time since the last
 * feed (at most one second of it).  Audio input latency is not taken
 * off, so an event shows a few buffers late against the sound */
JNIEXPORT jlong JNICALL
Java_org_galexander_tunertime_EventLog_sample_1pos(JNIEnv *env, jclass cl)
{
	struct timespec now;
	long long t;
	double dt;
	clock_gettime(CLOCK_MONOTONIC, &now);
	pthread_mutex_lock(&rec_lock);
	t = rec_total;
	dt = (now.tv_sec - rec_time.tv_sec) +
		(now.tv_nsec - rec_time.tv_nsec)*1e-9;
	if ((t > 0) && (dt > 0.0)) {
		t += (long long)((dt < 1.0 ? dt : 1.0)*rec_rate);
	}
	pthread_mutex_unlock(&rec_lock);
	return t;
}

/* copies the most recent samples, oldest first, into dst; returns how
 * many.  end[0]: the position (record_total()) just after the last */
JNIEXPORT jint JNICALL
Java_org_galexander_tunertime_DebugRecorder_grab(JNIEnv *env, jclass cl,
		jshortArray dst, jlongArray end)
{
	jint n = (*env)->GetArrayLength(env, dst);
	jlong e;
	pthread_mutex_lock(&rec_lock);
	e = rec_total;
	if (n > rec_filled) n = rec_filled;
	int start = (rec_pos - n + rec_len) % ((rec_len > 0) ? rec_len : 1);
	int first = rec_len - start;
	if (first > n) first = n;
	if (n > 0) {
		(*env)->SetShortArrayRegion(env, dst, 0, first, rec+start);
		if (n > first) {
			(*env)->SetShortArrayRegion(env, dst, first, n-first,
						rec);
		}
	}
	pthread_mutex_unlock(&rec_lock);
	(*env)->SetLongArrayRegion(env, end, 0, 1, &e);
	return n;
}

/* RunRecorder.grab_from(): the samples from input position from on, as
 * many as fit into dst, for streaming a chromatic run to a file.  from < 0:
 * none, just where the input is now.  pos[0]: the position of the first
 * sample copied (later than from if those were overwritten already, a gap
 * the caller fills), pos[1]: just after the last.  returns how many */
JNIEXPORT jint JNICALL
Java_org_galexander_tunertime_RunRecorder_grab_1from(JNIEnv *env, jclass cl,
		jlong from, jshortArray dst, jlongArray pos)
{
	jint max = (*env)->GetArrayLength(env, dst);
	jlong p[2];
	jint n;
	pthread_mutex_lock(&rec_lock);
	long long oldest = rec_total - rec_filled;
	if ((from < 0) || (from > rec_total)) from = rec_total;
	if (from < oldest) from = oldest;
	n = (jint)(rec_total - from);
	if (n > max) n = max;
	if ((n > 0) && (rec_len > 0)) {
		/* rec_pos is where rec_total goes */
		int start = (int)((rec_pos - (rec_total - from)) % rec_len);
		if (start < 0) start += rec_len;
		int first = rec_len - start;
		if (first > n) first = n;
		(*env)->SetShortArrayRegion(env, dst, 0, first, rec+start);
		if (n > first) {
			(*env)->SetShortArrayRegion(env, dst, first, n-first,
						rec);
		}
	}
	p[0] = from;
	p[1] = from + n;
	pthread_mutex_unlock(&rec_lock);
	(*env)->SetLongArrayRegion(env, pos, 0, 2, p);
	return n;
}
