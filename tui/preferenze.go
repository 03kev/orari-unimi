package tui

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
)

type preferenzeApplicazione struct {
	MostraFineSettimana bool  `json:"mostra_fine_settimana"`
	NavigazioneVim      *bool `json:"navigazione_vim,omitempty"`
}

// In assenza della nuova chiave, le scorciatoie Vim restano attive.
func (p preferenzeApplicazione) navigazioneVimAttiva() bool {
	return p.NavigazioneVim == nil || *p.NavigazioneVim
}

func alternaNavigazioneVim(percorso string) (bool, error) {
	preferenze, err := caricaPreferenze(percorso)
	if err != nil {
		return false, err
	}
	attiva := !preferenze.navigazioneVimAttiva()
	preferenze.NavigazioneVim = &attiva
	if err := salvaPreferenze(percorso, preferenze); err != nil {
		return false, err
	}
	return attiva, nil
}

func caricaPreferenze(percorso string) (preferenzeApplicazione, error) {
	dati, err := os.ReadFile(percorso)
	if errors.Is(err, os.ErrNotExist) {
		return preferenzeApplicazione{}, nil
	}
	if err != nil {
		return preferenzeApplicazione{}, fmt.Errorf("lettura preferenze: %w", err)
	}
	var preferenze preferenzeApplicazione
	if err := json.Unmarshal(dati, &preferenze); err != nil {
		return preferenzeApplicazione{}, fmt.Errorf("decodifica preferenze: %w", err)
	}
	return preferenze, nil
}

func salvaPreferenze(percorso string, preferenze preferenzeApplicazione) error {
	if err := os.MkdirAll(filepath.Dir(percorso), 0o700); err != nil {
		return fmt.Errorf("creazione cartella preferenze: %w", err)
	}
	dati, err := json.MarshalIndent(preferenze, "", "  ")
	if err != nil {
		return fmt.Errorf("codifica preferenze calendario: %w", err)
	}
	dati = append(dati, '\n')
	if err := os.WriteFile(percorso, dati, 0o600); err != nil {
		return fmt.Errorf("salvataggio preferenze: %w", err)
	}
	return nil
}
