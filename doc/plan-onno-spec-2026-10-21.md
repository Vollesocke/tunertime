# Plan: Onno's specification of 21 Oct 2026 (late evening)

Onno wrote this spec on the evening of 21 Oct and sent it on 22 Oct. His
design decisions win (HANDOFF, top); where it leaves something open, the
default below is used and the point is asked back (marked **Ask Onno**).
It comes in two packages: **A**, small UI changes (v18.9 if ready in
time, else v19.0), and **B**, entering temperaments by measuring them and
matching them (v19.0).

## The spec (Onno, original)

> UI:
> - Kammerton Reset Button teilen: Auto und Reset. Auto passt den Kammerton so an dass es für den aktuell erkannten Ton passt (2 sec Mittel reicht, wird eh händisch fein eingestellt als Kompromiss aus vielen Messungen).
> - Im Temperierung Mode ist aktuell das c dauerhaft fokussiert, und wenn man von dort zurück in den Ton Mode geht landet man immer wieder beim c, egal was vorher eingestellt war. Fokussierung hier komplett entfernen und als reine Ansichtsseite behandeln. Eingestellter Ton soll immer erhalten bleiben wenn man durch die Tabs wechselt, auch von Ton zu Mixtur und so weiter. Mixtur und Ton sollen aber ihren eigenen Fokus auf die strobe-Zeile erhalten und diese auch unabhängig voneinander behalten. Kammerton und Ton teilen sich hier die Einstellung.
> - wozu hat der auto/folgen/fixiert button 3 Modi? Was ist der unterschied zwischen aus (anzeige auto) und fixiert? Wenn nicht erheblich, nur zwischen "fixiert" und "folgen" toggeln, long press nicht notwendig.
>
> temperierung Lambert (1774) hinzufügen: http://www.instrument-tuner.com/temperaments_de.html
>
> Zusätzliches feature:
> Temperaturerkennung? Temperatureingabe über Ton Mode: neue Temperierung anlegen, Töne Messen, manuell cent offset einstellen, direkt in die neue Temperierung speichern, anschließend ---> Stimmungen matchen, in der Tabelle ist platz für mindestens 3 Spalten mit centwerten, die zu matchende plus die zwei nächsten bekannten, differenzen >2 cent gelb hervorheben, >4 cent rot. Es werden alle mit der app ausgelieferten Stimmungen so wie alle individuell angelegten einbezogen.
>
> Töne für Temperierung messen: Neue Seite mit Kammertonmenü öffnet sich, Strobe ist nur mit Oktaven gefüllt, von 8' bis 1/4'. Die Leiste mit den Reitern Ton/Kammerton/... entfällt Titel "Kammerton einstellen" statt "TunerTime Organ: Strobe", Tonerkennung aus, Auto/Signal/Länge Buttons zu einem Button "Set" zusammengefasst. Set setzt den Kammerton für die Einzeltonmessung, drücken führt zur nächsten neuen Seite mit Strobe Anzeige wie das Ton Menü, wieder ohne die Reiterleiste, Titel "Ton Eingeben: <preset>" statt "TunerTime: Strobe", nur mit Oktaven gefüllt, von 8' bis 1/4', keine Tonerkennung, die drei Buttons für Tonerkennung, Signal und Länge werden zu einem Ton Speichern Button. Die Buttons Okt+/- werden zu cent +/ -, cent +/- wird zu 0,1 cent +/- Tonname wird entsprechend zwischen die Ton +/- Buttons zentriert, der Centwert entsprechend zwischen die vier cent-buttons. Diese Seite bleibt immer in der 8' Oktave, also C-H. Zu erreichen ist diese Seite nur aus dem Temperierung Menü ---> bearbeiten--->unten eine dritte Schaltfläche hinzufügen "Töne Messen". hier kann auch noch eine vierte Schaltfläche: "Matching" hinzugefügt werden, die dann die ausgewählte temperiereung so wie die zwei nächsten matches anzeigt, s.o. Sind alle 12 Töne gespeichert kommt man zurück zur preset bearbeiten Seite.

## Package A: UI (small)

**A1. Kammerton tab: "Reset" becomes two buttons, "Auto" and "Reset".**
`afreq_buttons.xml` (one `reset_afreq` button), `TunerTime.reset_afreq`.
- Reset: as now (A = 440.0).
- Auto: from the detector estimates of the last 2 s, the median of
  those that agree on the shown note (the follow logic's semitone), set A
  so that this note's target (A, temperament offset, stretch) equals what
  is heard. Round to 0.1 Hz (A_freq is in 0.1 Hz). It works on any note,
  not just A. With fewer than ~1 s of agreeing estimates it does
  nothing (no popup; HANDOFF "UI rule").
- The detector must run in the Kammerton tab (it does: `auto_applies()`
  excludes only Preset, FFT and runs). The estimate history needs a small
  ring of (time, Hz) in `on_pitch`.
- Log it (`EventLog.ui("A auto: ...")`), like every other action.

**A2. Temperierung tab: a view only; the note survives every tab change.**
`TunerTime.set_mode` (the `was_preset` block "carry the preset note over
to note mode", ~line 350), `parms.preset_note/preset_oct`, the workbench
(`preset_workbench.xml`, `preset_buttons.xml`).
- Today the workbench's own note (`preset_note`, starting at C) drives the
  strobe, and leaving the tab copies it into note mode: back at C.
- New: the Temperierung tab drives nothing and focuses nothing. Leaving
  it, the note is what it was before. Onno (22 Oct): the tab has no
  buttons any more (gone since the list became a display only), so
  nothing to remove there; check that no leftover `preset_buttons` code
  still steps or focuses a note.
- The note set (`strobe_cents`, `note_alt`) stays the same across Ton,
  Kammerton, Mixtur, Temperierung and back. Check `mixture_set` (copies
  `strobe_cents` into `mixture_cents` on entry) and the way back.
- Focused row, one per mode: `parms.focus_ratio` is shared today. Keep
  two, `note_focus` (Ton and Kammerton share it) and `mixture_focus`,
  each persisted in prefs; switching tabs swaps the active one into
  `focus_ratio`. Mind length mode's `length_focus()` and the mixture
  measurement's "nothing focused: 8'" rule (`mixture_measure`), and
  `mixture_ref`.
- FollowTest (androidTest) covers "focus kept"; extend it: the focus of
  Ton and Mixtur are independent, and the note survives a round trip over
  all tabs.

**A3. Auto button: two states, "folgen" and "fixiert".**
`TunerTime.do_auto`, `auto_off`, the long press on the auto button,
`parms.auto_note/auto_locked`, `R.string.auto*`.
- Today: off ("Auto"), follow, locked. Off and locked both leave the note
  alone. The only difference is that off clears continuity (`near`, the
  overruled note, the stable note) and locked keeps it, so unlocking goes
  on within a fourth of the note shown. That difference is not
  substantial: a correction by hand sets continuity again, and 10 s of
  silence clears it.
- New: tap toggles follow ↔ locked; no long press. Locked keeps
  continuity as now. Migrate the prefs: an old "off" starts as locked.
  Run mode (auto off during a run, `auto_before` in the header) restores
  the state from before the run.
- The answer to his question ("what is the difference?") goes into the
  release notes in one line.

**A4. Temperament "Lambert (1774)".**
`temperaments.java` (names, about, the cents table C..B with C = 0).
Take the values from http://www.instrument-tuner.com/temperaments_de.html,
cross-check them with a second source, and note both in the comment. Lambert
1774 is commonly given as seven fifths tempered by 1/7 Pythagorean comma,
the rest pure. If the sources differ, use the site Onno named and say so.

## Package B: temperaments by measurement, and matching (large)

Entry: Temperierung → a preset → Bearbeiten (`PresetEditActivity`,
`preset_edit.xml`, today two buttons: "delete preset", "add note"). Two
new buttons at the bottom: **"Töne messen"** and **"Matching"**.

**B1. Page 1, "Kammerton einstellen".** Opens from "Töne messen".
- The strobe with octave rows only: 8', 4', 2', 1', 1/2', 1/4' (organ mode
  rows; above the sample rate's limit they stay blank as now).
- No tab bar; title "Kammerton einstellen".
- The Kammerton controls (A ±1, ±0.1, Reset). No Auto here (Onno, 22
  Oct): the A is set by ear on this page.
- Detection off. Auto, Signal (tone generator) and Länge are replaced by
  one button, "Set": it sets the A for this measurement and opens page 2.
- Technically: a separate Activity is cleaner than a third state of
  `TunerTime`, but the strobe (`Strobe` thread, `jni/`) is bound to
  `TunerTime`'s SurfaceView. First choice: a mode of `TunerTime` that hides
  the tab bar and swaps the button panels (as run mode already hides
  menus), entered by an Intent extra and left with Back. The rows of this
  mode are temporary and must not overwrite the user's footage setting.

**B2. Page 2, "Ton eingeben: <preset>".**
- Strobe as in Ton mode, octave rows only (8' to 1/4'), no detection.
- Note buttons: Ton± as now; Okt± become **cent ±1**; cent± become
  **cent ±0.1**. The note name centred between Ton+ and Ton-, the cent
  value centred between the four cent buttons.
- Always in the 8' octave, C to H (B); Ton± wraps within it.
- Detection, Signal and Länge become one button, **"Ton speichern"**: it
  stores the note's cent offset (0.1 c; `preset.UNITS_PER_CENT` = 10) into
  the preset being edited, then steps to the next note not yet stored.
  When all 12 are stored, back to the preset edit page.
- Offsets are relative to the A set on page 1 (so A = 0 holds as for the
  built-in tables, `temperaments.make_notes`). Only the 12 semitones are
  entered (Onno, 22 Oct), also in a preset with Dis/Es or Gis/As.
- A preset that already has notes: page 2 starts from their values (it
  edits them). Back leaves what was stored so far.

**B3. Matching.** "Matching" on the edit page, and offered after the 12th
note of B2.
- A table, one row per note C..H, at least 3 columns of cents: the preset
  being matched, then the two nearest known temperaments. Known means all
  built-in ones (`temperaments.names`) and all of the user's presets
  except the one matched.
- Nearest: the smallest RMS of the 12 differences, both sides at A = 0,
  with no free common offset (Onno, 22 Oct): page 1 sets the A to 0 c
  first. The tuner does that by looking at all the A pipes in every
  octave and choosing the right mean, which can't be automated, as it
  depends on which pipes are used where.
- Each candidate column marks its difference to the matched one: > 2 c
  yellow, > 4 c red (cell background; readable in light and dark theme).
  Header: the name and the RMS.
- The phone is narrow: 4 columns (note + 3) must fit at 360 dp.

## Order, and who does it

1. Agents: at most 2 at a time (Volker, 22 Oct). The noise work for v18.9
   holds both slots now; this starts when one is free.
2. Package A as one agent (worktree), A1-A4 as separate commits. Merged
   into v18.9 if it is done with the noise work, else v19.0.
3. Package B as one agent after A has merged (it builds on A2's focus and
   mode handling), in v19.0. UI tests: the layout tests on tt_fairphone4 for
   both new pages (`UiLayoutTest`), and a test that walks B1 → B2 → 12
   notes → back.
4. All text in German and English (`values-de`, `values`), no popups
   except errors (HANDOFF "UI rule", Onno 22 Oct: errors and recording
   messages are fine; the post-correction popup is not).

## Onno's answers (22 Oct, Signal)

1. The Temperierung tab has no buttons any more; nothing to remove.
2. No Auto on the "Kammerton einstellen" page.
3. Only the 12 semitones are entered.
4. Matching strictly at A = 0: the A is set to 0 c on the first page,
   by ear over the A pipes of all octaves.
