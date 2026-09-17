# Notifiche locali

Dalla versione 1.5.0 Orari UNIMI può controllare in background gli insegnamenti
salvati e raccogliere gli avvisi in un centro notifiche interno. Il sistema è
disattivato per impostazione predefinita. L'utente deve attivare l'interruttore
generale nelle Preferenze e scegliere singolarmente quali categorie usare.

## Categorie

- **Variazioni importanti**: annullamenti, ripristini, nuove lezioni e cambi di
  data, ora, aula, docente o note.
- **Prossima lezione**: promemoria silenzioso 15, 30 o 60 minuti prima
  dell'inizio.
- **Aggiornamenti dell'app**: avviso silenzioso quando GitHub pubblica una
  versione più recente.

La campanella accanto alle Preferenze mostra il numero di avvisi non letti.
Aprendola si trovano le notifiche raggruppate per data; uno swipe verso sinistra
elimina la singola voce. Lo storico contiene al massimo 100 elementi ed è
salvato solo sul dispositivo.

## Frequenza e limiti Android

Le variazioni e i promemoria vengono pianificati con WorkManager ogni 15 minuti,
il minimo consentito per il lavoro periodico. È la frequenza richiesta
dall'app, non una garanzia di esecuzione esatta: Android può rinviare un
controllo per batteria, modalità Doze, assenza di rete o limiti imposti dal
produttore. Gli aggiornamenti dell'app vengono controllati ogni 12 ore. Quando
si attiva una categoria viene richiesto anche un primo controllo appena la rete
è disponibile.

I promemoria dipendono quindi dall'esecuzione concessa dal sistema e non devono
essere considerati una sveglia esatta. Su Android 13 o successivo serve anche il
permesso di sistema per mostrare gli avvisi. Se il permesso viene revocato, gli
eventi possono comunque essere conservati nel centro notifiche locale.

## Controllo delle variazioni

Il primo recupero valido crea una fotografia di riferimento senza inviare
avvisi. I controlli successivi confrontano solo le lezioni odierne o future,
identificate prima di tutto dall'identificativo fornito dal portale UNIMI.

Per ridurre i falsi positivi vengono applicate queste protezioni:

1. un errore di rete o del portale non modifica la fotografia precedente;
2. una risposta completamente vuota, quando erano presenti lezioni future,
   deve ripetersi prima di essere accettata;
3. una singola lezione scomparsa deve mancare in due controlli consecutivi;
4. lo stesso cambiamento ha un identificativo stabile e non viene archiviato o
   notificato due volte;
5. quando cambia l'elenco degli insegnamenti salvati viene creata una nuova
   fotografia, così le lezioni appena aggiunte dall'utente non sono scambiate
   per variazioni dell'orario.

Annullamenti espliciti e cambi di campi in una lezione già identificata vengono
segnalati al primo controllo valido. Tutti i confronti e i dati di riferimento
restano nei file privati dell'app; non esiste un server dedicato e non vengono
caricati dati personali.

## Canali di sistema

Android espone tre canali separati: variazioni, promemoria e aggiornamenti.
Quello delle variazioni usa l'importanza normale; promemoria e aggiornamenti
sono creati silenziosi. Le impostazioni di sistema possono comunque cambiare
visibilità, suono e priorità di ciascun canale.
