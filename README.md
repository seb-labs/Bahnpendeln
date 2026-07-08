# Bahnpendeln

Bahnpendeln ist eine kleine Android-App für den schnellen Pendelblick.

## Was die App aktuell kann

- zwei Bahnhöfe eintragen
- Bahnhof 1 / Bahnhof 2 deutlich umschalten
- EFA-Vorschläge beim Tippen des Bahnhofsnamens
- Linienfilter mit kombiniertem RE/RB-Filter
- Abfahrten mit Uhrzeit und Verspätung
- moderne, kompakte Compose-Oberfläche
- VRR/EFA-Live-Abfrage **nur per Button**
- kein Netzwerkzugriff beim App-Start
- Fehleranzeige in der App statt Absturz
- keine privaten Zugangsdaten im Code

## Nutzung

1. App öffnen.
2. Bahnhof 1 oder Bahnhof 2 auswählen.
3. Bahnhofsnamen eintragen.
4. Optional einen Linienfilter wählen.
5. Auf **Live laden** tippen.

Die App lädt dann die nächsten sichtbaren Abfahrten aus der öffentlichen VRR/EFA-Abfahrtstafel.

## Download

Release-APKs werden über GitHub Releases bereitgestellt:

https://github.com/seb-labs/Bahnpendeln/releases/latest

## Entwicklung

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

## Datenschutz

Das Repo enthält keine privaten Tokens, keine Zugangsdaten und keine lokalen SDK-Pfade.
