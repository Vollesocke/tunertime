# TunerTime Organ – Manual

_This manual is generated from the in-app help (`app/src/main/assets/help/en.html`, with `tools/help_to_md.py`). Please edit it there, not here._ [Deutsche Fassung: Handbuch.md](Handbuch.md)

_Version 19.2, October 2026_

1. [What the app is](#1-what-the-app-is)
2. [Setting up](#2-setting-up)
3. [The screen](#3-the-screen)
4. [Tuning a note](#4-tuning-a-note)
5. [Follow and locked](#5-follow-and-locked)
6. [Setting the pitch of A](#6-setting-the-pitch-of-a)
7. [Choosing a temperament](#7-choosing-a-temperament)
8. [Your own temperaments (presets)](#8-your-own-temperaments-presets)
9. [Tuning a mixture](#9-tuning-a-mixture)
10. [Pipe length (length)](#10-pipe-length-length)
11. [Reference tone (tone)](#11-reference-tone-tone)
12. [Tips from practice](#12-tips-from-practice)
13. [Recording a chromatic run](#13-recording-a-chromatic-run)
14. [Debug recordings and crash reports](#14-debug-recordings-and-crash-reports)
15. [Recordings to GitHub](#15-recordings-to-github)
16. [The settings](#16-the-settings)
17. [Without organ mode: stretch and FFT](#17-without-organ-mode-stretch-and-fft)
18. [Measuring and matching temperaments](#18-measuring-and-matching-temperaments)
19. [Reporting problems, links](#19-reporting-problems-links)

## 1. What the app is

TunerTime Organ is a strobe tuner for organ builders, developed from Greg Alexander's TunerTime. It is a **tuning aid**: the strobe shows what you hear. What is tuned and what is not, you decide by ear.

- The app recognises the note played and makes the strobe follow it (**follow**). So with as little input as possible you see exactly what you hear, key by key.
- **The hand always wins.** A note you set by hand stands, even if the detection hears something else.
- There is a strobe row for each footage, so you see octaves and mutations (Quinte, Terz) pure against the fundamental.
- Mixtures are tuned rank by rank against a reference rank ([Mixture](#9-tuning-a-mixture)).

The screen stays on while the app is open. It works in portrait and in landscape.

## 2. Setting up

1. At the first start Android asks for the **microphone**: allow it. Nothing works without it. You can allow it later in the system settings under Apps → TunerTime Organ → Permissions.
2. Menu (⋮ at the top right) → **Settings** → **Organ mode**: on after installing. Only organ mode has the footage rows, mixture mode and length mode.
3. There, turn on **German note names** if you want to read Cis, B, H and c¹ instead of C#4 (a¹ = A4 = 440 Hz). The app's language is under **Language / Sprache**.
4. Choose the rows under **Footages shown**, up to 8. The default is 16′, 8′, 4′, 2 2/3′ and 2′. 8′ is the note itself.
5. Back on the main screen, set the A in the **A freq** tab ([Pitch of A](#6-setting-the-pitch-of-a)) and choose the temperament in the **temperament** tab ([Temperament](#7-choosing-a-temperament)).
6. Tap **locked** at the bottom right: the button then says **follow**, and the strobe follows the note played. After installing it is on locked.

## 3. The screen

### Title bar

"TunerTime Organ: strobe" ("TunerTime Organ: mixture" in mixture mode). With a temperament chosen, its name and its deviation for the note shown are below, e.g. "Meantone 1/4 comma +10.3c". The ⋮ menu has **Settings**, **Help** and **About** (with the version number). **Record chromatic run** and **Recordings to GitHub** are in the menu only when **Debug recordings** is on under Settings → Debug ([run](#13-recording-a-chromatic-run)).

### The strobe rows

Each row belongs to a footage, the lowest at the top. On the left are the footage and the row's frequency in Hz, e.g. "8' 130.8". The row shows how that footage's partial stands to the target:

- **In tune**, the stripes run level.
- **Sharp**, they rise to the right; **flat**, they fall. The steeper, the further off; far off, the picture turns vertical or restless.
- **Wavy** stripes: two tones beat on this partial.

The picture is written from left to right and then starts again on the left. A row too high for the phone's sample rate stays empty and says "too high".

### How steep the stripes run

Each row draws one column per cycle of its own frequency. A stripe rises or falls by the whole row height when the partial gains or loses one cycle on the row. High rows, so that the picture does not run too fast, draw only every 2nd, 3rd … cycle; where that starts depends on the screen's width: in portrait at about 1000 Hz (around C6), in landscape higher.

- Below that, the same mistuning in cents looks equally steep in every range. The ear hears it differently: against a pure pipe, 1 cent beats about once every 26 s at C2, every 7 s at C4, every 1.7 s at C6. A steep stripe in the bass is a slow beat, a shallow one in the treble already a clear one.
- Above it, the same mistuning in cents makes the stripes steeper in steps (about twice as steep at twice the frequency), roughly as the beat gets faster.
- The rows of one pipe: if its fundamental is a little off, all its partials are off by the same number of cents, but in hertz the second partial twice as far, the third three times. Below about 1000 Hz all its rows run equally slanted, above it the high rows more. When all rows of a pipe stand still, its partials fit the target.

### Tapping a row: focus

Tap a row to **focus** it (a frame in the focus color, set under Settings → Strobe → **Focus color**; it also colors the row's label and readout); tap it again to release it. The focused partial helps the detection: it then looks for that partial and reports the key it belongs to. So you tune a 4′ stop with the 4′ row focused, a Quinte 2 2/3′ with the 2 2/3′ row focused, and the note shown stays the key played. The **note** tab (with **A freq**) and the **mixture** tab each keep their own focus.

A long press on the strobe opens the choice of footages shown.

### Below the strobe

- The **tabs** **note**, **A freq**, **mixture** and **temperament**. The one chosen is underlined. The note set stays the same across all tabs.
- The **tab's buttons** and readouts (see the sections below).
- In the corner at the top right: **A=440.0** (the pitch of A) and **organ**.
- Below them three buttons: **follow** / **locked** ([Follow and locked](#5-follow-and-locked)), **tone** ([Reference tone](#11-reference-tone-tone)) and **length** ([Pipe length](#10-pipe-length-length)).

## 4. Tuning a note

In the **note** tab:

- The readout in the middle shows the note (the key played, at 8′ pitch) and below it an additional offset in whole cents, e.g. "+3c", set by hand. The temperament's deviation is in the title bar; the target is note + temperament + offset.
- **semi+** / **semi-**: a semitone on. Long press: a fifth on.
- **oct+** / **oct-**: an octave on.
- **cent+** / **cent-**: change the offset by 1 cent, e.g. for a celeste (Unda maris, Vox coelestis). The offset is kept while following.

To tune a stop:

1. Draw the stop, put the phone near the pipes.
2. For a 4′, a 2′ or a mutation, tap the row of that footage (focus); for an 8′ the 8′ row.
3. Turn on **follow** and hold the first key. The strobe moves to the key after about half a second.
4. Tune the pipe until the stripes of its row run level.
5. Next key. If the note shown is not the key pressed, correct it by hand ([Follow and locked](#5-follow-and-locked)).

Octaves and mutations are pure: the rows 4′, 2 2/3′, 2′, 1 3/5′ are exactly 2, 3, 4 and 5 times the 8′ frequency.

## 5. Follow and locked

Each tap on the button at the bottom right switches between two states:

- **follow** (blue): when the detection hears the same note four times in a row (about 0.4 s), the note shown moves there. In silence the last note stays.
- **locked** (amber): the note stays where it is; it only changes by hand.

During a chromatic run the button is grey and does nothing. While the [reference tone](#11-reference-tone-tone) plays, the app does not follow.

### Correcting by hand

If the app follows to a wrong note (mostly an octave off), set the right one with **semi+**, **semi-**, **oct+** or **oct-**. In mixture mode its key and octave buttons count the same. Then:

1. **The correction holds while the key sounds.** The note the detection had heard is not taken again until it hears another one or the key is released (the input stays down at the background noise for 0.3 s).
2. **After that it goes on near the note shown**, as a stop is tuned key by key: each note detected is taken in the octave nearest the note shown, and the note shown follows only up to a fourth (500 cents) up or down. A 4′ stop corrected down an octave once is then shown at 8′ pitch.
3. **It ends** after 10 s with no note detected (a new stop), or when a chromatic run starts. Locked and following again keep it.

Mutations (Quinte, Terz) are not followed through this octave folding: tap their row, then the detection reports the key.

If the detection keeps getting it wrong: switch to **locked** and step the keys by hand with **semi+**.

## 6. Setting the pitch of A

In the **A freq** tab:

- The readout shows the A and the note set for the strobe, e.g. "440.0 Hz · A4". An offset from the note tab shows only when it is not 0, and then highlighted in amber, e.g. "440.0 Hz · A4 +3c". Tap it for a keypad (220 to 880 Hz).
- **+1** / **-1** and **+0.1** / **-0.1**: adjust the A in steps of 1 Hz and 0.1 Hz.
- **auto**: sets the A so that the note shown (in the temperament chosen) is exactly the note sounding. The app takes the median of the detection over the last 2 s, as far as it agrees with that note, and rounds to 0.1 Hz. With less than about 1 s of it, nothing happens. It works with any note, not just A. A cent offset set by hand is left out.
- **reset**: A = 440.0 Hz.

To find the pitch of an organ: hold a pipe of the reference stop for about 2 s, tap **auto**; then adjust with the buttons as a compromise over several pipes (e.g. the A of every octave). There is no temperature correction: the pipes move by about 3 cents per degree, which you allow for through the A or the offset.

The A freq tab shares the note and the focus with the note tab, and the detection follows here too, when enabled.

## 7. Choosing a temperament

Back on the temperament tab returns to the previous tab; the app does not close. The table is a view only: it shows the temperament chosen (note and deviation from equal in cents).

- **Select** opens the list "Select preset". Tap a preset to choose it. A long press on the temperament tab opens this list directly, without changing tabs.
- **Edit** opens the preset chosen ([Presets](#8-your-own-temperaments-presets)).

### The built-in temperaments

After installing, the list is empty. On the "Select preset" screen, menu ⋮ → **Add historical temperament…** adds one of the built-in temperaments as a preset and chooses it:

Equal temperament · Pythagorean · Meantone 1/4 comma · Meantone 1/6 comma · Werckmeister III · Kirnberger III · Vallotti (1754) · Young II · Kellner (Bach) · Young-Sorge · Near equal temperament · Gräf-Sorge · Modified Vallotti · Wegscheider / Reinfeld · Johannes Lang (Freiburg) · Thomas Rothert (Bayreuth) · Frischknecht II · Modified Neidhardt · Neidhardt for a village (1732) · Neidhardt for a small town (1732) · Neidhardt for a large town (1724) · Modified Werckmeister 1691 (Niederrodenbach) · Werckmeister III, IV and V (Rohlf) · Lambert (1774)

The tables of Orgelbau Rohlf number Werckmeister one off the usual names (Rohlf's 1691 is the usual III, his III the usual IV). Lambert (1774): seven fifths F–C–G–D–A–E–B–F# narrowed by 1/7 Pythagorean comma, the rest pure. All tables have A = 0 cents, so that the A setting stays the A you hear.

A temperament chosen applies in the note tab (and A freq) and in mixture mode, which rounds to its notes.

### Wolf fifth, D#/Eb and G#/Ab

For Pythagorean and the meantone temperaments the name in the temperament tab is a button. It opens "Wolf fifth location or notes":

- one of the 12 places of the wolf fifth (by default between G# and Eb), or
- **Pick notes (also more than 12)…**: a list of the 21 notes Fb to B# with their cents. For a meantone organ with split keys tick, say, D# and Eb, G# and Ab. Every key needs a note; if one is missing, the list's title says so.

With two notes on a key, **semi+** goes D → D# → Eb → E, in mixture mode too, and the detection takes the nearer of the two. The notes are then spelled D#4 and Eb4 (German: dis¹, es¹). The choice is kept until another temperament is chosen.

## 8. Your own temperaments (presets)

A preset with exactly one note for each of the 12 keys of the octave is a temperament. The "Select preset" list:

- **new preset**: make an empty preset.
- Long press on a preset: **Edit**, **Rename**, **Duplicate**, **Export…**, **Delete**.
- Hold the grip ≡ on the right of a preset for about half a second, still until the row lifts and the grip turns blue (the phone vibrates briefly), then drag: move the preset to another place in the list. If you move early, the list just scrolls. A long press on the name opens the menu as before.
- Menu ⋮: **Add historical temperament…**, **Sort by name**, **Import…**, **Export all…**.

### Editing

"Edit preset" shows the name and the notes, with the cent values in a column: the name without octave on the left, the cents on the right (two notes on one key show as D#/Eb). **add note** adds a note, starting at the lowest key still missing from C; tap a note to open "Edit preset note":

- A piano keyboard of one octave (C to B) picks the note; the chosen key is filled blue. A small dot on a key shows that the preset already has that note. There is no choice of octave: an entry applies to all octaves. If you tap another key, the note moves there and the value you typed stays.
- The number pad below (7 8 9 / 4 5 6 / 1 2 3 / ± 0 comma, and backspace) is always shown and types into the **cents** field; the phone's own keyboard never appears. For a new note the field is empty (0). Allowed are -50.0 to +49.9 cents, one decimal.
- **save** stores the note and goes straight on to the note a semitone higher (B is followed by C): if the preset has it you edit it, otherwise you add it. When all 12 notes are there and the note was new or was B, it returns to the note list. **delete note** deletes the note.

The cents are the deviation from equal temperament, to 0.1 cent. Give A 0 cents, so that the A setting stays the A. **delete preset** deletes the whole preset.

### Import and export

Presets are exchanged as a text file (by mail or cloud, say), which can also be written by hand:

```
# lines starting with # are comments
preset My temperament
    C4   +5.9
    C#4  -3.9
    ...
    A4   +0.0
end
```

Note names C D E F G A B with # or b, octaves with C4 = middle C, A4 = the A; cents signed, to 0.1 cent. The description is at the top of every exported file. If a file has an error, nothing is imported and the errors are shown.

## 9. Tuning a mixture

Mixture mode assumes no target pitch. It **measures the reference rank** (the focused row) and puts the target where it sounds: that rank's row then stands still, and the other rows show how the other ranks stand, pure, to it.

### The mixture tab

- The readout shows the key with the measured deviation from its tempered note, e.g. "C5 +01.5c", next to it the note heard, and below it the state: "tap a strobe row", "tap measure", "measuring 2': 60%" or "measured on 2'".
- **semi+** / **semi-** and **oct+** / **oct-**: the next key or octave. The measured deviation is carried along.
- **measure** (or tap the mixture tab again): measure again.
- **reset**: back to the tempered note with no deviation.
- Large at the top right of the strobe, the focused rank's deviation from its determined pitch, e.g. "2' +01.5c".

### How to

1. Check that every footage of the mixture is shown as a row (Settings → Footages shown, up to 8).
2. Tab **mixture**. Tap the row of the reference rank. With none focused, the app measures on the 4′ row (without a 4′ row on the 8′ row).
3. Hold the key with the reference rank sounding. The measurement averages 2 s (below 8′: 3 s) and stops earlier once the result is steady. Then it says "measured on …".
4. Add the next rank and tune its pipe until its row stands still. To read the deviation as a number, tap that rank's row: the large readout then shows it. (After that, **measure** measures on that rank.)
5. Next key. With **follow** on, the app moves there itself and measures again, 0.5 s after the onset.

### Notes in mixture mode

- "beating: another pipe on this partial?": the partial measured beats; another pipe probably sounds on it (a rank not muted, a celeste). The result stands, but may be the louder of the two or between them.
- "heard over a semitone off the rows: check the key": what sounds is more than a semitone from every row. Then the strobe measures wrongly. Check the key, or a rank (after a break, say) has no row.
- While following, the key and octave buttons are [corrections by hand](#5-follow-and-locked).
- If the key is released before the measurement is done, what was measured so far counts if it was at least half the time; else the measurement is dropped.
- The measured deviation holds in the mixture tab only; leaving it, it is lost, and coming back the app measures again.
- Settings → **Mixture: follow the reference rank** (off): the key is then detected from the focused rank and moves by a whole tone at most, so another rank sounding alone does not move it. Bigger steps by hand.

## 10. Pipe length (length)

The **length** button turns on a readout below the strobe of how much the sounding pipe is too long or too short, e.g. "+12 mm (λ/2)". It is meant for setting up the tuning range, not for fine tuning.

- **+**: too long (flat), cut off. **−**: too short (sharp), lengthen. **±**: in tune (within 0.2 cent).
- The button beside it chooses **open pipe** (λ/2) or **stopped pipe** (λ/4). For chimney flutes and conical pipes the figure is only approximate.
- Colour: blue when the readout is steady; amber while it still moves; grey "(held)" once the pipe has stopped. "listening…": nothing heard yet. "unclear": what sounds is no partial of the target.
- With no row focused, length focuses the 8′ row. With the 4′ row focused, the length is that of the 4′ pipe.
- It is computed with the speed of sound at 18 °C. The end correction drops out, as only the difference counts.

## 11. Reference tone (tone)

The **speaker** button (tone) plays the target (at 8′ pitch, with temperament and offset) as a sine through the speaker until you tap it again. The speaker is blue meanwhile. While it plays, the detection does not follow. The tone lets you hear what the strobe aims at; it is also a self-test: the 8′ row must stand still.

## 12. Tips from practice

- **Where to put the phone:** near the pipes you tune, not on the music desk next to the blower if you can avoid it. The app learns the hum of the blower and the mains between keys and takes it off, but not right next to it. The **Input level bar** (Settings) shows whether the input is enough: red too quiet, amber marginal, green good.
- **Quiet Gedackts and chimney flutes** (Rohrflöte) are the hardest for the detection: nearly pure sines, often beating. The focused 8′ row helps, and if need be locked and semi+ by hand.
- **Hold each key about 3 s.** The detection needs about 0.5 s, the mixture measurement 2 to 3 s.
- **When the detection is wrong:** correct once by hand; the correction holds, and the next keys follow near it ([Follow and locked](#5-follow-and-locked)). For 4′, 2′ and mutations tap the row of the footage. If that does not help: **locked** and step by hand.
- Talking, footsteps and knocking nearby disturb the detection briefly; it only takes a note after hearing it several times in a row.
- Tremulant off.
- Temperature changes the pipes' pitch (about 3 cents per degree): adjust the A or the offset by hand.

## 13. Recording a chromatic run

A run is a recording of a whole stop, key by key. As you step the keys by hand, the note of every key is known. With it the developers improve the detection on real pipes. Every run helps, above all of difficult stops and noisy places.

1. In the note or mixture tab, set the first key as the note shown. Tap the row of the stop's footage; then how far each pipe is off is recorded too.
2. Menu ⋮ → **Record chromatic run** (visible only when Settings → Debug → **Debug recordings** is on). Fill in: organ, church; stop (e.g. Rohrflöte 8′); the kind (Open flue, Stopped (Gedackt), Reed, Mixture, Mutation, Other); the footage; notes (phone position, mic, temperature…). **Start**.
3. Every key in turn: hold it about 3 s, release it, then **semi+** (in mixture mode too). **semi-** records a key again.
4. **Mark** (in the bar at the top) notes at the current key: Noise, Pipe faulty, Wrong key played, or a text.
5. Stop with **■ Stop** in the bar at the top; it also shows the time and the number of keys.

During the run the app does not follow (the note shown is the key played), but the detection runs along. The screen does not rotate, Settings, Help and About are hidden, the temperament tab is blocked. Leaving the app ends and saves the run. At most 20 minutes (about 6 MB a minute). Afterwards the button is on follow or locked as before.

**What is saved:** the whole microphone input as WAV, and a text file with what was entered in the dialog, the A, the temperament, the device and microphone, one line per key (start, end, note, target frequency), the marks, and the course of the detection and of what was done. The files are in the app's folder (Android/data/…/files/runs) and are sent with [Recordings to GitHub](#15-recordings-to-github).

## 14. Debug recordings and crash reports

**Debug recordings** (Settings → Debug, on in the test builds): each time you correct the note by hand while following, the app saves 7 s of microphone input (5 s before, 2 s after) as WAV with a text file: which note it detected and which you set, and what it heard and did meanwhile. These are exactly the moments the development needs. The newest 100 are kept.

**Crash reports**: if the app crashes, it writes a report; from Android 11 it also collects the system's reports at the next start. They go along with [Recordings to GitHub](#15-recordings-to-github).

## 15. Recordings to GitHub

This is how runs, debug recordings and crash reports get from the phone to the developers. You need a GitHub account and to be logged in to it in the phone's browser.

1. Menu ⋮ → **Recordings to GitHub** (also only with **Debug recordings** on). A list shows a line per run (date, stop, keys, duration, size), the debug recordings per day, and the crash reports. Ticked is what was not packed before; "(packed before)" can be ticked again.
2. **Pack**. The app puts zip files into **Downloads/TunerTime**, each under 25 MB (GitHub's limit; big files are split). It deletes the zips of the time before from there first.
3. The browser opens a new issue on github.com/Vollesocke/tunertime with a list of the zips.
4. Tap **"Add Files"** (the paperclip) under the text. The file picker first shows recent files, often none: ☰ → **Downloads** → **TunerTime**, hold one zip, tap the others, then **Select**. Their links appear at the top of the text.
5. Add a few words if you like (organ, what you noticed), then create the issue.

## 16. The settings

**Main**
**Organ mode**: footage rows, mixture and length, no stretch. **German note names**: Cis, B, H; C₁ C c c¹ c². **Language / Sprache**: System default, English, Deutsch.

**Mixture**
**Mixture: follow the reference rank** ([Mixture](#9-tuning-a-mixture)).

**Audio**
**Audio source**: best **Auto**. The app picks the microphone itself (the one with the best signal).

**Strobe**
**Colors** (Batlow, Slate / White, Red / Green, Black / White, Blue / Yellow, Red / Blue). **Focus color**: frame, label and readout of the focused row, 16 colors (blue by default, light blue, cyan, teal, green, lime, yellow, amber, orange, red, pink, magenta, purple, violet, white, light grey). **Input level bar** (off): along the strobe's right edge, the input level; red too quiet to be read, amber marginal, green good; the white tick is where detection starts. **Footages shown**: up to 8 out of 32′ to 1/8′, including 10 2/3′, 5 1/3′, 3 1/5′, 2 2/3′, 1 3/5′, 1 1/3′, 1 1/7′.

**Debug**
**Debug recordings** ([Debug recordings](#14-debug-recordings-and-crash-reports)). Only when this is on are **Record chromatic run** and **Recordings to GitHub** in the menu.

## 17. Without organ mode: stretch and FFT

Without organ mode, TunerTime is Greg Alexander's tuner, e.g. for pianos:

- The rows show harmonics (1, 2, 4, 6, 8; Settings → Harmonics shown). Tapping pauses the strobe (**paused**).
- Instead of mixture there is the **stretch** tab (cents per octave, for string instruments).
- Instead of length there is **FFT**: a spectrogram. **N+** / **N-** changes the block size (bigger: more precise, smaller: faster), **scale** / **Hz** the legend; zoom with two fingers, scroll with one, tap to pause. **strobe** goes back.

## 18. Measuring and matching temperaments

This takes an organ's temperament by ear and compares it with the known ones. Under temperament, add a new preset (or take an existing one) and open **Edit**. At the bottom are two buttons: **measure notes** and **matching**.

### Measure notes

1. **Set standard A.** A strobe with octave rows only, 8′ to 1/4′ on the A (110 to 3520 Hz), no tabs and no detection. Set the A with ±1 and ±0.1 as it is right over the A pipes of all octaves; Reset sets 440.0 Hz. **set** takes this A for the measurement.
2. **Enter note: <preset>.** The notes C to B of the 8′ octave, against equal temperament. semi+ and semi− change the note (within the octave), cent ±1 and ±0.1 move it until its rows stand still. **store note** saves it in the preset and goes on to the next note not yet stored. "n/12" at the top shows how many there are; a stored note turns blue. If the preset already has notes, the page starts from their values.
3. After the twelfth note it goes back to Edit, with **matching** highlighted. If the A was not at 0 cents, the whole table is shifted so that A = 0 cents.

Back goes from "Enter note" to "Set standard A" and from there to Edit; what is stored stays (not yet shifted to A = 0). The measurement does not change your main screen's settings (note, A, rows, focus, follow/locked). Only the 12 semitones are measured. For Pythagorean and the meantone temperaments **measure notes** is grey.

### Matching

Available once the preset has exactly one note per semitone. The table shows the preset's cents for C to B and beside them the nearest known temperaments (all built-in ones and your own presets), each with its mean deviation (RMS) in the header: two on a narrow phone, more on a wider one. They are compared at A = 0 cents, with no shift. Differences over 2 cents are yellow, over 4 cents red.

## 19. Reporting problems, links

Please report bugs, crashes and wishes as an issue on GitHub, with [recordings](#15-recordings-to-github) if you can:
 [github.com/Vollesocke/tunertime/issues](https://github.com/Vollesocke/tunertime/issues)

Source code of TunerTime Organ:
 [github.com/Vollesocke/tunertime](https://github.com/Vollesocke/tunertime)

The original TunerTime by Greg Alexander, with his description of the strobe and the FFT:
 [galexander.org/software/tunertime](http://galexander.org/software/tunertime/)

TunerTime and TunerTime Organ are free software under the GNU GPL, version 2.
