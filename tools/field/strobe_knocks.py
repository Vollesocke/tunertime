#!/usr/bin/env python3
"""Work noise on the strobe (tools/field/README.md, "Strobe and work
noise"): real knocks of the tuning tool, cut out of Onno's run of 22 Oct,
put onto the held keys of other runs, and replayed through builds of
tools/strobe_replay.c (the real jni/strobe.c), against the same runs
without them.  The difference is what the knock does, and nothing else.

    python tools/field/strobe_knocks.py [--data DIR] [--out DIR]
        [--levels 1,4] [--focus 1,2,3,4] [--runs TXT...] EXE [EXE2]

EXE, EXE2: strobe_replay builds, e.g. of HEAD's jni/strobe.c and of a
change (gcc -O2 -Ijni -Itools/hostjni -o EXE tools/strobe_replay.c -lm).
Replays are kept in --out (default tools/field/build/knocks) by build
name: delete them after rebuilding an EXE of the same name.

The knocks: KNOCKS below, in the Gedackt 8' + Rohrfloete 4' run (Rinteln,
22 Oct), each from 30 ms before it to where its high band (second
difference) is back within 3 dB of before (0.1-0.25 s), with the pipes
and hum under it taken off (the stationary sinusoids before it, fitted
and subtracted: the rest is 20-28 dB under the pipe).  Each key of the
runs held 3 s or more gets one (in turn) 1.4 s after its tap, scaled to
the key's level as the knock stood to its own pipe (level 1) or N times
that (--levels; 4 = 12 dB louder).

Per knock and row, against the clean replay:
  dim      time the drawn pattern is more than 0.15 darker (strobe_replay's
           brightness, 0..1 from dark to full), 8'/4' rows and the others
  disturbed  time the row's band-passed signal is more than half off the
           clean one: the knock and the filter ringing on it, a pattern
           standing still that is not the pipe's
  drift    the focused row (--focus: 1 8', 2 4', 3 2 2/3', 4 2'): error of
           the drift over 2 s around the knock (as a mixture measurement
           would take it), where the clean replay had at least half of it
           usable and was within 25 c; usable seconds lost in the 1.5 s
           after it, and gaps of 0.5 s or more (mixture.java takes a 1 s
           gap for the key released)
With two builds, also on the clean runs: how often their display differs
at all, and the time from each key's onset to its first usable drift.
"""
import argparse, glob, json, os, subprocess, sys, wave
from concurrent.futures import ThreadPoolExecutor
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = 'runs-2026-10-22/run_20261022-091633_Gedackt-8-Rohrfloete-4'
KNOCKS = [193.828, 484.732, 485.340, 29.246, 548.607, 324.048, 140.372,
          78.027, 525.186]
RUNS = ['runs-2026-10-22/run_20261022-091633_Gedackt-8-Rohrfloete-4.txt',
        'runs-2026-10-21/*/run_*.txt']
MINHOLD, AT, WIN = 3.0, 1.4, 1.5
FOOT = {4: "8'", 8: "4'", 12: "2 2/3'", 16: "2'", 24: "1 1/3'", 32: "1'"}


def read_wav(path):
    w = wave.open(path)
    x = np.frombuffer(w.readframes(w.getnframes()), np.int16)
    if w.getnchannels() > 1:
        x = x[::w.getnchannels()]
    return x.astype(float), w.getframerate()


def write_wav(path, x, rate):
    o = wave.open(path, 'wb')
    o.setnchannels(1); o.setsampwidth(2); o.setframerate(rate)
    o.writeframes(np.clip(np.round(x), -32768, 32767).astype(np.int16).tobytes())
    o.close()


def keys_of(txt):
    ks = []
    for line in open(txt, encoding='utf-8', errors='replace'):
        if line.startswith('key '):
            p = line.split()
            ks.append((int(p[1]), float(p[2]), float(p[3]), p[4]))
    return ks


def sub_sines(t, x, fit):
    """x less its stationary sinusoids: the strongest spectral peak of the
    fit part, refined and fitted there, one at a time"""
    res = x.copy()
    tt = t[fit]
    top = None
    for it in range(40):
        y = res[fit]; n = len(y)
        sp = np.abs(np.fft.rfft(y*np.hanning(n), 8*n))
        fr = np.fft.rfftfreq(8*n, t[1]-t[0])
        sp[fr < 40] = 0
        i = np.argmax(sp)
        top = sp[i] if top is None else top
        if sp[i] < top*10**(-36/20):
            break
        best = None
        for f in fr[i] + np.arange(-0.6, 0.601, 0.02):
            a = np.stack([np.cos(2*np.pi*f*tt), np.sin(2*np.pi*f*tt)], 1)
            c = np.linalg.lstsq(a, res[fit], rcond=None)[0]
            e = np.sum((a@c)**2)
            if best is None or e > best[0]:
                best = (e, f, c)
        _, f, c = best
        res -= c[0]*np.cos(2*np.pi*f*t) + c[1]*np.sin(2*np.pi*f*t)
    return res


def cut_knocks(data):
    x, r = read_wav(os.path.join(data, SOURCE + '.wav'))
    clips = []
    for tk in KNOCKS:
        a = int((tk-0.6)*r)
        seg = x[a:int((tk+0.7)*r)]
        t = np.arange(len(seg))/r - 0.6
        res = sub_sines(t, seg, (t > -0.6) & (t < -0.03))
        hp = np.diff(seg, 2, prepend=seg[:2])
        fr = int(0.01*r); nf = len(hp)//fr
        e = 10*np.log10((hp[:nf*fr].reshape(nf, fr)**2).mean(1) + 1)
        ft = np.arange(nf)*0.01 - 0.6
        base = np.median(e[(ft > -0.5) & (ft < -0.05)])
        end = 0.6
        for j in range(np.searchsorted(ft, 0.0) + 4, nf-3):
            if ft[j] > 0.6:
                break
            if np.all(e[j:j+3] < base + 3):
                end = ft[j]; break
        clip = res[(t >= -0.03) & (t < end + 0.02)]
        fade = int(0.005*r); win = np.ones(len(clip))
        win[:fade] = np.linspace(0, 1, fade); win[-4*fade:] = np.linspace(1, 0, 4*fade)
        pipe = np.sqrt(np.mean(x[int((tk-0.5)*r):int((tk-0.1)*r)]**2))
        clips.append(clip*win/pipe)
    return clips


def inject(txt, clips, out, levels):
    """writes <out>/<run>_x<L>.wav; returns the knocks [(key, t, clip)]"""
    x, r = read_wav(txt[:-4] + '.wav')
    inj = []
    for (i, s, e, name) in keys_of(txt):
        if e - s < MINHOLD:
            continue
        t = s + AT
        rms = np.sqrt(np.mean(x[int((t-1.0)*r):int((t-0.05)*r)]**2))
        inj.append(dict(key=i, name=name, t=t, knock=len(inj) % len(clips), rms=rms))
    name = os.path.basename(txt)[:-4]
    for lv in levels:
        p = os.path.join(out, '%s_x%g.wav' % (name, lv))
        if os.path.exists(p):
            continue
        y = x.copy()
        for d in inj:
            c = clips[d['knock']]*d['rms']*lv
            a = int(d['t']*r) - int(0.03*r)
            y[a:a+len(c)] += c[:len(y)-a]
        write_wav(p, y, r)
    return inj


def load(path):
    rows = None; kf = {}; data = []
    for line in open(path):
        if line.startswith('# rows'):
            rows = [int(v) for v in line.split()[2:]]
        elif line.startswith('# key'):
            p = line.split()
            kf.setdefault(int(p[2]), {})[int(p[6])] = float(p[7])
        elif line[0] != '#':
            data.append([float(v) for v in line.split()])
    a = np.array(data); n = len(rows)
    return dict(t=a[:, 0], rows=rows, kf=kf,
                vis=(a[:, 2:2+5*n:5]-101)/153,
                z=a[:, 5:2+5*n:5] + 1j*a[:, 6:2+5*n:5],
                dc=a[:, -3], ds=a[:, -2])


def cents(f, c, s):
    return 1200*np.log2((f + c/s)/f) if s > 0 else np.nan


def longest(z):
    g = cur = 0
    for v in z:
        cur = cur+1 if v else 0; g = max(g, cur)
    return g


def compare(C, I, inj, F, agg):
    t = C['t']; T = t[1]-t[0]
    for d in inj:
        tk = d['t']; kf = C['kf'][d['key']]
        pre = (t > tk-0.7) & (t < tk-0.03)
        a8 = np.abs(C['z'][:, 0]); p8 = np.median(a8[pre])
        rel = np.where((t > tk) & (a8 < 0.25*p8))[0]
        tend = min(tk + WIN, t[rel[0]] if len(rel) else 1e9)
        if tend - tk < 0.6:
            continue
        w = (t >= tk) & (t < tend)
        if F == 1:
            for ri, m in enumerate(C['rows']):
                if kf[ri] <= 0 or np.median(np.abs(C['z'][pre, ri])) < 10:
                    continue
                k = 'main' if m in (4, 8) else 'upper'
                agg['dim_'+k].append((I['vis'][w, ri] < C['vis'][w, ri]-0.15).sum()*T)
                zc, zi = C['z'][w, ri], I['z'][w, ri]
                agg['dist_'+k].append((np.abs(zi-zc) > 0.5*np.abs(zc)).sum()*T)
        ri = C['rows'].index(4*F); f = kf[ri]
        mw = (t > tk-0.7) & (t <= tend)
        if C['ds'][mw].sum() < 0.3:
            continue
        agg['lost'].append(C['ds'][w].sum() - I['ds'][w].sum())
        agg['gap'].append((longest(I['ds'][w] == 0) - longest(C['ds'][w] == 0))*T)
        cc = cents(f, C['dc'][mw].sum(), C['ds'][mw].sum())
        if C['ds'][mw].sum() >= 0.5*(tend-tk+0.7) and abs(cc) <= 25:
            ci = cents(f, I['dc'][mw].sum(), I['ds'][mw].sum())
            agg.setdefault('err%d' % F, []).append(abs(ci-cc) if not np.isnan(ci) else 99)


def onsets(A, B, txt, agg):
    t = A['t']
    diff = np.abs(A['vis']-B['vis']) > 5/153.0
    agg['vdiff'] += diff.sum(); agg['vn'] += diff.size
    for (i, s, e, name) in keys_of(txt):
        k = (t >= s) & (t < e)
        if k.sum() < 10:
            continue
        amp = np.abs(A['z'][:, 0]); med = np.median(amp[k])
        on = np.where(k & (amp > 0.5*med))[0]
        if not len(on):
            continue
        fa = np.where(k & (t >= t[on[0]]) & (A['ds'] > 0))[0]
        fb = np.where(k & (t >= t[on[0]]) & (B['ds'] > 0))[0]
        if len(fa) and len(fb):
            agg['first'].append((t[fa[0]]-t[on[0]], t[fb[0]]-t[on[0]]))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--data', default=os.path.join(HERE, 'data'))
    ap.add_argument('--out', default=os.path.join(HERE, 'build', 'knocks'))
    ap.add_argument('--levels', default='1,4')
    ap.add_argument('--focus', default='1,2,3,4')
    ap.add_argument('--runs', nargs='*', default=None)
    ap.add_argument('exe', nargs='+')
    o = ap.parse_args()
    levels = [float(v) for v in o.levels.split(',')]
    focus = [int(v) for v in o.focus.split(',')]
    os.makedirs(o.out, exist_ok=True)
    txts = []
    for g in (o.runs or [os.path.join(o.data, p) for p in RUNS]):
        txts += sorted(glob.glob(g))
    cache = os.path.join(o.out, 'knocks.npz')
    if os.path.exists(cache):
        clips = list(np.load(cache, allow_pickle=True)['clips'])
    else:
        clips = cut_knocks(o.data)
        np.savez(cache, clips=np.array(clips, dtype=object))
    injs = {txt: inject(txt, clips, o.out, levels) for txt in txts}
    jobs = []
    for exe in o.exe:
        bn = os.path.splitext(os.path.basename(exe))[0]
        for txt in txts:
            name = os.path.basename(txt)[:-4]
            for lv in [0] + levels:
                wav = txt[:-4] + '.wav' if lv == 0 else os.path.join(o.out, '%s_x%g.wav' % (name, lv))
                for F in focus:
                    p = os.path.join(o.out, '%s_%s_x%g_F%d.txt' % (bn, name, lv, F))
                    if not os.path.exists(p):
                        jobs.append(([os.path.abspath(exe), '-F', str(F), wav, txt], p))

    def run(j):
        with open(j[1] + '.tmp', 'w') as f:
            subprocess.check_call(j[0], stdout=f)
        os.replace(j[1] + '.tmp', j[1])
    with ThreadPoolExecutor(os.cpu_count() or 4) as ex:
        list(ex.map(run, jobs))

    def rep(exe, txt, lv, F):
        bn = os.path.splitext(os.path.basename(exe))[0]
        return load(os.path.join(o.out, '%s_%s_x%g_F%d.txt' % (bn, os.path.basename(txt)[:-4], lv, F)))
    for exe in o.exe:
        for lv in levels:
            agg = dict(dim_main=[], dim_upper=[], dist_main=[], dist_upper=[], lost=[], gap=[])
            for txt in txts:
                for F in focus:
                    compare(rep(exe, txt, 0, F), rep(exe, txt, lv, F), injs[txt], F, agg)
            print('%s, knocks x%g: %d knocks' % (os.path.basename(exe), lv, sum(len(v) for v in injs.values())))
            for k in ('main', 'upper'):
                d, s = np.array(agg['dim_'+k]), np.array(agg['dist_'+k])
                print('  %-5s rows (%3d): dim total %5.1f s, max %.2f; disturbed mean %.3f s, max %.2f' % (
                    k, len(d), d.sum(), d.max(), s.mean(), s.max()))
            for F in focus:
                e = np.array(agg.get('err%d' % F, []))
                if len(e):
                    print('  drift %-6s (%3d): |error| mean %.3f c, max %.2f c' % (
                        FOOT[4*F], len(e), e.mean(), e.max()))
            lost, gap = np.array(agg['lost']), np.array(agg['gap'])
            print('  drift usable lost: total %.1f s, max %.2f s; gaps >= 0.5 s: %d' % (
                lost.sum(), lost.max(), (gap >= 0.5).sum()))
    if len(o.exe) == 2:
        agg = dict(vdiff=0, vn=0, first=[])
        for txt in txts:
            onsets(rep(o.exe[0], txt, 0, focus[0]), rep(o.exe[1], txt, 0, focus[0]), txt, agg)
        f = np.array(agg['first'])
        print('clean runs, %s vs %s:' % tuple(os.path.basename(e) for e in o.exe))
        print('  display: %.3f%% of buffers x rows differ; drift %s from onset to first usable: '
              'median %.3f / %.3f s, later in %d keys, earlier in %d (of %d)' % (
                  100.0*agg['vdiff']/agg['vn'], FOOT[4*focus[0]], np.median(f[:, 0]), np.median(f[:, 1]),
                  (f[:, 1] > f[:, 0]+0.01).sum(), (f[:, 1] < f[:, 0]-0.01).sum(), len(f)))


if __name__ == '__main__':
    main()
