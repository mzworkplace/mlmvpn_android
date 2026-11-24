// Desktop development tool: supervises the separately supplied official
// Psiphon ConsoleClient. This is proxy mode, not the final GUI or system VPN.
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"time"
	"trigate-next/internal/psiphon"
)

func main() {
	exe := flag.String("client", "", "Path to official Psiphon ConsoleClient executable")
	config := flag.String("config", "", "Path to valid Psiphon configuration JSON")
	servers := flag.String("servers", "", "Optional path to server entry list")
	data := flag.String("data", "", "Directory for Psiphon data")
	flag.Parse()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()
	s := psiphon.New()
	err := s.Start(ctx, psiphon.Config{Executable: *exe, ConfigFile: *config, ServerListFile: *servers, DataDirectory: *data})
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	encoder := json.NewEncoder(os.Stdout)
	for {
		select {
		case <-ctx.Done():
			shutdown, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			_ = s.Stop(shutdown)
			cancel()
			_ = encoder.Encode(s.Status())
			return
		case <-s.Updates():
			st := s.Status()
			_ = encoder.Encode(st)
			if st.State == psiphon.Failed {
				os.Exit(1)
			}
		}
	}
}
