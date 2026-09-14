# Orari UNIMI per Android

App nativa per Android, realizzata con Kotlin, Jetpack Compose e Material 3.
Usa gli stessi endpoint pubblici AgendaWeb del client Go: ricerca per corso,
docente o insegnamento, calendario settimanale, insegnamenti salvati e calendario
personale aggregato. Il weekend e la navigazione `h`/`j`/`k`/`l` con tastiera
esterna si possono attivare nelle Preferenze; entrambe le scelte restano salvate.

La ricerca e gli orari richiedono una connessione Internet. Insegnamenti e
preferenze sono memorizzati solo nell'app sul dispositivo; non si sincronizzano
automaticamente con la versione per terminale.

## Compilazione

Servono JDK 17 o più recente e Android SDK Platform 36. Da questa cartella:

```sh
./gradlew :app:assembleDebug
```

L'APK installabile viene creato in `app/build/outputs/apk/debug/app-debug.apk`.
In alternativa, apri questa cartella in Android Studio e scegli **Build APK(s)**.

## Installazione

Con il debug USB attivo e `adb` disponibile:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Puoi anche trasferire l'APK sul telefono e aprirlo da lì. Questa build è firmata
con la chiave di debug locale, adatta a prove e installazione personale; per
distribuirla pubblicamente serve una build release firmata con una propria chiave.
