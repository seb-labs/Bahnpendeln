# Bahnpendeln

Bahnpendeln ist eine kleine Android-App für den schnellen Pendelblick.

## Aktueller Stand

Diese Version ist eine frisch aufgebaute stabile Basis:

- zwei Bahnhöfe eintragen
- Bahnhof 1 / Bahnhof 2 umschalten
- Linienfilter auswählen
- modernes, kompaktes Compose-Layout
- kein Netzwerkzugriff beim App-Start
- keine privaten Zugangsdaten im Code

Der Live-Abruf für VRR/EFA wird im nächsten Schritt kontrolliert ergänzt, sobald die Basis auf dem Gerät stabil öffnet.

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
