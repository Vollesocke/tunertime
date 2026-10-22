#!/usr/bin/env python3
"""Scores the detector on chromatic runs (tools/field/README.md).

    python tools/field/run_score.py [-v] [--hints MODES]
                                    [--test-pitch EXE...] RUN...

RUN: run_*.txt files of the app's chromatic run mode, or directories
holding them.  A run cut short by a crash has only its .wav and
.timeline: the keys are then taken from the timeline's "run key" lines.  In a run every key's note is known: the tuner played the
keys in turn and stepped the shown note with Ton+ (a key played again
after Ton- is a "redo"; the last segment of a note that sounds is the
valid one: stepping back with Ton- passes notes without playing them).

The estimates come from the run's timeline (the app's own, "app") and,
with --test-pitch, from a replay of the WAV through each build, once per
hint mode in MODES (comma separated, default log):
  none   no hints
  keys   the shown key's Hz as expected note, with the run's focus ratio,
         from each key's start (what the app hints during a run)
  log    the timeline's hint lines (keys if it has none)

Per key, from its onset to the next tap.  Onset and release come from
the WAV: the level at the key's harmonics 1-4 (its target Hz), so mains
hum, blower rumble and the mic's hiss do not hide a soft treble pipe;
a key that never rises 12 dB above that level's quiet (the 10th
percentile around it; for a key held through nearly all of the run,
its level in the run's quietest stretch) counts as silent.  Without
the WAV, the first and
last estimate:
  ok      the key's note agreed as TunerTime.on_pitch() agrees it
          (DET_AGREE estimates in a row on one semitone, an empty one
          starts over) while it sounds, and how long after the onset
  wrong   agreements on another note while it sounds, of estimates
          from its onset on: interval, oct and 12th flagged
  empty   share of empty estimates while it sounds
  cents   median of the estimates on the right note, against the key's
          target Hz (A, temperament, stretch): how far the pipe is off
  drift   median strobe drift of the focused row from the timeline: the
          same, as the strobe shows it (app only)
  follow  follow mode emulated over the whole run: the shown note at the
          key's release was its note without a hand correction.  The
          first key is taken as heard, after it continuity is on
          (detections folded to the octave nearest the shown note, moving
          only within NEAR_SEMIS); a wrong note is corrected by hand at
          the next tap, and the note last agreed then (within
          CORR_RECENT) is not followed again until another one is
          agreed or the input stays at its noise floor for HOLD_QUIET
          (TunerTime.hold_correction; the level as pitch.c sees it,
          from the WAV, its floor from the run's quietest stretch on,
          rising only after FLOOR_UNHEARD with no note heard;
          --floor-18.8: rising always, and KEY_QUIET 1.4).
          A key right at its release needs no correction, so the next
          key, played before the tap, stays followed.
A table per source, one line per run; -v adds the keys.
"""
import array
import cmath
import glob
import itertools
import math
import os
import re
import subprocess
import sys
import tempfile
import wave

DET_AGREE = 4
NEAR_SEMIS = 5
NEAR_SILENCE = 10.0
CORR_RECENT = 4.0
HOLD_QUIET = 0.3
KEY_QUIET = 1.6		# TunerTime.KEY_QUIET (1.4 up to v18.8)
KEY_MIN = 20.0		# TunerTime.KEY_MIN: digital silence is quiet
FLOOR_RISE = 1.0005	# pitch.c, per analysis..
FLOOR_HELD = True	# ..with no note heard (False: every analysis, <= 18.8)
FLOOR_UNHEARD = 8	# pitch.c: that many analyses in a row
FRAME = 0.01		# envelope frames, seconds
HARMONICS = 4		# a key's level: at its harmonics 1..HARMONICS
PERIODS = 17		# over this many periods: a semitone away in its first null
AROUND = 5.0		# its quiet: over the key's segment and this much around
MIN_SOUND = 0.5		# seconds a key sounds at least (else a key passed by)
COVER = 0.8		# a key this much of the span around it: no quiet there..
QUIET_MIN = 0.3		# ..so the run's quietest stretch, if this long
QUIET_MAX = 2.0		# (at most this much of it)
KEY = re.compile(r'^key (\d+) (\S+) (\S+) (\S+) (-?\d+) (\S+)( redo)?\s*$')
MARK = re.compile(r'^mark (\S+) (.*)$')
EVENT = re.compile(r'^\s*([\d.]+)\s+(\d+)\s+(est|drift|hint|ui)\s+(.*)$')
RUN_KEY = re.compile(r'run key (\d+) (\S+) ([\d.]+) Hz( redo)?')
NAMES = ['C', 'Cs', 'D', 'Ds', 'E', 'F', 'Fs', 'G', 'Gs', 'A', 'As', 'B']
DRIFT = re.compile(r'(.+?)\s+([+-]?[\d.]+)c\s+([\d.]+)s')
FIXES = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'fixes')


def semi_of(name):
    """Cs2 -> 16 (48 = A4); also the spelled names of tunings with
    more than 12 notes, Eb4, Bs3 (= C4): the octave goes with the letter"""
    m = re.match(r'([A-G])(s|b?)(-?\d+)$', name)
    acc = {'s': 1, 'b': -1}.get(m.group(2), 0)
    return NAMES.index(m.group(1)) + acc + 12*int(m.group(3)) - 9


def median(v):
    v = sorted(v)
    n = len(v)
    if not n:
        return None
    return v[n//2] if n % 2 else 0.5*(v[n//2 - 1] + v[n//2])


def pct(v, q):
    v = sorted(v)
    return v[min(len(v) - 1, int(q*len(v)))] if v else None


class Run:
    """a run_*.txt, or the run_*.timeline the app keeps while recording
    (all there is after a crash: the keys then come from its "run key"
    ui lines, the header is not known)"""
    def __init__(self, path):
        self.path = path
        self.wav = path.rsplit('.', 1)[0] + '.wav'
        self.head, self.keys, self.marks = {}, [], []
        self.est, self.drift, self.hints, self.focus = [], [], 0, None
        self.t_align = None
        run_keys, run_end, crash_marks = [], None, []
        body = path.endswith('.timeline')
        for line in open(path, encoding='utf-8'):
            line = line.rstrip('\r\n')
            m = KEY.match(line)
            if m:
                self.keys.append(dict(
                    i=int(m.group(1)), start=float(m.group(2)),
                    end=float(m.group(3)), name=m.group(4),
                    semi=int(m.group(5)), hz=float(m.group(6)),
                    redo=bool(m.group(7))))
                continue
            m = MARK.match(line)
            if m:
                self.marks.append((float(m.group(1)), m.group(2)))
                continue
            if line.startswith('replay:'):
                m = re.search(r' -t (\S+)', line)
                self.t_align = m.group(1) if m else None
                continue
            if not line.strip():
                body = True
                continue
            m = EVENT.match(line)
            if m:
                t, what, rest = float(m.group(1)), m.group(3), m.group(4)
                if what == 'est':
                    p = rest.split()
                    self.est.append((t, None if p[0] == '-' else
                                     float(p[0])))
                elif what == 'drift':
                    d = DRIFT.match(rest)
                    if d:
                        self.drift.append((t, float(d.group(2))))
                elif what == 'hint':
                    self.hints += 1
                    if self.focus is None:
                        self.focus = float(rest.split()[0])
                else:
                    u = RUN_KEY.match(rest)
                    if u:
                        run_keys.append((t, u))
                    elif rest.startswith('run end'):
                        run_end = t
                    elif rest.startswith('run mark '):
                        crash_marks.append((t, rest[9:]))
                continue
            if line.startswith('#: '):
                # the header, as the .timeline also has it
                line = line[3:]
                if line.startswith('#'):
                    continue
                k, _, v = line.partition(' ')
                self.head.setdefault(k, v.strip())
                continue
            if not body and not line.startswith('#'):
                k, _, v = line.partition(' ')
                self.head.setdefault(k, v.strip())
        self.skips = {}
        self.fixes()
        self.a_freq = float(self.head.get('A_freq', '440'))
        self.stretch = float((self.head.get('stretch') or '0').split()[0])
        if 'focus_ratio' in self.head or self.focus is None:
            self.focus = float(self.head.get('focus_ratio', '0') or 0)
        self.rate = int(self.head.get('sample_rate', '48000'))
        if self.t_align is None and self.est:
            # the replay's analyses at the app's samples
            self.t_align = '%.5f' % self.est[0][0]
        self.load()
        if not self.keys and run_keys:
            self.marks = self.marks or crash_marks
            ends = [t for t, _ in run_keys[1:]]
            ends.append(run_end or (len(self.x)/self.rate if self.x
                                    else self.est[-1][0] if self.est
                                    else run_keys[-1][0]))
            for (t, u), end in zip(run_keys, ends):
                self.keys.append(dict(
                    i=int(u.group(1)), start=t, end=end, name=u.group(2),
                    semi=semi_of(u.group(2)), hz=float(u.group(3)),
                    redo=bool(u.group(4))))
        # the last segment of a note that sounds is the valid one, else
        # its last (Dis and Es share a semitone, so by name too)
        notes = {}
        for k in self.keys:
            notes.setdefault((k['semi'], k['name']), []).append(k)
            k['valid'], k['on'], k['off'] = False, None, None
            k['marks'] = [m for t, m in self.marks
                          if k['start'] <= t < k['end']]
        for seg in notes.values():
            v = next((k for k in reversed(seg) if self.sounding(k)), seg[-1])
            v['valid'] = True
        # not the key shown: marked so in the run, or skipped in a fix
        for k in self.keys:
            k['skip'] = self.skips.get(k['name']) or next(
                (m for m in k['marks'] if m.endswith('key wrong')), None)
            if k['skip']:
                k['valid'] = False

    def fixes(self):
        """corrections after the run, in <run>.fix next to it or in
        tools/field/fixes/: header lines as in the run ("kind open")
        replace its own, "skip <key> <why>" leaves a key out (a short
        octave's missing keys passed late with Ton+)"""
        base = os.path.basename(self.path).rsplit('.', 1)[0] + '.fix'
        for p in (self.path.rsplit('.', 1)[0] + '.fix',
                  os.path.join(FIXES, base)):
            if not os.path.exists(p):
                continue
            for line in open(p, encoding='utf-8'):
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                k, _, v = line.partition(' ')
                if k == 'skip':
                    name, _, why = v.partition(' ')
                    self.skips[name] = why.strip() or 'skipped'
                else:
                    self.head[k] = v.strip()

    def name(self):
        """run_<time>_<stop>.txt -> <time>_<stop>"""
        return os.path.basename(self.path).rsplit('.', 1)[0][4:]

    def label(self):
        s = self.head.get('stop') or self.name().split('_', 1)[-1]
        f = self.head.get('footage', '')
        if f and f not in s:
            s += ' ' + f
        return s

    def semi(self, hz):
        """the nearest semitone (48 = A4) as the app rounds it, with
        stretch; temperament offsets are not taken off"""
        return int(round(48 + 12*math.log2(hz/self.a_freq) /
                         (1.0 + self.stretch/1200.0)))

    def load(self):
        """the WAV's first channel, as self.x (None without the WAV)"""
        self.x = None
        if not os.path.exists(self.wav):
            return
        with wave.open(self.wav, 'rb') as w:
            if w.getsampwidth() != 2:
                return
            ch, self.rate = w.getnchannels(), w.getframerate()
            a = array.array('h', w.readframes(w.getnframes()))
        if sys.byteorder == 'big':
            a.byteswap()
        self.x = a[::ch] if ch > 1 else a

    def loud(self, est):
        """per estimate (time, Hz or None): the input at least KEY_QUIET
        over its noise floor, as pitch.c's level and floor see them (the
        rms of the 8192 samples 2:1 decimated, ending there; the floor a
        minimum rising FLOOR_RISE per analysis after FLOOR_UNHEARD with
        no note heard), the
        floor starting at the run's quiet(); all True without the WAV"""
        if self.x is None:
            return [True]*len(est)
        x = self.x
        d = [0.5*(x[i] + x[i + 1]) for i in range(0, len(x) - 1, 2)]
        s1 = [0.0] + list(itertools.accumulate(d))
        s2 = [0.0] + list(itertools.accumulate(v*v for v in d))
        out, fl, n, unheard = [], 0.0, 8192, 0
        q = self.quiet()
        if q:
            # the app heard the place before the run (a run that starts
            # on a key held would else take the key for the floor)
            a, b = int(q[0]*self.rate/2.0), int(q[1]*self.rate/2.0)
            m = max(b - a, 1)
            mean = (s1[b] - s1[a])/m
            fl = math.sqrt(max((s2[b] - s2[a])/m - mean*mean, 0.0))
        for t, hz in est:
            e = min(len(d), int(t*self.rate/2.0))
            b = max(0, e - n)
            m = max(e - b, 1)
            mean = (s1[e] - s1[b])/m
            lv = math.sqrt(max((s2[e] - s2[b])/m - mean*mean, 0.0))
            unheard = 0 if hz else unheard + 1
            if fl <= 0.0 or lv < fl:
                fl = lv
            elif unheard >= FLOOR_UNHEARD or not FLOOR_HELD:
                fl *= FLOOR_RISE
            out.append(lv >= KEY_QUIET*fl and lv >= KEY_MIN)
        return out

    def level(self, hz, t0, t1):
        """the level at the harmonics of hz in dB, every FRAME from about
        t0 to t1, the time its first frame is centred on and half its
        window: per harmonic the samples turned down to 0 Hz (times a
        phasor) and averaged over PERIODS periods"""
        hop = int(FRAME*self.rate)
        win = max(2*hop, int(round(PERIODS*self.rate/hz)))
        a = max(0, int(t0*self.rate) - win//2)
        b = min(len(self.x), int(t1*self.rate) + win//2)
        x = self.x[a:b]
        n = (len(x) - win)//hop + 1
        half = 0.5*win/self.rate
        if n <= 0:
            return a/self.rate + half, half, []
        power = [0.0]*n
        for h in range(1, HARMONICS + 1):
            if h*hz > self.rate/2.5:
                break
            w = cmath.exp(-2j*math.pi*h*hz/self.rate)
            turn = itertools.accumulate(itertools.repeat(w, len(x) - 1),
                                        lambda p, q: p*q, initial=1.0)
            c = list(itertools.accumulate(map(lambda v, p: v*p, x, turn),
                                          initial=0.0))
            for j in range(n):
                power[j] += abs((c[j*hop + win] - c[j*hop])/win)**2
        return a/self.rate + half, half, [10*math.log10(p + 1.0)
                                          for p in power]

    def quiet(self):
        """(t0, t1): the run's quietest stretch: of the runs of 0.1 s
        frames within 6 dB of the WAV's 1st percentile of level that
        last QUIET_MIN s at least, the one of the lowest level, at most
        QUIET_MAX s of it; None if there is none"""
        if hasattr(self, '_quiet'):
            return self._quiet
        self._quiet = None
        x, hop = self.x, int(0.1*self.rate)
        lv = [10*math.log10(sum(v*v for v in x[i:i + hop])/hop + 1.0)
              for i in range(0, len(x) - hop + 1, hop)]
        lim = pct(lv, 0.01)
        if lim is None:
            return None
        best, i = None, 0
        while i < len(lv):
            j = i
            while j < len(lv) and lv[j] <= lim + 6:
                j += 1
            j = min(j, i + int(QUIET_MAX/0.1))
            if (j - i)*0.1 >= QUIET_MIN - 1e-9:
                m = sum(lv[i:j])/(j - i)
                if best is None or m < best[0]:
                    best = (m, i, j)
            i = j + 1
        if best:
            self._quiet = (best[1]*0.1, best[2]*0.1)
        return self._quiet

    def sounding(self, k):
        """whether key k sounds, its onset and release as k['on'] and
        k['off'] (None if silent): on its level (level()), the trough
        before it first comes within 6 dB of its peak (the tail of the
        key before may still ring at the tap; the rise starts when the
        window's end reaches the onset, so the onset is taken there) to
        where it falls 15 dB below that peak (at the window's centre);
        silent if it never rises 12 dB above its quiet (the 10th
        percentile around the key) or sounds less than MIN_SOUND (a key
        passed by with Ton+ or Ton-).  Without the WAV, the first and last
        estimate with a note"""
        if self.x is None:
            hz = [t for t, h in self.est if h and k['start'] <= t < k['end']]
            if hz:
                k['on'], k['off'] = hz[0], hz[-1]
            return bool(hz)
        t0, half, env = self.level(k['hz'], k['start'] - AROUND,
                                   k['end'] + AROUND)
        a = max(0, int(round((k['start'] - t0)/FRAME)))
        b = min(len(env), int(round((k['end'] - t0)/FRAME)))
        seg = env[a:b]
        floor, peak = pct(env, 0.1), pct(seg, 0.9)
        q = self.quiet() if b - a >= COVER*len(env) else None
        if q and floor is not None:
            # the key fills nearly all around it (one key held for the
            # whole run): its quiet from the run's quietest stretch
            _, _, qe = self.level(k['hz'], q[0], q[1])
            if qe:
                floor = min(floor, median(qe))
        if peak is None or peak < floor + 12:
            return False
        thr = max(floor + 6, peak - 30)
        i = next(i for i, v in enumerate(seg) if v >= peak - 6)
        while i > 0 and seg[i - 1] >= thr and seg[i - 1] <= seg[i] + 0.5:
            i -= 1
        j = len(seg) - 1 - next(i for i, v in enumerate(reversed(seg))
                                if v >= peak - 6)
        while j < len(seg) - 1 and seg[j] >= peak - 15:
            j += 1
        on, off = t0 + (a + i)*FRAME + half, t0 + (a + j)*FRAME
        if off - on < MIN_SOUND:
            return False
        k['on'], k['off'] = on, off
        return True


def replay(exe, run, mode, tmp):
    cmd = [exe] + (['-t', run.t_align] if run.t_align else []) + [run.wav]
    if mode == 'log' and run.hints:
        cmd += ['--hints', run.path]
    elif mode in ('keys', 'log'):
        h = os.path.join(tmp, 'hints.txt')
        with open(h, 'w') as f:
            for k in run.keys:
                f.write('%9.5f %8d  hint %.4f %.3f\n'
                        % (k['start'], round(k['start']*run.rate),
                           run.focus, k['hz']))
        cmd += ['--hints', h]
    out = subprocess.run(cmd, capture_output=True, text=True).stdout
    est = []
    for line in out.splitlines():
        p = line.split()
        if len(p) >= 4 and p[2] == 'est':
            est.append((float(p[0]), None if p[3] == '-' else float(p[3])))
    return est


def interval(d):
    if d and d % 12 == 0:
        return '%+doct' % (d//12)
    if abs(d) == 19:
        return '%+d(12th)' % d
    return '%+d' % d


def score(run, est, trace=None):
    """per key: the dict of the docstring; follow emulated over the run.
    trace (a list): gets per estimate a dict of the follow state after
    it: shown, stable (the note last agreed), agree (the note agreed
    here, DET_AGREE in a row, else None), start (the time its streak
    began), quiet (a hold ended here by the input level)"""
    res = []
    for k in run.keys:
        res.append(dict(key=k, agreed=None, wrong=[], est=0, empty=0,
                        cents=[], drift=None, follow=None))
    seg = 0
    sem, n, streak = None, 0, 0.0
    shown, near, heard = None, False, 0.0
    stable, stable_t, overruled = None, -1e9, None
    loud, last_loud = run.loud(est), 0.0
    if trace is None:
        trace = []

    def close(i):
        """at the tap ending key i: judge follow, correct by hand"""
        nonlocal shown, near, overruled
        k, r = run.keys[i], res[i]
        if not k['valid'] or k['on'] is None:
            return
        r['follow'] = r.get('shown_at_off', shown) == k['semi']
        near = True
        if r['follow']:
            # shown right at its release: no hand needed, and the note
            # shown since may be the next key's, played before the tap
            return
        if shown != k['semi']:
            recent = k['end'] - stable_t < CORR_RECENT
            overruled = stable if recent else None
        shown = k['semi']

    def step(e, t, hz):
        """one estimate; returns the note agreed in it, if any"""
        nonlocal seg, sem, n, streak, shown, near, heard, stable, \
            stable_t, overruled
        if seg >= len(run.keys):
            return None
        k, r = run.keys[seg], res[seg]
        if t < k['start']:
            return None
        if k['off'] is not None and t > k['off'] and 'shown_at_off' not in r:
            r['shown_at_off'] = shown
        sounding = k['on'] is not None and k['on'] <= t <= k['off']
        if sounding:
            r['est'] += 1
            r['empty'] += hz is None
        if hz is None:
            n = 0
            if near and t - heard > NEAR_SILENCE:
                near = False
            return None
        heard = t
        s = run.semi(hz)
        if sounding and s == k['semi']:
            r['cents'].append(1200*math.log2(hz/k['hz']))
        n = n + 1 if s == sem else 1
        sem = s
        if n == 1:
            streak = t
        # while the key sounds: its note agreed (the streak may have
        # begun before its onset: the key played before the tap), or
        # another note agreed by estimates of its own sound (a streak
        # begun before the onset is the key before)
        if n >= DET_AGREE and sounding:
            if s == k['semi']:
                if r['agreed'] is None:
                    r['agreed'] = max(t - k['on'], 0.0)
            elif n == DET_AGREE and streak >= k['on']:
                r['wrong'].append(s - k['semi'])
        if n < DET_AGREE:
            return None
        stable, stable_t = s, t
        if s == overruled:
            return s
        overruled = None
        to = s
        if near and shown is not None:
            to = s + 12*math.floor((shown - s)/12.0 + 0.5)
            if abs(to - shown) > NEAR_SEMIS:
                return s
        shown = to
        return s

    for e, (t, hz) in enumerate(est):
        quiet = False
        if loud[e]:
            last_loud = t
        elif t - last_loud > HOLD_QUIET:
            quiet = True
            overruled = None	# the key released
        while seg < len(run.keys) and t >= run.keys[seg]['end']:
            close(seg)
            seg += 1
        agree = step(e, t, hz)
        trace.append(dict(shown=shown, stable=stable,
                          agree=agree if n == DET_AGREE else None,
                          start=streak, quiet=quiet))
    while seg < len(run.keys):
        close(seg)
        seg += 1
    for k, r in zip(run.keys, res):
        if k['on'] is None:
            continue
        r['cents'] = median(r['cents'][2:]) if len(r['cents']) > 2 \
            else median(r['cents'])
        d = [c for t, c in run.drift if k['on'] + 0.5 <= t <= k['off']]
        r['drift'] = median(d)
    return res


def show_cents(c):
    return '%+6.1f' % c if c is not None else '     -'


def detail(res):
    for r in res:
        k = r['key']
        if k['skip']:
            print('    %-4s %3d  left out: %s' % (k['name'], k['i'],
                                                 k['skip']))
            continue
        if not k['valid']:
            print('    %-4s %3d  superseded' % (k['name'], k['i']))
            continue
        if k['on'] is None:
            print('    %-4s %3d  silent' % (k['name'], k['i']))
            continue
        print('    %-4s %3d  %6.2f-%6.2f s  ok %-6s wrong %-14s empty %3.0f%%'
              '  cents %s  drift %s  follow %s%s'
              % (k['name'], k['i'], k['on'], k['off'],
                 '%.2fs' % r['agreed'] if r['agreed'] is not None else '-',
                 ' '.join(interval(d) for d in r['wrong']) or '-',
                 100.0*r['empty']/max(r['est'], 1), show_cents(r['cents']),
                 show_cents(r['drift']), 'ok' if r['follow'] else 'hand',
                 ''.join('  [%s]' % m for m in k['marks'])))


def table(name, rows, verbose):
    print('== %s' % name)
    print('  %-34s %4s %4s %6s %6s %5s %8s %6s %7s'
          % ('run', 'keys', 'ok', 'follow', 'wrong', 'oct', 'first ok',
             'empty', 'off>5c'))
    tot = dict(keys=0, ok=0, follow=0, wrong=0, oct=0, lat=[], est=0,
               empty=0, off=0)
    for run, res in rows:
        live = [r for r in res if r['key']['valid'] and
                r['key']['on'] is not None]
        lat = [r['agreed'] for r in live if r['agreed'] is not None]
        wrong = [d for r in live for d in r['wrong']]
        off = [r for r in live if abs((r['drift'] if r['drift'] is not None
                                       else r['cents'] or 0.0)) > 5.0]
        p = dict(keys=len(live), ok=len(lat),
                 follow=sum(bool(r['follow']) for r in live),
                 wrong=len(wrong),
                 oct=sum(d % 12 == 0 or abs(d) == 19 for d in wrong),
                 lat=lat, est=sum(r['est'] for r in live),
                 empty=sum(r['empty'] for r in live), off=len(off))
        for x in tot:
            tot[x] += p[x]
        line(run.name() if verbose else run.label(), p)
        if verbose:
            print('    %s, %s, %s' % (run.label(), run.head.get('kind', '?'),
                                     run.head.get('organ', '?')))
            detail(res)
    if len(rows) > 1:
        line('all', tot)


def line(label, p):
    lat = median(p['lat'])
    print('  %-34s %4d %4d %6d %6d %5d %8s %5.0f%% %7d'
          % (label[:34], p['keys'], p['ok'], p['follow'], p['wrong'],
             p['oct'], '%.2f s' % lat if lat is not None else '-',
             100.0*p['empty']/max(p['est'], 1), p['off']))


def run_files(paths):
    """the runs named, or in the directories named"""
    files = []
    for p in paths:
        if os.path.isdir(p):
            txt = glob.glob(os.path.join(p, '**', 'run_*.txt'),
                            recursive=True)
            # a run cut short by a crash has only its .timeline
            files += sorted(txt + [
                t for t in glob.glob(os.path.join(p, '**', 'run_*.timeline'),
                                     recursive=True)
                if t[:-9] + '.txt' not in txt])
        else:
            files.append(p)
    return files


def main():
    global FLOOR_HELD, KEY_QUIET
    args = sys.argv[1:]
    verbose, modes, exes, paths = False, ['log'], [], []
    while args:
        a = args.pop(0)
        if a == '-v':
            verbose = True
        elif a == '--floor-18.8':
            FLOOR_HELD, KEY_QUIET = False, 1.4
        elif a == '--hints':
            modes = args.pop(0).split(',')
        elif a == '--test-pitch':
            while args and not args[0].startswith('-') and \
                    not os.path.isdir(args[0]) and \
                    not args[0].endswith('.txt'):
                exes.append(args.pop(0))
        else:
            paths.append(a)
    if not paths or any(m not in ('none', 'keys', 'log') for m in modes):
        sys.exit(__doc__)
    # Windows compilers add .exe to the name given
    # and Windows does not start a relative path with forward slashes
    exes = [os.path.abspath(e + '.exe' if not os.path.exists(e) and
                            os.path.exists(e + '.exe') else e) for e in exes]
    runs = [Run(f) for f in run_files(paths)]
    runs = [r for r in runs if r.keys]
    if any(r.est for r in runs):
        table('app', [(r, score(r, r.est)) for r in runs if r.est], verbose)
    with tempfile.TemporaryDirectory() as tmp:
        for exe in exes:
            for mode in modes:
                rows = [(r, score(r, replay(exe, r, mode, tmp)))
                        for r in runs if os.path.exists(r.wav)]
                table('%s, hints %s' % (exe, mode), rows, verbose)


if __name__ == '__main__':
    main()
