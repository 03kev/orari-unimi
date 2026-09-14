package tui

import (
	"errors"
	"fmt"
	"io"

	"github.com/eiannone/keyboard"
)

// Selettore astrae una scelta interattiva. -1 indica il ritorno alla schermata precedente.
type Selettore interface {
	Scegli(titolo string, opzioni []string) (int, error)
}

// SelettoreTastiera gestisce frecce e comandi Vim nella stessa sessione
// keyboard usata dal lettore di testo e dal calendario.
type SelettoreTastiera struct {
	uscita             io.Writer
	percorsoPreferenze string
	navigazioneVim     bool
}

func NuovoSelettoreTastiera(uscita io.Writer, percorsoPreferenze string) (*SelettoreTastiera, error) {
	if uscita == nil {
		return nil, errors.New("l'output del selettore non può essere nil")
	}
	if percorsoPreferenze == "" {
		return nil, errors.New("il percorso delle preferenze è obbligatorio")
	}
	preferenze, err := caricaPreferenze(percorsoPreferenze)
	if err != nil {
		return nil, err
	}
	return &SelettoreTastiera{
		uscita: uscita, percorsoPreferenze: percorsoPreferenze,
		navigazioneVim: preferenze.navigazioneVimAttiva(),
	}, nil
}

func (s *SelettoreTastiera) alternaNavigazioneVim() error {
	attiva, err := alternaNavigazioneVim(s.percorsoPreferenze)
	if err != nil {
		return err
	}
	s.navigazioneVim = attiva
	return nil
}

func (s *SelettoreTastiera) Scegli(titolo string, opzioni []string) (int, error) {
	if len(opzioni) == 0 {
		return -1, errors.New("la select non contiene opzioni")
	}
	preferenze, err := caricaPreferenze(s.percorsoPreferenze)
	if err != nil {
		return -1, err
	}
	s.navigazioneVim = preferenze.navigazioneVimAttiva()
	if err := keyboard.Open(); err != nil {
		return -1, fmt.Errorf("apertura tastiera: %w", err)
	}
	fmt.Fprintln(s.uscita, "\n"+titolo)
	indice := 0
	mostraStato := false
	for {
		righe := s.renderizza(opzioni, indice, mostraStato)
		carattere, tasto, err := keyboard.GetKey()
		if err != nil {
			return -1, fmt.Errorf("lettura selezione: %w", err)
		}
		nuovoIndice, conferma, indietro := aggiornaSelezione(indice, len(opzioni), carattere, tasto, s.navigazioneVim)
		fmt.Fprintf(s.uscita, "\x1b[%dA\x1b[0J", righe)
		mostraStato = false
		if carattere == 'v' || carattere == 'V' {
			if err := s.alternaNavigazioneVim(); err != nil {
				return -1, err
			}
			mostraStato = true
			continue
		}
		if indietro {
			return -1, nil
		}
		if conferma {
			return indice, nil
		}
		indice = nuovoIndice
	}
}

func (s *SelettoreTastiera) renderizza(opzioni []string, indice int, mostraStato bool) int {
	lunghezza := 33
	if larghezza, _, err := dimensioniTerminale(); err == nil && larghezza > 4 && larghezza-3 < lunghezza {
		lunghezza = larghezza - 3
	}
	for i, opzione := range opzioni {
		opzione = tronca(opzione, lunghezza)
		if i == indice {
			fmt.Fprintf(s.uscita, "\x1b[36m> %s\x1b[0m\n", opzione)
		} else {
			fmt.Fprintf(s.uscita, "  %s\n", opzione)
		}
	}
	if !mostraStato {
		return len(opzioni)
	}
	stato := "sì"
	if !s.navigazioneVim {
		stato = "no"
	}
	fmt.Fprintf(s.uscita, "\n\x1b[90m  %s\x1b[0m\n", tronca("V navigazione Vim: "+stato, lunghezza))
	return len(opzioni) + 2
}

func aggiornaSelezione(indice, totale int, carattere rune, tasto keyboard.Key, navigazioneVim bool) (int, bool, bool) {
	switch {
	case tasto == keyboard.KeyArrowDown || (navigazioneVim && (carattere == 'j' || carattere == 'J')):
		return (indice + 1) % totale, false, false
	case tasto == keyboard.KeyArrowUp || (navigazioneVim && (carattere == 'k' || carattere == 'K')):
		return (indice - 1 + totale) % totale, false, false
	case tasto == keyboard.KeyEnter || tasto == keyboard.KeyArrowRight || (navigazioneVim && (carattere == 'l' || carattere == 'L')):
		return indice, true, false
	case tasto == keyboard.KeyEsc || tasto == keyboard.KeyArrowLeft || (navigazioneVim && (carattere == 'h' || carattere == 'H')):
		return indice, false, true
	default:
		return indice, false, false
	}
}
