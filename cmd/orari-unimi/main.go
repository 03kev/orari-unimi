package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"path/filepath"

	"orari-unimi/tui"
	"orari-unimi/unimi"
)

func main() {
	tipo := flag.String("tipo", "", "stampa una lista senza aprire la TUI: anni, corsi, facolta, docenti o insegnamenti")
	anno := flag.String("anno", "", "codice dell'anno accademico, ad esempio 2026; se omesso usa il più recente")
	flag.Parse()

	client := unimi.NuovoClient()
	ctx, interrompi := signal.NotifyContext(context.Background(), os.Interrupt)
	defer interrompi()

	if *tipo == "" {
		avviaTUI(ctx, client)
		return
	}
	if *tipo == "anni" {
		anni, err := client.RecuperaAnniAccademici(ctx)
		terminaSeErrore(err)
		for _, valore := range anni {
			fmt.Printf("%s\t%s\n", valore.Codice, valore.Nome)
		}
		return
	}

	annoSelezionato := *anno
	if annoSelezionato == "" {
		anni, err := client.RecuperaAnniAccademici(ctx)
		terminaSeErrore(err)
		if len(anni) == 0 {
			log.Fatal("il portale non ha restituito anni accademici")
		}
		annoSelezionato = anni[0].Codice
	}

	switch *tipo {
	case "corsi":
		valori, err := client.RecuperaCorsi(ctx, annoSelezionato)
		terminaSeErrore(err)
		for _, valore := range valori {
			fmt.Printf("%s\t%s\n", valore.Codice, valore.Nome)
		}
	case "facolta":
		valori, err := client.RecuperaFacolta(ctx, annoSelezionato)
		terminaSeErrore(err)
		for _, valore := range valori {
			fmt.Printf("%s\t%s\n", valore.Codice, valore.Nome)
		}
	case "docenti":
		valori, err := client.RecuperaDocenti(ctx, annoSelezionato)
		terminaSeErrore(err)
		for _, valore := range valori {
			fmt.Printf("%s\t%s\n", valore.Codice, valore.Nome)
		}
	case "insegnamenti":
		valori, err := client.RecuperaInsegnamenti(ctx, annoSelezionato)
		terminaSeErrore(err)
		for _, valore := range valori {
			fmt.Printf("%s\t%s\n", valore.Codice, valore.Descrizione)
		}
	default:
		fmt.Fprintf(os.Stderr, "tipo %q non valido\n", *tipo)
		flag.Usage()
		os.Exit(2)
	}
}

func avviaTUI(ctx context.Context, client *unimi.Client) {
	defer tui.ChiudiTastiera()
	percorso := os.Getenv("ORARI_UNIMI_FILE")
	if percorso == "" {
		cartella, err := os.UserConfigDir()
		terminaSeErrore(err)
		percorso = filepath.Join(cartella, "orari-unimi", "insegnamenti.json")
	}
	archivio, err := tui.NuovoArchivioInsegnamenti(percorso)
	terminaSeErrore(err)
	lettore, err := tui.NuovoLettoreTastiera(os.Stdout)
	terminaSeErrore(err)
	percorsoPreferenze := filepath.Join(filepath.Dir(percorso), "preferenze.json")
	calendario, err := tui.NuovoCalendarioTerminale(os.Stdout, percorsoPreferenze)
	terminaSeErrore(err)
	selettore, err := tui.NuovoSelettoreTastiera(os.Stdout, percorsoPreferenze)
	terminaSeErrore(err)
	applicazione, err := tui.NuovaApplicazione(lettore, os.Stdout, client, archivio, selettore, calendario)
	terminaSeErrore(err)
	terminaSeErrore(applicazione.Esegui(ctx))
}

func terminaSeErrore(err error) {
	if err != nil {
		log.Fatal(err)
	}
}
