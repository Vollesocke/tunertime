#!/usr/bin/env python3
"""Scores detector builds on the field recordings (tools/field/README.md).

    python tools/field/score.py [-v] DATA_DIR TEST_PITCH [TEST_PITCH...]

Replays every debug recording in DATA_DIR through each test_pitch build,
with the detector hints the app used (the timeline's hint lines, or the
NAME.hints.txt of rebuild_hints.py), and follows the estimates the way
TunerTime.on_pitch() does: a note is agreed after DET_AGREE estimates in
a row on the same semitone, an empty estimate starts over.

The truth is the note the tuner corrected to (the file name's "user-"),
octaves aside, as follow mode folds them after a correction.  Per
recording:
  reached   the corrected note was agreed at some point in the 7 s
  after     ... within 2 s after the correction (5.0 s into the file),
            when the new key was already sounding
  wrong     agreements on a note neither the corrected one nor the one
            before it (octaves aside): the shown note jumping wrongly
  empty     share of empty estimates
Totals and one line per stop (tools/field/stops.csv).
"""
import csv
import glob
import math
import os
import re
import subprocess
import sys
from collections import defaultdict

DET_AGREE = 4
NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
HERE = os.path.dirname(os.path.abspath(__file__))


def midi(name):
    m = re.match(r'([A-G])(s?)(-?\d)$', name)
    return (NAMES.index(m.group(1) + ('#' if m.group(2) else ''))
            + 12*(int(m.group(3)) + 1)) if m else None


def stops():
    rows = []
    with open(os.path.join(HERE, 'stops.csv'), encoding='utf-8') as f:
        for r in csv.reader(l for l in f if not l.startswith('#')):
            if r and r[0] != 'date':
                rows.append(r)
    return rows


def stop_of(rows, date, hm):
    for d, a, b, organ, stop, kind in rows:
        if d == date and a <= hm < b:
            return '%s %s' % (organ, stop)
    return '?'


def replay(exe, rec):
    text = open(rec, encoding='utf-8').read()
    m = re.search(r'^replay: test_pitch(.*)$', text, re.M)
    args = m.group(1).split() if m else []
    t = args[args.index('-t') + 1] if '-t' in args else None
    hints = rec if re.search(r'^\s+[\d.]+\s+\d+\s+hint ', text, re.M) \
        else rec[:-4] + '.hints.txt'
    cmd = [exe] + (['-t', t] if t else []) + [rec[:-4] + '.wav']
    if os.path.exists(hints):
        cmd += ['--hints', hints]
    out = subprocess.run(cmd, capture_output=True, text=True).stdout
    est = []
    for line in out.splitlines():
        p = line.split()
        if len(p) >= 4 and p[2] == 'est':
            est.append((float(p[0]), None if p[3] == '-' else float(p[3])))
    return text, est


def score(text, est):
    a_freq = float(re.search(r'^A_freq (\S+)', text, re.M).group(1))
    user = midi(re.search(r'^user_note (\S+)', text, re.M).group(1))
    before = midi(re.search(r'^note_before (\S+)', text, re.M).group(1))
    sem, n = None, 0
    r = {'reached': 0, 'after': 0, 'wrong': 0,
         'empty': sum(hz is None for _, hz in est), 'est': len(est)}
    for secs, hz in est:
        if hz is None:
            n = 0
            continue
        k = round(69 + 12*math.log2(hz/a_freq))
        n = n + 1 if k == sem else 1
        sem = k
        if n != DET_AGREE:
            continue
        if (k - user) % 12 == 0:
            r['reached'] = 1
            if 5.0 <= secs <= 7.0:
                r['after'] = 1
        elif (k - before) % 12 != 0:
            r['wrong'] += 1
    return r


def main():
    args = sys.argv[1:]
    verbose = '-v' in args
    args = [a for a in args if a != '-v']
    data = args[0]
    # Windows compilers add .exe to the name given
    # and Windows does not start a relative path with forward slashes
    exes = [os.path.abspath(e + '.exe' if not os.path.exists(e) and
                            os.path.exists(e + '.exe') else e)
            for e in args[1:]]
    rows = stops()
    recs = sorted(p for p in glob.glob(os.path.join(data, '**', '*.txt'),
                                       recursive=True)
                  if not p.endswith('.hints.txt')
                  and os.path.exists(p[:-4] + '.wav')
                  and re.search(r'user-[A-G]s?-?\d', p))
    for exe in exes:
        per = defaultdict(lambda: defaultdict(int))
        for rec in recs:
            text, est = replay(exe, rec)
            if not re.search(r'^user_note ', text, re.M):
                continue
            tm = re.search(r'^time (\S+) (\d\d:\d\d)', text, re.M)
            stop = stop_of(rows, tm.group(1), tm.group(2))
            if stop.startswith('-'):
                continue
            r = score(text, est)
            for key in (stop, 'all'):
                per[key]['n'] += 1
                for k, v in r.items():
                    per[key][k] += v
            if verbose:
                print('  %-46s %-34s reached %d after %d wrong %d empty %3.0f%%'
                      % (os.path.basename(rec)[:46], stop, r['reached'],
                         r['after'], r['wrong'],
                         100.0*r['empty']/max(r['est'], 1)))
        print('== %s' % exe)
        print('  %-40s %5s %8s %8s %6s %6s' % ('stop', 'recs', 'reached',
                                              'after', 'wrong', 'empty'))
        for key in sorted(per, key=lambda k: (k == 'all', k)):
            p = per[key]
            print('  %-40s %5d %8d %8d %6d %5.0f%%'
                  % (key, p['n'], p['reached'], p['after'], p['wrong'],
                     100.0*p['empty']/max(p['est'], 1)))


if __name__ == '__main__':
    main()
