package tui

import (
	"bytes"
	"path/filepath"
	"testing"

	"github.com/eiannone/keyboard"
)

func TestNavigazioneVimNeiMenu(t *testing.T) {
	test := []struct {
		nome      string
		indice    int
		carattere rune
		tasto     keyboard.Key
		atteso    int
		conferma  bool
		indietro  bool
	}{
		{"j scende", 0, 'j', 0, 1, false, false},
		{"j ricomincia dall'alto", 2, 'j', 0, 0, false, false},
		{"k sale", 2, 'k', 0, 1, false, false},
		{"k ricomincia dal basso", 0, 'k', 0, 2, false, false},
		{"l conferma", 1, 'l', 0, 1, true, false},
		{"h torna", 1, 'h', 0, 1, false, true},
		{"freccia giu ancora valida", 0, 0, keyboard.KeyArrowDown, 1, false, false},
		{"invio ancora valido", 1, 0, keyboard.KeyEnter, 1, true, false},
		{"esc ancora valido", 1, 0, keyboard.KeyEsc, 1, false, true},
	}
	for _, tc := range test {
		t.Run(tc.nome, func(t *testing.T) {
			indice, conferma, indietro := aggiornaSelezione(tc.indice, 3, tc.carattere, tc.tasto, true)
			if indice != tc.atteso || conferma != tc.conferma || indietro != tc.indietro {
				t.Fatalf("indice=%d conferma=%v indietro=%v", indice, conferma, indietro)
			}
		})
	}
}

func TestNavigazioneVimDisattivataNeiMenu(t *testing.T) {
	for _, carattere := range "hjkl" {
		indice, conferma, indietro := aggiornaSelezione(1, 3, carattere, 0, false)
		if indice != 1 || conferma || indietro {
			t.Fatalf("%q ha agito con navigazione Vim disattivata", carattere)
		}
	}
	indice, _, _ := aggiornaSelezione(1, 3, 0, keyboard.KeyArrowDown, false)
	if indice != 2 {
		t.Fatal("le frecce devono restare attive")
	}
}

func TestToggleNavigazioneVimPersisteESalvaWeekend(t *testing.T) {
	percorso := filepath.Join(t.TempDir(), "preferenze.json")
	selettore, err := NuovoSelettoreTastiera(&bytes.Buffer{}, percorso)
	if err != nil || !selettore.navigazioneVim {
		t.Fatalf("navigazione Vim iniziale: selettore=%#v, errore=%v", selettore, err)
	}
	calendario, err := NuovoCalendarioTerminale(&bytes.Buffer{}, percorso)
	if err != nil {
		t.Fatal(err)
	}
	if err := calendario.alternaFineSettimana(); err != nil {
		t.Fatal(err)
	}
	if err := selettore.alternaNavigazioneVim(); err != nil {
		t.Fatal(err)
	}
	preferenze, err := caricaPreferenze(percorso)
	if err != nil || preferenze.navigazioneVimAttiva() || !preferenze.MostraFineSettimana {
		t.Fatalf("preferenze dopo toggle Vim: %#v, errore=%v", preferenze, err)
	}
	if err := calendario.alternaFineSettimana(); err != nil {
		t.Fatal(err)
	}
	selettore, err = NuovoSelettoreTastiera(&bytes.Buffer{}, percorso)
	if err != nil || selettore.navigazioneVim {
		t.Fatalf("navigazione Vim ricaricata: selettore=%#v, errore=%v", selettore, err)
	}
	if err := selettore.alternaNavigazioneVim(); err != nil {
		t.Fatal(err)
	}
	preferenze, err = caricaPreferenze(percorso)
	if err != nil || !preferenze.navigazioneVimAttiva() || preferenze.MostraFineSettimana {
		t.Fatalf("preferenze finali: %#v, errore=%v", preferenze, err)
	}
	if err := calendario.alternaNavigazioneVim(); err != nil {
		t.Fatal(err)
	}
	selettore, err = NuovoSelettoreTastiera(&bytes.Buffer{}, percorso)
	if err != nil || selettore.navigazioneVim {
		t.Fatalf("toggle dal calendario non ricaricato: selettore=%#v, errore=%v", selettore, err)
	}
}
