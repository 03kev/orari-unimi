package tui

import (
	"context"
	"errors"
	"fmt"
	"io"
	"strings"

	"orari-unimi/unimi"
)

const (
	numeroSuggerimenti = 10
	pulisciTerminale   = "\x1b[2J\x1b[H"
)

type sorgenteUNIMI interface {
	RecuperaAnniAccademici(context.Context) ([]unimi.AnnoAccademico, error)
	RecuperaCorsi(context.Context, string) ([]unimi.CorsoDiStudio, error)
	RecuperaDocenti(context.Context, string) ([]unimi.Docente, error)
	RecuperaInsegnamenti(context.Context, string) ([]unimi.Insegnamento, error)
	RecuperaOrariCorso(context.Context, string, string, unimi.IntervalloDate) ([]unimi.Lezione, error)
	RecuperaOrariDocente(context.Context, string, string, unimi.IntervalloDate) ([]unimi.Lezione, error)
	RecuperaOrariInsegnamento(context.Context, string, string, unimi.IntervalloDate) ([]unimi.Lezione, error)
}

// Applicazione gestisce la navigazione testuale e mantiene in memoria le liste
// già scaricate durante la sessione.
type Applicazione struct {
	lettore    LettoreTesto
	uscita     io.Writer
	sorgente   sorgenteUNIMI
	archivio   *ArchivioInsegnamenti
	selettore  Selettore
	calendario VisualizzatoreCalendario
	anno       unimi.AnnoAccademico
	corsi      []unimi.CorsoDiStudio
	docenti    []unimi.Docente
	attivita   []unimi.Insegnamento
	notifica   string
}

func NuovaApplicazione(lettore LettoreTesto, uscita io.Writer, sorgente sorgenteUNIMI, archivio *ArchivioInsegnamenti, selettore Selettore, calendario VisualizzatoreCalendario) (*Applicazione, error) {
	if lettore == nil || uscita == nil || sorgente == nil || archivio == nil || selettore == nil || calendario == nil {
		return nil, errors.New("dipendenze dell'applicazione non valide")
	}
	return &Applicazione{
		lettore:    lettore,
		uscita:     uscita,
		sorgente:   sorgente,
		archivio:   archivio,
		selettore:  selettore,
		calendario: calendario,
	}, nil
}

// Esegui mostra il menu principale finché l'utente non sceglie di uscire.
func (a *Applicazione) Esegui(ctx context.Context) error {
	if err := a.caricaAnno(ctx); err != nil {
		return err
	}

	for {
		a.pulisciSchermo()
		a.mostraNotifica()
		fmt.Fprintf(a.uscita, "Orari UNIMI — anno accademico %s\n", a.anno.Nome)
		scelta, err := a.selettore.Scegli("Scegli come consultare gli orari", []string{
			"Per corso",
			"Per docente",
			"Per insegnamento",
			"I miei orari",
			"Esci",
		})
		if err != nil {
			return err
		}
		switch scelta {
		case -1:
			a.pulisciSchermo()
			fmt.Fprintln(a.uscita, "A presto!")
			return nil
		case 0:
			a.menuPerCorso(ctx)
		case 1:
			a.menuPerDocente(ctx)
		case 2:
			a.menuPerInsegnamento(ctx)
		case 3:
			if err := a.menuMieiOrari(ctx); err != nil {
				a.impostaNotifica("Errore: %v", err)
			}
		case 4:
			a.pulisciSchermo()
			fmt.Fprintln(a.uscita, "A presto!")
			return nil
		}
	}
}

func (a *Applicazione) menuPerCorso(ctx context.Context) {
	a.pulisciSchermo()
	corso, selezionato, err := a.selezionaCorso(ctx)
	if err != nil {
		a.impostaNotifica("Errore: %v", err)
		return
	}
	if selezionato {
		lezioni, err := a.sorgente.RecuperaOrariCorso(ctx, a.anno.Codice, corso.Codice, unimi.IntervalloDate{})
		if err != nil {
			a.impostaNotifica("Errore: recupero orari del corso: %v", err)
			return
		}
		a.mostraCalendario(corso.Nome, lezioni)
	}
}

func (a *Applicazione) menuPerDocente(ctx context.Context) {
	a.pulisciSchermo()
	if err := a.caricaDocenti(ctx); err != nil {
		a.impostaNotifica("Errore: %v", err)
		return
	}
	voci := make([]voceRicerca, len(a.docenti))
	for i, docente := range a.docenti {
		voci[i] = voceRicerca{Codice: docente.Codice, Nome: docente.Nome}
	}
	voce, selezionata, err := a.selezionaConRicerca("Cerca docente", voci)
	if err != nil {
		a.impostaNotifica("Errore: %v", err)
		return
	}
	if selezionata {
		lezioni, err := a.sorgente.RecuperaOrariDocente(ctx, a.anno.Codice, voce.Codice, unimi.IntervalloDate{})
		if err != nil {
			a.impostaNotifica("Errore: recupero orari del docente: %v", err)
			return
		}
		a.mostraCalendario(voce.Nome, lezioni)
	}
}

func (a *Applicazione) menuPerInsegnamento(ctx context.Context) {
	a.pulisciSchermo()
	insegnamento, selezionato, err := a.selezionaInsegnamento(ctx)
	if err != nil {
		a.impostaNotifica("Errore: %v", err)
		return
	}
	if selezionato {
		lezioni, err := a.sorgente.RecuperaOrariInsegnamento(ctx, a.anno.Codice, insegnamento.Codice, unimi.IntervalloDate{})
		if err != nil {
			a.impostaNotifica("Errore: recupero orari dell'insegnamento: %v", err)
			return
		}
		a.mostraCalendario(nomeInsegnamento(insegnamento), lezioni)
	}
}

func (a *Applicazione) menuMieiOrari(ctx context.Context) error {
	for {
		a.pulisciSchermo()
		a.mostraNotifica()
		scelta, err := a.selettore.Scegli("I miei orari", []string{
			"Calendario degli insegnamenti salvati",
			"Aggiungi insegnamento",
			"Rimuovi insegnamento",
			"Pulisci insegnamenti",
			"Torna al menu principale",
		})
		if err != nil {
			return err
		}
		switch scelta {
		case -1:
			return nil
		case 0:
			if err := a.mostraOrariSalvati(ctx); err != nil {
				return err
			}
		case 1:
			if err := a.aggiungiInsegnamento(ctx); err != nil {
				return err
			}
		case 2:
			if err := a.rimuoviInsegnamento(); err != nil {
				return err
			}
		case 3:
			if err := a.pulisciInsegnamenti(); err != nil {
				return err
			}
		case 4:
			return nil
		}
	}
}

func (a *Applicazione) selezionaCorso(ctx context.Context) (unimi.CorsoDiStudio, bool, error) {
	if err := a.caricaCorsi(ctx); err != nil {
		return unimi.CorsoDiStudio{}, false, err
	}
	voci := make([]voceRicerca, len(a.corsi))
	perCodice := make(map[string]unimi.CorsoDiStudio, len(a.corsi))
	for i, corso := range a.corsi {
		voci[i] = voceRicerca{Codice: corso.Codice, Nome: corso.Nome}
		perCodice[corso.Codice] = corso
	}
	voce, selezionata, err := a.selezionaConRicerca("Cerca corso", voci)
	if err != nil || !selezionata {
		return unimi.CorsoDiStudio{}, false, err
	}
	return perCodice[voce.Codice], true, nil
}

func (a *Applicazione) selezionaInsegnamento(ctx context.Context) (unimi.Insegnamento, bool, error) {
	if err := a.caricaInsegnamenti(ctx); err != nil {
		return unimi.Insegnamento{}, false, err
	}
	voci := make([]voceRicerca, len(a.attivita))
	perCodice := make(map[string]unimi.Insegnamento, len(a.attivita))
	for i, insegnamento := range a.attivita {
		voci[i] = voceRicerca{Codice: insegnamento.Codice, Nome: nomeInsegnamento(insegnamento)}
		perCodice[insegnamento.Codice] = insegnamento
	}
	voce, selezionata, err := a.selezionaConRicerca("Cerca insegnamento", voci)
	if err != nil || !selezionata {
		return unimi.Insegnamento{}, false, err
	}
	return perCodice[voce.Codice], true, nil
}

func nomeInsegnamento(insegnamento unimi.Insegnamento) string {
	if nome := strings.TrimSpace(insegnamento.Descrizione); nome != "" {
		return nome
	}
	return strings.TrimSpace(insegnamento.Nome)
}

func (a *Applicazione) selezionaConRicerca(titolo string, voci []voceRicerca) (voceRicerca, bool, error) {
	ricerca := ""
	for {
		if ricerca == "" {
			var err error
			ricerca, err = a.leggi(titolo + " (INVIO per tornare): ")
			if err != nil {
				return voceRicerca{}, false, err
			}
			if strings.TrimSpace(ricerca) == "" {
				return voceRicerca{}, false, nil
			}
		}

		risultati := filtraVoci(voci, ricerca, numeroSuggerimenti)
		if len(risultati) == 0 {
			fmt.Fprintln(a.uscita, "Nessun risultato. Prova con un'altra ricerca.")
			ricerca = ""
			continue
		}
		opzioni := make([]string, 0, len(risultati)+2)
		for _, voce := range risultati {
			opzioni = append(opzioni, fmt.Sprintf("%s [%s]", voce.Nome, voce.Codice))
		}
		opzioni = append(opzioni, "Nuova ricerca", "Indietro")
		a.pulisciSchermo()
		scelta, err := a.selettore.Scegli(fmt.Sprintf("Suggerimenti per %q", ricerca), opzioni)
		if err != nil {
			return voceRicerca{}, false, err
		}
		if scelta == -1 || scelta == len(risultati)+1 {
			return voceRicerca{}, false, nil
		}
		if scelta == len(risultati) {
			ricerca = ""
			continue
		}
		return risultati[scelta], true, nil
	}
}

func (a *Applicazione) aggiungiInsegnamento(ctx context.Context) error {
	a.pulisciSchermo()
	insegnamento, selezionato, err := a.selezionaInsegnamento(ctx)
	if err != nil || !selezionato {
		return err
	}
	nome := nomeInsegnamento(insegnamento)
	aggiunto, err := a.archivio.Aggiungi(InsegnamentoSalvato{Anno: a.anno.Codice, Codice: insegnamento.Codice, Nome: nome})
	if err != nil {
		return err
	}
	if aggiunto {
		a.impostaNotifica("%s aggiunto ai tuoi insegnamenti.", nome)
	} else {
		a.impostaNotifica("L'insegnamento è già presente nei tuoi orari.")
	}
	return nil
}

func (a *Applicazione) mostraOrariSalvati(ctx context.Context) error {
	insegnamenti, err := a.archivio.Elenca()
	if err != nil {
		return err
	}
	if len(insegnamenti) == 0 {
		a.impostaNotifica("Non hai ancora salvato insegnamenti.")
		return nil
	}
	lezioni := make([]unimi.Lezione, 0)
	for _, insegnamento := range insegnamenti {
		orariInsegnamento, err := a.sorgente.RecuperaOrariInsegnamento(ctx, insegnamento.Anno, insegnamento.Codice, unimi.IntervalloDate{})
		if err != nil {
			return fmt.Errorf("recupero orari di %s: %w", insegnamento.Nome, err)
		}
		lezioni = append(lezioni, orariInsegnamento...)
	}
	return a.apriCalendario("I miei orari", lezioni)
}

func (a *Applicazione) rimuoviInsegnamento() error {
	insegnamenti, err := a.archivio.Elenca()
	if err != nil {
		return err
	}
	if len(insegnamenti) == 0 {
		a.impostaNotifica("Non ci sono insegnamenti da rimuovere.")
		return nil
	}
	opzioni := make([]string, 0, len(insegnamenti)+1)
	for _, insegnamento := range insegnamenti {
		opzioni = append(opzioni, fmt.Sprintf("%s [%s]", insegnamento.Nome, insegnamento.Codice))
	}
	opzioni = append(opzioni, "Annulla")
	a.pulisciSchermo()
	scelta, err := a.selettore.Scegli("Scegli l'insegnamento da rimuovere", opzioni)
	if err != nil {
		return err
	}
	if scelta == -1 || scelta == len(insegnamenti) {
		return nil
	}
	if err := a.archivio.Rimuovi(scelta); err != nil {
		return err
	}
	a.impostaNotifica("%s rimosso dai tuoi insegnamenti.", insegnamenti[scelta].Nome)
	return nil
}

func (a *Applicazione) pulisciInsegnamenti() error {
	insegnamenti, err := a.archivio.Elenca()
	if err != nil {
		return err
	}
	if len(insegnamenti) == 0 {
		a.impostaNotifica("L'elenco è già vuoto.")
		return nil
	}
	a.pulisciSchermo()
	conferma, err := a.selettore.Scegli("Eliminare tutti gli insegnamenti?", []string{"No, annulla", "Sì, elimina tutto"})
	if err != nil {
		return err
	}
	if conferma != 1 {
		a.impostaNotifica("Operazione annullata.")
		return nil
	}
	if err := a.archivio.Pulisci(); err != nil {
		return err
	}
	a.impostaNotifica("Tutti gli insegnamenti sono stati rimossi.")
	return nil
}

func (a *Applicazione) caricaAnno(ctx context.Context) error {
	anni, err := a.sorgente.RecuperaAnniAccademici(ctx)
	if err != nil {
		return fmt.Errorf("recupero anni accademici: %w", err)
	}
	if len(anni) == 0 {
		return errors.New("il portale non ha restituito anni accademici")
	}
	a.anno = anni[0]
	return nil
}

func (a *Applicazione) caricaCorsi(ctx context.Context) error {
	if a.corsi != nil {
		return nil
	}
	corsi, err := a.sorgente.RecuperaCorsi(ctx, a.anno.Codice)
	if err != nil {
		return fmt.Errorf("recupero corsi: %w", err)
	}
	a.corsi = corsi
	return nil
}

func (a *Applicazione) caricaDocenti(ctx context.Context) error {
	if a.docenti != nil {
		return nil
	}
	docenti, err := a.sorgente.RecuperaDocenti(ctx, a.anno.Codice)
	if err != nil {
		return fmt.Errorf("recupero docenti: %w", err)
	}
	a.docenti = docenti
	return nil
}

func (a *Applicazione) caricaInsegnamenti(ctx context.Context) error {
	if a.attivita != nil {
		return nil
	}
	attivita, err := a.sorgente.RecuperaInsegnamenti(ctx, a.anno.Codice)
	if err != nil {
		return fmt.Errorf("recupero insegnamenti: %w", err)
	}
	a.attivita = attivita
	return nil
}

func (a *Applicazione) mostraCalendario(soggetto string, lezioni []unimi.Lezione) {
	if err := a.apriCalendario(soggetto, lezioni); err != nil {
		a.impostaNotifica("Errore: visualizzazione calendario: %v", err)
	}
}

func (a *Applicazione) apriCalendario(soggetto string, lezioni []unimi.Lezione) error {
	if len(lezioni) == 0 {
		a.impostaNotifica("Nessuna lezione pubblicata per %s.", soggetto)
		return nil
	}
	return a.calendario.Mostra(soggetto, lezioni)
}

func (a *Applicazione) leggi(messaggio string) (string, error) {
	return a.lettore.Leggi(messaggio)
}

func (a *Applicazione) pulisciSchermo() {
	fmt.Fprint(a.uscita, pulisciTerminale)
}

func (a *Applicazione) impostaNotifica(formato string, argomenti ...any) {
	a.notifica = fmt.Sprintf(formato, argomenti...)
}

func (a *Applicazione) mostraNotifica() {
	if a.notifica == "" {
		return
	}
	fmt.Fprintln(a.uscita, a.notifica)
	a.notifica = ""
}
