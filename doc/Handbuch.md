# TunerTime Organ – Handbuch

_Dieses Handbuch wird aus der Hilfe der App erzeugt (`app/src/main/assets/help/de.html`, mit `tools/help_to_md.py`). Änderungen bitte dort machen, nicht hier._ [English version: manual.md](manual.md)

_Stand: Version 19.2, Oktober 2026_

1. [Was die App ist](#1-was-die-app-ist)
2. [Einrichten](#2-einrichten)
3. [Der Bildschirm](#3-der-bildschirm)
4. [Einen Ton stimmen](#4-einen-ton-stimmen)
5. [Folgen und Fixiert](#5-folgen-und-fixiert)
6. [Den Kammerton einstellen](#6-den-kammerton-einstellen)
7. [Eine Temperierung wählen](#7-eine-temperierung-wählen)
8. [Eigene Temperierungen (Presets)](#8-eigene-temperierungen-presets)
9. [Eine Mixtur stimmen](#9-eine-mixtur-stimmen)
10. [Pfeifenlänge (Länge)](#10-pfeifenlänge-länge)
11. [Vergleichston (Signal)](#11-vergleichston-signal)
12. [Tipps für die Praxis](#12-tipps-für-die-praxis)
13. [Einen chromatischen Lauf aufnehmen](#13-einen-chromatischen-lauf-aufnehmen)
14. [Debug-Aufnahmen und Absturzberichte](#14-debug-aufnahmen-und-absturzberichte)
15. [Aufnahmen an GitHub](#15-aufnahmen-an-github)
16. [Die Einstellungen](#16-die-einstellungen)
17. [Ohne Orgelmodus: Spreizung und FFT](#17-ohne-orgelmodus-spreizung-und-fft)
18. [Temperierung messen und vergleichen](#18-temperierung-messen-und-vergleichen)
19. [Probleme melden, Links](#19-probleme-melden-links)

## 1. Was die App ist

TunerTime Organ ist ein Strobe-Stimmgerät für den Orgelbau, eine Weiterentwicklung von Greg Alexanders TunerTime. Es ist eine **Stimmhilfe**: Der Strobe zeigt, was Sie hören. Was gestimmt wird und was nicht, entscheiden Sie nach Gehör.

- Die App erkennt den gespielten Ton und lässt den Strobe ihm folgen (**Folgen**). So sehen Sie mit möglichst wenig Eingaben genau das, was Sie hören, Taste für Taste.
- **Die Hand hat immer Vorrang.** Stellen Sie einen Ton von Hand ein, gilt er, auch wenn die Erkennung etwas anderes hört.
- Für jede Fußtonlage gibt es eine Strobe-Zeile, so dass Sie Oktaven und Aliquoten (Quinte, Terz) rein gegen den Grundton sehen.
- Mixturen werden Chor für Chor gegen einen Bezugschor gestimmt ([Mixtur](#9-eine-mixtur-stimmen)).

Der Bildschirm bleibt an, solange die App offen ist. Sie läuft im Hoch- und im Querformat.

## 2. Einrichten

1. Beim ersten Start fragt Android nach dem **Mikrofon**: erlauben. Ohne Mikrofon geht nichts. Später lässt es sich in den Systemeinstellungen unter Apps → TunerTime Organ → Berechtigungen erlauben.
2. Menü (⋮ oben rechts) → **Einstellungen** → **Orgelmodus**: nach der Installation eingeschaltet. Nur der Orgelmodus hat Zeilen nach Fußtonlage, den Mixtur- und den Längenmodus.
3. Ebenda **Deutsche Tonnamen** einschalten, wenn Sie Cis, B, H und c¹ statt C#4 lesen möchten (a¹ = 440 Hz; große Oktave C, Kontra-Oktave C₁). Die Sprache der App stellen Sie unter **Language / Sprache** ein.
4. Unter **Angezeigte Fußtonlagen** die Zeilen wählen, höchstens 8. Voreingestellt sind 16′, 8′, 4′, 2 2/3′ und 2′. 8′ ist der Ton selbst.
5. Zurück im Hauptbildschirm im Reiter **Kammerton** das A einstellen ([Kammerton](#6-den-kammerton-einstellen)) und im Reiter **Temperierung** die Stimmung wählen ([Temperierung](#7-eine-temperierung-wählen)).
6. Rechts unten **Fixiert** antippen: Der Knopf zeigt dann **Folgen**, und der Strobe folgt dem gespielten Ton. Nach der Installation steht er auf Fixiert.

## 3. Der Bildschirm

### Titelzeile

„TunerTime Organ: Strobe“ (im Mixturmodus „TunerTime Organ: Mixtur“). Ist eine Temperierung gewählt, steht darunter ihr Name und ihre Abweichung für den angezeigten Ton, etwa „Mitteltönig 1/4 Komma +10.3c“. Das Menü ⋮ enthält **Einstellungen**, **Hilfe** und **Über TunerTime Organ** (mit der Versionsnummer). **Lauf aufnehmen** und **Aufnahmen an GitHub** stehen nur dann im Menü, wenn unter Einstellungen → Debug **Debug-Aufnahmen** eingeschaltet ist ([Lauf](#13-einen-chromatischen-lauf-aufnehmen)).

### Die Strobe-Zeilen

Jede Zeile gehört zu einer Fußtonlage, die tiefste oben. Links steht die Fußtonlage und die Frequenz der Zeile in Hz, etwa „8' 130.8“. Die Zeile zeigt, wie der Teilton dieser Lage zum Sollton steht:

- **Stimmt** er, laufen die Streifen waagerecht.
- Ist er **zu hoch**, steigen sie nach rechts an, ist er **zu tief**, fallen sie. Je steiler, desto weiter daneben; ganz weit daneben wird das Bild senkrecht oder unruhig.
- **Wellige** Streifen: Zwei Töne schweben auf diesem Teilton.

Das Bild wird von links nach rechts geschrieben und beginnt dann wieder links. Eine Zeile, die für die Abtastrate des Telefons zu hoch liegt, bleibt leer und zeigt „zu hoch“.

### Wie steil die Streifen laufen

Jede Zeile schreibt eine Spalte je Schwingung ihrer eigenen Frequenz. Ein Streifen steigt oder fällt um die ganze Zeilenhöhe, wenn der Teilton eine Schwingung gegenüber der Zeile gewinnt oder verliert. Hohe Zeilen schreiben, damit das Bild nicht zu schnell läuft, nur jede 2., 3. … Schwingung; ab wann, hängt von der Breite des Bildschirms ab: im Hochformat etwa ab 1000 Hz (um c³), im Querformat höher.

- Bis dahin zeigt die gleiche Verstimmung in Cent in jeder Tonlage gleich steil. Das Ohr hört sie verschieden: Gegen eine reine Pfeife schwebt 1 Cent am großen C etwa alle 26 s einmal, am c¹ alle 7 s, am c³ alle 1,7 s. Ein steiler Streifen im Bass ist also eine langsame Schwebung, ein flacher in der Höhe schon eine deutliche.
- Darüber werden die Streifen bei gleicher Verstimmung in Cent in Stufen steiler (bei doppelter Frequenz etwa doppelt so steil), ungefähr so, wie die Schwebung schneller wird.
- Die Zeilen einer Pfeife: Liegt ihr Grundton etwas daneben, liegen alle ihre Teiltöne um gleich viele Cent daneben, in Hertz aber der doppelte Teilton doppelt so weit, der dreifache dreimal so weit. Bis etwa 1000 Hz laufen alle ihre Zeilen gleich schräg, darüber die hohen Zeilen stärker. Stehen alle Zeilen einer Pfeife still, passen ihre Teiltöne zum Sollton.

### Eine Zeile antippen: Fokus

Tippen Sie eine Zeile an, wird sie **fokussiert** (Rahmen in der Fokusfarbe, einstellbar unter Einstellungen → Strobe → **Fokusfarbe**; sie färbt auch Beschriftung und Anzeige der Zeile); nochmals antippen hebt den Fokus auf. Der fokussierte Teilton hilft der Erkennung: Sie sucht dann diesen Teilton und meldet die Taste, zu der er gehört. So stimmen Sie ein 4′-Register mit fokussierter 4′-Zeile, eine Quinte 2 2/3′ mit fokussierter 2 2/3′-Zeile, und der angezeigte Ton bleibt die gespielte Taste. Der Reiter **Ton** (mit **Kammerton**) und der Reiter **Mixtur** merken sich ihren Fokus getrennt.

Langes Drücken auf den Strobe öffnet direkt die Auswahl der angezeigten Fußtonlagen.

### Unter dem Strobe

- Die **Reiter** **Ton**, **Kammerton**, **Mixtur** und **Temperierung**. Der gewählte ist unterstrichen. Der eingestellte Ton bleibt beim Wechsel zwischen allen Reitern erhalten.
- Die **Knöpfe des Reiters** mit ihren Anzeigen (siehe die folgenden Abschnitte).
- Rechts oben in der Ecke: **A=440.0** (der Kammerton) und **Orgel**.
- Rechts darunter drei Knöpfe: **Folgen** / **Fixiert** ([Folgen und Fixiert](#5-folgen-und-fixiert)), der **Lautsprecher** ([Vergleichston](#11-vergleichston-signal)) und **Länge** ([Pfeifenlänge](#10-pfeifenlänge-länge)).

## 4. Einen Ton stimmen

Im Reiter **Ton**:

- Die Anzeige in der Mitte zeigt den Ton (die gespielte Taste in der 8′-Lage) und darunter einen zusätzlichen Versatz in ganzen Cent, etwa „+3c“, den Sie von Hand einstellen. Die Abweichung der Temperierung steht in der Titelzeile; der Sollton ist Ton + Temperierung + Versatz.
- **Ton+** / **Ton-**: einen Halbton weiter. Lang drücken: eine Quinte weiter.
- **Okt+** / **Okt-**: eine Oktave weiter.
- **Cent+** / **Cent-**: den Versatz um 1 Cent ändern, etwa für eine Schwebung (Unda maris, Vox coelestis). Der Versatz bleibt beim Folgen erhalten.

So stimmen Sie ein Register:

1. Register ziehen, Telefon in die Nähe der Pfeifen legen.
2. Bei einem 4′, 2′ oder einer Aliquote die Zeile dieser Fußtonlage antippen (Fokus), bei einem 8′ die 8′-Zeile.
3. **Folgen** einschalten und die erste Taste halten. Der Strobe springt nach etwa einer halben Sekunde auf die Taste.
4. Die Pfeife stimmen, bis die Streifen ihrer Zeile waagerecht stehen.
5. Nächste Taste. Stimmt der angezeigte Ton nicht mit der gedrückten Taste überein, von Hand korrigieren ([Folgen und Fixiert](#5-folgen-und-fixiert)).

Oktaven und Aliquoten sind rein: Die Zeilen 4′, 2 2/3′, 2′, 1 3/5′ liegen genau auf dem 2-, 3-, 4- und 5-fachen der 8′-Frequenz.

## 5. Folgen und Fixiert

Der Knopf rechts unten wechselt mit jedem Antippen zwischen zwei Zuständen:

- **Folgen** (blau): Hört die Erkennung viermal hintereinander denselben Ton (etwa 0,4 s), springt der angezeigte Ton dorthin. In der Stille bleibt der letzte Ton stehen.
- **Fixiert** (gelb): Der Ton bleibt, wo er ist; er ändert sich nur von Hand.

Während eines chromatischen Laufs ist der Knopf grau und ohne Wirkung. Während der [Vergleichston](#11-vergleichston-signal) spielt, folgt die App nicht.

### Von Hand korrigieren

Zeigt die App beim Folgen einen falschen Ton (meist eine Oktave daneben), stellen Sie den richtigen mit **Ton+**, **Ton-**, **Okt+** oder **Okt-** ein. Im Mixturmodus gelten dessen Ton- und Oktavknöpfe ebenso. Dann:

1. **Die Korrektur hält, solange die Taste klingt.** Den Ton, den die Erkennung gehört hatte, übernimmt sie nicht wieder, bis sie einen anderen hört oder die Taste losgelassen wird (das Signal fällt 0,3 s lang auf das Grundgeräusch).
2. **Danach geht es nah am angezeigten Ton weiter**, wie beim Stimmen eines Registers Taste für Taste: Jeder erkannte Ton wird in die Oktave gelegt, die dem angezeigten am nächsten ist, und der angezeigte Ton folgt nur bis zu einer Quarte (500 Cent) nach oben oder unten. Ein 4′-Register, einmal eine Oktave nach unten korrigiert, wird danach in der 8′-Lage angezeigt.
3. **Ende:** nach 10 s, in denen kein Ton erkannt wird (ein neues Register), oder wenn ein chromatischer Lauf beginnt. Fixiert und wieder Folgen ändert daran nichts.

Aliquoten (Quinte, Terz) folgen nicht über diese Oktavfaltung: Tippen Sie ihre Zeile an, dann meldet die Erkennung die Taste.

Wenn die Erkennung immer wieder danebenliegt: auf **Fixiert** schalten und die Tasten mit **Ton+** von Hand weiterschalten.

## 6. Den Kammerton einstellen

Im Reiter **Kammerton**:

- Die Anzeige zeigt den Kammerton und den für die Strobe-Anzeige eingestellten Ton, etwa „440.0 Hz · a¹“. Einen Versatz aus dem Reiter Ton zeigt sie nur, wenn er nicht 0 ist, und dann gelb hervorgehoben, etwa „440.0 Hz · a¹ +3c“. Antippen öffnet ein Zahlenfeld für den Kammerton (220 bis 880 Hz).
- **+1** / **-1** und **+0.1** / **-0.1**: den Kammerton in Schritten von 1 Hz und 0,1 Hz anpassen.
- **Auto**: setzt den Kammerton so, dass der angezeigte Ton (in der gewählten Temperierung) genau auf den klingenden Ton passt. Dazu nimmt die App den Mittelwert (Median) der Erkennung aus den letzten 2 s, soweit sie auf diesen Ton passt, und rundet auf 0,1 Hz. Hat sie davon weniger als etwa 1 s gehört, geschieht nichts. Das geht mit jedem Ton, nicht nur mit dem A. Ein von Hand eingestellter Cent-Versatz zählt dabei nicht mit.
- **Reset**: A = 440,0 Hz.

So bestimmen Sie die Tonhöhe einer Orgel: eine Pfeife des Bezugsregisters etwa 2 s halten, **Auto** tippen; dann mit den Knöpfen als Kompromiss aus mehreren Pfeifen (etwa den A aller Oktaven) nachstellen. Eine Temperaturkorrektur gibt es nicht: Die Pfeifen ändern sich um etwa 3 Cent je Grad, das gleichen Sie über den Kammerton oder Versatz aus.

Der Reiter Kammerton teilt Ton und Fokus mit dem Reiter Ton, und die Erkennung folgt auch hier, wenn aktiviert.

## 7. Eine Temperierung wählen

Zurück im Reiter Temperierung führt zum vorigen Reiter, die App schließt dabei nicht. Die Tabelle ist eine reine Ansicht: Sie zeigt die gewählte Temperierung (Ton und Abweichung von gleichstufig in Cent).

- **Auswahl** öffnet die Liste „Preset wählen“. Ein Preset antippen wählt es. Langes Drücken auf den Reiter Temperierung öffnet diese Liste direkt, ohne den Reiter zu wechseln.
- **Bearbeiten** öffnet das gewählte Preset ([Presets](#8-eigene-temperierungen-presets)).

### Die eingebauten Temperierungen

Nach der Installation ist die Liste leer. Im Bildschirm „Preset wählen“ fügt Menü ⋮ → **Stimmung hinzufügen…** eine der eingebauten Temperierungen als Preset hinzu und wählt sie gleich:

Gleichstufig · Pythagoreisch · Mitteltönig 1/4 Komma · Mitteltönig 1/6 Komma · Werckmeister III · Kirnberger III · Vallotti (1754) · Young II · Kellner (Bach) · Young-Sorge · Fast gleichstufig · Gräf-Sorge · Vallotti modifiziert · Wegscheider / Reinfeld · Johannes Lang (Freiburg) · Thomas Rothert (Bayreuth) · Frischknecht II · Neidhardt modifiziert · Neidhardt für das Dorf (1732) · Neidhardt für die kleine Stadt (1732) · Neidhardt für die große Stadt (1724) · Werckmeister 1691 modifiziert (Niederrodenbach) · Werckmeister III, IV und V (Rohlf) · Lambert (1774)

Die Tabellen der Orgelbau Rohlf zählen Werckmeister um eins anders als üblich (Rohlfs 1691 ist das übliche III, sein III das übliche IV). Lambert (1774): sieben Quinten F–C–G–D–A–E–H–Fis um 1/7 pythagoreisches Komma verkleinert, die übrigen rein. Alle Tabellen haben A = 0 Cent, damit der Kammerton das A bleibt, das Sie hören.

Eine gewählte Temperierung gilt im Reiter Ton (und Kammerton) und im Mixturmodus, der auf ihre Töne rundet.

### Wolfsquinte, Dis und Es, Gis und As

Bei Pythagoreisch und den mitteltönigen Stimmungen ist der Name im Reiter Temperierung ein Knopf. Er öffnet „Lage der Wolfsquinte oder Töne“:

- eine der 12 Lagen der Wolfsquinte (voreingestellt zwischen Gis und Es), oder
- **Töne wählen (auch mehr als 12)…**: eine Liste der 21 Töne Fes bis His mit ihren Cent. Für eine mitteltönige Orgel mit Subsemitonien kreuzen Sie etwa Dis und Es, Gis und As an. Jede Taste braucht einen Ton; fehlt einer, steht das im Titel der Liste.

Mit zwei Tönen auf einer Taste geht **Ton+** D → Dis → Es → E, im Mixturmodus ebenso, und die Erkennung nimmt den näheren der beiden. Die Töne heißen dann „dis¹“ und „es¹“ (mit genau 12 Tönen schreibt die App „Ds“). Die Wahl bleibt gespeichert, bis eine andere Temperierung gewählt wird.

## 8. Eigene Temperierungen (Presets)

Ein Preset mit genau einem Ton für jede der 12 Tasten der Oktave ist eine Temperierung. Die Liste „Preset wählen“:

- **Neues Preset**: ein leeres Preset anlegen.
- Lang auf ein Preset drücken: **Bearbeiten**, **Umbenennen**, **Duplizieren**, **Exportieren…**, **Löschen**.
- Den Griff ≡ rechts an einem Preset etwa eine halbe Sekunde gedrückt still halten, bis die Zeile abhebt und der Griff blau wird (das Telefon vibriert kurz), dann ziehen: das Preset an eine andere Stelle der Liste schieben. Wer sich vorher bewegt, blättert nur in der Liste. Langes Drücken auf den Namen öffnet wie gewohnt das Menü.
- Menü ⋮: **Stimmung hinzufügen…**, **Nach Namen sortieren**, **Importieren…**, **Alle exportieren…**.

### Bearbeiten

„Preset bearbeiten“ zeigt den Namen und die Töne, die Cent-Werte untereinander in einer Spalte: links der Name ohne Oktave, rechts die Cent (zwei Töne auf einer Taste stehen als Dis/Es). **Ton hinzufügen** fügt einen Ton an, beginnend bei der tiefsten noch fehlenden Taste ab C; einen Ton antippen öffnet „Preset-Ton bearbeiten“:

- Eine Klaviertastatur mit einer Oktave (C bis H) wählt den Ton, die gewählte Taste ist blau gefüllt. Ein kleiner Punkt auf einer Taste zeigt, dass das Preset diesen Ton schon hat. Eine Oktave wird nicht gewählt: Ein Eintrag gilt für alle Oktaven. Tippen Sie eine andere Taste an, wandert der Ton dorthin und der getippte Wert bleibt.
- Das Zahlenfeld darunter (7 8 9 / 4 5 6 / 1 2 3 / ± 0 Komma und Rückschritt) ist immer da und schreibt in das Feld **Cent**; die Tastatur des Telefons erscheint nicht. Bei einem neuen Ton ist das Feld leer (0). Erlaubt sind −50,0 bis +49,9 Cent, eine Nachkommastelle.
- **Speichern** legt den Ton ab und geht gleich zum Ton einen Halbton höher weiter (nach H kommt C); den gibt es schon, dann ändern Sie ihn, sonst legen Sie ihn neu an. Sind alle 12 Töne da und der Ton war neu oder H, geht es zurück zur Liste der Töne. **Ton löschen** löscht den Ton.

Die Cent sind die Abweichung von gleichstufig, auf 0,1 Cent. Geben Sie dem A 0 Cent, damit der Kammerton das A bleibt. **Preset löschen** löscht das ganze Preset.

### Import und Export

Presets werden als Textdatei ausgetauscht (etwa per Mail oder Cloud), die sich auch von Hand schreiben lässt:

```
# Zeilen mit # sind Kommentare
preset Meine Stimmung
    C4   +5.9
    C#4  -3.9
    ...
    A4   +0.0
end
```

Tonnamen englisch (C D E F G A B mit # oder b), Oktaven wie C4 = c¹, A4 = a¹; Cent mit Vorzeichen, auf 0,1 Cent. Die Beschreibung steht oben in jeder exportierten Datei. Enthält eine Datei einen Fehler, wird nichts importiert und die Fehler werden angezeigt.

## 9. Eine Mixtur stimmen

Im Mixturmodus nimmt die App keinen Sollton an. Sie **misst den Bezugschor** (die fokussierte Zeile) und legt den Sollton dorthin, wo dieser klingt: Dessen Zeile steht dann still, und die anderen Zeilen zeigen, wie die übrigen Chöre rein zu ihm stehen.

### Der Reiter Mixtur

- Die Anzeige zeigt die Taste mit der gemessenen Abweichung von ihrem temperierten Ton, etwa „c¹ +01.5c“, daneben den gehörten Ton und darunter den Stand: „Strobe-Zeile antippen“, „Messen antippen“, „messe 2': 60 %“ oder „gemessen an 2'“.
- **Ton+** / **Ton-** und **Okt+** / **Okt-**: die nächste Taste oder Oktave. Die gemessene Abweichung wird mitgenommen.
- **Messen** (oder den Reiter Mixtur nochmals antippen): neu messen.
- **Reset**: zurück auf den temperierten Ton ohne Abweichung.
- Rechts oben im Strobe groß die Abweichung des fokussierten Chors von dessen ermittelten Tonhöhe, etwa „2' +01.5c“.

### So geht es

1. Prüfen, ob alle Fußtonlagen der Mixtur als Zeilen angezeigt werden (Einstellungen → Angezeigte Fußtonlagen, höchstens 8).
2. Reiter **Mixtur**. Die Zeile des Bezugschors antippen. Ist keine fokussiert, misst die App an der 4′-Zeile (ohne 4′-Zeile an der 8′-Zeile).
3. Die Taste mit dem Bezugschor halten. Die Messung mittelt 2 s (unter 8′: 3 s) und hört früher auf, sobald das Ergebnis ruhig ist. Dann steht „gemessen an …“.
4. Den nächsten Chor dazunehmen und seine Pfeife stimmen, bis seine Zeile stillsteht. Wer die Abweichung als Zahl lesen will, tippt die Zeile dieses Chors an: Die große Anzeige zeigt dann ihn. (Danach **Messen** misst an diesem Chor.)
5. Nächste Taste. Mit **Folgen** springt die App selbst dorthin und misst neu, 0,5 s nach dem Einsatz.

### Hinweise im Mixturmodus

- „Schwebung: zweite Pfeife auf diesem Teilton?“: Der gemessene Teilton schwebt, wohl klingt noch eine Pfeife darauf (ein Chor nicht abgestellt, eine Schwebung). Das Ergebnis gilt, kann aber die lautere der beiden sein oder dazwischen liegen.
- „Ton über einen Halbton neben den Zeilen: Taste prüfen“: Was klingt, liegt mehr als einen Halbton neben jeder Zeile. Dann misst der Strobe falsch. Taste prüfen, oder ein Chor (etwa nach einer Repetition) hat keine Zeile.
- Beim Folgen gelten die Ton- und Oktavknöpfe als [Korrektur von Hand](#5-folgen-und-fixiert).
- Wird die Taste losgelassen, bevor die Messung fertig ist, gilt, was bis dahin gemessen wurde, wenn es mindestens die halbe Zeit war; sonst gilt die Messung nicht.
- Die gemessene Abweichung gilt nur im Reiter Mixtur; beim Verlassen geht sie verloren, beim Wiederkommen misst die App neu.
- Einstellungen → **Mixtur: dem Bezugschor folgen** (aus): Dann wird die Taste am fokussierten Chor erkannt und bewegt sich höchstens um einen Ganzton, so dass ein allein klingender anderer Chor sie nicht verschiebt. Größere Schritte von Hand.

## 10. Pfeifenlänge (Länge)

Der Knopf **Länge** schaltet unter dem Strobe eine Anzeige ein, um wie viel die klingende Pfeife zu lang oder zu kurz ist, etwa „+12 mm (λ/2)“. Das ist zum Einrichten des Stimmbereichs gedacht, nicht zum Feinstimmen.

- **+**: zu lang (zu tief), abschneiden. **−**: zu kurz (zu hoch), verlängern. **±**: stimmt (innerhalb 0,2 Cent).
- Der Knopf links daneben wählt **offen** (λ/2) oder **gedackt** (λ/4). Für Rohrflöten und konische Pfeifen ist die Angabe nur ungefähr.
- Farbe: blau, wenn die Anzeige ruhig ist; gelb, solange sie sich noch bewegt; grau „(gehalten)“, wenn die Pfeife verstummt ist. „warte auf Ton…“: noch nichts gehört. „unklar“: Was klingt, ist kein Teilton des Solltons.
- Ist keine Zeile fokussiert, fokussiert Länge die 8′-Zeile. Mit fokussierter 4′-Zeile gilt die Länge für die 4′-Pfeife.
- Gerechnet wird mit der Schallgeschwindigkeit bei 18 °C. Die Mündungskorrektur fällt heraus, da nur der Unterschied zählt.

## 11. Vergleichston (Signal)

Der **Lautsprecher**-Knopf (Signal) spielt den Sollton (die 8′-Lage, mit Temperierung und Versatz) als Sinuston über den Lautsprecher, bis Sie nochmals tippen. Der Lautsprecher ist dann blau. Solange er spielt, folgt die Erkennung nicht. Mit dem Vergleichston hören Sie, wohin der Strobe zielt; zugleich ist es ein Selbsttest: Die 8′-Zeile muss dabei waagerecht stehen.

## 12. Tipps für die Praxis

- **Wohin mit dem Telefon:** nah an die Pfeifen, die Sie stimmen, nicht auf das Notenpult neben dem Gebläse, wenn es sich vermeiden lässt. Brummen von Gebläse und Netz lernt die App zwischen den Tasten und zieht es ab, aber direkt daneben gelingt das nicht. Die **Pegelanzeige** (Einstellungen) zeigt, ob das Signal reicht: rot zu leise, gelb knapp, grün gut.
- **Leise Gedackte und Rohrflöten** sind für die Erkennung am schwersten: fast reine Sinustöne, oft schwebend. Hier hilft die fokussierte 8′-Zeile, und notfalls Fixiert und Ton+ von Hand.
- **Jede Taste etwa 3 s halten.** Die Erkennung braucht rund 0,5 s, die Mixturmessung 2 bis 3 s.
- **Wenn die Erkennung falsch liegt:** einmal von Hand korrigieren; die Korrektur hält, und die nächsten Tasten folgen nah an ihr ([Folgen und Fixiert](#5-folgen-und-fixiert)). Bei 4′, 2′ und Aliquoten die Zeile der Fußtonlage antippen. Hilft das nicht: **Fixiert** und von Hand weiterschalten.
- Sprechen, Schritte und Klopfen in der Nähe stören die Erkennung kurz; sie übernimmt einen Ton erst, wenn sie ihn mehrmals hintereinander gehört hat.
- Tremulant aus.
- Die Temperatur ändert die Tonhöhe der Pfeifen (etwa 3 Cent je Grad): den Kammerton oder den Versatz von Hand anpassen.

## 13. Einen chromatischen Lauf aufnehmen

Ein Lauf ist die Aufnahme eines ganzen Registers, Taste für Taste. Da Sie die Tasten von Hand weiterschalten, ist für jede Taste bekannt, welcher Ton es ist. Damit verbessern die Entwickler die Erkennung an echten Pfeifen. Jeder Lauf hilft, besonders von schwierigen Registern und lauten Orten.

1. Im Reiter Ton oder Mixtur die erste Taste als angezeigten Ton einstellen. Die Zeile der Fußtonlage des Registers antippen, dann wird auch aufgezeichnet, wie weit jede Pfeife verstimmt ist.
2. Menü ⋮ → **Lauf aufnehmen** (nur sichtbar, wenn Einstellungen → Debug → **Debug-Aufnahmen** an ist). Ausfüllen: Orgel, Kirche; Register (z. B. Rohrflöte 8′); die Art (Offen (Labial), Gedackt, Zungenregister, Mixtur, Aliquot, Anderes); die Fußlage; Notizen (Handyposition, Mikrofon, Temperatur…). **Start**.
3. Jede Taste der Reihe nach: etwa 3 s halten, loslassen, dann **Ton+** (im Mixturmodus ebenso). **Ton-** nimmt eine Taste noch einmal auf.
4. **Markieren** (oben in der Leiste) vermerkt bei der aktuellen Taste: Störgeräusch, Pfeife defekt, Falsche Taste gespielt oder einen Text.
5. Beenden mit **■ Ende** oben in der Leiste; dort stehen auch die Zeit und die Zahl der Tasten.

Während des Laufs folgt die App nicht (der angezeigte Ton ist die gespielte Taste), die Erkennung läuft aber mit. Der Bildschirm dreht sich nicht, Einstellungen, Hilfe und Über sind ausgeblendet, Temperierung ist gesperrt. Die App zu verlassen beendet und speichert den Lauf. Höchstens 20 Minuten (etwa 6 MB je Minute). Danach steht der Knopf wieder auf Folgen oder Fixiert wie vorher.

**Gespeichert** wird das ganze Mikrofonsignal als WAV und eine Textdatei mit den Angaben aus dem Dialog, Kammerton, Temperierung, Gerät und Mikrofon, einer Zeile je Taste (Beginn, Ende, Ton, Sollfrequenz), den Markierungen und dem Verlauf der Erkennung und der Bedienung. Die Dateien liegen im Ordner der App (Android/data/…/files/runs) und werden mit [Aufnahmen an GitHub](#15-aufnahmen-an-github) verschickt.

## 14. Debug-Aufnahmen und Absturzberichte

**Debug-Aufnahmen** (Einstellungen → Debug, in den Testversionen an): Jedes Mal, wenn Sie beim Folgen den Ton von Hand korrigieren, speichert die App 7 s Mikrofonsignal (5 s davor, 2 s danach) als WAV mit einer Textdatei: welchen Ton sie erkannt und welchen Sie eingestellt haben, und was sie in dieser Zeit gehört und getan hat. Genau diese Stellen braucht die Entwicklung. Die neuesten 100 bleiben.

**Absturzberichte**: Stürzt die App ab, schreibt sie einen Bericht; ab Android 11 holt sie beim nächsten Start auch die Berichte des Systems nach. Sie gehen mit [Aufnahmen an GitHub](#15-aufnahmen-an-github) mit.

## 15. Aufnahmen an GitHub

So kommen Läufe, Debug-Aufnahmen und Absturzberichte vom Telefon zu den Entwicklern. Sie brauchen ein Konto bei GitHub und müssen im Browser des Telefons angemeldet sein.

1. Menü ⋮ → **Aufnahmen an GitHub** (ebenfalls nur mit **Debug-Aufnahmen** an). Eine Liste zeigt je Lauf eine Zeile (Datum, Register, Tasten, Dauer, Größe), je Tag die Debug-Aufnahmen und die Absturzberichte. Angekreuzt ist, was noch nicht gepackt wurde; „(schon gepackt)“ lässt sich wieder ankreuzen.
2. **Packen**. Die App legt Zip-Dateien in **Downloads/TunerTime** ab, jede unter 25 MB (die Grenze von GitHub; große Dateien werden geteilt). Die Zips vom letzten Mal löscht sie dort vorher.
3. Der Browser öffnet ein neues Issue auf github.com/Vollesocke/tunertime mit einer Liste der Zips.
4. Unter dem Text **„Add Files“** (Büroklammer) antippen. Die Dateiauswahl zeigt zuerst die zuletzt verwendeten Dateien, oft keine: ☰ → **Downloads** → **TunerTime**, eine Zip lang drücken, die anderen antippen, dann **Auswählen**. Die Links erscheinen oben im Text.
5. Gern ein paar Worte dazu schreiben (Orgel, was auffiel), dann das Issue anlegen.

## 16. Die Einstellungen

**Allgemein**
**Orgelmodus**: Zeilen nach Fußtonlage, Mixtur und Länge, keine Spreizung. **Deutsche Tonnamen**: Cis, B, H; C₁ C c c¹ c². **Language / Sprache**: Systemsprache, English, Deutsch.

**Mixtur**
**Mixtur: dem Bezugschor folgen** ([Mixtur](#9-eine-mixtur-stimmen)).

**Audio**
**Audioquelle**: am besten **Automatisch**. Das Mikrofon wählt die App selbst (das mit dem besten Signal).

**Strobe**
**Farben** (Batlow, Schiefer / Weiß, Rot / Grün, Schwarz / Weiß, Blau / Gelb, Rot / Blau). **Fokusfarbe**: Rahmen, Beschriftung und Anzeige der fokussierten Zeile, 16 Farben (Blau voreingestellt, Hellblau, Cyan, Blaugrün, Grün, Limone, Gelb, Bernstein, Orange, Rot, Rosa, Magenta, Violett, Blauviolett, Weiß, Hellgrau). **Pegelanzeige** (aus): am rechten Rand des Strobe der Eingangspegel; rot zu leise zum Erkennen, gelb knapp, grün gut; der weiße Strich ist die Schwelle der Erkennung. **Angezeigte Fußtonlagen**: bis zu 8 aus 32′ bis 1/8′, auch 10 2/3′, 5 1/3′, 3 1/5′, 2 2/3′, 1 3/5′, 1 1/3′, 1 1/7′.

**Debug**
**Debug-Aufnahmen** ([Debug-Aufnahmen](#14-debug-aufnahmen-und-absturzberichte)). Nur wenn das an ist, stehen **Lauf aufnehmen** und **Aufnahmen an GitHub** im Menü.

## 17. Ohne Orgelmodus: Spreizung und FFT

Ohne Orgelmodus ist TunerTime das Stimmgerät von Greg Alexander, etwa für Klaviere:

- Die Zeilen zeigen Teiltöne (1., 2., 4., 6., 8.; Einstellungen → Angezeigte Teiltöne). Antippen hält den Strobe an (**Pause**).
- Statt Mixtur gibt es den Reiter **Spreizung** (Cent je Oktave, für Saiteninstrumente).
- Statt Länge gibt es **FFT**: ein Spektrogramm. **N+** / **N-** ändert die Blockgröße (größer: genauer, kleiner: schneller), **Skala** / **Hz** die Beschriftung; zoomen mit zwei Fingern, verschieben mit einem, antippen hält an. **Strobe** führt zurück.

## 18. Temperierung messen und vergleichen

So lässt sich die Temperierung einer Orgel nach Gehör abnehmen und mit den bekannten vergleichen. Legen Sie dafür unter Temperierung ein neues Preset an (oder nehmen Sie ein vorhandenes) und öffnen Sie **Bearbeiten**. Unten sind zwei Knöpfe: **Töne messen** und **Matching**.

### Töne messen

1. **Kammerton einstellen.** Ein Strobe nur mit Oktavzeilen, 8′ bis 1/4′ auf dem A (110 bis 3520 Hz), ohne Reiter und ohne Tonerkennung. Stellen Sie das A mit ±1 und ±0,1 so ein, wie es über die A-Pfeifen aller Oktaven richtig ist; Reset setzt 440,0 Hz. **Set** übernimmt das A für diese Messung.
2. **Ton eingeben: <Preset>.** Die Töne C bis H der 8′-Oktave, gegen die gleichstufige Stimmung. Ton+ und Ton− wechseln den Ton (innerhalb der Oktave), Cent ±1 und ±0,1 verschieben ihn, bis seine Zeilen stehen. **Ton speichern** legt ihn im Preset ab und geht zum nächsten noch nicht gespeicherten. Oben zeigt „n/12“, wie viele es sind; ein gespeicherter Ton wird blau. Hat das Preset schon Töne, beginnt die Seite mit diesen Werten.
3. Nach dem zwölften Ton geht es zurück zum Bearbeiten, und **Matching** ist hervorgehoben. Hatte das A dabei nicht 0 Cent, wird die ganze Tabelle so verschoben, dass A = 0 Cent ist.

Zurück führt von „Ton eingeben“ zum Kammerton und von dort zum Bearbeiten; was schon gespeichert ist, bleibt (noch ohne die Verschiebung auf A = 0). Ihre Einstellungen im Hauptbildschirm (Ton, Kammerton, Zeilen, Fokus, Folgen/Fixiert) ändert die Messung nicht. Gemessen werden nur die 12 Halbtöne. Bei Pythagoreisch und den mitteltönigen Stimmungen ist **Töne messen** grau.

### Matching

Geht, sobald das Preset genau einen Ton je Halbton hat. Die Tabelle zeigt für C bis H die Centwerte des Presets und daneben die nächsten bekannten Temperierungen (alle eingebauten und Ihre eigenen Presets), jede mit ihrer mittleren Abweichung (RMS) im Kopf; auf einem schmalen Telefon zwei, auf einem breiteren mehr. Verglichen wird bei A = 0 Cent, ohne Verschiebung. Unterschiede über 2 Cent sind gelb, über 4 Cent rot.

## 19. Probleme melden, Links

Fehler, Abstürze und Wünsche bitte als Issue auf GitHub melden, gern mit [Aufnahmen](#15-aufnahmen-an-github):
 [github.com/Vollesocke/tunertime/issues](https://github.com/Vollesocke/tunertime/issues)

Quelltext von TunerTime Organ:
 [github.com/Vollesocke/tunertime](https://github.com/Vollesocke/tunertime)

Das ursprüngliche TunerTime von Greg Alexander, mit seiner Beschreibung des Strobe und des FFT (englisch):
 [galexander.org/software/tunertime](http://galexander.org/software/tunertime/)

TunerTime und TunerTime Organ sind freie Software unter der GNU GPL, Version 2.
