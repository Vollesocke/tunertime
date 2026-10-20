# Field recordings as a detector regression test

Onno's debug recordings from two organs on 19 Oct 2026 (issue #1): 81 WAV
files of 7 s, each saved when he corrected the followed note by hand, with
the app's timeline next to it. `stops.csv` says which stop was played when,
from his notes.

```sh
sh tools/field/run.sh                  # fetch once, build test_pitch, score
CC="python -m ziglang cc" sh tools/field/run.sh   # without gcc (pip install ziglang)
sh tools/field/run.sh score -v build1/test_pitch build2/test_pitch
```

`score.py` replays each recording through `test_pitch` with the detector
hints the app used. Recordings before v18.3 have none in their timeline;
`rebuild_hints.py` derives them from the UI events (target shown, row
focused). It then follows the estimates as `TunerTime.on_pitch()` does
(4 in a row on one semitone, an empty one starts over). Counted per stop:

- **reached**: the note Onno corrected to was agreed at some point
  (octaves aside, as follow mode folds them after a correction)
- **after**: ... within the 2 s after his correction
- **wrong**: agreements on a note that is neither that one nor the one
  before it: the shown note jumping to a wrong note
- **empty**: share of estimates without a result

The truth is only what Onno corrected to, so "reached" undercounts where
he stepped on before the next key sounded (silence after the correction).
Compare builds, not absolute numbers.

v18.2 against v18.3 (detector of 06f3337):

| stop | recs | reached v18.2 | reached v18.3 | wrong v18.2 | wrong v18.3 |
|---|---|---|---|---|---|
| 1 Rohrfloete 8' | 13 | 1 | 10 | 1 | 1 |
| 1 Prinzipal 4' | 7 | 5 | 7 | 0 | 0 |
| 1 Gedackt 4' | 12 | 3 | 9 | 4 | 5 |
| 1 Rohrfloete + Prinzipal + Waldfloete | 6 | 1 | 3 | 3 | 3 |
| 1 Subbass 16' | 4 | 2 | 2 | 3 | 3 |
| 1 Trompete 8' | 12 | 4 | 4 | 15 | 15 |
| 2 Prinzipal 8' | 1 | 0 | 0 | 0 | 0 |
| 2 Rohrfloete 8' | 15 | 2 | 7 | 3 | 3 |
| 2 Gedackt 8' | 11 | 4 | 5 | 8 | 10 |
| all | 81 | 22 | 47 | 37 | 40 |
