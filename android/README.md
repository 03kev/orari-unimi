# Orari UNIMI per Android

App nativa per Android, realizzata con Kotlin, Jetpack Compose e Material 3.
Usa gli stessi endpoint pubblici AgendaWeb del client Go: ricerca per corso,
docente o insegnamento, calendario settimanale, insegnamenti salvati e calendario
personale aggregato. I corsi di laurea sono distinti per tipo con colore ed
etichetta; aprendo un corso si vedono gli insegnamenti di ogni percorso e si può
salvare il corso nei Preferiti. Dal calendario di un corso o docente si può
aggiungere direttamente un insegnamento ai propri orari. La visibilità del
weekend si sceglie nelle Preferenze e resta salvata.
Se il record di un corso non elenca ancora gli insegnamenti, l'app cerca quelli
associati al suo codice nell'elenco generale pubblicato dal portale per lo
stesso anno accademico.

La ricerca e gli orari richiedono una connessione Internet. Insegnamenti, corsi
preferiti e preferenze sono memorizzati solo nell'app sul dispositivo; non si
sincronizzano automaticamente con la versione per terminale.

## Compilazione

Servono JDK 17 o più recente e Android SDK Platform 36. Da questa cartella:

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Gli APK sono creati in `app/build/outputs/apk/debug/app-debug.apk` e
`app/build/outputs/apk/release/app-release.apk`. La variante release è firmata
con la chiave locale in `signing/release.keystore` e configurata da
`signing/release.properties`. Questa cartella è esclusa da Git: conserva una
copia sicura di **entrambi** i file, perché la stessa chiave serve per firmare
gli aggiornamenti futuri. Senza questi file la compilazione debug funziona,
mentre la compilazione release si interrompe con un messaggio esplicito.

Per predisporre una nuova chiave su un altro computer, crea la cartella
`signing/` e genera un keystore PKCS12 con alias `orari-unimi` usando `keytool`.
Per esempio:

```sh
mkdir -p signing
keytool -genkeypair -keystore signing/release.keystore -storetype PKCS12 \
  -alias orari-unimi -keyalg RSA -keysize 4096 -validity 36500 \
  -dname "CN=Orari UNIMI"
```

Nel file `signing/release.properties` inserisci `storePassword`, `keyPassword`
e `keyAlias=orari-unimi`. Usa la chiave originale per aggiornare un'app già
installata; una chiave diversa crea una firma incompatibile.

## Installazione

Con il debug USB attivo e `adb` disponibile:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

Puoi anche trasferire l'APK sul telefono e aprirlo da lì. La vecchia build debug
usa la stessa identità dell'app ma una chiave diversa: se è installata, devi
disinstallarla prima di installare la release. La disinstallazione elimina gli
insegnamenti e le preferenze salvati in quella installazione.
