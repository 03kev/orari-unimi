//go:build windows

package tui

// ChiudiTastiera su Windows è intenzionalmente un no-op: la versione di
// keyboard usata dai menu attende un ulteriore evento durante Close. Windows
// rilascia l'handle della console alla normale terminazione del processo.
func ChiudiTastiera() error {
	return nil
}
