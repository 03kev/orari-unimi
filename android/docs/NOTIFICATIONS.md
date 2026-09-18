# Notifiche locali

Dalla versione 1.5.0 Orari UNIMI può controllare in background gli insegnamenti
salvati e raccogliere gli avvisi in un centro notifiche interno. Il sistema è
disattivato per impostazione predefinita. L'utente deve attivare l'interruttore
generale nelle Preferenze e scegliere singolarmente quali categorie usare.
Nella versione 1.5.1 le categorie sono raccolte nella schermata
**Impostazioni notifiche**, accessibile dalla classica riga con freccia che
compare sotto l'interruttore generale. La scelta dell'anticipo è incorporata
nel riquadro **Prossima lezione**.

## Categorie

- **Variazioni importanti**: annullamenti, ripristini, nuove lezioni e cambi di
  data, ora, aula, docente o note.
- **Prossima lezione**: promemoria silenzioso 15, 30 o 60 minuti prima
  dell'inizio.
- **Aggiornamenti dell'app**: avviso silenzioso quando GitHub pubblica una
  versione più recente.

La campanella accanto alle Preferenze mostra il numero di avvisi non letti.
Aprendola si trovano le notifiche raggruppate per data; uno swipe verso sinistra
elimina la singola voce. Trascinando l'elenco verso il basso si avvia subito un
nuovo controllo delle categorie abilitate. Lo storico contiene al massimo 100
elementi ed è salvato solo sul dispositivo.

## Frequenza e limiti Android

Tutte le categorie, compresi gli aggiornamenti dell'app, vengono pianificate con
WorkManager ogni 15 minuti, il minimo consentito per il lavoro periodico. È la
frequenza richiesta dall'app, non una garanzia di esecuzione esatta: Android può
rinviare un controllo per batteria, modalità Doze, assenza di rete o limiti
imposti dal produttore. Quando si attiva una categoria viene richiesto anche un
primo controllo appena la rete è disponibile. Ogni apertura o ritorno in primo
piano dell'app richiede inoltre un controllo immediato di tutte le categorie
abilitate; WorkManager accorpa e sostituisce le richieste immediate omonime per
evitare controlli simultanei duplicati.

Dalla versione 1.5.4 i recuperi automatici condividono una finestra di
freschezza di due minuti: se l'interfaccia o un altro worker hanno appena
ottenuto gli stessi dati, il controllo riutilizza quella risposta. Gli
insegnamenti salvati dello stesso anno vengono inoltre richiesti insieme, in
gruppi da massimo 20. Il refresh manuale continua a forzare la rete. Dettagli e
vincoli sono raccolti in [Sincronizzazione locale degli orari](SCHEDULE_SYNC.md).

I controlli avviati mentre si usa l'app, inclusi quelli richiesti trascinando il
centro notifiche verso il basso, aggiungono gli avvisi soltanto al centro interno.
Le notifiche di sistema vengono pubblicate esclusivamente dai controlli periodici
eseguiti mentre l'app è chiusa o in background. Un avviso già registrato dentro
l'app non viene ripubblicato nel sistema per la stessa variazione o versione.

Il recupero periodico programma un lavoro singolo per l'anticipo della prossima
lezione. Una fotografia successiva lo lascia invariato, lo sostituisce o lo
cancella in base al nuovo orario. Il promemoria non effettua richieste di rete,
ma dipende comunque dall'esecuzione concessa dal sistema e non deve essere
considerato una sveglia esatta. Su Android 13 o successivo serve anche il
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
