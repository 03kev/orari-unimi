# orari-unimi

Applicazione Go per consultare da terminale gli orari delle lezioni UNIMI.

## Avvio

```sh
go run ./cmd/orari-unimi
```

I menu e le scelte interattive usano
[ManuCli](https://github.com/manuelefetto/ManuCli): usa le frecce verticali per
spostarti e `Invio` per confermare. È possibile cercare corsi, docenti e singoli
insegnamenti attraverso suggerimenti ottenuti dal portale UNIMI, oltre a gestire
gli insegnamenti salvati nella sezione "I miei orari". In questa sezione si
possono aggiungere e rimuovere singoli insegnamenti, poi visualizzarli insieme
nel calendario personale.
La ricerca non distingue tra maiuscole, minuscole e vocali accentate.

La selezione di un corso, docente o insegnamento recupera gli orari effettivi
pubblicati da UNIMI e li mostra in un calendario settimanale. Premi `A` o la
freccia sinistra per la settimana precedente, `D` o la freccia destra per la
successiva, `W` per mostrare o nascondere sabato e domenica e `Q` o `Esc` per
tornare al menu. Il fine settimana è nascosto inizialmente; la scelta viene
ricordata alle aperture successive. Il calendario si adatta alla
finestra: usa una griglia anche nei comuni terminali da 80
caratteri e passa a un'agenda compatta solo nelle finestre molto strette,
limitando le lezioni visibili senza uscire dall'area disponibile.
I nomi degli insegnamenti vanno a capo dentro la propria cella e ogni
insegnamento mantiene un colore identificativo nelle diverse settimane.

Il package `unimi` espone inoltre
le funzioni `RecuperaOrariCorso`, `RecuperaOrariDocente` e
`RecuperaOrariInsegnamento`: passando un `IntervalloDate` vuoto si ottiene
l'intero anno accademico, mentre un intervallo valorizzato filtra entrambe le
date incluse.

Gli insegnamenti personali vengono salvati in
`orari-unimi/insegnamenti.json` all'interno della cartella di configurazione
dell'utente. Il percorso può essere personalizzato impostando la variabile
d'ambiente `ORARI_UNIMI_FILE`. La preferenza del fine settimana viene salvata
nel file `preferenze.json` nella stessa cartella.

Lo schermo viene ripulito a ogni passaggio tra menu, ricerca, conferme e
calendario. Eventuali messaggi di esito vengono riportati nella schermata
successiva, senza lasciare residui del contenuto precedente.

## Recupero delle liste

- anni accademici;
- corsi di studio e facoltà/scuole;
- docenti;
- insegnamenti.

## Comandi diagnostici per l'ottenimento delle liste

```sh
go run ./cmd/orari-unimi -tipo anni
go run ./cmd/orari-unimi -tipo corsi -anno 2026
go run ./cmd/orari-unimi -tipo docenti -anno 2026
go run ./cmd/orari-unimi -tipo insegnamenti -anno 2026
```

Se `-anno` non viene indicato, viene selezionato automaticamente l'anno più
recente pubblicato dal portale.

Per eseguire i test:

```sh
go test ./...
```

## License

This project is licensed under the GNU General Public License v3.0.
See the [LICENSE](LICENSE) file for details.
