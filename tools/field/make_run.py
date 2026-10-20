#!/usr/bin/env python3
"""Writes fake chromatic runs, to test run_score.py without the app.

    python tools/field/make_run.py [-o DIR] [--keys N] [--first C2]
                                   [--test-pitch EXE] [--crash] [STOP...]

STOP: prinzipal-8 (open), gedackt-8 (stopped), trompete-8 (reed); all
three by default.  Each becomes run_<time>_<stop>.wav and .txt in DIR
(default tools/field/runs/synth), in the format the app's chromatic run
mode writes (tools/field/README.md).  The tones: harmonics with the
stop's spectrum through a phone microphone's high-pass, a chiff on the
flues, a little noise and an exponential reverb tail.  Each key is
shown at a tap, sounds 0.25 s later for 3 s and the next tap comes
about 1 s after it ends.  Keys 3, 6 and 10 are mistuned by +12, -20 and
+35 cents (the "mark" lines say so), and key 7 is played twice: Ton+
to key 8, nothing played, Ton- back to key 7 ("redo").

The timeline has a hint line at each tap (the key's Hz with the focus
ratio, as the app hints the detector) and drift lines with the known
mistuning plus noise.  With --test-pitch, the estimates of a replay
through that build go in as the app's "est" lines, and "ui run key"
lines mark the taps.  --crash writes the .timeline instead of the .txt,
as a run cut short by a crash leaves it.
"""
import array
import math
import os
import random
import re
import subprocess
import sys
import wave

SR = 48000
A_FREQ = 440.0
NAMES = ['C', 'Cs', 'D', 'Ds', 'E', 'F', 'Fs', 'G', 'Gs', 'A', 'As', 'B']
HERE = os.path.dirname(os.path.abspath(__file__))
MISTUNED = {3: 12.0, 6: -20.0, 10: 35.0}	# key number: cents
REDO = 7

STOPS = {
    'prinzipal-8': ("Prinzipal 8'", 'open', "8'", 0.25,
                    [1, .6, .45, .3, .22, .15, .1, .08, .06, .04, .03, .02]),
    'gedackt-8': ("Gedackt 8'", 'stopped', "8'", 0.15,
                  [1, .04, .35, .03, .12, .02, .05, .01, .02]),
    'trompete-8': ("Trompete 8'", 'reed', "8'", 0.0,
                   [.4, .8, 1, .9, .8, .7, .6, .5, .45, .4, .35, .3, .26,
                    .22, .19, .16, .13, .11, .09, .07]),
}


def name(semi):
    """48 = A4, as the run files count"""
    m = semi + 21
    return '%s%d' % (NAMES[m % 12], m//12 - 1)


def semi_of(n):
    m = re.match(r'([A-G]s?)(-?\d)$', n)
    return NAMES.index(m.group(1)) + 12*int(m.group(2)) - 9


def hz_of(semi):
    return A_FREQ * 2.0**((semi - 48)/12.0)


def hp_gain(f, fc=100.0):
    r = (f/fc)**2
    return r / math.sqrt(1.0 + r*r)


def tone(out, start, secs, f0, amps, chiff, rng):
    """adds a held pipe at f0 from sample start: 40 ms attack, the
    fundamental slower, a short noise chiff, then a reverb tail"""
    n_tab = 4096
    parts = [(h, a*hp_gain(h*f0)) for h, a in enumerate(amps, 1)
             if a > 0 and h*f0 < SR/2.2]
    norm = sum(a for _, a in parts)
    tab = [sum(a*math.sin(2*math.pi*h*i/n_tab + 0.37*h) for h, a in parts)
           / norm for i in range(n_tab + 1)]
    level = 9000.0
    hold = int(secs*SR)
    tail = int(1.6*SR)
    step = f0*n_tab/SR
    ph = 0.0
    for i in range(min(hold + tail, len(out) - start)):
        if i < hold:
            env = min(1.0, i/(0.04*SR))
        else:
            env = math.exp(-(i - hold)/(0.4*SR))
        j = int(ph)
        v = tab[j] + (tab[j + 1] - tab[j])*(ph - j)
        if chiff and i < 0.03*SR:
            v += chiff*rng.gauss(0.0, 1.0)*(1.0 - i/(0.03*SR))
        out[start + i] += level*env*v
        ph += step
        if ph >= n_tab:
            ph -= n_tab


def make(slug, keys, first, d, exe, rng, stamp, crash):
    stop, kind, footage, chiff, amps = STOPS[slug]
    # the taps: (semi, start, redo); key REDO is played, Ton+ shows the
    # next key for 0.6 s, Ton- goes back to it
    plan, t, sounds, marks = [], 0.0, [], []
    k = 0
    while k < keys:
        semi = first + k
        plan.append([semi, t, False])
        on = t + 0.25
        cents = MISTUNED.get(k + 1, 0.0)
        sounds.append((on, 3.0, hz_of(semi)*2.0**(cents/1200.0), cents))
        if cents:
            marks.append((on, 'key %d: synthetic: %s pipe %+.1f c' %
                          (len(plan), name(semi), cents)))
        t = on + 3.0 + 0.9 + rng.uniform(-0.1, 0.2)
        if k + 1 == REDO and not plan[-1][2]:
            plan.append([semi + 1, t, False])
            t += 0.6
            plan.append([semi, t, True])
            on = t + 0.25
            sounds.append((on, 3.0, hz_of(semi), 0.0))
            t = on + 3.0 + 0.9
        k += 1
    n = int((t + 0.5)*SR)
    buf = [0.0]*n
    for on, secs, f0, _ in sounds:
        tone(buf, int(on*SR), secs, f0, amps, chiff, rng)
    pcm = array.array('h', (max(-32767, min(32767, int(v + rng.gauss(0, 12))))
                            for v in buf))
    base = os.path.join(d, 'run_%s_%s' % (stamp, slug))
    with wave.open(base + '.wav', 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())

    focus = 1.0
    ends = [p[1] for p in plan[1:]] + [n/SR]
    line = '%9.5f %8d  '
    events = [(int(p[1]*SR), line % (p[1], int(p[1]*SR)) +
               'hint %.4f %.3f' % (focus, hz_of(p[0]))) for p in plan]
    for i, (semi, s, redo) in enumerate(plan):
        p = int(s*SR)
        events.append((p, line % (s, p) + 'ui    run key %d %s %.3f Hz%s'
                       % (i + 1, name(semi), hz_of(semi),
                          ' redo' if redo else '')))
    events.append((n, line % (n/SR, n) + 'ui    run end (stop button)'))
    for on, secs, _, cents in sounds:
        s = on + 0.15
        while s < on + secs:
            p = int(s*SR)
            events.append((p, line % (p/SR, p) + "drift %s %+.2fc %.3fs"
                           % (footage, cents + rng.gauss(0, 1.5), 0.085)))
            s += 4096.0/SR
    t_est = None
    if exe:
        hints = base + '.hints.tmp'
        open(hints, 'w').write('\n'.join(e for _, e in events
                                         if ' hint ' in e) + '\n')
        out = subprocess.run([exe, base + '.wav', '--hints', hints],
                             capture_output=True, text=True).stdout
        os.remove(hints)
        for ln in out.splitlines():
            p = ln.split()
            if len(p) >= 4 and p[2] == 'est':
                events.append((int(p[1]), ln))
                if t_est is None:
                    t_est = float(p[0])
    events.sort(key=lambda e: e[0])

    txt = ['# TunerTime Organ chromatic run', 'format 1',
           'time %s-%s-%s %s:%s:%s' % (stamp[:4], stamp[4:6], stamp[6:8],
                                       stamp[9:11], stamp[11:13],
                                       stamp[13:15]),
           'app make_run.py (synthetic)', 'audio synthetic',
           'A_freq %.1f' % A_FREQ, 'stretch 0.0 c/oct',
           'temperament equal', 'temper_offsets none', 'organ_mode true',
           'mixture_mode false', 'focus_ratio %s' % focus,
           "rows 8', 4', 2 2/3', 2'", 'auto_before off',
           'device none (synthetic)', 'sample_rate %d' % SR,
           'organ synthetic', 'stop %s' % stop, 'kind %s' % kind,
           'footage %s' % footage,
           'note keys 3, 6, 10 mistuned, key %d played twice' % REDO,
           'samples %d (%.1f s)' % (n, n/SR),
           'ended stop button', 'keys %d' % len(plan),
           '# key <i> <start s> <end s> <name> <semi> <target Hz> [redo]'
           '; semi: 48 = A4']
    for i, (semi, s, redo) in enumerate(plan):
        txt.append('key %d %.3f %.3f %s %d %.3f%s'
                   % (i + 1, s, ends[i], name(semi), semi, hz_of(semi),
                      ' redo' if redo else ''))
    for s, m in marks:
        txt.append('mark %.3f %s' % (s, m))
    txt.append('replay: test_pitch %s%s.wav' %
               ('-t %.5f ' % t_est if t_est is not None else '',
                os.path.basename(base)))
    txt.append('')
    txt.append('# timeline: seconds from the start of the WAV (sample/rate), '
               'the sample, what')
    txt.append('#  (synthetic: hint and drift lines, est from a replay '
               'if --test-pitch was given)')
    txt += [e for _, e in events]
    if crash:
        # what a crash leaves: the WAV and the timeline, no .txt
        # with the header marked "#: ", as the app writes it there
        i = txt.index('')
        h = [l for l in txt[:i] if l.startswith('samples ')]
        head = ['#: ' + l for l in txt[:txt.index(h[0])]]
        open(base + '.timeline', 'w', encoding='utf-8').write(
            '\n'.join(head + txt[i + 1:]) + '\n')
        return base + '.timeline'
    open(base + '.txt', 'w', encoding='utf-8').write('\n'.join(txt) + '\n')
    return base + '.txt'


def main():
    args = sys.argv[1:]
    d = os.path.join(HERE, 'runs', 'synth')
    keys, first, exe, crash = 13, semi_of('C2'), None, False
    stops = []
    while args:
        a = args.pop(0)
        if a == '-o':
            d = args.pop(0)
        elif a == '--keys':
            keys = int(args.pop(0))
        elif a == '--first':
            first = semi_of(args.pop(0))
        elif a == '--test-pitch':
            exe = args.pop(0)
            if not os.path.exists(exe) and os.path.exists(exe + '.exe'):
                exe += '.exe'
            exe = os.path.abspath(exe)
        elif a == '--crash':
            crash = True
        elif a in STOPS:
            stops.append(a)
        else:
            sys.exit(__doc__)
    os.makedirs(d, exist_ok=True)
    rng = random.Random(1)
    for i, slug in enumerate(stops or list(STOPS)):
        print(make(slug, keys, first, d, exe, rng,
                   '20261021-1%d1500' % i, crash))


if __name__ == '__main__':
    main()
