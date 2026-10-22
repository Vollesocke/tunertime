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
sounds less than 0.5 s (passed by with Ton+ or Ton-), is silent. A key
that fills 80% of that span (one key held for a whole run, as when a
mixture is tuned) has no quiet around it: then the quiet is its level
in the run's quietest stretch (0.3-2 s within 6 dB of the WAV's 1st
percentile, the lowest of them). In pure Python that takes 20-30 s per
run of 3-4 minutes.

- **ok**: the key's note agreed (4 in a row, as `on_pitch()`) while
  it sounds (onset to release), and **first ok** the median time from
  the onset to it (0 where the agreement began before the onset: the
  key played before the tap)
- **wrong**: agreements on another note while the key sounds, from a
  streak of estimates that began at or after its onset (one begun
  before is the key before, or this one played before the tap);
  **oct** the octaves and twelfths among them
- **follow**: follow mode emulated over the run: the first key taken as
  heard, then continuity (the octave nearest the note shown, at most a
  fourth away), a wrong note corrected by hand at the next tap. The
  keys right at their release without a hand correction; where the
  note was right there, the next key, played before the tap, stays
  followed. The hold of a correction ends on the input at its floor as
  pitch.c's `pitch_floor()` sees it, the floor starting at the run's
  quietest stretch (the app heard the place before the run) and rising
  only with no note heard (`--floor-18.8`: rising always, as up to
  v18.8)
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

Changed on 22 Oct (Onno's run of that day, Gedackt 8 + Rohrflöte 4,
app: wrong 12 -> 2, ok 26 -> 30, follow 28 -> 30): 10 of the 12 wrong
were the scorer's (5 after the key's release, 5 in its first 0.15 s
from the key before), the two left are the 4' alone (E5: "Register
einzeln angehört") and an F#6 at G5 (the 4' a semitone off while he
tuned it). Onno's runs of 21 Oct: unchanged but for two first ok
(2.1 -> 0 s) and, in the warped runs, wrong 35 -> 34. The two mixture
runs of 22 Oct were silent; now Cs6 (139 s held) is ok, and Plenum
scharf's E4/E5 are read as E5, E6, E7 for tens of seconds at a time:
the mixture's ranks (tone mode, unfocused), not noise.

## Work noise

`noise_score.py` (needs numpy; arguments as `run_score.py`, plus
`--floor-18.8`): what the noise of working on the pipe while its key
is held does to the detector and to follow mode. Bursts are found per
10 ms in the detector's top band (4-8 and 8-11 kHz) against its running
median (1.5 s either side), 12 dB up, inside each key (0.5 s from its
onset and release); for each, from its start to 0.5 s after its end:

- **empty**, **xempty**: empty estimates, and how many more than the
  key's own share elsewhere
- **wrong**: agreements on another note than the key's, begun in the
  window (and not on the next key's note, played before the tap)
- **out**: estimates on the key's note more than 5 cents from both the
  median before the burst and the one after (a tuning step moves both)
- **moved**: the note shown (run_score.py's follow emulation) changed
  from an agreement begun in the window
- **hold**: a correction held there would have ended: another note
  agreed than the detector's last
- **quiet**: per run, not per burst: times the input was taken for
  released while a key sounds (TunerTime KEY_QUIET, HOLD_QUIET), where a
  hold ends, burst or not

`-v` lists the bursts. On the runs of 21 and 22 Oct (142 bursts in
1271 s of held keys, one per 9 s; 97 of them in Onno's normal pass of
22 Oct): the app's estimates and a replay alike, 28% of the estimates
in the windows empty (+174), 16 off by more than 5 cents, no wrong
agreement, the note shown never moved, no hold ended. The detector
drops the windows a knock falls in, and that is all. What ended holds
was the floor (`--floor-18.8`): it rose under the notes in a pass with
no quiet between keys until soft keys were taken for released, 16
times (9 in that pass). Now pitch.c's `pitch_floor()` rises only after
0.7 s with no note heard, and TunerTime's KEY_QUIET is 1.6 (was 1.4):
6 times (the 4' heard alone at E5, and five soft keys of the Gedackt
at Hmth 204, a quiet place, two of them never read), while a release
is seen before the next key's onset as often as before (14 of 239
keys, median 0.68 s after the release). Follow in the runs unchanged.

`tools/sim_organ.c` has the noise, calibrated on these bursts, in two
scenarios: "Field work noise, Gedackt" and "..., Quinte" (keys held
20-24 s back to back, knocks every 3 s, scraping every 10 s; see
`work_run`; the Quinte read a twelfth up and corrected by hand 3 s into
each key). Against the same keys without it (`-DWORK_LEVEL=0`): empty
31% -> 35% and 4% -> 7%, the mixture measurement's worst error 0.58 ->
0.74 and 0.38 -> 0.54 c, nothing else; the one correction undone (a
soft E5 at 1.5 times the floor) is undone without the noise too (3
with the floor and KEY_QUIET of v18.8). The bursts match the runs' in
the broadband level (+1 dB median, +4..+9 at the 90th percentile) and
length (33 ms median); in the top band the sim's stand ~10 dB higher,
as its background there is emptier than a church's with a phone's
hiss.

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
