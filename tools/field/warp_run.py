#!/usr/bin/env python3
"""Makes new chromatic runs from real ones (tools/field/README.md, "Warped
runs"): the real pipes with their onsets, sustain and room, and a known
truth, through changes the detector and follow mode should cope with.

    python tools/field/warp_run.py [-o DIR] [--seed N] [--tag NAME]
        [--cents X | --cents-random N] [--semis K] [--hum-notch]
        [--hum DB [--hum-hz F]] [--noise-from RUN [--noise DB]]
        [--gain G] [--agc] [--clip] [--order run|up|down|shuffle]
        [--legato S] RUN...

RUN: run_*.txt of the app's chromatic run mode (with its .wav), or
directories holding them.  Each key that counts in run_score.py (the
last take of a note that sounds, not left out by a mark or a
tools/field/fixes file) is cut from its tap to the next tap, so its
onset, sustain, release and the room's tail stay as recorded.  Its
truth: the key's note, and how far the pipe was off: the median strobe
drift of the focused row while it sounded, else the median estimate on
the right note (run_score.py).

  --cents X        each pipe X cents higher (resampled; the key's target
                   Hz stays, so run_score's cents column moves by X)
  --cents-random N each pipe by a random amount in -N..N cents
  --semis K        the whole run K semitones up (resampled); the keys'
                   names and target Hz move along (the source's targets
                   times 2^(K/12): its temperament, transposed)
  --hum-notch      first takes the source's own noise lines out: the
                   stationary peaks below 1.2 kHz of its two quietest
                   stretches (at Hmth 202 a 49.25 Hz series from the
                   blower, not the mains), notched in the whole file.
                   A pipe partial within half a hertz of one goes too
  --hum DB         adds a hum series DB dB relative to the keys' median
                   level, with the mix measured at Hmth 202 (49.25 Hz
                   and its 3rd harmonic strongest, a line wandering
                   around 43.5 Hz); --hum-hz F moves its fundamental
  --noise-from RUN adds the noise bed of another run: its quietest
                   stretches, looped with crossfades, at the level it
                   had against that run's keys (--noise DB: relative to
                   these keys instead)
  --gain G         times G before the AGC and clipping
  --agc            a phone AGC as tools/sim_organ.c's: rms follower 10 ms
                   up and 1 s down, aims at -12 dBFS, at most +24 dB
                   (on 1 ms blocks)
  --clip           clips at 16 bits; without it a run that would clip
                   is scaled down (the header says so)
  --order          run (as recorded, the default), up, down, shuffle
  --legato S       each key's cut starts S s before the one before it
                   ends, the two mixed: beyond the gap after a release
                   the keys overlap
  --seed N         for the random choices (1)

Writes run_<time>_<stop>-<variant>.wav and .txt to DIR (default
tools/field/runs/warped), in the app's format: the source's header with a
"warp" line, key lines with the new timing, the source's marks moved
along plus a mark per key with the truth where it was detuned, hint
lines at each tap (the key's Hz with the run's focus, as the app hints
during a run) and the source's drift lines moved along (plus the cents
applied).  No estimates: replay with run_score.py --test-pitch.

Needs numpy.
"""
import math
import os
import random
import re
import sys
import wave

try:
    import numpy as np
except ImportError:
    sys.exit('warp_run.py needs numpy (pip install numpy)')

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import run_score  # noqa: E402

NAMES = ['C', 'Cs', 'D', 'Ds', 'E', 'F', 'Fs', 'G', 'Gs', 'A', 'As', 'B']
FLATS = [None, 'Db', None, 'Eb', 'Fb', None, 'Gb', None, 'Ab', None,
         'Bb', 'Cb']
FADE = 0.01		# at each cut, seconds
SINC_TAPS = 16		# per side, resampler
# Hmth 202, Gedackt run of 21 Oct, quietest 4 s: the blower's series
# (harmonic: dB to the fundamental) and a line wandering 42.5-44.3 Hz
HUM_HZ = 49.25
HUM_MIX = {1: 0.0, 2: -11.0, 3: -4.0, 4: -22.0, 5: -22.0, 6: -33.0,
           7: -25.5}
HUM_SIDE = (43.5, 0.9, -8.0)	# Hz, wander +-Hz, dB
NOTCH_TOP = 1200.0	# Hz
NOTCH_HALF = 0.5	# Hz, flat part each side, then a 0.3 Hz taper
QUIET = 3.0		# s, quiet stretches for the noise lines and beds


def name(semi, flat=False):
    """48 = A4; flat: spelled with a flat where one letter does (Eb4,
    Fb4 = E4, Cb5 = B4: the octave goes with the letter, as
    run_score.semi_of reads it), for the second note of a pitch class
    with two (Es of Dis and Es) transposed"""
    m = semi + 21
    n = FLATS[m % 12] if flat else None
    if not n:
        return '%s%d' % (NAMES[m % 12], m//12 - 1)
    return '%s%d' % (n, (m + 1)//12 - 1 if n == 'Cb' else m//12 - 1)


def read_wav(path):
    with wave.open(path, 'rb') as w:
        ch, rate = w.getnchannels(), w.getframerate()
        a = np.frombuffer(w.readframes(w.getnframes()), '<i2')
    return a[::ch].astype(np.float64), rate


def write_wav(path, x, rate):
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(np.round(x).astype('<i2').tobytes())


def resample(x, ratio):
    """x played ratio times faster (pitch up by ratio): windowed sinc,
    low-passed below the new Nyquist when going up"""
    if abs(ratio - 1.0) < 1e-9:
        return x.copy()
    n = int((len(x) - 1)/ratio)
    fc = min(1.0, 1.0/ratio)
    out = np.empty(n)
    for a in range(0, n, 1 << 17):
        p = np.arange(a, min(n, a + (1 << 17)))*ratio
        i = np.floor(p).astype(np.int64)
        acc = np.zeros(len(p))
        for j in range(-SINC_TAPS + 1, SINC_TAPS + 1):
            k = np.clip(i + j, 0, len(x) - 1)
            d = p - (i + j)
            win = 0.5 + 0.5*np.cos(np.pi*np.clip(d/SINC_TAPS, -1, 1))
            acc += x[k]*fc*np.sinc(fc*d)*win
        out[a:a + len(p)] = acc
    return out


def frames_db(x, rate, hop=0.01):
    n = int(rate*hop)
    m = len(x)//n
    return 10*np.log10((x[:m*n].reshape(m, n)**2).mean(1) + 1.0), n


def quiet_stretches(x, rate, count, secs=QUIET):
    """the start samples of the count quietest stretches of secs,
    apart from each other"""
    e, n = frames_db(x, rate)
    k = int(secs*rate/n)
    if len(e) <= k:
        return [0]
    c = np.convolve(10**(e/10), np.ones(k), 'valid')
    out = []
    for _ in range(count):
        i = int(np.argmin(c))
        if not np.isfinite(c[i]):
            break
        out.append(i*n)
        c[max(0, i - k):i + k] = np.inf
    return out


def noise_lines(x, rate, low_key_hz):
    """the stationary peaks below NOTCH_TOP in the two quietest
    stretches: [(lo, hi)] Hz to notch.  Below the lowest key a line may
    wander (tracked over the run in 4 s windows); above, only where
    both stretches have it"""
    L = int(QUIET*rate)
    win = np.hanning(L)
    f = np.fft.rfftfreq(L, 1/rate)
    found = []
    for s in quiet_stretches(x, rate, 2):
        q = x[s:s + L]
        if len(q) < L:
            continue
        db = 10*np.log10(np.abs(np.fft.rfft(q*win))**2 + 1.0)
        pk = []
        for j in np.where((f > 20) & (f < NOTCH_TOP))[0]:
            lo, hi = max(0, j - int(20*QUIET)), j + int(20*QUIET)
            if db[j] > db[j - 1] and db[j] >= db[j + 1] and \
                    db[j] > np.median(db[lo:hi]) + 15:
                pk.append(f[j])
        found.append(pk)
    if not found:
        return []
    lines = []
    for fq in found[0]:
        if fq < low_key_hz - 5.0:
            lo, hi = track(x, rate, fq)
            lines.append((lo, hi))
        elif len(found) < 2 or any(abs(fq - g) < 0.5 for g in found[1]):
            lines.append((fq, fq))
    return lines


def track(x, rate, fq):
    """where a line near fq goes over the run: 4 s windows, its peak
    within 2 Hz"""
    L = 4*rate
    f = np.fft.rfftfreq(L, 1/rate)
    m = (f > fq - 2.0) & (f < fq + 2.0)
    got = [fq]
    for s in range(0, len(x) - L, L):
        sp = np.abs(np.fft.rfft(x[s:s + L]*np.hanning(L)))
        got.append(f[m][np.argmax(sp[m])])
    return min(got), max(got)


def notch(x, rate, lines):
    """zeroes the lines' bins in the spectrum of the whole run, with a
    raised-cosine taper of 0.3 Hz"""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1/rate)
    g = np.ones(len(f))
    for lo, hi in lines:
        lo, hi = lo - NOTCH_HALF, hi + NOTCH_HALF
        d = np.maximum(lo - f, f - hi)		# >0 outside
        t = np.clip(d/0.3, 0.0, 1.0)
        g = np.minimum(g, 0.5 - 0.5*np.cos(np.pi*t))
    return np.fft.irfft(X*g, len(x))


def hum(n, rate, f0, rng):
    """the Hmth 202 series at f0, rms 1: slow random wander of its
    frequency (0.02 Hz) and the side line's (HUM_SIDE)"""
    t = np.arange(n)/rate
    steps = max(2, int(n/rate*2))
    def walk(sd):
        w = np.cumsum(rng.normal(0.0, sd, steps))
        w -= w.mean()
        return np.interp(t, np.linspace(0, t[-1] if n > 1 else 1, steps), w)
    dev = walk(0.004)
    ph = 2*np.pi*np.cumsum(f0 + dev)/rate
    y = np.zeros(n)
    for h, db in HUM_MIX.items():
        if h*f0 < rate/2.2:
            y += 10**(db/20)*np.sin(h*ph + rng.uniform(0, 2*np.pi))
    side, wander, db = HUM_SIDE
    sw = walk(0.05)
    sw = side + wander*np.tanh(sw/(np.abs(sw).max() + 1e-9)*2)
    y += 10**(db/20)*np.sin(2*np.pi*np.cumsum(sw)/rate)
    return y/np.sqrt((y**2).mean())


def bed(src, n, rate):
    """the quietest stretches of src (samples), looped to n with
    equal-power crossfades of 0.5 s"""
    L = int(QUIET*rate)
    xf = int(0.5*rate)
    parts = [src[s:s + L] for s in quiet_stretches(src, rate, 4)
             if len(src[s:s + L]) == L]
    if not parts:
        return np.zeros(n)
    c = np.linspace(0, np.pi/2, xf)
    out = parts[0].copy()
    i = 1
    while len(out) < n + xf:
        p = parts[i % len(parts)]
        i += 1
        out[-xf:] = out[-xf:]*np.cos(c) + p[:xf]*np.sin(c)
        out = np.concatenate([out, p[xf:]])
    return out[:n]


def agc(x, rate):
    """tools/sim_organ.c's phone AGC on 1 ms blocks"""
    b = max(1, rate//1000)
    m = (len(x) + b - 1)//b
    pad = np.zeros(m*b)
    pad[:len(x)] = x
    ms = (pad.reshape(m, b)**2).mean(1)
    env, gain, g_out = 0.0, 1.0, np.empty(m)
    up, down, glide = 1.0 - math.exp(-0.001/0.01), \
        1.0 - math.exp(-0.001/1.0), 1.0 - math.exp(-0.001/0.005)
    for i in range(m):
        env += (up if ms[i] > env else down)*(ms[i] - env)
        g = min(16.0, 8000.0/(math.sqrt(env) + 1.0))
        gain += (g - gain)*glide
        g_out[i] = gain
    return x*np.interp(np.arange(len(x)), np.arange(m)*b + b/2, g_out)


def key_rms(x, rate, keys):
    """median rms of the keys' sustains: (on, off) in seconds"""
    v = []
    for on, off in keys:
        s = x[int((on + 0.3)*rate):int((off - 0.2)*rate)]
        if len(s) > rate//10:
            v.append(np.sqrt((s**2).mean()))
    return float(np.median(v)) if v else 1.0


def source_events(path):
    """the drift lines of a run: (t, footage, cents, secs)"""
    out = []
    for line in open(path, encoding='utf-8'):
        m = run_score.EVENT.match(line.rstrip('\r\n'))
        if m and m.group(3) == 'drift':
            d = run_score.DRIFT.match(m.group(4))
            if d:
                out.append((float(m.group(1)), d.group(1),
                            float(d.group(2)), float(d.group(3))))
    return out


def header(path, head):
    """the source's header lines up to the keys, with the fixes'
    values (run_score.Run.fixes) put in"""
    out = []
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\r\n')
        if line.startswith('key ') or line.startswith('# key'):
            break
        k, _, v = line.partition(' ')
        if not line.startswith('#') and k in head and head[k] != v.strip():
            line = '%s %s' % (k, head[k])
        out.append(line)
    return out


def warp(path, o, d, rng, nrng):
    run = run_score.Run(path)
    x, rate = read_wav(run.wav)
    res = run_score.score(run, run.est) if run.est else \
        [dict(drift=None, cents=None) for _ in run.keys]
    keys = [(k, r) for k, r in zip(run.keys, res)
            if k['valid'] and k['on'] is not None]
    if not keys:
        print('%s: no keys' % path)
        return None
    notched = []
    if o['notch']:
        lines = noise_lines(x, rate, min(k['hz'] for k, _ in keys))
        x = notch(x, rate, lines)
        notched = ['notched at %s Hz' % ' '.join(
            '%.2f' % lo if hi - lo < 0.01 else '%.2f-%.2f' % (lo, hi)
            for lo, hi in lines)]
    if o['order'] == 'up':
        keys.sort(key=lambda kr: (kr[0]['semi'], kr[0]['name']))
    elif o['order'] == 'down':
        keys.sort(key=lambda kr: (-kr[0]['semi'], kr[0]['name']))
    elif o['order'] == 'shuffle':
        rng.shuffle(keys)
    drifts = source_events(path)
    fade = int(FADE*rate)
    segs = []		# (audio, key, pipe cents, applied cents, ratio)
    for k, r in keys:
        a, b = int(k['start']*rate), int(k['end']*rate)
        if o['cents_random']:
            c = rng.uniform(-o['cents_random'], o['cents_random'])
        else:
            c = o['cents']
        ratio = 2.0**((c + 100.0*o['semis'])/1200.0)
        y = resample(x[a:b], ratio)
        f = min(fade, len(y)//4)
        if f:
            ramp = 0.5 - 0.5*np.cos(np.linspace(0, np.pi, f))
            y[:f] *= ramp
            y[-f:] *= ramp[::-1]
        pipe = r['drift'] if r['drift'] is not None else r['cents']
        segs.append((y, k, pipe, c, ratio,
                     'drift' if r['drift'] is not None else 'estimates'))
    leg = int(o['legato']*rate)
    pos, p = [], 0
    for y, *_ in segs:
        pos.append(p)
        p += max(len(y) - leg, int(0.05*rate))
    n = max(q + len(s[0]) for q, s in zip(pos, segs))
    out = np.zeros(n)
    for q, s in zip(pos, segs):
        out[q:q + len(s[0])] += s[0]
    sound = [(q/rate + (k['on'] - k['start'])/ratio,
              q/rate + (k['off'] - k['start'])/ratio)
             for q, (y, k, pipe, c, ratio, _) in zip(pos, segs)]
    level = key_rms(out, rate, sound)
    what = []
    if o['hum'] is not None:
        out += 10**(o['hum']/20)*level*hum(n, rate, o['hum_hz'], nrng)
        what.append('hum %+g dB at %g Hz' % (o['hum'], o['hum_hz']))
    if o['noise_from']:
        nr = run_score.Run(o['noise_from'])
        nx, nrate = read_wav(nr.wav)
        if nrate != rate:
            nx = resample(nx, nrate/rate)
        b = bed(nx, n, rate)
        brms = np.sqrt((b**2).mean()) + 1e-9
        if o['noise'] is None:
            nk = [(k['on'], k['off']) for k in nr.keys
                  if k['valid'] and k['on'] is not None]
            db = 20*math.log10(brms/key_rms(nx, rate, nk))
        else:
            db = o['noise']
        out += b/brms*10**(db/20)*level
        what.append('noise bed of %s %+.1f dB' %
                    (os.path.basename(o['noise_from']), db))
    if o['gain'] != 1.0:
        out *= o['gain']
    if o['agc']:
        out = agc(out, rate)
    peak = np.abs(out).max()
    if o['clip']:
        what.append('clipped %.2f%% of samples' %
                    (100.0*(np.abs(out) > 32767).mean()))
        out = np.clip(out, -32767, 32767)
    elif peak > 32767:
        out *= 32767/peak
        what.append('scaled by %.2f against clipping' % (32767/peak))

    tag = o['tag'] or variant(o)
    base = os.path.join(d, os.path.basename(path).rsplit('.', 1)[0] +
                        '-' + tag)
    write_wav(base + '.wav', out, rate)

    line = '%9.5f %8d  '
    ev, txt_keys, marks = [], [], []
    for i, (q, (y, k, pipe, c, ratio, src)) in enumerate(zip(pos, segs)):
        semi = k['semi'] + o['semis']
        nm = k['name'] if not o['semis'] else \
            name(semi, 'b' in k['name'][1:])
        hz = k['hz']*2.0**(o['semis']/12.0)
        s = q/rate
        e = pos[i + 1]/rate if i + 1 < len(pos) else n/rate
        txt_keys.append('key %d %.3f %.3f %s %d %.3f' %
                        (i + 1, s, e, nm, semi, hz))
        ev.append((q, line % (s, q) + 'hint %.4f %.3f' % (run.focus, hz)))
        ev.append((q, line % (s, q) + 'ui    run key %d %s %.3f Hz' %
                   (i + 1, nm, hz)))
        for t, m in run.marks:
            if k['start'] <= t < k['end']:
                tm = s + (t - k['start'])/ratio
                marks.append((tm, re.sub(r'^key \d+:', 'key %d:' % (i + 1),
                                         m)))
        if c:
            marks.append((s, 'key %d: warped: %s pipe %s c (%s), %+.1f c '
                          'applied: %s c' %
                          (i + 1, nm, '%+.1f' % pipe if pipe is not None
                           else '?', src, c, '%+.1f' % (pipe + c)
                           if pipe is not None else '?')))
        for t, foot, dc, secs in drifts:
            if k['start'] <= t < k['end']:
                tm = s + (t - k['start'])/ratio
                p = int(round(tm*rate))
                ev.append((p, line % (tm, p) + 'drift %s %+.2fc %.3fs' %
                           (foot, dc + c, secs/ratio)))
    ev.append((n, line % (n/rate, n) + 'ui    run end (stop button)'))
    ev.sort(key=lambda e: e[0])
    what = [describe(o)] + notched + what
    head = header(path, run.head)
    for i, h in enumerate(head):
        if h.startswith('stop '):
            head[i] = '%s [%s]' % (h, tag)	# run_score's table label
        elif h.startswith('samples '):
            head[i] = 'samples %d (%.1f s)' % (n, n/rate)
        elif h.startswith('keys '):
            head[i] = 'keys %d' % len(segs)
    i = next((i for i, h in enumerate(head) if h.startswith('samples ')),
             len(head))
    head.insert(i, 'warp warp_run.py from %s: %s' %
                (os.path.basename(path), '; '.join(what)))
    txt = head + ['# key <i> <start s> <end s> <name> <semi> <target Hz> '
                  '[redo]; semi: 48 = A4'] + txt_keys
    txt += ['mark %.3f %s' % m for m in sorted(marks)]
    txt += ['replay: test_pitch %s.wav' % os.path.basename(base), '',
            '# timeline: seconds from the start of the WAV (sample/rate), '
            'the sample, what',
            '#  (warp_run.py: hint, drift and ui lines; est from a replay)']
    txt += [e for _, e in ev]
    open(base + '.txt', 'w', encoding='utf-8').write('\n'.join(txt) + '\n')
    return base + '.txt'


def describe(o):
    s = []
    if o['cents_random']:
        s.append('pipes detuned randomly within +-%g c (seed %d)' %
                 (o['cents_random'], o['seed']))
    elif o['cents']:
        s.append('pipes %+g c' % o['cents'])
    if o['semis']:
        s.append('transposed %+d semitones' % o['semis'])
    if o['notch']:
        s.append("the source's noise lines notched")
    if o['gain'] != 1.0:
        s.append('gain %g' % o['gain'])
    if o['agc']:
        s.append('AGC')
    if o['order'] != 'run':
        s.append('order %s' % o['order'])
    if o['legato']:
        s.append('legato %g s' % o['legato'])
    return ', '.join(s) or 'a copy'


def variant(o):
    s = []
    if o['cents_random']:
        s.append('cr%gs%d' % (o['cents_random'], o['seed']))
    elif o['cents']:
        s.append('c%+g' % o['cents'])
    if o['semis']:
        s.append('s%+d' % o['semis'])
    if o['notch']:
        s.append('notch')
    if o['hum'] is not None:
        s.append('hum%+g' % o['hum'])
    if o['noise_from']:
        nb = os.path.basename(o['noise_from']).rsplit('.', 1)[0]
        s.append('noise-' + nb.split('_', 2)[-1] +
                 ('%+g' % o['noise'] if o['noise'] is not None else ''))
    if o['gain'] != 1.0:
        s.append('g%g' % o['gain'])
    if o['agc']:
        s.append('agc')
    if o['clip']:
        s.append('clip')
    if o['order'] != 'run':
        s.append(o['order'])
    if o['legato']:
        s.append('leg%g' % o['legato'])
    return '-'.join(s) or 'copy'


def runs_in(p):
    if os.path.isdir(p):
        out = []
        for root, _, files in os.walk(p):
            out += [os.path.join(root, f) for f in files
                    if f.startswith('run_') and f.endswith('.txt')]
        return sorted(out)
    return [p]


def main():
    args = sys.argv[1:]
    o = dict(d=os.path.join(HERE, 'runs', 'warped'), seed=1, tag=None,
             cents=0.0, cents_random=0.0, semis=0, notch=False, hum=None,
             hum_hz=HUM_HZ, noise_from=None, noise=None, gain=1.0,
             agc=False, clip=False, order='run', legato=0.0)
    paths = []
    num = {'--cents': ('cents', float), '--cents-random':
           ('cents_random', float), '--semis': ('semis', int),
           '--hum': ('hum', float), '--hum-hz': ('hum_hz', float),
           '--noise': ('noise', float), '--gain': ('gain', float),
           '--legato': ('legato', float), '--seed': ('seed', int)}
    while args:
        a = args.pop(0)
        if a in num:
            k, f = num[a]
            o[k] = f(args.pop(0))
        elif a == '-o':
            o['d'] = args.pop(0)
        elif a == '--tag':
            o['tag'] = args.pop(0)
        elif a in ('--noise-from', '--hum-from'):
            p = args.pop(0)
            o['noise_from'] = runs_in(p)[0]
        elif a == '--hum-notch':
            o['notch'] = True
        elif a == '--agc':
            o['agc'] = True
        elif a == '--clip':
            o['clip'] = True
        elif a == '--order':
            o['order'] = args.pop(0)
        elif a.startswith('-'):
            sys.exit(__doc__)
        else:
            paths += runs_in(a)
    if not paths or o['order'] not in ('run', 'up', 'down', 'shuffle'):
        sys.exit(__doc__)
    os.makedirs(o['d'], exist_ok=True)
    rng = random.Random(o['seed'])
    nrng = np.random.default_rng(o['seed'])
    for p in paths:
        out = warp(p, o, o['d'], rng, nrng)
        if out:
            print(out)


if __name__ == '__main__':
    main()
