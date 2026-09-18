# Appelli degli esami

La sezione Appelli usa il servizio JSON richiamato dalla pagina ufficiale
"Calendario degli appelli" dell'Università degli Studi di Milano:

- `https://work.unimi.it/foProssimiEsami/json/{codice-corso}/1` per oggi;
- `https://work.unimi.it/foProssimiEsami/json/{codice-corso}/30` per i prossimi 30 giorni;
- `https://work.unimi.it/foProssimiEsami/json/{codice-corso}` per tutti gli appelli programmati.

Il servizio è pubblico e non richiede autenticazione, ma non è documentato come
API stabile. L'integrazione è quindi isolata in `ExamApi`: modifiche future al
servizio non devono propagarsi alla UI o al resto del client AgendaWeb.

## Dati utilizzati

Ogni risposta raggruppa gli appelli per insegnamento. L'app conserva il codice e
il nome dell'insegnamento e, per ogni appello, data, ora, aula, docente, fascia
alfabetica, tipo di prova, tipo di appello e apertura e chiusura delle iscrizioni.
`idAppello` è usato come identità primaria; se manca, viene costruita una chiave
locale dai campi disponibili.

Ora, aula e alcune descrizioni possono non essere ancora pubblicate. La UI le
tratta come opzionali e non inventa valori mancanti. L'endpoint non fornisce la
durata dell'esame: l'inserimento nel calendario propone l'ora iniziale e lascia
all'app calendario la gestione della durata. Se manca anche l'ora, l'evento è
proposto come evento giornaliero.

## I miei appelli

Il segnalibro presente su ogni appello lo salva esclusivamente sul dispositivo.
La schermata mensile “I miei appelli” evidenzia con uno o più punti i giorni che
contengono appelli salvati. Selezionando una data mostra gli appelli del giorno;
selezionando un appello apre tutti i dettagli disponibili e consente di
rimuoverlo dai salvati oppure aggiungerlo al calendario Android. Il calendario
può essere sfogliato con le frecce o con uno scorrimento laterale.
Questa funzione serve come promemoria visivo e non effettua automaticamente
l'iscrizione all'esame; l'iscrizione ufficiale resta su Unimia.
Quando il corso viene aggiornato dalla rete, i dati degli appelli già salvati
vengono riallineati usando `idAppello`, senza cancellare automaticamente un
promemoria se l'appello non compare nella finestra richiesta.

## Cache e aggiornamento

Aprendo un corso viene mostrata subito una risposta recente presente nella cache
locale e viene sempre avviato un aggiornamento di rete. Una risposta può essere
usata offline per non più di 24 ore; la schermata indica chiaramente quando sta
mostrando dati offline e riporta l'ora dell'ultimo aggiornamento. La cache usa lo
stesso limite complessivo di 8 MB degli orari.

Il servizio a monte dichiara una cache HTTP fino a otto ore. L'app richiede una
risposta aggiornata, ma non può garantire che una modifica sia visibile prima
che UNIMI la renda disponibile nel servizio.

## Limiti

- Le richieste sono per corso di laurea, non per singolo insegnamento. Il filtro
  per insegnamento viene quindi applicato ai risultati del corso.
- Non è disponibile un parametro per l'anno accademico o per gli appelli storici.
- Una risposta `404` per un codice selezionato viene interpretata come elenco
  vuoto, come fa la pagina ufficiale.
- Il servizio non espone un collegamento autenticato per iscriversi all'appello:
  l'iscrizione continua a essere effettuata tramite Unimia.
- Non esiste un campo esplicito per un appello annullato. Un futuro sistema di
  notifiche dovrà confermare una sparizione in più controlli prima di segnalarla.
