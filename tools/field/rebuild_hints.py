#!/usr/bin/env python3
"""Rebuilds the detector hints of debug recordings made before v18.3.

The app hints the detector with the focused row's footage ratio and the
frequency of the target shown (jni/interface.c, pitch_expect).  Recordings
from v18.3 on log these as "hint" lines in their timeline; older ones do
not, so `test_pitch --hints` cannot replay them as the app heard them.
This takes the target and focus from the timeline's "ui" lines instead
and writes NAME.hints.txt next to each NAME.txt, in the same format.

    python tools/field/rebuild_hints.py DIR...

Recordings that already have hint lines are left alone.
"""
import glob
import os
import re
import sys

PC = {'c': 0, 'cis': 1, 'des': 1, 'd': 2, 'dis': 3, 'es': 3, 'e': 4,
      'f': 5, 'fis': 6, 'ges': 6, 'g': 7, 'gis': 8, 'as': 8, 'a': 9,
      'ais': 10, 'b': 10, 'h': 11,
      'cs': 1, 'ds': 3, 'fs': 6, 'gs': 8}
SUP = {'¹': 1, '²': 2, '³': 3, '⁴': 4, '⁵': 5}
SUB = {'₁': 1, '₂': 2, '₃': 3}
FOOT = {"32'": 0.25, "16'": 0.5, "10 2/3'": 0.75, "8'": 1.0,
        "5 1/3'": 1.5, "4'": 2.0, "3 1/5'": 2.5, "2 2/3'": 3.0, "2'": 4.0,
        "1 3/5'": 5.0, "1 1/3'": 6.0, "1'": 8.0, 'none': 0.0}
TARGET = re.compile(r'target (\S+) ([+-]\d+\.\dc)(?: -> (\S+) ([+-]\d+\.\dc))?')
FOCUS = re.compile(r"focus (none|[\d /]+')$")
UI = re.compile(r'\s+([\d.]+)\s+(\d+)\s+ui\s+(.*)$')


def midi(name):
    """A note name as the app writes it in German (C, Fis, c, fis¹, C₁,
    or the organ names Cs, Ds), as MIDI; None if it is not one."""
    m = re.match(r'([A-Ha-h][a-z]*)([¹²³⁴⁵₁₂₃]?)$', name)
    if not m or m.group(1).lower() not in PC:
        return None
    base, mark = m.groups()
    octave = (2 - SUB.get(mark, 0)) if base[0].isupper() \
        else (3 + SUP.get(mark, 0))
    return 12*(octave + 1) + PC[base.lower()]


def hz(note, a_freq):
    name, cents = note
    return a_freq * 2.0**((midi(name) - 69)/12.0 + cents/1200.0)


def target(m, after):
    """(name, cents) of a "target X -> Y" match, after or before"""
    if after and m.group(3):
        return m.group(3), float(m.group(4)[:-1])
    return m.group(1), float(m.group(2)[:-1])


def rebuild(path):
    text = open(path, encoding='utf-8').read()
    if re.search(r'^\s+[\d.]+\s+\d+\s+hint ', text, re.M):
        return 'has hints'
    a = re.search(r'^A_freq (\S+)', text, re.M)
    state = re.search(r'^state_now (.*)$', text, re.M)
    focus_m = re.search(r'^focus_ratio (\S+)', text, re.M)
    if not (a and state and focus_m):
        return 'no header'
    a_freq = float(a.group(1))
    ui = [m.groups() for m in map(UI.match, text.splitlines()) if m]
    # the target before the first change, or the one at the end
    shown = None
    for _, _, what in ui:
        m = TARGET.search(what)
        if m and m.group(3):
            shown = target(m, False)
            break
    if shown is None:
        shown = target(TARGET.search(state.group(1)), True)
    # the focus: at the end, unless it was tapped meanwhile (then the
    # one before the first tap is not known; 8' is the usual)
    taps = [FOCUS.search(w) for _, _, w in ui]
    focus = 1.0 if any(taps) else float(focus_m.group(1))
    if midi(shown[0]) is None:
        return 'cannot read %s' % shown[0]
    lines = ['%9.5f %8d  hint %.4f %.3f' % (0.0, 0, focus, hz(shown, a_freq))]
    for secs, sample, what in ui:
        m = TARGET.search(what)
        f = FOCUS.search(what)
        if f and f.group(1) in FOOT:
            focus = FOOT[f.group(1)]
        if m and m.group(3) and midi(m.group(3)) is not None:
            shown = target(m, True)
        if (m and m.group(3)) or f:
            lines.append('%9.5f %8d  hint %.4f %.3f'
                         % (float(secs), int(sample), focus,
                            hz(shown, a_freq)))
    out = path[:-4] + '.hints.txt'
    open(out, 'w', encoding='utf-8').write('\n'.join(lines) + '\n')
    return None


def main():
    done = 0
    for d in sys.argv[1:]:
        for path in sorted(glob.glob(os.path.join(d, '**', '*.txt'),
                                     recursive=True)):
            if path.endswith('.hints.txt') or \
               not os.path.exists(path[:-4] + '.wav'):
                continue
            why = rebuild(path)
            if why:
                print('%s: %s' % (os.path.basename(path), why),
                      file=sys.stderr)
            else:
                done += 1
    print('%d hint files written' % done)


if __name__ == '__main__':
    main()
