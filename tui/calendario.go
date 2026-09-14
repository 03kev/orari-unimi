package tui

import (
	"errors"
	"fmt"
	"io"
	"sort"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/eiannone/keyboard"

	"orari-unimi/unimi"
)

const (
	// Con nove caratteri per giorno entrano nome, data e contenuto compatto:
	// la griglia resta quindi utilizzabile anche nel classico terminale da 80 colonne.
	larghezzaMinimaGriglia = 72
	larghezzaFallback      = 100
	altezzaFallback        = 30
)

var nomiGiorni = [...]string{"Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"}

var tavolozzaInsegnamenti = [...]int{39, 45, 75, 81, 111, 141, 177, 207, 208, 214}

type rigaCella struct {
	testo  string
	colore int
}

// VisualizzatoreCalendario mostra una collezione di lezioni settimana per
// settimana.
type VisualizzatoreCalendario interface {
	Mostra(titolo string, lezioni []unimi.Lezione) error
}

// CalendarioTerminale adatta automaticamente il calendario alle dimensioni
// correnti della finestra del terminale.
type CalendarioTerminale struct {
	uscita              io.Writer
	ora                 func() time.Time
	percorsoPreferenze  string
	mostraFineSettimana bool
}

func NuovoCalendarioTerminale(uscita io.Writer, percorsoPreferenze string) (*CalendarioTerminale, error) {
	if uscita == nil {
		return nil, errors.New("l'output del calendario non può essere nil")
	}
	if percorsoPreferenze == "" {
		return nil, errors.New("il percorso delle preferenze è obbligatorio")
	}
	preferenze, err := caricaPreferenzeCalendario(percorsoPreferenze)
	if err != nil {
		return nil, err
	}
	return &CalendarioTerminale{
		uscita: uscita, ora: time.Now, percorsoPreferenze: percorsoPreferenze,
		mostraFineSettimana: preferenze.MostraFineSettimana,
	}, nil
}

func (c *CalendarioTerminale) alternaFineSettimana() error {
	preferenze := preferenzeCalendario{MostraFineSettimana: !c.mostraFineSettimana}
	if err := salvaPreferenzeCalendario(c.percorsoPreferenze, preferenze); err != nil {
		return err
	}
	c.mostraFineSettimana = preferenze.MostraFineSettimana
	return nil
}

// Mostra gestisce A/D (navigazione), W (fine settimana) e Q (ritorno).
func (c *CalendarioTerminale) Mostra(titolo string, lezioni []unimi.Lezione) error {
	lezioni = ordinaEDeduplica(lezioni)
	if len(lezioni) == 0 {
		return nil
	}
	settimana := settimanaIniziale(lezioni, c.ora())
	for {
		larghezza, altezza, err := dimensioniTerminale()
		if err != nil || larghezza < 30 || altezza < 10 {
			larghezza, altezza = larghezzaFallback, altezzaFallback
		}
		fmt.Fprint(c.uscita, "\x1b[2J\x1b[H")
		fmt.Fprint(c.uscita, renderCalendario(titolo, lezioni, settimana, larghezza, altezza, c.mostraFineSettimana))

		carattere, tasto, err := keyboard.GetKey()
		if err != nil {
			return fmt.Errorf("lettura comandi calendario: %w", err)
		}
		switch {
		case tasto == keyboard.KeyArrowLeft || carattere == 'a' || carattere == 'A':
			settimana = settimana.AddDate(0, 0, -7)
		case tasto == keyboard.KeyArrowRight || carattere == 'd' || carattere == 'D':
			settimana = settimana.AddDate(0, 0, 7)
		case carattere == 'w' || carattere == 'W':
			if err := c.alternaFineSettimana(); err != nil {
				return err
			}
		case tasto == keyboard.KeyEsc || carattere == 'q' || carattere == 'Q':
			fmt.Fprint(c.uscita, "\x1b[2J\x1b[H")
			return nil
		}
	}
}

func renderCalendario(titolo string, lezioni []unimi.Lezione, settimana time.Time, larghezza, altezza int, mostraFineSettimana bool) string {
	if larghezza < 30 {
		larghezza = 30
	}
	if altezza < 10 {
		altezza = 10
	}
	settimana = inizioSettimana(settimana)
	giorni := raggruppaSettimana(lezioni, settimana)
	numeroGiorni := 5
	statoFineSettimana := "no"
	if mostraFineSettimana {
		numeroGiorni = 7
		statoFineSettimana = "sì"
	}
	intestazione := fmt.Sprintf("%s | %s - %s", titolo, settimana.Format("02/01/2006"), settimana.AddDate(0, 0, numeroGiorni-1).Format("02/01/2006"))
	comandi := fmt.Sprintf("A/← precedente   D/→ successiva   W weekend: %s   Q/Esc menu", statoFineSettimana)
	if larghezza < 70 {
		comandi = fmt.Sprintf("A/← prec. D/→ succ. W weekend: %s Q/Esc", statoFineSettimana)
	}
	if larghezza < 50 {
		comandi = fmt.Sprintf("A/← D/→ W:%s Q/Esc", statoFineSettimana)
	}

	var corpo string
	if larghezza >= larghezzaMinimaGriglia && altezza >= 12 {
		corpo = renderGriglia(giorni, settimana, larghezza, altezza-2, numeroGiorni)
	} else {
		corpo = renderAgendaCompatta(giorni, settimana, larghezza, altezza-2, numeroGiorni)
	}
	return tronca(intestazione, larghezza) + "\n" + corpo + tronca(comandi, larghezza) + "\n"
}

func renderGriglia(giorni [7][]unimi.Lezione, settimana time.Time, larghezza, altezza, numeroGiorni int) string {
	larghezzaColonna := (larghezza - numeroGiorni - 1) / numeroGiorni
	rigaOrizzontale := "+" + strings.Repeat(strings.Repeat("-", larghezzaColonna)+"+", numeroGiorni)

	var risultato strings.Builder
	risultato.WriteString(rigaOrizzontale)
	risultato.WriteByte('\n')
	risultato.WriteByte('|')
	for giorno := 0; giorno < numeroGiorni; giorno++ {
		data := settimana.AddDate(0, 0, giorno)
		intestazione := fmt.Sprintf("%s %s", nomiGiorni[giorno], data.Format("02/01"))
		risultato.WriteString(centra(intestazione, larghezzaColonna))
		risultato.WriteByte('|')
	}
	risultato.WriteByte('\n')
	risultato.WriteString(rigaOrizzontale)
	risultato.WriteByte('\n')

	righeDisponibili := altezza - 4 // intestazione, due bordi e bordo finale
	if righeDisponibili < 2 {
		righeDisponibili = 2
	}
	righeGiorni := make([][]rigaCella, numeroGiorni)
	massimoRighe := 1
	for giorno := 0; giorno < numeroGiorni; giorno++ {
		righeGiorni[giorno] = righeCella(giorni[giorno], larghezzaColonna, righeDisponibili)
		if len(righeGiorni[giorno]) > massimoRighe {
			massimoRighe = len(righeGiorni[giorno])
		}
	}
	for riga := 0; riga < massimoRighe; riga++ {
		risultato.WriteByte('|')
		for giorno := 0; giorno < numeroGiorni; giorno++ {
			contenuto := rigaCella{}
			if riga < len(righeGiorni[giorno]) {
				contenuto = righeGiorni[giorno][riga]
			}
			risultato.WriteString(colora(completa(contenuto.testo, larghezzaColonna), contenuto.colore))
			risultato.WriteByte('|')
		}
		risultato.WriteByte('\n')
	}
	risultato.WriteString(rigaOrizzontale)
	risultato.WriteByte('\n')

	return risultato.String()
}

func righeCella(lezioni []unimi.Lezione, larghezza, massimo int) []rigaCella {
	if len(lezioni) == 0 {
		return []rigaCella{{testo: "-"}}
	}

	eventi := make([][]rigaCella, 0, len(lezioni))
	for _, lezione := range lezioni {
		colore := coloreInsegnamento(lezione.Insegnamento)
		orario := lezione.OraInizio + "-" + lezione.OraFine
		if larghezza < utf8.RuneCountInString(orario) {
			orario = lezione.OraInizio
		}
		if lezione.Annullata {
			orario = "X " + orario
		}
		descrizione := lezione.Insegnamento
		if strings.TrimSpace(descrizione) == "" {
			descrizione = "(senza nome)"
		}
		righeEvento := []rigaCella{{testo: tronca(orario, larghezza), colore: colore}}
		for _, riga := range mandaACapo(descrizione, larghezza) {
			righeEvento = append(righeEvento, rigaCella{testo: riga, colore: colore})
		}
		if lezione.Aula != "" {
			righeEvento = append(righeEvento, rigaCella{testo: tronca("@ "+lezione.Aula, larghezza), colore: colore})
		}
		eventi = append(eventi, righeEvento)
	}

	righe := make([]rigaCella, 0, massimo)
	eventiMostrati := 0
	for indice, evento := range eventi {
		riservaIndicatore := 0
		if indice < len(eventi)-1 {
			riservaIndicatore = 1
		}
		if len(righe)+len(evento)+riservaIndicatore > massimo {
			break
		}
		righe = append(righe, evento...)
		eventiMostrati++
	}
	if eventiMostrati == 0 {
		// Anche con una finestra estremamente bassa mostriamo almeno l'inizio
		// del primo evento, conservando l'ultima riga per l'indicatore.
		quante := massimo - 1
		if quante < 1 {
			quante = 1
		}
		if quante > len(eventi[0]) {
			quante = len(eventi[0])
		}
		righe = append(righe, eventi[0][:quante]...)
		eventiMostrati = 1
	}
	if eventiMostrati < len(eventi) && len(righe) < massimo {
		righe = append(righe, rigaCella{testo: fmt.Sprintf("+%d altre", len(eventi)-eventiMostrati)})
	}
	return righe
}

func mandaACapo(testo string, larghezza int) []string {
	parole := strings.Fields(testo)
	if len(parole) == 0 || larghezza <= 0 {
		return nil
	}
	righe := make([]string, 0, len(parole))
	corrente := ""
	for _, parola := range parole {
		for utf8.RuneCountInString(parola) > larghezza {
			if corrente != "" {
				righe = append(righe, corrente)
				corrente = ""
			}
			rune := []rune(parola)
			righe = append(righe, string(rune[:larghezza]))
			parola = string(rune[larghezza:])
		}
		candidata := parola
		if corrente != "" {
			candidata = corrente + " " + parola
		}
		if utf8.RuneCountInString(candidata) <= larghezza {
			corrente = candidata
			continue
		}
		righe = append(righe, corrente)
		corrente = parola
	}
	if corrente != "" {
		righe = append(righe, corrente)
	}
	return righe
}

func coloreInsegnamento(insegnamento string) int {
	// FNV-1a produce una distribuzione pseudo-casuale ma ripetibile: il colore
	// non cambia quando si passa da una settimana all'altra.
	var hash uint32 = 2166136261
	for _, valore := range []byte(strings.ToLower(strings.TrimSpace(insegnamento))) {
		hash ^= uint32(valore)
		hash *= 16777619
	}
	return tavolozzaInsegnamenti[int(hash%uint32(len(tavolozzaInsegnamenti)))]
}

func colora(testo string, colore int) string {
	if colore == 0 {
		return testo
	}
	return fmt.Sprintf("\x1b[38;5;%dm%s\x1b[0m", colore, testo)
}

func renderAgendaCompatta(giorni [7][]unimi.Lezione, settimana time.Time, larghezza, altezza, numeroGiorni int) string {
	righePerGiorno := altezza / numeroGiorni
	if righePerGiorno < 1 {
		righePerGiorno = 1
	}
	var risultato strings.Builder
	for giorno := 0; giorno < numeroGiorni; giorno++ {
		data := settimana.AddDate(0, 0, giorno)
		prefisso := fmt.Sprintf("%s %s | ", nomiGiorni[giorno], data.Format("02/01"))
		lezioni := giorni[giorno]
		if len(lezioni) == 0 {
			risultato.WriteString(tronca(prefisso+"-", larghezza))
			risultato.WriteByte('\n')
			continue
		}
		quante := righePerGiorno
		if quante > len(lezioni) {
			quante = len(lezioni)
		}
		if righePerGiorno == 1 {
			testo := prefisso + descrizioneCompatta(lezioni[0])
			if len(lezioni) > 1 {
				testo += fmt.Sprintf(" (+%d)", len(lezioni)-1)
			}
			risultato.WriteString(colora(tronca(testo, larghezza), coloreInsegnamento(lezioni[0].Insegnamento)))
			risultato.WriteByte('\n')
			continue
		}
		if len(lezioni) > righePerGiorno {
			quante = righePerGiorno - 1
		}
		for indice := 0; indice < quante; indice++ {
			inizio := strings.Repeat(" ", utf8.RuneCountInString(prefisso))
			if indice == 0 {
				inizio = prefisso
			}
			risultato.WriteString(colora(
				tronca(inizio+descrizioneCompatta(lezioni[indice]), larghezza),
				coloreInsegnamento(lezioni[indice].Insegnamento),
			))
			risultato.WriteByte('\n')
		}
		if len(lezioni) > quante {
			risultato.WriteString(tronca(strings.Repeat(" ", utf8.RuneCountInString(prefisso))+fmt.Sprintf("+%d altre", len(lezioni)-quante), larghezza))
			risultato.WriteByte('\n')
		}
	}
	return risultato.String()
}

func descrizioneCompatta(lezione unimi.Lezione) string {
	stato := ""
	if lezione.Annullata {
		stato = "[ANNULLATA] "
	}
	testo := fmt.Sprintf("%s%s-%s %s", stato, lezione.OraInizio, lezione.OraFine, lezione.Insegnamento)
	if lezione.Aula != "" {
		testo += " @ " + lezione.Aula
	}
	return testo
}

func raggruppaSettimana(lezioni []unimi.Lezione, settimana time.Time) [7][]unimi.Lezione {
	var giorni [7][]unimi.Lezione
	inizio := dataUTC(inizioSettimana(settimana))
	fine := inizio.AddDate(0, 0, 7)
	for _, lezione := range lezioni {
		data := dataUTC(lezione.Data)
		if data.Before(inizio) || !data.Before(fine) {
			continue
		}
		indice := int(data.Sub(inizio).Hours() / 24)
		giorni[indice] = append(giorni[indice], lezione)
	}
	return giorni
}

func settimanaIniziale(lezioni []unimi.Lezione, riferimento time.Time) time.Time {
	riferimento = dataUTC(riferimento)
	settimanaRiferimento := inizioSettimana(riferimento)
	fineSettimana := settimanaRiferimento.AddDate(0, 0, 7)
	piuVicina := dataUTC(lezioni[0].Data)
	distanzaMinima := distanzaAssoluta(piuVicina, riferimento)
	for _, lezione := range lezioni {
		data := dataUTC(lezione.Data)
		if !data.Before(settimanaRiferimento) && data.Before(fineSettimana) {
			return settimanaRiferimento
		}
		distanza := distanzaAssoluta(data, riferimento)
		if distanza < distanzaMinima || (distanza == distanzaMinima && data.After(piuVicina)) {
			piuVicina, distanzaMinima = data, distanza
		}
	}
	return inizioSettimana(piuVicina)
}

func distanzaAssoluta(a, b time.Time) time.Duration {
	distanza := a.Sub(b)
	if distanza < 0 {
		return -distanza
	}
	return distanza
}

func inizioSettimana(data time.Time) time.Time {
	data = dataUTC(data)
	giorniDaLunedi := (int(data.Weekday()) + 6) % 7
	return data.AddDate(0, 0, -giorniDaLunedi)
}

func dataUTC(data time.Time) time.Time {
	return time.Date(data.Year(), data.Month(), data.Day(), 0, 0, 0, 0, time.UTC)
}

func ordinaEDeduplica(lezioni []unimi.Lezione) []unimi.Lezione {
	uniche := make([]unimi.Lezione, 0, len(lezioni))
	viste := make(map[string]struct{}, len(lezioni))
	for _, lezione := range lezioni {
		chiave := lezione.ID
		if chiave == "" {
			chiave = lezione.CodiceInsegnamento + "|" + lezione.Data.Format("2006-01-02") + "|" + lezione.OraInizio + "|" + lezione.Aula
		}
		if _, presente := viste[chiave]; presente {
			continue
		}
		viste[chiave] = struct{}{}
		uniche = append(uniche, lezione)
	}
	sort.SliceStable(uniche, func(i, j int) bool {
		if !uniche[i].Data.Equal(uniche[j].Data) {
			return uniche[i].Data.Before(uniche[j].Data)
		}
		return uniche[i].OraInizio < uniche[j].OraInizio
	})
	return uniche
}

func tronca(testo string, larghezza int) string {
	if larghezza <= 0 {
		return ""
	}
	rune := []rune(testo)
	if len(rune) <= larghezza {
		return testo
	}
	if larghezza == 1 {
		return "…"
	}
	return string(rune[:larghezza-1]) + "…"
}

func completa(testo string, larghezza int) string {
	testo = tronca(testo, larghezza)
	return testo + strings.Repeat(" ", larghezza-utf8.RuneCountInString(testo))
}

func centra(testo string, larghezza int) string {
	testo = tronca(testo, larghezza)
	spazi := larghezza - utf8.RuneCountInString(testo)
	sinistra := spazi / 2
	return strings.Repeat(" ", sinistra) + testo + strings.Repeat(" ", spazi-sinistra)
}
