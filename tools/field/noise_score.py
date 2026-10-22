#!/usr/bin/env python3
"""Work noise on held keys: what knocks and scraping do to the detector
and the follow logic (tools/field/README.md "Work noise").

    python tools/field/noise_score.py [-v] [--hints MODES] [--floor-18.8]
                                      [--test-pitch EXE...] RUN...

RUN as for run_score.py (run_*.txt files or directories).  Needs numpy.

While a key sounds (its onset and release as run_score.py finds them),
the organ builder works on the pipe: the tuning tool knocks on it, the
cone, the slide or the stopper scrapes, he handles the pipe.  A pipe
held steady has a steady spectrum, so these show as short bursts of
broadband sound: per 10 ms the level in BANDS (4-8 and 8-11 kHz, the
detector's top), against its running median (MEDIAN_S either side),
rising HF_DB or more in one of them.  Bursts closer than
GROUP_S are one; none is taken within EDGE_S of the onset or the
release (chiff, the release's own noise).

For each burst, the window from its start to WINDOW_S after its end
(the detector's analysis window is 0.37 s, its estimates come at the
window's end), the estimates in it:
  empty    estimates with no result, beyond the key's own share of them
           elsewhere (the expected number taken off)
  wrong    agreements (DET_AGREE in a row, the streak starting in the
           window) on another note than the key's, or the next key's
           (played before the tap: its onset is a burst too)
  out      estimates on the key's note more than OUT_C cents from both
           the median before the burst and the one after (a tuning step
           moves both: not an outlier)
and in follow mode emulated as run_score.py does it (continuity on):
  moved    the note shown changed, by an agreement begun in the window
  hold     a correction held there would have ended: an agreement begun
           in the window on another note than the detector's last one
And per run, burst or not:
  quiet    times the input was taken for released while a key sounds
           (from EDGE_S after its onset to EDGE_S before its release):
           down at its floor for HOLD_QUIET (TunerTime.on_pitch), where
           a hold of a correction ends; --floor-18.8: as up to v18.8,
           the floor rising under the notes too and KEY_QUIET 1.4

One line per run and source (the app's estimates; with --test-pitch each
build replayed, hints as in run_score.py); -v lists the bursts.
"""
import os
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import run_score as rs  # noqa: E402

FRAME_N = 1024		# STFT frame, samples
HOP_S = 0.01
BANDS = ((4000, 8000), (8000, 11000))	# the burst bands: above the
			# pipes' strong partials, under the detector's top
HF_DB = 12.0		# over the band's running median
GROUP_S = 0.05
EDGE_S = 0.5
WINDOW_S = 0.5		# after the burst's end: the analysis window and more
OUT_C = 5.0
MEDIAN_S = 1.5		# the running median's half, and the medians of the
			# cents before and after a burst


def running_median(v, k):
    """the median of v over +-k frames (every 5th)"""
    return np.array([np.median(v[max(0, i - k):i + k + 1:5])
                     for i in range(len(v))])


def bursts(run, k):
    """[(start, end, peak dB over the key, broadband dB over it)]"""
    r = run.rate
    a = int((k['on'] + EDGE_S)*r) - FRAME_N
    b = int((k['off'] - EDGE_S)*r)
    if b - a < 4*FRAME_N:
        return []
    x = np.asarray(run.x[max(a, 0):b], dtype=float)
    hop = int(HOP_S*r)
    n = (len(x) - FRAME_N)//hop + 1
    if n < 10:
        return []
    idx = np.arange(FRAME_N)[None, :] + hop*np.arange(n)[:, None]
    w = np.hanning(FRAME_N)
    spec = np.abs(np.fft.rfft(x[idx]*w, axis=1))**2
    f = np.fft.rfftfreq(FRAME_N, 1.0/r)
    t = (max(a, 0) + hop*np.arange(n) + FRAME_N)/r	# frame end
    # per band, over its running median: a mixture's top ranks fill the
    # lower one, a knock then shows only in the upper
    hf = np.full(n, -99.0)
    for lo, hi in BANDS:
        if lo >= r/2:
            continue
        v = 10*np.log10(spec[:, (f >= lo) & (f < hi)].sum(1) + 1.0)
        hf = np.maximum(hf, v - running_median(v, int(MEDIAN_S/HOP_S)))
    bb = 10*np.log10(spec.sum(1) + 1.0)
    bb -= running_median(bb, int(MEDIAN_S/HOP_S))
    out = []
    for i in np.where(hf >= HF_DB)[0]:
        if out and t[i] - out[-1][1] <= GROUP_S:
            s, e, p, q = out[-1]
            out[-1] = (s, t[i], max(p, hf[i]), max(q, bb[i]))
        else:
            out.append((t[i] - FRAME_N/r, t[i], hf[i], bb[i]))
    return [o for o in out if o[0] >= k['on'] + EDGE_S - 0.1]


def follow_trace(run, est):
    """run_score.score()'s follow emulation, per estimate (its trace)"""
    trace = []
    rs.score(run, est, trace)
    return trace


def analyse(run, est, verbose):
    trace = follow_trace(run, est)
    tt = np.array([t for t, _ in est])
    rows, quiet = [], []
    for k in run.keys:
        if not k['valid'] or k['on'] is None:
            continue
        i0, i1 = np.searchsorted(tt, [k['on'], k['off']])
        seg = list(range(i0, i1))
        if not seg:
            continue
        empty_rate = sum(est[i][1] is None for i in seg)/len(seg)
        nxt = next((q['semi'] for q in run.keys if q['i'] > k['i']
                    and q['start'] >= k['end']), None)
        bl = bursts(run, k)
        for s, e, p, q in bl:
            j0, j1 = np.searchsorted(tt, [s, e + WINDOW_S])
            win = list(range(j0, j1))
            if not win:
                continue
            emp = sum(est[i][1] is None for i in win)
            on = [1200*np.log2(est[i][1]/k['hz']) for i in win
                  if est[i][1] and run.semi(est[i][1]) == k['semi']]
            before = [1200*np.log2(est[i][1]/k['hz'])
                      for i in range(*np.searchsorted(tt, [s - MEDIAN_S, s]))
                      if est[i][1] and run.semi(est[i][1]) == k['semi']]
            after = [1200*np.log2(est[i][1]/k['hz'])
                     for i in range(*np.searchsorted(
                         tt, [e + WINDOW_S, e + WINDOW_S + MEDIAN_S]))
                     if est[i][1] and run.semi(est[i][1]) == k['semi']]
            mb, ma = rs.median(before), rs.median(after)
            out = sum(1 for c in on if
                      (mb is None or abs(c - mb) > OUT_C) and
                      (ma is None or abs(c - ma) > OUT_C) and
                      (mb is not None or ma is not None))
            # caused in the window: the streak began at or after the
            # burst, and not the next key, played before the tap (its
            # onset is a burst too)
            def caused(i, note):
                return (trace[i]['start'] >= s and note is not None and
                        (nxt is None or (note - nxt) % 12 != 0))
            wrong = [trace[i]['agree'] - k['semi'] for i in win
                     if trace[i]['agree'] is not None and
                     trace[i]['agree'] != k['semi'] and
                     caused(i, trace[i]['agree'])]
            moved = any(i > 0 and trace[i]['shown'] != trace[i - 1]['shown']
                        and caused(i, trace[i]['shown']) for i in win)
            stable0 = trace[j0 - 1]['stable'] if j0 > 0 else None
            hold = any(trace[i]['agree'] is not None and
                       trace[i]['agree'] != stable0 and
                       caused(i, trace[i]['agree']) for i in win)
            rows.append(dict(key=k, t=s, dur=e - s, hf=p, bb=q, n=len(win),
                             empty=emp, xempty=emp - empty_rate*len(win),
                             wrong=wrong, out=out, moved=moved, hold=hold))
        # the input taken for released while the key sounds, burst or
        # not: a hold would end there (its floor crept up to the key)
        was = True
        for i in seg:
            if est[i][0] < k['on'] + EDGE_S or est[i][0] > k['off'] - EDGE_S:
                continue
            if trace[i]['quiet'] and not was:
                quiet.append((k, est[i][0]))
            was = trace[i]['quiet']
    if verbose:
        for r in rows:
            print('    %-4s %7.2f s %4.0f ms  hf %+5.1f  bb %+5.1f dB  '
                  'est %2d empty %2d wrong %-8s out %d%s%s'
                  % (r['key']['name'], r['t'], 1000*r['dur'], r['hf'],
                     r['bb'], r['n'], r['empty'],
                     ' '.join(rs.interval(d) for d in r['wrong']) or '-',
                     r['out'], '  MOVED' if r['moved'] else '',
                     '  hold' if r['hold'] else ''))
        for k, t in quiet:
            print('    %-4s %7.2f s  released while it sounds' % (k['name'], t))
    return rows, quiet


def line(label, rows, quiet):
    n = sum(r['n'] for r in rows)
    print('  %-34s %6d %5d %6.0f%% %6.1f %5d %5d %5d %5d %7d'
          % (label[:34], len(rows), n,
             100.0*sum(r['empty'] for r in rows)/max(n, 1),
             sum(r['xempty'] for r in rows),
             sum(len(r['wrong']) for r in rows),
             sum(r['out'] for r in rows),
             sum(r['moved'] for r in rows), sum(r['hold'] for r in rows),
             len(quiet)))


def table(name, runs_rows, verbose):
    print('== %s' % name)
    print('  %-34s %6s %5s %7s %6s %5s %5s %5s %5s %7s'
          % ('run', 'bursts', 'est', 'empty', 'xempty', 'wrong', 'out',
             'moved', 'hold', 'quiet'))
    allr, allq = [], []
    for run, (rows, quiet) in runs_rows:
        line(run.name() if verbose else run.label(), rows, quiet)
        allr += rows
        allq += quiet
    if len(runs_rows) > 1:
        line('all', allr, allq)


def main():
    args = sys.argv[1:]
    verbose, modes, exes, paths = False, ['log'], [], []
    while args:
        a = args.pop(0)
        if a == '-v':
            verbose = True
        elif a == '--floor-18.8':
            rs.FLOOR_HELD, rs.KEY_QUIET = False, 1.4
        elif a == '--hints':
            modes = args.pop(0).split(',')
        elif a == '--test-pitch':
            while args and not args[0].startswith('-') and \
                    not os.path.isdir(args[0]) and \
                    not args[0].endswith('.txt'):
                exes.append(args.pop(0))
        else:
            paths.append(a)
    if not paths:
        sys.exit(__doc__)
    exes = [os.path.abspath(e + '.exe' if not os.path.exists(e) and
                            os.path.exists(e + '.exe') else e) for e in exes]
    runs = [r for r in (rs.Run(f) for f in rs.run_files(paths))
            if r.keys and r.x is not None]
    if any(r.est for r in runs):
        rows = []
        for r in runs:
            if r.est:
                if verbose:
                    print('-- %s (app)' % r.name())
                rows.append((r, analyse(r, r.est, verbose)))
        table('app', rows, verbose)
    with tempfile.TemporaryDirectory() as tmp:
        for exe in exes:
            for mode in modes:
                rows = []
                for r in runs:
                    if verbose:
                        print('-- %s (%s, %s)' % (r.name(), exe, mode))
                    rows.append((r, analyse(r, rs.replay(exe, r, mode, tmp),
                                            verbose)))
                table('%s, hints %s' % (exe, mode), rows, verbose)


if __name__ == '__main__':
    main()
