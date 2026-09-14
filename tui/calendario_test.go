package tui

import (
	"bytes"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
	"time"
	"unicode/utf8"

	"orari-unimi/unimi"
)

var sequenzaColoreANSI = regexp.MustCompile(`\x1b\[[0-9;]*m`)

func TestRenderCalendarioRispettaDimensioniFinestra(t *testing.T) {
	settimana := time.Date(2026, 9, 14, 0, 0, 0, 0, time.UTC)
	lezioni := []unimi.Lezione{
		{ID: "1", Data: settimana, OraInizio: "08:30", OraFine: "10:30", Insegnamento: "Programmazione", Aula: "Aula Alfa"},
		{ID: "2", Data: settimana, OraInizio: "10:30", OraFine: "12:30", Insegnamento: "Algoritmi e strutture dati", Aula: "Aula Beta"},
		{ID: "3", Data: settimana, OraInizio: "14:30", OraFine: "16:30", Insegnamento: "Basi di dati", Aula: "Aula Gamma"},
		{ID: "4", Data: settimana.AddDate(0, 0, 2), OraInizio: "09:30", OraFine: "11:30", Insegnamento: "Reti", Annullata: true},
	}

	for _, mostraFineSettimana := range []bool{false, true} {
		verificaDimensioniRender(t, renderCalendario("Informatica", lezioni, settimana, 140, 16, mostraFineSettimana), 140, 16)
		verificaDimensioniRender(t, renderCalendario("Informatica", lezioni, settimana, 80, 12, mostraFineSettimana), 80, 12)
		stretto := renderCalendario("Informatica", lezioni, settimana, 30, 10, mostraFineSettimana)
		verificaDimensioniRender(t, stretto, 30, 10)
		if !strings.Contains(stretto, "W:") || !strings.Contains(stretto, "Q/Esc") {
			t.Fatalf("comandi incompleti nel terminale stretto:\n%s", stretto)
		}
	}
}

func TestRenderCalendarioUsaLaGrigliaInUnTerminaleStandard(t *testing.T) {
	settimana := time.Date(2026, 9, 14, 0, 0, 0, 0, time.UTC)
	lezioni := []unimi.Lezione{{
		ID: "1", Data: settimana, OraInizio: "08:30", OraFine: "10:30", Insegnamento: "Programmazione", Aula: "Aula Alfa",
	}}

	griglia := renderCalendario("Informatica", lezioni, settimana, 80, 24, true)
	if !strings.Contains(griglia, "+----------+") || !strings.Contains(griglia, "Lun 14/09") || !strings.Contains(griglia, "Dom 20/09") {
		t.Fatalf("griglia settimanale inattesa:\n%s", griglia)
	}
	if !strings.Contains(griglia, "08:30") || !strings.Contains(griglia, "|Lun 14/09 ") {
		t.Fatalf("le lezioni non sono disposte nelle celle del calendario:\n%s", griglia)
	}
	compatto := renderCalendario("Informatica", lezioni, settimana, 60, 18, true)
	if strings.Contains(compatto, "+----------+") || !strings.Contains(compatto, "Lun 14/09 |") || !strings.Contains(compatto, "Dom 20/09 | -") {
		t.Fatalf("agenda compatta inattesa:\n%s", compatto)
	}
}

func TestFineSettimanaNascostoEMostratoInGrigliaEAgenda(t *testing.T) {
	settimana := time.Date(2026, 9, 14, 0, 0, 0, 0, time.UTC)
	lezioni := []unimi.Lezione{
		{ID: "sabato", Data: settimana.AddDate(0, 0, 5), OraInizio: "09:00", OraFine: "10:00", Insegnamento: "Lezione sabato"},
		{ID: "domenica", Data: settimana.AddDate(0, 0, 6), OraInizio: "11:00", OraFine: "12:00", Insegnamento: "Lezione domenica"},
	}
	for _, larghezza := range []int{80, 60} {
		nascosto := renderCalendario("Informatica", lezioni, settimana, larghezza, 20, false)
		if strings.Contains(nascosto, "Sab 19/09") || strings.Contains(nascosto, "Dom 20/09") || strings.Contains(nascosto, "09:00") || strings.Contains(nascosto, "11:00") || !strings.Contains(nascosto, "W weekend: no") {
			t.Fatalf("fine settimana visibile con opzione disattivata (%d colonne):\n%s", larghezza, nascosto)
		}
		mostrato := renderCalendario("Informatica", lezioni, settimana, larghezza, 20, true)
		if !strings.Contains(mostrato, "Sab 19/09") || !strings.Contains(mostrato, "Dom 20/09") || !strings.Contains(mostrato, "09:00") || !strings.Contains(mostrato, "11:00") || !strings.Contains(mostrato, "W weekend: sì") {
			t.Fatalf("fine settimana assente con opzione attivata (%d colonne):\n%s", larghezza, mostrato)
		}
	}
}

func TestPreferenzaFineSettimanaRestaSalvata(t *testing.T) {
	percorso := filepath.Join(t.TempDir(), "preferenze.json")
	calendario, err := NuovoCalendarioTerminale(&bytes.Buffer{}, percorso)
	if err != nil {
		t.Fatal(err)
	}
	if calendario.mostraFineSettimana {
		t.Fatal("il fine settimana deve essere nascosto per impostazione predefinita")
	}
	if err := calendario.alternaFineSettimana(); err != nil {
		t.Fatal(err)
	}
	calendario, err = NuovoCalendarioTerminale(&bytes.Buffer{}, percorso)
	if err != nil || !calendario.mostraFineSettimana {
		t.Fatalf("preferenza attivata non ricaricata: calendario=%#v, errore=%v", calendario, err)
	}
	if err := calendario.alternaFineSettimana(); err != nil {
		t.Fatal(err)
	}
	calendario, err = NuovoCalendarioTerminale(&bytes.Buffer{}, percorso)
	if err != nil || calendario.mostraFineSettimana {
		t.Fatalf("preferenza disattivata non ricaricata: calendario=%#v, errore=%v", calendario, err)
	}
}

func TestSettimanaInizialeSceglieLaLezionePiuVicina(t *testing.T) {
	lezioni := []unimi.Lezione{
		{Data: time.Date(2026, 2, 2, 0, 0, 0, 0, time.UTC)},
		{Data: time.Date(2026, 9, 15, 0, 0, 0, 0, time.UTC)},
	}
	riferimento := time.Date(2026, 8, 23, 15, 0, 0, 0, time.Local)
	ottenuta := settimanaIniziale(lezioni, riferimento)
	attesa := time.Date(2026, 9, 14, 0, 0, 0, 0, time.UTC)
	if !ottenuta.Equal(attesa) {
		t.Fatalf("settimana iniziale = %s, attesa %s", ottenuta, attesa)
	}
}

func TestOrdinaEDeduplicaLezioni(t *testing.T) {
	giorno := time.Date(2026, 9, 14, 0, 0, 0, 0, time.UTC)
	lezioni := []unimi.Lezione{
		{ID: "2", Data: giorno, OraInizio: "10:30"},
		{ID: "1", Data: giorno, OraInizio: "08:30"},
		{ID: "1", Data: giorno, OraInizio: "08:30"},
	}
	ottenute := ordinaEDeduplica(lezioni)
	if len(ottenute) != 2 || ottenute[0].ID != "1" || ottenute[1].ID != "2" {
		t.Fatalf("lezioni ordinate inattese: %#v", ottenute)
	}
}

func TestNomeInsegnamentoVaACapoEdHaUnColoreStabile(t *testing.T) {
	righe := mandaACapo("Ingegneria del software", 10)
	attese := []string{"Ingegneria", "del", "software"}
	if strings.Join(righe, "|") != strings.Join(attese, "|") {
		t.Fatalf("a capo inatteso: %#v", righe)
	}

	primo := coloreInsegnamento("Ingegneria del software")
	secondo := coloreInsegnamento("Ingegneria del software")
	if primo == 0 || primo != secondo {
		t.Fatalf("colore non stabile: %d, %d", primo, secondo)
	}
}

func verificaDimensioniRender(t *testing.T, render string, larghezza, altezza int) {
	t.Helper()
	righe := strings.Split(strings.TrimSuffix(render, "\n"), "\n")
	if len(righe) > altezza {
		t.Fatalf("il render usa %d righe, massimo %d:\n%s", len(righe), altezza, render)
	}
	for indice, riga := range righe {
		rigaVisibile := sequenzaColoreANSI.ReplaceAllString(riga, "")
		if utf8.RuneCountInString(rigaVisibile) > larghezza {
			t.Fatalf("riga %d larga %d caratteri, massimo %d: %q", indice+1, utf8.RuneCountInString(rigaVisibile), larghezza, rigaVisibile)
		}
	}
}
