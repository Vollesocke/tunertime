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
(4 in a row on one semitone, an empty one starts over).

The truth is the note Onno corrected to, and only around the correction,
5.0 s into each file: a recording often spans several keys of a run.
Counted per stop:

- **after**: the corrected note was agreed in the 2 s after the
  correction (octaves aside, as follow mode folds them). Where he stepped
  on before the next key sounded it is not, so compare builds, not
  absolute numbers.
- **wrong**: agreements on another note in those 2 s, the shown note
  jumping to a wrong one
- **octave fix**: where the correction was an octave, the share of
  estimates in the 2 s before it that had the octave right. These stay
  wrong with every build: with a row focused, the detector looks for the
  partial near the note shown, so a wrong octave holds until corrected by
  hand. That is a design question, not a threshold.
- **empty**: share of estimates without a result

| stop | recs | after v18.2 | after v18.3 | after now | wrong v18.2 / v18.3 / now | empty v18.2 / now |
|---|---|---|---|---|---|---|
| 1 Rohrfloete 8' | 13 | 0 | 9 | 10 | 0 / 0 / 0 | 82% / 46% |
| 1 Prinzipal 4' | 7 | 5 | 6 | 7 | 0 / 0 / 0 | 51% / 30% |
| 1 Gedackt 4' | 12 | 3 | 8 | 8 | 3 / 3 / 3 | 75% / 47% |
| 1 Rohrfloete + Prinzipal + Waldfloete | 6 | 0 | 1 | 2 | 0 / 0 / 0 | 49% / 31% |
| 1 Subbass 16' | 4 | 2 | 2 | 2 | 1 / 1 / 1 | 20% / 16% |
| 1 Trompete 8' | 12 | 1 | 1 | 2 | 5 / 5 / 5 | 30% / 27% |
| 2 Prinzipal 8' | 1 | 0 | 0 | 0 | 0 / 0 / 0 | 100% / 100% |
| 2 Rohrfloete 8' | 15 | 1 | 5 | 7 | 2 / 2 / 2 | 74% / 54% |
| 2 Gedackt 8' | 11 | 1 | 2 | 2 | 1 / 2 / 2 | 41% / 26% |
| all | 81 | 13 | 34 | 40 | 12 / 13 / 13 | 58% / 39% |

"now": `WEAK_CONF` 0.55 instead of 0.60 (jni/pitch.c). 0.50 reaches 44 but
with 15 wrong jumps and lets speech through in `sim_organ`.
