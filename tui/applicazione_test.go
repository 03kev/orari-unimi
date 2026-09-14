package tui

import (
	"bytes"
	"context"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"orari-unimi/unimi"
)

type sorgenteFinta struct {
	anni                      []unimi.AnnoAccademico
	corsi                     []unimi.CorsoDiStudio
	docenti                   []unimi.Docente
	insegnamenti              []unimi.Insegnamento
	chiamateOrariCorso        int
	chiamateOrariInsegnamento int
}

func (s *sorgenteFinta) RecuperaAnniAccademici(context.Context) ([]unimi.AnnoAccademico, error) {
	return s.anni, nil
}

func (s *sorgenteFinta) RecuperaCorsi(context.Context, string) ([]unimi.CorsoDiStudio, error) {
	return s.corsi, nil
}

func (s *sorgenteFinta) RecuperaDocenti(context.Context, string) ([]unimi.Docente, error) {
	return s.docenti, nil
}

func (s *sorgenteFinta) RecuperaInsegnamenti(context.Context, string) ([]unimi.Insegnamento, error) {
	return s.insegnamenti, nil
}

func (s *sorgenteFinta) RecuperaOrariCorso(context.Context, string, string, unimi.IntervalloDate) ([]unimi.Lezione, error) {
	s.chiamateOrariCorso++
	return lezioniFinte(), nil
}

func (s *sorgenteFinta) RecuperaOrariDocente(context.Context, string, string, unimi.IntervalloDate) ([]unimi.Lezione, error) {
	return lezioniFinte(), nil
}

func (s *sorgenteFinta) RecuperaOrariInsegnamento(context.Context, string, string, unimi.IntervalloDate) ([]unimi.Lezione, error) {
	s.chiamateOrariInsegnamento++
	return lezioniFinte(), nil
}

func lezioniFinte() []unimi.Lezione {
	return []unimi.Lezione{{
		ID: "1", Data: time.Date(2026, 9, 15, 0, 0, 0, 0, time.UTC),
	}}
}

type selettoreFinto struct {
	scelte    []int
	posizione int
}

type lettoreFinto struct {
	risposte  []string
	posizione int
}

type calendarioFinto struct {
	chiamate int
	titolo   string
	lezioni  []unimi.Lezione
}

func (c *calendarioFinto) Mostra(titolo string, lezioni []unimi.Lezione) error {
	c.chiamate++
	c.titolo = titolo
	c.lezioni = append([]unimi.Lezione(nil), lezioni...)
	return nil
}

func (l *lettoreFinto) Leggi(string) (string, error) {
	risposta := l.risposte[l.posizione]
	l.posizione++
	return risposta, nil
}

func (s *selettoreFinto) Scegli(_ string, opzioni []string) (int, error) {
	scelta := s.scelte[s.posizione]
	s.posizione++
	if scelta < -1 || scelta >= len(opzioni) {
		panic("scelta del test fuori dalle opzioni")
	}
	return scelta, nil
}

func TestIndietroDaSuggerimentiESottomenu(t *testing.T) {
	sorgente := &sorgenteFinta{
		anni:  []unimi.AnnoAccademico{{Codice: "2026", Nome: "2026/2027"}},
		corsi: []unimi.CorsoDiStudio{{Codice: "F1X", Nome: "Informatica"}},
	}
	archivio, _ := NuovoArchivioInsegnamenti(filepath.Join(t.TempDir(), "insegnamenti.json"))
	lettore := &lettoreFinto{risposte: []string{"info"}}
	selettore := &selettoreFinto{scelte: []int{0, -1, 3, -1, -1}}
	calendario := &calendarioFinto{}
	var uscita bytes.Buffer
	app, err := NuovaApplicazione(lettore, &uscita, sorgente, archivio, selettore, calendario)
	if err != nil {
		t.Fatal(err)
	}
	if err := app.Esegui(context.Background()); err != nil {
		t.Fatal(err)
	}
	if selettore.posizione != len(selettore.scelte) || calendario.chiamate != 0 || sorgente.chiamateOrariCorso != 0 {
		t.Fatalf("ritorno dai menu inatteso: selezioni=%d, calendario=%d, recuperi=%d", selettore.posizione, calendario.chiamate, sorgente.chiamateOrariCorso)
	}
}

func TestApplicazioneSelezionaCorso(t *testing.T) {
	sorgente := &sorgenteFinta{
		anni:  []unimi.AnnoAccademico{{Codice: "2026", Nome: "2026/2027"}},
		corsi: []unimi.CorsoDiStudio{{Codice: "F1X", Nome: "Informatica"}},
	}
	archivio, _ := NuovoArchivioInsegnamenti(filepath.Join(t.TempDir(), "insegnamenti.json"))
	lettore := &lettoreFinto{risposte: []string{"info"}}
	var uscita bytes.Buffer
	selettore := &selettoreFinto{scelte: []int{0, 0, 4}}
	calendario := &calendarioFinto{}
	app, err := NuovaApplicazione(lettore, &uscita, sorgente, archivio, selettore, calendario)
	if err != nil {
		t.Fatal(err)
	}
	if err := app.Esegui(context.Background()); err != nil {
		t.Fatal(err)
	}
	if calendario.chiamate != 1 || calendario.titolo != "Informatica" || len(calendario.lezioni) != 1 {
		t.Fatalf("calendario inatteso: %#v", calendario)
	}
}

func TestApplicazioneGestisceMieiInsegnamenti(t *testing.T) {
	sorgente := &sorgenteFinta{
		anni: []unimi.AnnoAccademico{{Codice: "2026", Nome: "2026/2027"}},
		insegnamenti: []unimi.Insegnamento{{
			Codice: "ECF1X-1", Descrizione: "Algoritmi [M. ROSSI]",
		}},
	}
	archivio, _ := NuovoArchivioInsegnamenti(filepath.Join(t.TempDir(), "insegnamenti.json"))
	// I miei orari -> aggiungi -> cerca -> seleziona -> mostra calendario ->
	// rimuovi -> seleziona -> indietro -> esci.
	lettore := &lettoreFinto{risposte: []string{"algoritmi"}}
	var uscita bytes.Buffer
	selettore := &selettoreFinto{scelte: []int{3, 1, 0, 0, 2, 0, 4, 4}}
	calendario := &calendarioFinto{}
	app, _ := NuovaApplicazione(lettore, &uscita, sorgente, archivio, selettore, calendario)
	if err := app.Esegui(context.Background()); err != nil {
		t.Fatal(err)
	}
	insegnamenti, err := archivio.Elenca()
	if err != nil || len(insegnamenti) != 0 {
		t.Fatalf("archivio inatteso: %#v, errore=%v", insegnamenti, err)
	}
	if sorgente.chiamateOrariCorso != 0 || sorgente.chiamateOrariInsegnamento != 1 {
		t.Fatalf("endpoint orari errato: corsi=%d, insegnamenti=%d", sorgente.chiamateOrariCorso, sorgente.chiamateOrariInsegnamento)
	}
	if calendario.chiamate != 1 || !strings.Contains(uscita.String(), "aggiunto ai tuoi insegnamenti") {
		t.Fatalf("flusso insegnamenti inatteso:\n%s", uscita.String())
	}
	if strings.Count(uscita.String(), pulisciTerminale) < 5 {
		t.Fatalf("il terminale non viene pulito ai cambi di menu:\n%q", uscita.String())
	}
}

func TestApplicazioneSelezionaInsegnamento(t *testing.T) {
	sorgente := &sorgenteFinta{
		anni: []unimi.AnnoAccademico{{Codice: "2026", Nome: "2026/2027"}},
		insegnamenti: []unimi.Insegnamento{{
			Codice: "ECF1X-1", Descrizione: "Algoritmi [M. ROSSI]",
		}},
	}
	archivio, _ := NuovoArchivioInsegnamenti(filepath.Join(t.TempDir(), "insegnamenti.json"))
	selettore := &selettoreFinto{scelte: []int{2, 0, 4}}
	var uscita bytes.Buffer
	lettore := &lettoreFinto{risposte: []string{"algoritmi"}}
	calendario := &calendarioFinto{}
	app, _ := NuovaApplicazione(lettore, &uscita, sorgente, archivio, selettore, calendario)
	if err := app.Esegui(context.Background()); err != nil {
		t.Fatal(err)
	}
	if calendario.chiamate != 1 || calendario.titolo != "Algoritmi [M. ROSSI]" || len(calendario.lezioni) != 1 {
		t.Fatalf("calendario inatteso: %#v", calendario)
	}
}
