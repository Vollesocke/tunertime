# TunerTime (fork)

A fork of [TunerTime](http://galexander.org/), the Android strobe tuner by
Greg Alexander. Upstream lives at `http://galexander.org/git/tunertime.git`.
The `upstream` branch here mirrors its `master` as of this fork, and `main`
holds the changes below. The user manual is in `doc/index.html`, and the
author's development log is `NOTES`.

TunerTime is free software under the GNU GPL v2 (see `COPYING`), and so is
this fork.

## What this fork adds

- **Auto note detection** (`jni/pitch.c`). A separate detector runs next to
  the strobes: 2:1 decimation, an 8192-point Hann FFT every 2048 samples, and
  a harmonic-sum search over the spectral peaks (candidates: each peak / 1..6
  and the spacing of neighbouring peaks, so a missing or weak fundamental is
  found, and stopped pipes with odd partials only do not come out an octave
  high), gated on level and on the share of peak energy that lies on the
  winner's harmonics. Down to C0 (32' C). Note mode follows after 4 agreeing
  estimates, holds on silence and keeps the cent offset set by hand. The
  "auto" button cycles off → follow → lock → follow; a long press turns it
  off. In organ mode a focused partial (`parms.focus_ratio`) makes the note
  that partial's peak / ratio. Tested in the field with a ukulele only.
- **Corrections while following** (`TunerTime.on_pitch`): moving the note by
  hand while following applies to that note only, and turns on continuity:
  later detections are taken in the octave nearest the note shown, and move
  it only within a fourth (500 cents) either way, as a stop is tuned key by
  key. So a 4' stop corrected down an octave once follows in the 8' octave.
  It ends when auto is turned off or on afresh, or after 10 s of silence.
  Mutations (Quinte, Terz) are followed by focusing their row instead.
  The correction holds while the key sounds: the note the detector heard
  is not followed again until it hears another one or nothing for 0.3 s
  (the key released); also Dis or Es picked by hand, and a key set by hand
  in mixture mode. `tools/sim_organ -f old|new` compares the v18.1 rule
  with this one.
- **Debug recordings** (`DebugRecorder.java`, `jni/record.c`; Settings →
  "Debug recordings", on in the dev build): each hand correction while
  following saves 5 s before and 2 s after as a 16-bit mono WAV plus a .txt
  with the detected and the corrected note. Get them with
  `adb pull /sdcard/Android/data/org.galexander.tunertime.dev/files/recordings`.
  The .txt also holds a timeline of the recorded 7 s (`EventLog.java`), one
  line per event, in seconds and samples from the start of the WAV: every
  detector estimate (`est`: Hz, note, cents, confidence), the detector hints
  (`hint`: focus ratio and expected note frequency), the focused row's
  strobe drift per audio buffer (`drift`), and what the app did and why
  (`ui`: note changes with their cause, follow, which button, auto
  follow/lock/off, focus taps, tabs, mixture, length mode, A, stretch,
  presets, microphone switches). The `replay:` line in the .txt runs
  `test_pitch -t secs file.wav --hints file.txt`; it applies the recorded
  focus and expected-note hints as they changed in the app, at the same
  samples, so the two estimate lists can be compared line by line. UI events
  are stamped when the app handled them; the audio input latency (a few
  buffers) is not taken off.
- **Chromatic run recordings** (`RunRecorder.java`; menu "Record chromatic
  run"): a whole stop key by key, so that every key's note is known, for
  tuning the detector on real pipes. A dialog asks for organ, stop, kind
  (open, stopped, reed, mixture, mutation), footage and notes. During the
  run auto is off and the note shown is the key played: hold each key
  about 3 s, release, then semi+ (also in mixture mode); semi- plays a key
  again ("redo"). The detector still runs and logs. The action bar shows
  the time and keys, with stop and "Mark" (noise, faulty pipe, wrong key,
  text). The input is streamed to `files/runs/run_<time>_<stop>.wav`, the
  .txt has the header (tuning, device, microphone), one `key` line per key
  (start, end, note, target Hz) and the full timeline as above. With a row
  focused, its strobe drift says how far each pipe is off. At most 20 min;
  leaving the app ends the run. `tools/field/run_score.py` scores them.
- **Recordings to GitHub** (`ShareRecordings.java`; menu): the runs and
  debug recordings not sent before are packed into zips in
  Downloads/TunerTime, each under 25 MB (GitHub's attachment limit; a
  bigger file goes on in the next zip as pieces `name.001`, `name.002`),
  and a new issue on Vollesocke/tunertime opens in the browser with a list
  of them, to attach the zips to. `tools/field/unpack.py DIR` unpacks and
  joins them.
- **Bench test for the detector** (`tools/test_pitch.c`): synthetic tones from
  A0 to C8 (harmonic, missing fundamental, piano inharmonicity, sine, noisy)
  and organ pipes (Gedackt, Quintadena, Principal, reeds, 16'/32' through a
  phone mic, mixtures, focused partials). `test_pitch [-t secs] file.wav
  [focus_ratio [expect_hz]] [--hints file.txt]` replays a recording and
  prints the estimates over time, in seconds and samples from the start of
  the file (`-t`: align with the app; `--hints`: apply its recorded dynamic
  pitch hints). It is plain C, so build it with any compiler, together with
  `jni/pitch.c`.
- **Realistic organ bench** (`tools/sim_organ.c`): simulated pipes (speech
  transients, chiff, reed onset, wind, tremulant, celeste, mixtures, 32'
  to 1') in a reverberant church through a phone (high-pass mic, AGC,
  clipping), with blower hum, speech and footsteps; runs them through
  `jni/pitch.c` and `jni/strobe.c` the way the app does and reports
  detection latency and errors, length-mode stability and the mixture
  measurement's error. `-w dir` writes the scenarios as WAV files for
  `test_pitch`; `-r off` measures mixtures on through a key's release.
  Build: `gcc -O2 -Ijni -Itools/hostjni -o sim_organ
  tools/sim_organ.c jni/pitch.c jni/strobe.c -lm`.
- **Selectable harmonic rows**: Settings → "Harmonics shown", any of 1..20,
  up to 8 strobe rows.
- **Presets in tenths of a cent** (`preset.UNITS_PER_CENT`, 48000 = A4), with
  ±1c / ±0.1c buttons and a ± key in the note editor. Existing whole-cent
  presets and settings are migrated.
- **Temperament workbench for organ builders**: the Preset tab is now the
  Temperierung screen, with a note/deviation table and the temperament menu
  along the bottom. The first four special entries stay fixed; the remaining
  choices are alphabetized in the active language. A selected temperament
  always affects note mode. For Pythagorean and meantone tunings, tap the
  temperament name to choose a temporary wolf fifth; that choice resets when
  another temperament is selected. Tapping a note in the table makes it the
  current note, in the octave of the one before. The A tab shows the note
  set next to the A frequency, as it hides the note buttons.
- **Historical temperaments** (`temperaments.java`): equal, Pythagorean, ¼-
  and ⅙-comma meantone, Werckmeister III, Kirnberger III, Vallotti, Young II
  and Kellner, and all charts of
  [Orgelbau Rohlf](http://www.orgelbau-rohlf.de/themen/stimmungen/stimmungen.htm):
  Young-Sorge, near-equal, Gräf-Sorge, modified Vallotti,
  Wegscheider/Reinfeld, Johannes Lang, Thomas Rothert, Frischknecht II,
  Neidhardt (modified, village 1732, small town 1732, large town 1724),
  modified Werckmeister 1691 (= Rohlf's Niederrodenbach) and Werckmeister
  III/IV/V in Rohlf's numbering, which is one off the usual names (his 1691 is
  the usual III, his III the usual IV). Tables are normalized to A = 0 so the
  A-frequency setting still means the A you hear.
- **Mixture mode follows detected notes** like tone mode, displays the detected
  pitch and has key and octave buttons. Where the workbench picked two notes
  on a pitch class (Dis and Es), the key buttons go through both (D Dis Es
  E) and follow tells them apart, as in note mode. A key the follow moves
  to is measured again from 0.5 s after the onset, as a trailing average (0.65 s from 500 Hz
  up, 1 s from 250 Hz, else 2 s / 3 s) that ends once it is steady.
  Detections more than 20 cents from the mixture's measured pitch are
  rejected. Key detection ignores the focused rank. Settings → "Mixture:
  follow the reference rank" instead looks for that rank and moves the key by
  a whole tone at most, so another rank sounding alone does not move it.
  A key released before the measurement is done (its partial's amplitude
  below 30% of its mean for 0.5 s) ends it with what came before, if that
  is half the window, else discards it and says so. The status warns when
  the pitch heard is more than a semitone from every row, where the strobe
  drift wraps.
- **Organ display refinements**: German organ note labels use Cs/Ds/Fs/Gs;
  length mode presents the cut/add distance in millimetres with the
  stopped/open reference.
- **Preset management** (`PresetIO.java`): long-press for
  edit/rename/duplicate/move/export/delete, sort, import and export all.
  Plain-text, hand-editable format, described in the header of every
  exported file.
- **Neutral dark UI** (`res/values/colors.xml`, theme `Tuner`), with the
  native drawing in `jni/fft.c` and `jni/strobe.c` using the same palette.
  Both the strobe (new default colour scheme) and the FFT spectrogram use
  Crameri's batlow colormap; slate, red/green and the others are still in
  Settings.
- **Android 15+ edge-to-edge fixes**: no screen draws under the status or
  navigation bar, and the strobe tab bar no longer covers the last row.
- **`dev` and `local` build types**: `org.galexander.tunertime.dev`
  (`assembleDev`, the releases) and `org.galexander.tunertime.local`
  (`assembleLocal`, Onno's own builds), labelled "TunerTime Organ", which
  install next to the Play Store app with their own settings and presets.
  Each updates only an app signed with the same key, so keep building with
  the keystore the installed app came from.

Open ideas from users, in `NOTES`: octave advance when stepping past the end
of a preset, and CSV import/export.

## Building

You need the Android SDK (platform 36, build-tools 35), the NDK (the
releases use r21, 21.1.6352462; r28 works too) and JDK 17.

1. Create `local.properties` with `sdk.dir=/path/to/Android/Sdk`.
2. Point `keystore.properties` at a keystore. The Android debug keystore
   works (`~/.android/debug.keystore`, passwords `android`, alias
   `androiddebugkey`).
3. Build the native library, then the app:

   ```sh
   ndk-build NDK_PROJECT_PATH=. NDK_LIBS_OUT=app/src/main/lib -j8
   ./gradlew assembleDev       # or assembleRelease
   ```

On Windows:

- Git checks the upstream symlinks out as text files. Replace
  `app/src/main/lib` with a real directory, and edit `local.properties` and
  `keystore.properties` in place. `git update-index --skip-worktree` keeps
  those local edits out of commits.
- The NDK does not accept paths with spaces, so call it through the 8.3 short
  path (`cygpath -d`).

Traps when editing:

- Files mix LF and CRLF line endings, and some contain Latin-1 bytes. Keep
  each file's existing line endings and encoding.
- Write non-ASCII characters in XML as entities (e.g. `&#177;`), also
  in the German `res/values-de/strings.xml` (`&#228;` for a-umlaut).
- User-visible text belongs in `res/values/strings.xml` with its German
  in `res/values-de/strings.xml` (same keys, same order); none in
  layouts or Java.  The native code's English row labels are translated
  in `Strobe.shown_labels()`.
- Do not `git add app/src/main` wholesale, because it would commit the built
  `.so` files.

## UI layout tests

`app/src/androidTest` holds instrumented tests (`UiLayoutTest`) that open
every screen, tab, menu and dialog in English and German, at font scale 1.0
and 1.3, in portrait and landscape, in normal and organ mode, and check each view hierarchy
generically (`UiCheck`): every button, text and the strobe surface must be
inside the window, not cut by its parent, not under the status/navigation bar
or the camera cutout, not overlapping another one, with text that fits;
clickables need 48dp and a label. The Accessibility Test Framework runs
on top (its warnings are only reported). They need Android 11 or later (13
for the language switch) and run on their own `uitest` build
(`org.galexander.tunertime.uitest`, next to the Play Store and dev apps), on an
emulator or a phone:

```sh
./gradlew connectedUitestAndroidTest
# one test, one language and font scale, a name for the output folder:
./gradlew connectedUitestAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=org.galexander.tunertime.UiLayoutTest#main_organ \
  -Pandroid.testInstrumentationRunnerArguments.languages=de \
  -Pandroid.testInstrumentationRunnerArguments.fontScales=1.3 \
  -Pandroid.testInstrumentationRunnerArguments.device=fairphone4
adb pull /data/local/tmp/tt-ui-tests     # screenshots and report.txt
```

With several devices attached, pick one with `ANDROID_SERIAL` (otherwise
Gradle runs on all of them). The tests rotate the phone and change its font
scale while they run (restored at the end). Gradle uninstalls the uitest app
afterwards; to keep it, install both APKs (`assembleUitest
assembleUitestAndroidTest`) and run `adb shell am instrument -w -e device NAME
org.galexander.tunertime.uitest.test/androidx.test.runner.AndroidJUnitRunner`.

A test fails only on problems not listed in
`app/src/androidTest/assets/known_ui_issues.txt`, after going through all its
states; the failure message and `report.txt` list them as
`screen|language|orientation|fontscale|CHECK|element`. Remove a line there once the
problem is fixed (`-e strict true` fails on the known ones too).

Emulators matching the target phones (1080x2340; the Fairphone 4 at 420dpi
with Android 13, the Galaxy S25 at 450dpi with Android 15), with a
hole-punch cutout:

```sh
sdkmanager emulator "system-images;android-33;default;x86_64" \
  "system-images;android-35;default;x86_64"
avdmanager create avd -n tt_fairphone4 -d pixel_4 \
  -k "system-images;android-33;default;x86_64"
# in ~/.android/avd/tt_fairphone4.avd/config.ini: hw.lcd.height=2340,
# hw.lcd.density=420 (450 for the S25 one), hw.ramSize=2048M
emulator -avd tt_fairphone4 -no-window &
adb shell cmd overlay enable com.android.internal.display.cutout.emulation.hole
```
