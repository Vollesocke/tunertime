# Handoff: TunerTime Organ

**UI rule (Onno, 22 Oct; replaces "no popups, ever" of 21 Oct):** no
popups for everyday hints. The one after a manual correction ("follow:
only within a fourth") was "really annoying" and stays out, as do the
auto hint and preset added/imported/exported. Error messages are wanted
(A out of range, row too high, no file picker, a tab refused in a run,
packing failed), and so are the messages about recordings (run
started/saved/failed, GitHub packing). Dialogs the user opens are fine.

**What it is for (Onno, 21 Oct):** a tuning aid. The organ builder always
decides by ear what he tunes and what not. Its main use is to keep the
strobe following so that, with as little input as possible, he sees
exactly what he hears. So the hand always wins over the detector, and the
app does not explain or warn where he already knows.

**Season plan (Onno, 22 Oct):** "the tracking is really good now". Finish
the features and UI until everything runs plausibly, then pause until
the next tuning season (from the end of May 2027). Real tuning (work on
the pipe changes its sound) can only be tested then. Meanwhile he may
recruit other testers: the app has to be stable and documented. Quiet
Gedackte are the hardest, then Rohrfloeten; reeds, Prinzipale and 16'
work well. Noise beds differ per organ (>20 organs needed to learn from
them), so no noise library and no sample simulator for now.

Status as of 23 Oct 2026: release
[**v19.2-organ**](https://github.com/Vollesocke/tunertime/releases/tag/v19.2-organ)
(code 32) is on `main`: v19.0 (Onno's spec of 21 Oct complete, packages A
and B), his two wishes of 22 Oct (v19.1) and his issue #9 (v19.2, below).
After it, per Onno's season plan: only what testers need (stability, the
manual, his answers), detector work again from May 2027. **Start with
"23 Oct: v19.2" below.**

## 23 Oct: v19.2 (issue #9)

Onno reviewed the German manual (his LibreOffice export `de_2.html` is in
`tools/field/data/issue9`, git-ignored; its text edits were merged into
our `de.html` by hand, and into `en.html`) and asked for:
- **Mixture: no 20-cent rule.** `on_pitch` no longer drops a detection
  more than 20 c from the mixture's measured pitch; the nearest step
  (with Dis/Es) is taken. sim_organ never had the rule.
- **Mixture focus defaults to 4'**: `mixture_measure` with nothing
  focused takes 4', else 8', else the lowest row; `parms.load` defaults
  `mixture_focus` to 2.0 when the pref is missing (installs that saved one
  keep it). FollowTest.focus_per_mode expects 4'.
- **Corner "Mix+01.5c" removed**: it was `mixture_offset_str()`, the same
  number as the mixture display beside the key. The corner shows "Orgel"
  in every organ mode; `status_mix` deleted.
- **Offset without the tenth** in the Ton tab (`note.whole_cents_str`);
  `parms.load` rounds `note_cents` to whole cents (the legacy default took
  the temperament's tenths). The Kammerton tab shows the offset only when
  it is not 0, bold amber. His alternative (ignore it there, reset on
  going back to Ton) is open; he is still thinking.
- **"Mixtur: dem Bezugschor folgen"** in its own category "Mixtur" in the
  settings, out of Debug.
- **"Töne messen" greyed** for presets matching Pythagorean or a meantone
  (`preset.wolf_template() >= 1`): calculated, not measured.
- English B1 title "Set standard A".
- Manual: new section "Wie steil die Streifen laufen" from strobe.c: a row
  draws a column per cycle of its frequency, `multiplier =
  floor(1 + f/width)` cycles per column, so up to about the screen width in
  Hz (1080 px portrait: ~1 kHz) a cent looks equally steep everywhere and
  all rows of one pipe lean alike; above, steeper in steps. Onno's note
  said higher partials always lean more: asked him (release notes).

**Ask Onno (in the v19.2 notes):** is the strobe-slope text what he sees,
or should the slope follow the beat rate everywhere; does deleting "im
Reiter Temperierung" / "Strobe ausgeblendet" mean he wants strobe and
follow in that tab; the open Kammerton-offset alternative; which of the
"up for debate" points (speaker icon for Signal, Preset vs. Temperierung,
drag to reorder presets) he wants. His manual wording "Abweichung ... von
dessen ermittelten Tonhöhe" was made exact: for the reference rank from
its measured pitch, for others from the pure interval to it.

## 22 Oct night: v19.1

Onno on 19.0: "einziges Haar in der Suppe".
- "Cent ± und 0.1 cent ± vertauschen, die Nachkommastelle rechts", said of
  "Töne eingeben". "Ton eingeben" (B2) already had ±1c in the middle and
  ±0.1c on the right; the A buttons (`afreq_buttons`, both orientations:
  the A tab and B1 "Kammerton einstellen", the first page of the same
  flow) had +0.1 left, +1 right. Swapped there: ±1 left, ±0.1 right. If
  he meant B2 after all, ask him what he expects there.
- Organ mode on by default (`preferences.xml`, `parms`, `Settings`; manual
  step 2). The pref is only stored once switched, so an install where it
  was never touched is in organ mode after the update too.

Tests: JVM tests, MeasureTest, FollowTest, UiLayoutTest main_organ,
main_normal, measure on tt_fairphone4; the buttons tapped on the emulator.

## Pick up here (23 Oct)

1. **Onno's answers** to the questions in the v19.2 notes (above). His
   answers in #9 to the earlier ones: "set standard A" (he reviews the
   English UI later), Back B2 -> B1 is right, Dis/Es only matter for
   meantone/Pythagorean (measuring greyed there), "C#" fine, the tips stay
   (his manual kept them), no recommendation of German note names. The
   light help theme he did not answer.
2. **Issue #9** has a short reply (23 Oct) linking v19.2 and pointing to
   the questions in its release notes; watch it for his answers. Issues
   #7 and #8 have no reply; Volker decides whether to post one.
3. **Crash reports from 18.8+:** the next "Aufnahmen an GitHub" share can
   carry `files/crashes`. If he still sees the app close, ask for one.
4. **Small leftovers**, none urgent:
   - stale comments: `pipelength.java` mentions a temperature setting (the
     length is fixed at 18 °C); a ShareRecordings comment still says
     "Paste, drop, or click" (the issue text itself says "Add Files")
   - the wolf button shows "Gs–Ds", the temperament texts say "Gis–Es"
   - Help is not in UiLayoutTest yet; the full UI suite was not run for
     18.9/19.0
   - sim: a hand correction made before the detector's first agreement on
     a key is undone by that agreement; knocks raise the mixture
     measurement's worst error (0.58 -> 0.74 c); a knock's start can shift
     a weak upper row's drift by up to 2.4 c (strobe.c)
   - Hmth 204: soft keys near 1.6x the floor are still sometimes taken for
     released (5 cases)
5. **Not now (season plan):** the sample-based simulator (real key
   samples, work noise, noise beds) was planned and set aside on Onno's
   answer: work on a pipe changes its own sound, and noise beds need >20
   organs. Revisit with real tuning recordings from May 2027; he can also
   deliver Prinzipal, reed and 16' runs, though those already work well.
6. **Housekeeping:** agent worktrees under `.claude/worktrees/` (spec-a,
   spec-b, crash-hunt, manual, the two noise branches, older ones); all
   merged. Remove with `git worktree remove` (unlock first where locked)
   and `git branch -d`. The data of issues #7/#8 is in
   `tools/field/data/issue7-2026-10-22`, `runs-2026-10-22`, `issue8`
   (git-ignored).

## 22 Oct late: v19.0

**Package B** (`doc/plan-onno-spec-2026-10-21.md`; branch `spec-b`):
- Hosting: "Töne messen" on the preset edit page starts a second
  `TunerTime` instance with `measure.EXTRA_PRESET`
  (startActivityForResult); the strobe is bound to TunerTime's SurfaceView
  and jni, so no separate Activity. Logic in `measure.java`, hooks in
  TunerTime's onResume/onPause/onSaveInstanceState/onBackPressed/
  update_status/menu. In measure mode `onPause` skips `parms.save()`, so the
  user's prefs stay and the next `parms.load()` restores them; stored notes
  are saved at once (`parms.save_presets()`).
- B1 "Kammerton einstellen": octave rows 8' to 1/4' on the 8' A, A ±1/±0.1,
  Reset, "Set"; no Auto, no detection. B2 "Ton eingeben: <preset>": C..H of
  the 8' octave against equal temperament, cent ±1/±0.1, "Ton speichern"
  (replaces the preset's note on that pitch class, Dis and Es alike; steps
  to the next unsaved; n/12 in the corner, stored names blue). After the
  12th: shifted to A = 0 c (offsets clamped to ±49.9 c), back to the edit
  page with "Matching" highlighted (the offer, no popup). Back: B2 -> B1 ->
  edit page, stored notes kept unshifted.
- B3 Matching (`matching.java`, `MatchActivity`): needs exactly one note
  per pitch class. RMS of the 12 differences at A = 0, no free offset;
  candidates all built-ins plus user presets except the one matched and
  copies of a built-in within 0.1 c; > 2 c amber, > 4 c red. Note + 3
  columns at 360 dp. Example (agent): Lambert measured with ±0.3 c errors
  and A 0.5 c high -> Lambert 0.53, Young II 1.05, Rothert 1.08 c.
- Manual section 18 rewritten from the code (was "coming soon").

**Ask Onno (package B):** the English labels ("Set concert pitch", "set",
"store note"); Back from B2 to B1 rather than out of the flow; one stored
note replacing both Dis and Es; "C#" in the table with German UI but
English note names. From the manual: light help page in a light phone
theme; the tips (about 3 c per degree, tremulant off, Rohrfloete lengths
approximate, cent+ for an Unda maris); recommend German note names?

**Tests for 19.0** (tt_fairphone4, 1080x2340 at 420 dpi): FollowTest
11/11, MeasureTest 2/2, UiLayoutTest measure, matching, main_organ,
preset_edit, main_normal; JVM tests (matching, measured, temperaments,
meantone). The full UI suite was not run.

**Emulator screens reset (check before UI tests):** at 18:19 on 22 Oct both
AVDs' `config.ini` went back to the pixel_4 profile (1080x2280, 440 dpi),
probably when an agent copied tt_fairphone4 to run a second instance. The
layout tests then fail with TOUCH_TARGET (buttons 44-47dp) and OVERLAP at
the cent display in portrait at font scale 1.3. Restored to README's values
(tt_fairphone4 420 dpi, tt_galaxy_s25 450 dpi, both 2340 high); check with
`adb shell wm size` / `wm density`. Agents: never copy or edit the AVDs.

## 22 Oct evening: v18.9

**The mixture-mode crashes (issue #8, Onno's `dumpsys dropbox`)**, two bugs:
- All four 18.7 crashes: `ArrayIndexOutOfBoundsException` in
  `note.class_name`. With "Mixture: follow the reference rank" on (his
  setting), pitch.c divides the partial by a high focused row's ratio, the
  pitch lands under C0, and `note()` took Java's negative remainder as an
  index. `note()` floors now; `on_pitch` drops estimates under 15 Hz.
- The 18.3 crash in the same log (null TextView in `on_pitch`):
  `layout-land/mixture_buttons.xml` never had `mixture_pitch`, so in
  landscape the first note heard in mixture mode closed the app, untouched.
  Commit 349e91d says it was fixed in 18.4: wrong, it was still in 18.8;
  fixed now (7314618), shown as a caption so the buttons keep 48dp.
- The crash hunt (branch `crash-hunt`) also fixed: strobe.c writing outside
  the bitmap when the surface is lower than the rows, ShareRecordings on
  rotation while packing, Help without a browser, PresetNoteActivity after
  Delete. Checked clean: 4.27 M note-arithmetic cases (NaN, Inf, 1 Hz to
  200 kHz, A 220-880), layout ids of both variants, JNI refs, buffer bounds,
  threading, preset import.

**Work noise (Onno: next step after "the tracking is really good")**, on
the Rinteln runs of 22 Oct (issue #7, `tools/field/data/runs-2026-10-22`;
the Gedackt 8 + Rohrfloete 4 run is "a normal pass in real life"):
- Detector and follow already ignore it: 142 bursts in 1271 s of held keys
  (`tools/field/noise_score.py`): 0 wrong agreements, the note never moved,
  no hold ended. No burst rejection added to pitch.c.
- The floor crept up to soft pipes in a long pass (65 -> 280 in 270 s), and
  follow took them for released. `pitch_floor()` now rises only after 0.7 s
  with no note; `KEY_QUIET` 1.4 -> 1.6. Taken for released while sounding
  16 -> 6; eval.sh otherwise unchanged (bench 537/537, runs 191, warped 398).
- Strobe: a knock no longer darkens the upper rows (up to 0.8 s -> ~0.1 s)
  or gaps the drift (gaps >= 0.5 s 19 -> 0), so a knock no longer looks like
  a key release to the mixture measurement. `tools/strobe_replay.c`,
  `tools/field/strobe_knocks.py`. Left: a knock's start can shift the drift
  of a weak upper row by up to 2.4 c; knocks raise the sim's worst mixture
  error (0.58 -> 0.74 c).
- run_score.py: wrong agreements only while the key sounds and from its
  onset; a key held through the whole run is not "silent". Gedackt 8 +
  Rohrfloete 4: ok 26 -> 30 of 30, wrong 12 -> 2 (both real: the 4' alone at
  E5, an F#6 while a pipe was being tuned). sim_organ: "Field work noise"
  scenarios.

**Onno's spec of 21 Oct**: `doc/plan-onno-spec-2026-10-21.md`, with his
answers. Package A in v18.9: A tab Auto/Reset (median of 2 s on the shown
note), Temperierung tab a view only, the note kept over all tabs, a
focused row per mode (`parms.note_focus` for Ton and Kammerton,
`parms.mixture_focus`), the auto button Folgen/Fixiert by tap (off and
locked differed only in clearing continuity), Lambert (1774). Package B
(branch `spec-b`, an agent was building it at release time): "Toene
messen" and "Matching" from the preset edit page.

**Manual**: Help opens `app/src/main/assets/help/{de,en}.html` offline
(HelpActivity), light or dark with the phone. Its section 18 announces
package B for 19.0: update it when B merges. Questions for Onno from it:
light help page in a light phone theme ok?; the tips (about 3 c per
degree, tremulant off, Rohrfloete lengths only approximate, cent+ for an
Unda maris); recommend German note names?

**Tests for 18.9:** FollowTest 11/11 and UiLayoutTest main_normal and
main_organ on tt_galaxy_s25 (the first run there); JVM tests 8/8. Package
A's agent ran FollowTest and the main-screen layout tests on
tt_fairphone4 before the merges.

## 22 Oct: crashes in mixture mode, the GitHub share (v18.8)

**Crashes (open).** Onno, on v18.7: the app closes completely in mixture
mode, several times since the evening of 21 Oct, also with nobody touching
it, so the trigger is in the sound path (detector, follow, measurement),
not the buttons. No pattern known. Not reproduced: the code changed since
18.5 (pitch.c noise lines and gate, the hold ended by the level, Toasts
gone) read clean; monkey 8000 events (silent input) and 15 min of the Hmth
204 Plenum run played into mixture mode with follow on (WAV injection,
below) ran without a crash. Next: Onno's crash files and his Rinteln runs
of 22 Oct morning ("Plenum mixtur, 1 key, 139 s", "Plenum scharf, 2 keys,
175 s": maybe cut off by a crash; a run's .txt/.timeline are flushed every
second). Issue #5 (his share of 22 Oct) has no attachments.

- `CrashLog.java`: a Java exception writes `files/crashes/crash-*.txt`
  (stack, `EventLog.state`, with debug recordings on the last 20 s of the
  event log). At start (Android 11+) the system's `ApplicationExitInfo`
  records not seen before (pref `crashlog_last_exit`) become
  `exit-*-{crash,native,anr}.txt`, from Android 12 with the trace (`.trace`:
  an ANR's thread dump, a native tombstone as protobuf). Records survive an
  app update (checked), so crashes from before 18.8 show up, the native
  ones with their trace; an old Java crash only says "crash". Tested on the
  emulator: `am crash`, and `kill -11` as root.
- WAV injection, test only: `git apply tools/field/inject-wav.patch` (revert
  before a release); it adds
  `Listener.inject` (a mono 16-bit WAV in `files/inject.wav` played in real
  time in place of the mic).
  Push the WAV as the shell user (`adb unroot` first), else
  SELinux denies the app reading it.

**GitHub share (v18.8).** Onno's share of 22 Oct packed all 202 MB (7
zips: the runs of 21 Oct he had attached to #4 by hand and 100 old debug
recordings) and none got attached from the phone. Now
(`ShareRecordings.java`): a dialog with one line per run ("21.10. 12:39
Plenum prinzipale (stopped), Hmth 204, 57 keys, 233 s, 20.1 MB"), per day of
debug recordings and one for the crash reports; ticked are those never
packed, the others say "(packed before)" and can be ticked; no fallback to
"all". Each line gets its own zips, named after it (`run_<time>_<stop>.zip`,
`_1`, `_2` over 23 MB); the zips of the share before are deleted from
Downloads/TunerTime first. `tools/field/unpack.py` takes any `*.zip`.
- The three Rinteln runs were marked packed by the failed share: Onno has
  to tick them.
- Tested for real (issue #6, closed): emulator tt_fairphone4 with Firefox
  158 (Mozilla's APK, logged in to GitHub as Vollesocke: **stay logged in,
  for later tests**). On a phone GitHub's new-issue page has "Add Files"
  (paperclip under the text), not "Paste, drop, or click"; the picker opens
  on "Recent" (empty): hamburger, Downloads, TunerTime, long-press one zip,
  tap the others, Select. The links go to the top of the text. The issue
  text now says so. The attachments came back byte-identical.

Before it, 21 Oct, late: release
[**v18.6-organ**](https://github.com/Vollesocke/tunertime/releases/tag/v18.6-organ)
(below, "21 Oct late"): the detector against blower hum,
an input level bar, the hold ended by the input level, the temperament
list a display only, no "key released" text in mixture mode, the sim
calibrated on the church runs, warped runs. FollowTest and the main-screen
layout tests clean on tt_fairphone4; the full UI suite was not run.

Before it, 21 Oct, evening: release
[**v18.5-organ**](https://github.com/Vollesocke/tunertime/releases/tag/v18.5-organ): Onno's feedback on v18.4 (below, "21 Oct evening"): hand
corrections hold, mixture mode with Dis/Es and its buttons as corrections,
the A tab shows the note. FollowTest and the main-screen layout tests
clean on tt_fairphone4; the full UI suite was not run for it.

Before it, 20 Oct, late: release
[**v18.4-organ**](https://github.com/Vollesocke/tunertime/releases/tag/v18.4-organ)
is on `main` (pre-release v18.4-organ-pre2 before it). UI tests clean on
tt_fairphone4 (full suite on pre2, the menu tests again on the release);
tt_galaxy_s25 still not run. It adds to v18.3: chromatic run recordings, meantone with more
than 12 notes, the mixture measurement ending on key release, a warning when
the mixture is heard more than a semitone off, and tapping a workbench note.

## Pick up here (21 Oct, night)

**Releases of 21 Oct** (all dev builds, `org.galexander.tunertime.dev`,
signed with the debug key; APKs attached to the GitHub releases; the
native libs of all four ABIs checked for the new code in 18.6 and 18.7):
v18.5 (hand corrections hold, mixture Dis/Es), v18.6 (detector against
blower hum, level bar, hold ended by the level), v18.7 (no popups, level
bar off by default). Onno has not tried 18.5-18.7 in a church yet.

**Ask Onno:**
1. Two popups are left that are not Toasts, both after an explicit
   action: the error dialog when a preset file import/export fails
   (`PresetSelectActivity.error_dialog`), and the progress dialog while
   recordings are zipped (`ShareRecordings`). Do they go too ("no popups
   ever")?
2. His answer to question 4 (mixture warning), "follow in mixture mode as
   in tone mode without a focused row", is what the default already does.
   Did he mean more, e.g. the far warning ("more than a semitone off")
   gone too? It is a status line, not a popup.
3. Aliquots "always shown as such" (they are tuned pure): needs a design.
   Proposal to put to him: with continuity on, a detection a twelfth (or a
   seventeenth) over the note shown, held steadily, is taken as the
   2 2/3' (1 3/5') partial of that key: the key stays, its row is focused
   if shown. Today only tapping the row does that.
4. The level bar (Settings, off): width and place fine? He has not seen
   it on a phone.

**Detector, next** (measure everything with `sh tools/field/eval.sh`;
the baseline numbers are in "21 Oct late" below):
- B4 and Cs5 at Hmth 204 are still never read: they pass the level gate
  now, but beat with a second tone (5-9 c wobble at 10-20 Hz) and score
  0.4-0.5 confidence. Ideas: merge two peaks closer than ~25 Hz into one
  before the harmonic search, or a longer window for a lone partial.
- The noise lines are not learnt where the hum is over MIN_RMS (60): a
  phone right next to a blower falls back to the old behaviour. The
  extreme warped run (`--noise-from` at 0 dB, noise rms 169) shows it.
- The Plenum in run mode gets no hints (mixture mode, unfocused): its
  octave errors in the run's own estimates are an artefact of that; with
  the key hints the replay is 47/47. In run mode the detector could be
  hinted with the key shown, as the truth is known there.
- The hold of a correction: `sim_organ -f new` Field Gedackt 202 still
  undoes 2 corrections; look at which detections do it.

**Tools of 21 Oct:**
- `tools/field/eval.sh [OUT]` (or `PITCH=variant.c sh ...`): one
  detector build against the synthetic bench, `sim_organ -f new`, the 19
  Oct recordings, the 21 Oct runs and the warped runs, in one summary
  (~5 min; numpy needed). Variants side by side in different OUT dirs.
- The warped evaluation set (`tools/field/runs/eval`, git-ignored) is not
  in git; make it again with `warp_run.py -o tools/field/runs/eval`:
  `--tag noise202 --noise-from <run_gedackt1> --noise 0` on
  run_Gedackt2; `--tag quiet --gain 0.5` on run_Gedackt2 and
  run_Rohrflote; `--tag up2 --semis 2` on run_Rohrflote and run_gedackt1;
  `--tag detune --seed 7 --cents-random 15` on all four (dirs under
  `tools/field/data/runs-2026-10-21`, unpacked from issue #4's zips).
- `tools/field/fixes/*.fix`: corrections to runs after the fact (the
  short octave of Hmth 204, the Plenum's kind).
- C compiler: `python -m venv <dir>; <dir>/Scripts/pip install ziglang`
  (retry, the network drops), then `CC="<dir>/Scripts/python.exe -m
  ziglang cc"`. The venv of 21 Oct was in a session scratch dir and is
  gone.

**Tests:** FollowTest (androidTest, 7 tests: corrections hold, Dis/Es,
mixture keys, focus kept) and the main-screen, Settings and preset-list
layout tests are clean on tt_fairphone4 for 18.7. Not run since 18.4:
the full UI suite, and tt_galaxy_s25 at all. **The emulator stops
rotating after a few hours of tests** (WRONG_ORIENTATION in the report,
and CLIPPED menu items that follow from it): cold-boot it (`adb emu
kill`, then `emulator -avd tt_fairphone4 -no-snapshot-load`) and set
`settings put system accelerometer_rotation 0`; the tests then pass.

## 21 Oct late: v18.6, the detector on the church runs

**Why the churches were hard** (`sim_organ` header, `warp_run.py`):
- Hmth 202 is the Becker organ: the blower motor (single-phase
  asynchronous, with a capacitor) hangs in the case, and the phone stood
  on the music desk. Its lines: a 49.25 Hz series (the motor, not the
  mains) and a line at 42.5-44.3 Hz, 12 dB under the soft Gedackt pipes.
  Hmth 204 (meantone) has a new blower, built apart from the organ: weak
  hum, but a quiet phone input; its two keys never read (B4, Cs5) were
  the only ones under the detector's MIN_RMS of 60.
- Small flue pipes are nearly sines, and a quarter of the keys beat with
  a second tone (5-9 c wobble at 10-20 Hz) -- the sim had neither.

**Detector** (`jni/pitch.c`, measured with `tools/field/eval.sh`):
- Noise lines: learnt from windows near the noise floor, under MIN_RMS,
  with nothing detected (between keys), below 1 kHz; taken off the
  spectrum (1.5x) before the peaks are found; the peak threshold stays
  on the spectrum as it was. Not learnt when the noise is over MIN_RMS
  (then as before).
- Level gate: MIN_RMS 60, down to 1.6x the noise floor in a quiet place,
  not under 20. The floor rises 0.5%/s (2%/s forgot the gaps).
- Results: runs 186 -> 191 of 206 agreed (Gedackt Hmth 202 44 -> 48);
  warped 367 -> 398 of 465; 19 Oct recordings 40 -> 44 of 81 (wrong 13
  -> 14); sim Field empty 33/16/7% -> 16/2/0%, shown 491 -> 514 of 568;
  synthetic bench 537/537. B4/Cs5 at Hmth 204 still empty: confidence
  0.4-0.5 (the beating); next to look at.
- The detector runs whenever the strobe does (0.3% of a core) and passes
  its level, floor and gate: the level bar, and the hold of a correction
  now ends on the input dropping to its floor (KEY_QUIET 1.4, at least
  20) instead of 0.3 s of empty estimates (Onno: the hand always wins).
  sim_organ and run_score mirror it.

**Onno's answers of 21 Oct** (to the questions of 20 Oct, numbered as in
"Done on 20 Oct (evening)"):
1. Taste+/- in mixture mode on Dis and Es: yes (v18.5). Exactly 12 notes
   keep the organ names ("Ds"): leave so.
2. Dis/Es by hand: the manual override always has priority (the hold;
   now ended by the level, above).
3. A measurement cut short by the release: no mark. "Taste zu frueh
   losgelassen": show nothing, the organ builder knows (removed).
4. Mixture warning: "follow in mixture mode as in tone mode without a
   focused row" -- the default already (the detector ignores the
   reference row; Settings "follow the reference rank" off). The far
   warning is unchanged; ask if he meant more.
5. Tapping in the temperament list: out entirely, as in his fork (done).
- Octave held with a focused row: yes, as is.
- Aliquots in follow mode: show them as such wherever possible, they are
  tuned pure and would otherwise be tempered. Not built yet: today only by
  tapping their row. A proposal is needed (e.g. with continuity, a
  detection a twelfth or a seventeenth over the note shown taken as the
  mutation's row of that key).
- Preset tab: settled, a temperament menu only. Menus: benched.

## 21 Oct evening: Onno's runs scored, his feedback (branch `onno-22oct`)

**Runs** (issue #4, four zips, recorded with v18.4-organ-pre2; the release
only adds the GitHub menu item, so nothing is missing from them): Rohrflöte
8' and Gedackt (Hmth 202, A 439.5), Gedackt and Plenum in mixture mode
(Hmth 204, 1/4 comma meantone with Dis/Es and Gis/As, A 443.7). Unpacked
in `tools/field/data/runs-2026-10-21` (not committed).

- `run_score.py` called 131 of 210 keys silent: its broadband RMS floor was
  the organ itself (keys held nearly back to back) and 50 Hz hum. Now the
  key's level at its own harmonics 1-4; 208 keys scored (the other two are
  0.04 s taps). App: 186 ok, follow 187, 12 wrong (10 octaves, all Plenum).
- Plenum in run mode sends no hints (mixture mode, unfocused detector):
  C2 an octave high 7 times. Replayed with the key hints: 47/47.
- Gedackt (Hmth 202): 12 middle/treble keys 100% empty in the app though
  the pipes sound and are in tune (drift -0.2..-2.6 c); without hints the
  detector drops 2.5-4 octaves to the 50 Hz hum. A detector task.
- Rohrflöte F5 Gs5 A5 F6 90-100% empty, in tune. Gedackt (204): the Cs2
  key sounds as D2 and Ds2 as E2: a short octave, Ton+ pressed a little
  late (Onno). Left out with `tools/field/fixes/*.fix`, and the Plenum's
  kind set to open (he left the dialog's "stopped"); 206 keys, 10 wrong.
- Pipes off: Rohrflöte A2 -26 c; Gedackt (202) bass Cs2 +15.7, Ds2 +15.3,
  G2 +12.7 c.

**Onno's feedback** (relayed by Volker), and what was done:
1. "Correcting the detection by hand was much better before": the follow
   logic is unchanged since his fork except that a correction did not hold.
   The detector kept agreeing on the wrong note and undid it within 0.4 s
   (sim_organ -f new: 28 of 55 corrections undone with the key held), and a
   Dis/Es picked by hand always went back. Now the overruled note is not
   followed until another note is agreed or 0.3 s of nothing (release).
   sim: 0 undone, wrong shown 58 -> 30; runs: follow unchanged (187).
   Onno confirmed: before, the correction "was kept as the right note,
   and the next one looked for within 500 cents of it; now a correction
   was completely ignored". In mixture mode the key and octave buttons
   did not count as corrections at all (in his fork neither): now they
   do, with continuity, as in note mode (sim_organ already assumed so).
   His Plenum: in the bass it jumped C <-> c and could not be corrected
   without turning detection off; in his fork he declared C once (in
   note mode, where an octave correction turns on continuity) and went
   through the row. Replayed from the run (run_score follow emulation):
   as v18.4 mixture mode, C2 jumps an octave 7 times, D2 and G4 shown an
   octave high; with this branch, after C declared once, 46 of 46 keys.
2. A tab: shows the note set next to the Hz.
3. Mixture mode and Dis/Es: key+/- skipped Es (D Dis E); follow rejected Es
   (41 c from Dis), and an Es measured at Dis carried +41 c as the organ's
   pitch to every key after it, all rejected: "detection crashes". Now
   both notes, as in note mode (parms.note_alt shared), named apart.
   No real crash found in the code; if it still happens, a debug recording.
4. Mixture mode: the focused row stays until he taps another (a
   regression in v18.4 for him). It moved by itself in two places, both
   older than his fork: Ton± focused the row last measured on again, and
   a row whose partial went too high for the strobe was dropped for the
   8'. Now a too-high row is just not measured (a toast when measure is
   tapped); with nothing focused, measure still takes the 8' row.
- Tests: `FollowTest` (androidTest, 7 tests, fail on v18.4); UiLayoutTest
  main_normal/main_organ clean on tt_fairphone4.

## Onno's church day (21 Oct): chromatic runs

The point: real truth for the detector. Menu → "Chromatischen Lauf
aufnehmen" (`RunRecorder.java`, README "Chromatic run recordings").

- **How:** set the first key as the note shown, fill in organ, Register,
  kind, footage, notes; Start. Then each key: hold about 3 s, release, Ton+.
  Ton− plays a key again (later take wins). "Markieren" for noise, faulty
  pipe, wrong key, text. "■ Ende" in the action bar stops.
- **What is recorded:** the whole input as WAV (streamed, header updated
  every second), and in the .txt: header (app, device, microphone setting
  and the one in use, A, stretch, temperament and offsets, organ/mixture
  mode, focus, rows, auto before), one `key` line per key (start, end, note,
  semitone, target Hz, redo), marks, and the full timeline: every detector
  estimate (the detector runs with auto off, `parms.run_active`), hints,
  the focused row's strobe drift (= how far each pipe is off; tap the row
  of the stop's footage), UI events.
- **During a run:** auto is off (the note shown is the truth); the screen
  orientation is locked; Settings/Help/About are hidden; FFT and Preset
  tabs are refused. Leaving the app or the screen turning off ends and
  saves the run (the wake lock keeps it on). At most 20 min (~5.8 MB/min
  at 48 kHz). A crash leaves the WAV and a `.timeline` with the header
  (`#: ` lines); `run_score.py` reads those too. Auto stays off after a run.
- **Getting the files:**
  `adb pull /sdcard/Android/data/org.galexander.tunertime.local/files/runs`
  (Onno's `.local` app; `.dev` on the S25). No share button yet.
- **Scoring:** `python tools/field/run_score.py [-v] [--test-pitch
  build/test_pitch ...] [--hints none,keys,log] runs/` (tools/field/README
  "Chromatic runs"): per key onset from the WAV envelope, the follow logic's
  agreement, wrong notes (octave/twelfth flagged), empty share, cents,
  strobe drift, and follow emulated over the run. `make_run.py` makes
  synthetic runs to test the tool (`sh tools/field/run.sh synth`).
- **Also in pre2 (Onno, 20 Oct):** the microphone choice is gone from
  Settings and the mic-switch toast too (always auto; logged only, and the
  run header names the mic in use); tapping the A readout opens a keypad
  (220-880 Hz); the strobe long press opens the partial settings as soon
  as it is recognised.
- **Recordings to GitHub** (menu, `ShareRecordings.java`, Volker's
  request): zips the new runs and debug recordings into
  Downloads/TunerTime (under 25 MB each, big files in pieces), then opens
  a new issue with the list; the zips are attached by hand (no GitHub
  login in the app). `tools/field/unpack.py` joins them. Tested on the
  emulator with a 50 MB file: 3 zips, the files come back identical.
- **Then:** tune `MIN_CONF`/`WEAK_CONF`, the octave lock with a focused
  row and the Trompete bass on these runs (Next steps 2).

## Done on 20 Oct (evening), for Onno to try

- **Meantone with more than 12 notes** (design below, now built): the
  workbench's wolf menu ends with "Töne wählen (auch mehr als 12)…", a
  checklist of the 21 chain notes Fes..His with cents. Picked notes and the
  temporary wolf are one state, saved in the preferences (the wolf was lost
  on restart before), cleared when another temperament is selected.
  `preset.temper_offsets()` is a table with 1 or 2 offsets per pitch class;
  `parms.note_alt` picks one; Ton± goes D→Dis→Es→E; follow takes the
  nearest of all notes; mixture mode keeps to the first. Names spelled from
  the chain with more than 12 notes (German "dis¹"/"es¹"). JUnit test
  `meantoneTest` (`./gradlew testDevUnitTest`).
- **Mixture: key released early** (proposal 1): the focused row's level
  under 30 % of its mean for 0.5 s, or no usable block for 1 s, ends the
  measurement; with at least half the window before the drop that is the
  result, else it is discarded ("Taste zu früh losgelassen"). `sim_organ -r
  off` for the old behaviour. The simulator's mixture truth now counts only
  while the pipe sounds: the old "large errors" were mostly that. 3 s holds:
  1.75 c → 0.14 c mean error; last 16' keys no longer flagged as beating.
- **Mixture: heard over a semitone off the rows** (proposal 4): 4 estimates
  in a row more than 100 c from every row (octaves folded) add a status line.
- **Workbench tap:** tapping a note in the Temperierung table makes it the
  current note (Onno's string from 06f3337 is back).

**Questions for Onno** (from the work above):
1. Meantone picker: one list (12 wolf places, then "Töne wählen…")? The
   21-note checklist with cents? Mixture Taste± also on both Dis and Es?
   Spelled names also with exactly 12 picked notes (Dis instead of Es)?
2. A Dis↔Es step by hand does not start continuity (no octave fold) in
   follow mode: follow may flip back to the nearer one. Fine?
3. Key release: 0.5 s of low level before a release counts (shorter makes
   celeste ranks look released). Mark a cut-short measurement in the status?
   "Taste zu früh losgelassen" also shows when the reference rank is muted.
4. Semitone warning against all rows or only the reference row?
5. Workbench tap: also switch to note mode?
6. Run mode: restore follow after a run? A share button for the files?

**Onno's answers (20 Oct, late), to 1, 3 and 6:**
- Meantone picker (1): he had dropped the idea after thinking it over,
  but it is fine as built.
- Key release timing (3): tune it on the real recordings he provides on
  21 Oct.
- Restoring follow after a run (6): does not matter.
- Share button (6): not needed yet, he uses adb; interesting once wider
  testing starts. Volker asked for a "Recordings to GitHub" menu item
  anyway (ShareRecordings.java, below).
- Still open: 2, 4, 5 and the meantone details in 1.

## State before the evening of 20 Oct

Status as of 20 Oct 2026: release
[**v18.3-organ**](https://github.com/Vollesocke/tunertime/releases/tag/v18.3-organ)
is on `main` (GitHub Vollesocke/tunertime). It holds Onno's commits from his
fork (PR #2) and the fixes after reviewing them (below). The APK is installed
on the S25. `organ` is the older integration branch that became v18.0.

**Since then (end of 20 Oct):**
- `main` also has the field regression test and `WEAK_CONF` 0.55 (PR #3).
- Branch `onno-list` (pushed, not merged) adds:
  - the strobe readout only in mixture mode;
  - version 18.4-organ-pre1 (code 22).

  Its APK is the pre-release
  [v18.4-organ-pre1](https://github.com/Vollesocke/tunertime/releases/tag/v18.4-organ-pre1),
  not installed on the S25 yet.
- **Next, not started:** meantone with more than 12 notes (Next steps 3).
  The design worked out so far:
  - A tuning from the picker is a set of positions on the chain of fifths
    (Fes Ces Ges Des As Es B F C G D A E H Fis Cis Gis Dis Ais Eis His),
    pitch n x fifth (1/4 comma 696.58 c, 1/6 comma 698.37 c,
    Pythagorean 701.96 c), normalized so A = 0. Picking a wolf is a
    contiguous 12; picking notes is any set, each natural note plus ♭/♯.
  - `preset.temper_offsets()` (now null on duplicate pitch classes) becomes
    a table with 1 or 2 offsets per pitch class. A new
    `parms.note_alt` picks which one, and semi± step through both
    (D -> Dis -> Es -> E; in meantone Dis is below Es).
  - Names: spelled from the chain position; German with "is"/"es" when
    more than 12 notes (Onno). Organ Cs/Ds otherwise.
  - Follow mode takes the nearest of all notes. Mixture rounding can stay
    on the first one.
  - The temporary wolf (`preset.temporary_wolf_root`) and the new
    note set would share that state: "until another temperament is
    selected".

## Review fixes in v18.3 (20 Oct 2026)

Onno is *the* user of this app; Volker only helps. Where Onno changed
behaviour on purpose, that is the requirement. Fix bugs in it, measure it,
and ask him, but don't revert it.

- **Mixture timing:** Onno's trailing window stays (done about 1.6 s after
  the key instead of 2.5 s). Its buffer was too short for the low rows and
  did not subtract what it overwrote; fixed. A measurement that follow starts
  skips the first 0.5 s of the onset (29 keys off by > 0.5 c without, 9
  with). `sim_organ` models it (`-m fixed` for the old 2 s / 3 s).
- **Mixture follow:** a setting, "Mixture: follow the reference rank", off
  by default.
  - Off (Onno's design): the detector ignores the focus.
  - On: the detector looks for the focused rank, and the key moves by a whole
    tone at most (`MIX_SEMIS`).
  - `sim_organ -x focus` against the default, wrong keys: 0 instead of 4
    ("Mixture III muted"), 0 instead of 8 ("Mixture chromatic, ranks
    alone").
  - Onno compares in practice.
- **20 c window** around the mixture's measured pitch, not the note-mode
  offset.
- **Landscape crash:** the containers 06f3337 added were missing from
  `layout-land/main.xml`. Found by UiLayoutTest.
- **Workbench:** the temperament name wraps instead of being cut.
- **Debug recordings:** the timeline starts with the hints in effect, and
  `--hints` only appears in the replay line when there are any.
  `test_pitch` no longer fails on files without hints.
- **Temperaments:** Rohlf's Werckmeister III (the usual IV) was missing.
  Rohlf's III/IV/V are now marked "(Rohlf)"; his numbering is one off the
  usual names. Niederrodenbach is the same table as modified Werckmeister
  1691. All 23 Rohlf charts are in.
- **Wolf picker** also for presets saved in whole cents.
- **Resources:** length-mode symbols moved to strings.xml, unused strings
  removed, non-ASCII as entities.
- **Build:** `local` build type (`assembleLocal`, `.local`); version
  18.3-organ (code 21).
- **The ">90 cent without moving to the next note" report** (Onno, 19 Oct):
  v18.2 reset the agreement count on every empty estimate, and on real organs
  about half the estimates are empty. Replaying the 19 Oct recordings, Onno's
  weak-confidence path in `pitch.c` already fixes most of it. The corrected
  note is reached in 47 of 81 recordings, against 22 with v18.2 (replayed
  with the detector hints rebuilt from each file's UI timeline, as recordings
  before v18.3 have none; without them 45 and 19); tolerating
  gaps on top changes nothing. Most of the rest are silence after a hand
  step.

Field recordings of 19 Oct (two organs, 79 WAV+TXT, Onno's notes on which
stop was played when) are attached to issue #1.

## Onno session update (19 Oct 2026)

Implemented in the working tree and installed on Onno's Fairphone 4 local app
(`org.galexander.tunertime.local`, version 18.2-organ):

- Reworked the Preset tab into the Temperierung workbench: temperament name,
  note/deviation table, temporary wolf-fifth selection for Pythagorean and
  meantone presets, and the navigation bar along the bottom. The wolf choice
  lasts until a different temperament is selected. The first four built-in
  temperaments retain their special positions; the rest of the selection list
  is alphabetized using the active locale.
- Added the remaining tunings from Orgelbau Rohlf's list, including the
  Neidhardt village (1732), small-town (1732), large-town (1724), and modified
  temperaments. Existing equivalent entries were not duplicated.
- Removed the optional “Temper note mode” setting. A selected temperament now
  always affects note mode.
- Mixture mode now follows detected notes like tone mode, displays the detected
  pitch, has octave-step buttons, and rejects detections more than 20 cents
  from the nearest expected mixture note. The focus still controls the tuning
  offset reference rather than note recognition.
- Updated organ note labels, length-mode controls, strobe partial-selection
  long-press shortcut, and detector debug recordings/replay hints.

The offline `assembleDev` build succeeded and the APK updated the existing
`.local` package without clearing its data. In this environment the repository
`keystore.properties` symlink points to a missing `/home/greg` file and the
checked-in build type uses the `.dev` suffix. To make the local update, the
source was copied to `/tmp/tunertime-localbuild`, the temporary copy's dev
suffix was changed to `.local`, and it was signed with
`/home/onno/.android/debug.keystore` (SHA-256
`a99683ada5e58b0ff9a1047ed37cf416632407e798eed4ae48be62685581d20c`). The
installed local APK was checked to use that same certificate before updating.
The repository build configuration itself was not changed for this workaround.

Verification this session: `git diff --check` passed and `assembleDev`
completed. No automated test suite was run. Keep the `.dev`, `.local`, and
Play Store packages distinct; the installed Play Store app remains untouched.
The changes are committed on `main` as `Add organ temperament workbench and
tuning updates` and pushed to Onno's fork at
https://github.com/Slaughthammer/tunertime. The upstream repository is
`Vollesocke/tunertime`; the active GitHub account does not have write access
there, so send a pull request from the fork when upstream integration is
wanted.

## Where things stand

- **Releases:** [v18.0-organ](https://github.com/Vollesocke/tunertime/releases/tag/v18.0-organ)
  [v18.1-organ](https://github.com/Vollesocke/tunertime/releases/tag/v18.1-organ)
  and [v18.2-organ](https://github.com/Vollesocke/tunertime/releases/tag/v18.2-organ)
  (Onno's answers), each with an APK attached. The APK is the `dev` build type, app ID
  `org.galexander.tunertime.dev`, labelled "TunerTime Organ". It installs next
  to Greg's Play Store app.
- **Features:** see README "What this fork adds" and the release notes. In
  short: organ mode (footage rows, tap to select a partial, mixture mode,
  length mode), German note names and interface, organ-aware pitch detection
  with debug recordings, microphone selection, new name and icon.
- **Tested:**
  - Synthetic tones: `tools/test_pitch`, 537/537.
  - Realistic organ simulator: `tools/sim_organ`, 262/264 keys detected,
    0 wrong notes.
  - Automated UI tests on emulators like the Fairphone 4 and Galaxy S25: clean
    except one known issue.
  - The app starts and runs on a real Galaxy S25 (Android 16).
- **Not tested:** real organ pipes in a real church. That is the next
  important step. Follow-mode corrections save WAV recordings (dev build) for
  tuning the detector; each recording now has a timeline of what the app did.

## Next steps (of 20 Oct; superseded by "Pick up here" above)

1. **Onno retests v18.3**, above all the ">90 cent" case, and mixture
   follow with the setting off and on. His `.local` app updates with
   `assembleLocal` signed with his key. The UI tests ran on tt_fairphone4
   only; tt_galaxy_s25 is still to do.
2. **Detector on real organs.** The 19 Oct recordings are a regression
   test now (`tools/field`, `sh tools/field/run.sh`). `WEAK_CONF` 0.55
   came from it. What it shows beyond thresholds:
   - **Octave lock with a focused row:** the detector looks for the
     partial near the note shown, so once the shown octave is wrong it
     stays wrong until corrected by hand. All 12 octave corrections had
     the octave wrong throughout the 2 s before (Prinzipal 4', Subbass
     16', Trompete). Options: search both neighbouring octaves and take
     the one that explains the spectrum better, or stop profiles
     (proposal 5) that fix the octave per stop. Ask Onno.
   - **Trompete:** wrong jumps in big leaps (5 of 13). In the bass it
     reads high (2nd/3rd harmonic), as the phone mic cuts the low
     partials. Its runs are followed correctly.
   - **Too little truth** (run mode built 20 Oct evening, above): a recording only says which note Onno corrected
     to, and only around the correction. For real tuning on real pipes:
     a recording mode that records a whole chromatic run while he steps
     the key by hand (Ton+), so every key has its note. That would be
     Onno's job in the church; ask him.
3. **Onno's list of 19 Oct, still open:**
   - ~~meantone with more than 12 notes~~ (done 20 Oct evening, above): (subsemitones D#/Eb, G#/Ab), with one
     menu to place the wolf or pick the notes, and German names with "is"
     for such tunings;
   - the Preset tab has no note stepping since it became the workbench.
     Ask Onno whether that is right.
   - ~~workbench tap~~ (done 20 Oct evening): 06f3337 had an unused string "Tap a note to set it as the current
     reference pitch" for the workbench: a feature he may have planned
     (removed as unused in 8fb013b).
4. **Tidy up the menus** after his tests. There is much more going on than in
   the original app. Onno sees the features as deliberate, long-considered
   ideas, not feature creep, so this is about arrangement, not removal.
5. **Mutations in follow mode:** with continuity, a Quinte 2 2/3' played
   chromatically without its row tapped needs a hand correction on every key
   (simulator: 0/16 right without help, the old rule 15/16). With the row
   tapped it is 16/16. Check with Onno whether tapping the row is acceptable
   practice, or whether continuity should also accept a twelfth.

## Onno's answers (in v18.2)

The questions sent to the organ tuner (`C:\Users\Volker\tuner\TunerTime-Organ-Fragen.html`)
are answered and implemented:

| # | Topic | Decision |
|---|---|---|
| 1 | Length mode and temperature | No change. The tuner allows for the temperature by hand via the A frequency (~0.7 Hz/K). Since 06f3337 (Onno, one-line length readout) the T± buttons are gone and the speed of sound is taken at 18 °C. |
| 2 | Octave correction in follow mode | Current note only. After a hand correction, continuity: detections are taken in the octave nearest the note shown and move it only within a fourth (±500 c). Ends on auto off/on or 10 s of silence. `sim_organ -f old/new` compares. Mutations: focus their row. |
| 3 | Cent readout top right in organ mode | Tapped row: its measured offset. Only in mixture mode (Onno, 19 Oct): there the focused row's measured offset; otherwise nothing, the target and its offset are in the title. |
| 4 | Debug recordings | Same triggers; the .txt now has a timeline of estimates, strobe drift and UI events (`EventLog.java`), on the WAV's time base; `test_pitch -t` replays on the same base. |
| 5 | Wording for stopped pipes | Unchanged. |
| 6 | Mixture step by step | Ton± focused the reference row last measured again; since v18.5 the focused row stays until tapped (Onno, 21 Oct). |

Settled by the user: 4/5' (not 2/5') and 3 1/5' in the footage list, no other
extra footages. Mixture ranks are muted by hand (abstecken). 2 s mixture
averaging stays, to be tested in practice (now 3 s below 8'). Length mode
shows only the mm to cut or add.

## Proposals not done yet, ranked

1. ~~**Key released before a 3 s bass measurement ends:**~~ (done 20 Oct evening) the mixture
   measurement keeps collecting the release and reverb tail. The simulator
   showed large errors. Stop or discard the measurement on release. The
   release notes tell users to hold the key.
2. **Real recordings:** tune `MIN_CONF` (jni/pitch.c), the beat threshold
   `BEAT_SWING` 0.7 (mixture.java) and `STABLE_CENTS` (pipelength.java) on
   real organ WAVs (`tools/test_pitch file.wav`).
3. ~~Octave shift plus a selected row can double the shift~~: gone with
   question 2 (continuity folds, it does not shift).
4. (done 20 Oct evening) **Mixture: warn when the reference is more than about 1 semitone from the
   target,** where the strobe drift wraps.
5. **Stop profiles** (larger): a preset per stop with its rows, open/stopped
   and expected octave, e.g. "Gedackt 8'", "Mixtur IV 1 1/3'".
6. **Detector above 9 kHz:** raising `MAX_PARTIAL` broke top pipes in the
   simulator. Leave it unless real data says otherwise.
7. **NDK r28** instead of r21. Needed for Play Store publishing; the 16 KB
   alignment flag is already in `jni/Android.mk`.
8. **Patches for Greg** (optional): the fixes to his original code, i.e. the
   preset note editor crash, Android 15 edge-to-edge and 16 KB pages, could go
   to him as small separate patches.
9. **Rohrgedackt option** in length mode: not recommended. It is computed as
   stopped (λ/4); the direction is right, but the mm can be off by some tens
   of percent. Better: an optional calibration (cut 1 mm, measure the cents).

Measurements and plots: `tools/measure/FINDINGS.md`. The same report is
`C:\Users\Volker\tuner\TunerTime-Organ-Messbericht.html`. FINDINGS.md quotes
numbers from before the simulation fixes and DET_AGREE 4.

## Building on this PC

Details in README "Building"; what is specific here. Since 20 Oct the
checkout is `C:\Users\Volker\Documents\tunertime`. The old profile, with
the previous checkout and its agent worktrees, is on `E:\Users\Volker`.

- **JDK 17:** `%LOCALAPPDATA%\jdk17` (Temurin, unpacked, not installed).
  `export JAVA_HOME="$(cygpath -w "$LOCALAPPDATA/jdk17")"`.
- **SDK** in `%LOCALAPPDATA%\Android\Sdk`: platform 36, build-tools 35 and
  36, platform-tools, the emulator and system images (copied from E:).
- **NDK 21** (`%LOCALAPPDATA%\Android\Sdk\ndk\21.1.6352462`), called through
  the 8.3 short path:
  ```sh
  NDK=$(cygpath -d "$LOCALAPPDATA/Android/Sdk/ndk/21.1.6352462"); P=$(cygpath -d "$PWD")
  cmd //c "$NDK\\ndk-build.cmd NDK_PROJECT_PATH=$P NDK_LIBS_OUT=$P\\app\\src\\main\\lib -j8"
  ./gradlew assembleDev --offline
  ```
- **Network:** the network here drops TLS connections, especially from Java.
  The Gradle cache in `~/.gradle` is copied from E: and complete, so always
  build with `--offline`. Fetch downloads with curl in a retry loop.
- **C tools** (`test_pitch`, `sim_organ`): there is no gcc. `pip install
  ziglang` into a venv, then `python -m ziglang cc -O2 -Ijni -Itools/hostjni
  …` in place of gcc.
- **Signing key:** `keystore.properties` points to
  `C:\Users\Volker\tuner\keys\debug.keystore` (copied from
  `E:\Users\Volker\tuner\keys`; SHA-256 `21:42:89:17:…:49:DC`). The releases
  and the dev app on the S25 are signed with it. Keep `keys\` out of the repo.
  Onno's `.local` app is signed with his own debug key.
- **Local files out of git:** `local.properties`, `keystore.properties` and
  `app/src/main/lib` are marked `skip-worktree`. Build output, `.claude/` and
  `obj/` are in `.git/info/exclude`.
- **Line endings:** most files are CRLF in the working tree
  (`core.autocrlf=true`). `sed -i` in Git Bash silently strips the CRs; use an
  editor, Python with `newline=''`, or `unix2dos`.
- **Icon:** `python tools/make_icon.py` redraws all launcher icon sizes.

## Phone, emulators, tests

- **Galaxy S25 over Wi-Fi:** `adb connect 192.168.178.104:5555`, using the
  SDK's `platform-tools\adb.exe` (not on PATH). After a phone restart, enable
  it again over USB with `adb tcpip 5555`. Never run tests or uninstall on the
  phone: the dev app there holds the user's presets.
- **Emulators** (hardware acceleration works): `tt_fairphone4` (API 33,
  420 dpi) and `tt_galaxy_s25` (API 35, 450 dpi), both with cutout and
  3-button navigation.
- **UI tests:** README "UI layout tests". Always set
  `ANDROID_SERIAL=emulator-…`, or Gradle uses every device, including the
  phone. The suite runs in its own `uitest` build type. Known issues are in
  `app/src/androidTest/assets/known_ui_issues.txt`.
- **Detector bench:** `tools/test_pitch.c` (537 synthetic cases; also replays
  WAVs).
- **Realistic simulator:** `tools/sim_organ.c` (50 scenarios: reverb, wind,
  speech, phone mic, mixtures, chromatic runs; the three "Field" ones
  calibrated on Onno's runs of 21 Oct, see its header). It mirrors the Java constants
  DET_AGREE, follow continuity (`-f new`), mixture timing and length logic;
  keep it in sync when changing them.
- **Gradle and the native library:** Gradle once packed a stale
  `libstrobe-jni.so` after ndk-build (same file size). After native changes
  delete `app/build/intermediates/{merged_jni_libs,merged_native_libs,stripped_native_libs}`
  or check the APK (`unzip -l`, grep a new symbol).
- **Measurements:** `CC=/c/Strawberry/c/bin/gcc sh tools/measure/run.sh`
  (about 35 min).
- **Debug recordings from the phone:**
  `adb pull /sdcard/Android/data/org.galexander.tunertime.dev/files/recordings`.

## Housekeeping

- 14 agent worktrees are still under `.claude/worktrees/` of the old
  checkout (`E:\Users\Volker\tuner\tunertime`), with their branches
  (`worktree-agent-…` and the feature branches). Everything in them is merged
  into `main`. Remove them with `git worktree remove <path>` and
  `git branch -d <branch>` when no longer needed.
- Play Store publishing is planned for later, with Greg's approval. It needs:
  - its own app ID and signing key
  - NDK r28
  - a privacy policy (microphone) and the data safety form
  - closed testing with about 12 testers for 14 days
