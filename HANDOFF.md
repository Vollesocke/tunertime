# Handoff: TunerTime Organ

Status as of 20 Oct 2026, late: release
[**v18.4-organ**](https://github.com/Vollesocke/tunertime/releases/tag/v18.4-organ)
is on `main` (pre-release v18.4-organ-pre2 before it). UI tests clean on
tt_fairphone4 (full suite on pre2, the menu tests again on the release);
tt_galaxy_s25 still not run. It adds to v18.3: chromatic run recordings, meantone with more
than 12 notes, the mixture measurement ending on key release, a warning when
the mixture is heard more than a semitone off, and tapping a workbench note.

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
2. A tab: shows the note set next to the Hz.
3. Mixture mode and Dis/Es: key+/- skipped Es (D Dis E); follow rejected Es
   (41 c from Dis), and an Es measured at Dis carried +41 c as the organ's
   pitch to every key after it, all rejected: "detection crashes". Now
   both notes, as in note mode (parms.note_alt shared), named apart.
   No real crash found in the code; if it still happens, a debug recording.
- Tests: `FollowTest` (androidTest, 5 tests, fail on v18.4); UiLayoutTest
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

## Next steps

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
| 6 | Mixture step by step | Ton± focuses the reference row last measured again. |

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
- **Realistic simulator:** `tools/sim_organ.c` (47 scenarios: reverb, wind,
  speech, phone mic, mixtures, chromatic runs). It mirrors the Java constants
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
