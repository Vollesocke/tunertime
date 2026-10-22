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

## Chromatic runs

The app's chromatic run mode records a whole stop: every key in turn,
the shown note stepped with Ton+, so each key's note is known. Its .txt
lists the keys (`key i start end name semi target_hz [redo]`, 48 = A4;
a segment runs from one tap to the next, the last one of a note that
sounds counts: stepping back with Ton- passes notes without playing them)
before the usual timeline. After a crash there is only the .wav and a
.timeline; the keys then come from its `ui run key` lines. A key marked
"Wrong key played" in the run is left out, and so is one named in a
`<run>.fix` (next to the run, or in `tools/field/fixes/`, committed):
`skip <key> <why>` lines and header lines that replace the run's own,
e.g. `kind open`. Onno's of 21 Oct: the short octave of Hmth 204 (no
Cs2, Ds2), and the Plenum's kind.

```sh
python tools/field/run_score.py [-v] runs/              # the app's estimates
python tools/field/run_score.py --hints none,keys,log \
    --test-pitch build1/test_pitch build2/test_pitch runs/
sh tools/field/run.sh synth            # fake runs (make_run.py), scored
```

Per key, from its onset to the next tap. Onset and release come from
the key's level in the WAV: at its harmonics 1-4 (its target Hz), each
averaged over 17 periods, so the neighbouring semitones fall in the
first null. A plain RMS envelope does not work in a church: mains hum,
blower rumble (40-300 Hz) and the mic's hiss sit within 3-10 dB of a
soft treble Gedackt, and with the organ sounding almost all the time a
run-wide noise floor is organ sound. The onset is where the level
starts to rise towards its peak (the window's end reaching the pipe),
the release where it falls 15 dB below it; a key that never rises
12 dB above its quiet (the 10th percentile within 5 s around it), or
sounds less than 0.5 s (passed by with Ton+ or Ton-), is silent. In
pure Python that takes 20-30 s per run of 3-4 minutes.

- **ok**: the key's note agreed (4 in a row, as `on_pitch()`), and
  **first ok** the median time from the onset to it
- **wrong**: agreements on another note; **oct** the octaves and
  twelfths among them
- **follow**: follow mode emulated over the run: the first key taken as
  heard, then continuity (the octave nearest the note shown, at most a
  fourth away), a wrong note corrected by hand at the next tap. The
  keys right at their release without a hand correction.
- **empty**: share of empty estimates while the key sounds
- **off>5c**: keys whose pipe is more than 5 cents off: the median
  strobe drift of the focused row (timeline), else of the estimates on
  the right note against the key's target Hz. `-v` shows both per key.

Hints for the replay: `none`, `keys` (each key's Hz with the run's
focus ratio, what the app hints during a run), `log` (the timeline's
hint lines; the default). `make_run.py` writes three stops (open,
stopped, reed, C2-C3) with keys 3, 6, 10 mistuned by +12, -20, +35
cents and key 7 played twice; with `--test-pitch` its timeline gets
the estimates of a replay. All 39 keys come out right: it tests the
tools, not the detector. With hints the estimates read up to 1.2 cents
off on the synthetic tones (0.1 without): judge pipes by the drift.

## Warped runs

`warp_run.py` (needs numpy) makes new runs from real ones: each key that
counts in `run_score.py` is cut from its tap to the next, so the real
pipe, onset, room and noise stay, with a known truth (the note, and the
pipe's own offset from the drift). Options detune the pipes (`--cents`,
`--cents-random`), transpose (`--semis`), notch the source's own noise
lines (`--hum-notch`: the stationary peaks of its quiet stretches), add
a hum series (`--hum DB` against the keys' level, mixed as measured at
Hmth 202) or another run's noise bed (`--noise-from`), apply gain, AGC,
clipping, reorder (`--order up|down|shuffle`) and overlap keys
(`--legato S`). Output goes to `runs/warped` in the app's format with
hint lines and no estimates: score with
`run_score.py --hints keys --test-pitch EXE runs/warped`.

```sh
python tools/field/warp_run.py --hum -14 tools/field/data/runs-2026-10-21/run_Gedackt2
python tools/field/warp_run.py --cents-random 25 --seed 3 --order shuffle \
    tools/field/data/runs-2026-10-21
```

A plain copy scores as its source (21 Oct runs: same ok and wrong
per key, cents within 0.4). `--cents 20` moves the cents column by 20.0
(median; most keys within 1 c); run_score then calls a few detuned bass
keys silent, as its key level is taken at the target Hz. Hmth 202's
noise lines (a 49.25 Hz blower series, not the mains) stand at -12 dB
against its Gedackt's keys, Hmth 204's at -33 dB; the Hmth 204 Gedackt
with `--hum -14` scores as the Hmth 202 one (79% ok, 34% empty).

## Strobe and work noise

`tools/strobe_replay.c` plays a WAV through the real `jni/strobe.c`,
1024 samples per buffer as the app reads them, with each key of a run's
.txt set as by its tap. Per buffer and row it prints the brightness the
row draws (caught from the pixels), the band-passed signal's peak and
its phase at the row frequency, and the focused row's drift (`-F`, the
focus ratio).

`strobe_knocks.py` measures what the knocks of tuning work do to the
strobe: nine real knocks cut from the Gedackt 8' + Rohrfloete 4' run of
22 Oct (Rinteln), the pipes under them subtracted, put onto every key
held 3 s or more of that run and the four of 21 Oct, at their recorded
level against the pipe and 12 dB louder; each build replays the runs
with and without them, so the difference is the knock alone.

```sh
cc -O2 -Ijni -Itools/hostjni -o old tools/strobe_replay.c -lm  # before a change
cc -O2 -Ijni -Itools/hostjni -o new tools/strobe_replay.c -lm  # after it
python tools/field/strobe_knocks.py old new
```

The replays are kept in `tools/field/build/knocks` by build name (~1 min
for two builds); delete them after rebuilding one.

It prints per build and level: how long the pattern is drawn darker
(8'/4' rows and the upper ones), how long the knock and the filter's
ringing stand in for the pipe ("disturbed"), the drift error of a 2 s
measurement around the knock per focused row, and the usable drift lost
(gaps of 0.5 s and more: mixture.java takes a 1 s gap for a release);
for two builds also how much their display differs on the clean runs
and the time from each onset to the first usable drift.

On the runs as recorded (22 Oct), the knocks hardly reach the 8' and 4'
rows: their energy lies far above the pipe's band (the big one in E5,
33 dB over the pipe broadband, raised the 4' row 3 dB). They darkened
the weak upper rows, where the pipe has little, for up to 0.8 s; since
`strobe_gain()` (jni/strobe.c) about 0.1 s.
