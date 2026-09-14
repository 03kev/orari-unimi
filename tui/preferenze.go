package tui

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
)

type preferenzeCalendario struct {
	MostraFineSettimana bool `json:"mostra_fine_settimana"`
}

func caricaPreferenzeCalendario(percorso string) (preferenzeCalendario, error) {
	dati, err := os.ReadFile(percorso)
	if errors.Is(err, os.ErrNotExist) {
		return preferenzeCalendario{}, nil
	}
	if err != nil {
		return preferenzeCalendario{}, fmt.Errorf("lettura preferenze calendario: %w", err)
	}
	var preferenze preferenzeCalendario
	if err := json.Unmarshal(dati, &preferenze); err != nil {
		return preferenzeCalendario{}, fmt.Errorf("decodifica preferenze calendario: %w", err)
	}
	return preferenze, nil
}

func salvaPreferenzeCalendario(percorso string, preferenze preferenzeCalendario) error {
	if err := os.MkdirAll(filepath.Dir(percorso), 0o700); err != nil {
		return fmt.Errorf("creazione cartella preferenze: %w", err)
	}
	dati, err := json.MarshalIndent(preferenze, "", "  ")
	if err != nil {
		return fmt.Errorf("codifica preferenze calendario: %w", err)
	}
	dati = append(dati, '\n')
	if err := os.WriteFile(percorso, dati, 0o600); err != nil {
		return fmt.Errorf("salvataggio preferenze calendario: %w", err)
	}
	return nil
}
