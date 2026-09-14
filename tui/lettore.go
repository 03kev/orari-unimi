package tui

import (
	"errors"
	"fmt"
	"io"
	"strings"
	"unicode"

	"github.com/eiannone/keyboard"
)

// LettoreTesto legge una riga all'interno della sessione interattiva.
type LettoreTesto interface {
	Leggi(messaggio string) (string, error)
}

// LettoreTastiera usa la stessa sessione keyboard dei menu e del calendario.
// Questo evita conflitti tra la modalità raw e bufio/os.Stdin.
type LettoreTastiera struct {
	uscita io.Writer
}

func NuovoLettoreTastiera(uscita io.Writer) (*LettoreTastiera, error) {
	if uscita == nil {
		return nil, errors.New("l'output del lettore non può essere nil")
	}
	return &LettoreTastiera{uscita: uscita}, nil
}

func (l *LettoreTastiera) Leggi(messaggio string) (string, error) {
	fmt.Fprint(l.uscita, messaggio)
	caratteri := make([]rune, 0)
	for {
		carattere, tasto, err := keyboard.GetKey()
		if err != nil {
			return "", fmt.Errorf("lettura tastiera: %w", err)
		}
		switch tasto {
		case keyboard.KeyEnter:
			fmt.Fprintln(l.uscita)
			return strings.TrimSpace(string(caratteri)), nil
		case keyboard.KeyEsc:
			fmt.Fprintln(l.uscita)
			return "", nil
		case keyboard.KeyBackspace, keyboard.KeyBackspace2:
			if len(caratteri) > 0 {
				caratteri = caratteri[:len(caratteri)-1]
				fmt.Fprint(l.uscita, "\b \b")
			}
		case keyboard.KeySpace:
			caratteri = append(caratteri, ' ')
			fmt.Fprint(l.uscita, " ")
		case keyboard.KeyCtrlC:
			return "", io.EOF
		default:
			if carattere != 0 && unicode.IsPrint(carattere) {
				caratteri = append(caratteri, carattere)
				fmt.Fprint(l.uscita, string(carattere))
			}
		}
	}
}
