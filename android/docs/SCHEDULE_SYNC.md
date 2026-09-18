# Sincronizzazione locale degli orari

Questo documento descrive il comportamento introdotto in Orari UNIMI 1.5.4,
le scelte tecniche che lo sostengono e il punto di partenza per un futuro
backend con notifiche push. Va aggiornato quando cambia uno dei tempi, delle
chiavi cache o delle garanzie elencate qui.

## Obiettivi funzionali

La sincronizzazione deve rispettare contemporaneamente quattro requisiti:

1. mostrare subito un calendario già disponibile sul dispositivo;
2. controllare spesso le variazioni pubblicate da UNIMI;
3. evitare che apertura dell'app, schermata del calendario e WorkManager
   ripetano contemporaneamente la stessa richiesta;
4. non trasformare una risposta incompleta o un errore del portale in una
   cancellazione di lezioni.

Dal punto di vista dell'utente, aprire un calendario mostra prima la copia
locale e avvia un aggiornamento automatico solo quando l'ultima risposta valida
ha più di due minuti. Il pulsante di aggiornamento forza sempre un nuovo
controllo. In assenza di rete rimane disponibile una copia recente fino a 24
ore, accompagnata dallo stato offline già mostrato dall'interfaccia.

## Tempi e politiche

| Dato o azione | Politica |
| --- | --- |
| Calendario aperto automaticamente | usa la risposta se ha al massimo 2 minuti, altrimenti aggiorna |
| Ritorno dell'app in primo piano | il worker immediato usa la stessa finestra di 2 minuti |
| Controllo periodico delle notifiche | richiesto ogni 15 minuti da WorkManager |
| Refresh manuale del calendario | forza la rete |
| Pull-to-refresh del centro notifiche | forza la rete |
| Calendario offline | conservato per un massimo di 24 ore |
| Cataloghi di anni, corsi, docenti e insegnamenti | cache primaria 12 ore, ripiego offline 7 giorni |
| Cache complessiva delle risposte | massimo 8 MiB, eliminazione dei file più vecchi |

I due minuti sono una finestra anti-duplicazione, non l'intervallo di
sincronizzazione. Se un worker ha terminato alle 10:00 e l'utente apre l'app
alle 10:01, viene riutilizzata quella risposta. Un refresh manuale alle 10:01
continua invece a interrogare il portale.

WorkManager non garantisce che il lavoro periodico parta esattamente ogni 15
minuti: Doze, batteria, rete e politiche del produttore possono rinviarlo. Il
client non deve quindi descrivere i 15 minuti come una scadenza garantita.

## Componenti coinvolti

- `UnimiApi.kt` costruisce le richieste AgendaWeb, applica cache, batch e
  deduplicazione delle richieste identiche.
- `ResponseCache.kt` salva le risposte HTTP in file nominati con SHA-256 della
  chiave logica e usa la data di modifica come ora dell'ultimo recupero.
- `OrariApp.kt` mostra prima la cache, distingue apertura automatica e refresh
  manuale e aggiorna il widget dopo una nuova fotografia valida.
- `NotificationWork.kt` pianifica sincronizzazioni, confronta le fotografie e
  programma il promemoria della prossima lezione.
- `NotificationBaselineStore.kt` conserva la fotografia usata per rilevare le
  variazioni.
- `LocalStore.kt` conserva preferenze, inbox e identificativo del promemoria
  programmato.
- `ScheduleWidgetProvider.kt` e `ScheduleWidgetLessonsService.kt` leggono
  soltanto la cache e non interrogano direttamente UNIMI.

## Flusso di una richiesta

1. Il chiamante richiede un calendario singolo o l'insieme degli insegnamenti
   salvati.
2. Una richiesta automatica cerca una risposta con età massima di due minuti.
3. In caso di cache valida, la risposta viene analizzata e restituita senza
   rete.
4. In caso contrario viene acquisito il lock associato alla chiave HTTP.
5. Dopo il lock la cache viene controllata nuovamente: un altro chiamante può
   aver terminato il recupero mentre questo era in attesa.
6. Solo il primo chiamante interroga `grid_call.php`; gli altri riutilizzano il
   file appena scritto.
7. Parsing e accesso alla rete vengono eseguiti fuori dal thread UI.

I lock sono condivisi da tutte le istanze di `UnimiApi` nello stesso processo.
L'app, i worker e il widget usano attualmente il processo predefinito, quindi
questa garanzia copre il funzionamento reale. Se in futuro un componente viene
spostato in un processo Android separato servirà un lock inter-processo o, più
semplicemente, il backend descritto sotto.

Anche un refresh forzato viene accorpato a un altro refresh forzato già in
corso con la stessa chiave. Un refresh iniziato dopo il completamento del primo
rimane invece una nuova richiesta, come richiesto dall'azione manuale.

## Raggruppamento degli insegnamenti

AgendaWeb accetta più campi `attivita[]` nella stessa POST. Gli insegnamenti
salvati vengono:

1. deduplicati per coppia anno accademico e codice;
2. raggruppati per anno accademico, perché la richiesta contiene un solo campo
   `anno`;
3. ordinati per codice, così la chiave cache è stabile;
4. divisi in gruppi da massimo 20 elementi;
5. recuperati con una POST per gruppo e poi riuniti in una fotografia unica.

Il 18 settembre 2026 il contratto è stato verificato sul portale pubblico con
due insegnamenti dell'anno 2025: le risposte singole contenevano 27 e 24 eventi;
la richiesta combinata ne ha restituiti 51, senza eventi mancanti o aggiuntivi.
I test automatici verificano inoltre forma dei campi, separazione tra anni e
unione dei risultati usando un trasporto finto.

Una versione precedente salvava un file per ogni insegnamento. La lettura
mantiene un fallback per quei file, quindi l'aggiornamento dell'app non elimina
subito una cache offline ancora valida. Dopo il primo recupero la selezione usa
la nuova chiave combinata.

Con `N` insegnamenti nello stesso anno, il numero teorico di chiamate per ciclo
passa da `N` a `ceil(N / 20)`. Per sei insegnamenti e 96 cicli teorici al giorno
si passa da 576 richieste a 96, prima ancora di eliminare le collisioni con
l'apertura dell'app.

## Fotografie e rilevamento delle variazioni

Il recupero di tutti i gruppi deve terminare con successo prima che la
fotografia combinata venga consegnata al motore delle notifiche. Se un gruppo
fallisce, la baseline precedente resta invariata.

Il confronto considera soltanto lezioni odierne o future e mantiene queste
protezioni:

- errori HTTP, timeout e parsing non modificano la baseline;
- una fotografia interamente vuota viene rinviata una volta se prima esistevano
  lezioni future;
- una lezione scomparsa deve mancare in due fotografie consecutive;
- annullamenti espliciti e modifiche di data, ora, aula, docente o note vengono
  rilevati alla prima fotografia valida;
- gli identificativi stabili impediscono di archiviare due volte la stessa
  variazione;
- cambiare gli insegnamenti salvati azzera la baseline, evitando che una scelta
  dell'utente venga scambiata per una variazione UNIMI.

La richiesta continua a usare `all_events=1`. Limitare l'intervallo temporale
potrebbe ridurre il payload, ma richiede prima una verifica del contratto del
portale e una strategia che non perda modifiche a lezioni più lontane.

## Promemoria della prossima lezione

La sincronizzazione periodica individua la prossima lezione non annullata e
programma un `OneTimeWorkRequest` per l'anticipo scelto dall'utente. Il worker
contiene soltanto i dati necessari alla notifica e non effettua una richiesta
di rete.

Ogni nuova fotografia può:

- lasciare invariato il lavoro se lezione e orario non sono cambiati;
- sostituirlo se la prossima lezione è cambiata;
- cancellarlo se non esistono lezioni future o se i promemoria vengono
  disattivati.

La chiave del lavoro programmato e il suo istante vengono conservati nelle
preferenze. Il worker verifica di essere ancora quello atteso prima di creare
l'avviso, così un lavoro superato non può pubblicare una notifica. WorkManager
persiste il lavoro attraverso la chiusura normale dell'app e il riavvio del
dispositivo, ma l'orario resta soggetto alle ottimizzazioni Android.

Il recupero degli orari rimane ogni 15 minuti quando sono abilitate le
variazioni o i promemoria: una cancellazione può infatti rendere obsoleto anche
un promemoria già programmato.

## Errori e comportamento offline

- La cache viene scritta soltanto dopo una risposta HTTP valida e completa.
- Un errore di rete non aggiorna l'ora del file e non lo rende artificialmente
  recente.
- L'interfaccia continua a mostrare la copia offline disponibile.
- Il worker periodico restituisce `retry`, lasciando a WorkManager il backoff.
- Il widget non apre connessioni e mostra un invito ad aprire l'app quando non
  esiste una fotografia utilizzabile.
- Il limite di risposta resta 64 MiB; il batch da 20 impedisce di costruire
  richieste personali senza limite.

## Test e verifiche richieste

`UnimiApiSyncTest.kt` copre:

- una POST per anno accademico;
- ordine stabile dei codici nel form;
- riutilizzo automatico entro due minuti;
- nuovo recupero dopo la scadenza;
- una sola chiamata per due refresh forzati concorrenti.

`NotificationEngineTest.kt` copre le protezioni contro falsi positivi e il
calcolo dell'istante del promemoria. Prima di una release vanno eseguiti almeno:

```text
./gradlew test
./gradlew lint
./gradlew assembleRelease
```

La release deve inoltre essere provata su un dispositivo verificando apertura
con cache, refresh manuale, passaggio background/foreground, promemoria e
aggiornamento del widget.

## Migrazione futura al backend

Il backend dovrà sostituire la sorgente delle fotografie, non il modello usato
dall'interfaccia. La separazione consigliata è:

```text
UNIMI AgendaWeb -> backend di polling -> fotografia/delta -> app
                                      -> FCM per variazione
```

Quando inizierà quel lavoro:

1. estrarre da `UnimiApi` un'interfaccia `ScheduleDataSource` che restituisca
   `ScheduleSnapshot` per calendario singolo e selezione personale;
2. mantenere `ResponseCache`, visualizzazione immediata e refresh manuale come
   fallback locale;
3. lasciare nel client `NotificationEngine` durante la prima fase, confrontando
   anche le fotografie ricevute dal backend;
4. usare sul backend la stessa chiave canonica: anno, tipo di sorgente, codici
   ordinati e percorsi del corso;
5. associare gli utenti soltanto a topic o identificativi anonimi dei calendari,
   senza memorizzare nomi, orari personali o account universitari;
6. deduplicare il polling tra tutti gli utenti e applicare una scadenza agli
   interessi non confermati dall'app;
7. includere in ogni fotografia versione, ora del recupero, origine e hash del
   contenuto;
8. fare in modo che FCM segnali la disponibilità di dati nuovi; l'app deve poi
   recuperare una fotografia verificabile e aggiornare cache e baseline;
9. conservare WorkManager come fallback più lento quando push o backend non
   sono raggiungibili;
10. durante la migrazione evitare che backend e worker locale notifichino la
    stessa variazione usando lo stesso identificativo stabile dell'evento.

### Invarianti da conservare con il backend

- Il calendario resta consultabile offline.
- Il refresh manuale produce un controllo esplicito e mostra l'ora reale della
  fotografia restituita.
- Una risposta parziale non sostituisce una fotografia completa.
- Le selezioni personali restano sul dispositivo; al server basta conoscere
  identificativi anonimi dei calendari osservati.
- Il widget continua a leggere dati locali già validati.
- Una variazione viene archiviata e notificata una sola volta.
- La disattivazione delle notifiche rimuove iscrizioni e lavori locali.
- Il fallback locale non deve moltiplicare le richieste quando il backend è
  sano.

### Telemetria minima, senza dati personali

Per valutare il backend bastano contatori aggregati: calendari unici attivi,
richieste UNIMI riuscite o fallite, durata, byte, cache hit, fotografie cambiate
e notifiche inviate. Non servono nomi degli insegnamenti, utenti o token esposti
nei log. Sul client eventuali contatori diagnostici devono restare locali salvo
consenso esplicito futuro.
