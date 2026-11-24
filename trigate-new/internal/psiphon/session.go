// Package psiphon supervises the official Psiphon ConsoleClient in proxy mode.
// The official client, its configuration and server entries must be supplied
// separately. This package is not a system VPN.
package psiphon

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"sync"
)

type State string

const (
	Stopped    State = "stopped"
	Starting   State = "starting"
	Connecting State = "connecting"
	Connected  State = "connected"
	Failed     State = "failed"
)

type Status struct {
	State     State `json:"state"`
	SocksPort int   `json:"socks_port,omitempty"`
	// Message is deliberately generic: diagnostic notices may contain network
	// identifiers or credentials and are never copied to the UI.
	Message string `json:"message,omitempty"`
}

type Config struct{ Executable, ConfigFile, ServerListFile, DataDirectory string }

type Session struct {
	mu      sync.Mutex
	state   Status
	cancel  context.CancelFunc
	done    chan struct{}
	updates chan Status
}

func New() *Session               { return &Session{state: Status{State: Stopped}, updates: make(chan Status, 16)} }
func (s *Session) Status() Status { s.mu.Lock(); defer s.mu.Unlock(); return s.state }

// Updates is a best-effort notification stream; Status is authoritative.
func (s *Session) Updates() <-chan Status { return s.updates }
func (s *Session) set(status Status) {
	s.mu.Lock()
	s.state = status
	s.mu.Unlock()
	select {
	case s.updates <- status:
	default:
	}
}

// Start launches a separately obtained official ConsoleClient, using a supplied
// Psiphon configuration and (optionally) server-entry list. ListenInterface is
// constrained to the client's loopback default. Startup is asynchronous.
func (s *Session) Start(parent context.Context, c Config) error {
	if err := validate(c); err != nil {
		return err
	}
	s.mu.Lock()
	if s.done != nil {
		s.mu.Unlock()
		return errors.New("session already running or stopping")
	}
	ctx, cancel := context.WithCancel(parent)
	done := make(chan struct{})
	s.cancel = cancel
	s.done = done
	s.state = Status{State: Starting}
	s.mu.Unlock()
	select {
	case s.updates <- Status{State: Starting}:
	default:
	}
	go s.run(ctx, c, done)
	return nil
}

func validate(c Config) error {
	if c.Executable == "" || c.ConfigFile == "" || c.DataDirectory == "" {
		return errors.New("executable, config file and data directory are required")
	}
	f, err := os.Open(c.ConfigFile)
	if err != nil {
		return fmt.Errorf("open config: %w", err)
	}
	defer f.Close()
	// Limit total JSON size; reject extra JSON objects and oversized files.
	data, err := io.ReadAll(io.LimitReader(f, 1024*1024+1))
	if err != nil || len(data) > 1024*1024 {
		return errors.New("cannot read config or config exceeds 1 MiB")
	}
	var cfg struct {
		ListenInterface string `json:"ListenInterface"`
	}
	if err = json.Unmarshal(data, &cfg); err != nil {
		return errors.New("invalid Psiphon configuration JSON")
	}
	if cfg.ListenInterface != "" {
		return errors.New("ListenInterface must be omitted or empty to keep proxy on loopback")
	}
	return nil
}

func (s *Session) run(ctx context.Context, c Config, done chan struct{}) {
	defer close(done)
	args := []string{"-config", c.ConfigFile, "-dataRootDirectory", c.DataDirectory}
	if c.ServerListFile != "" {
		args = append(args, "-serverList", c.ServerListFile)
	}
	cmd := exec.CommandContext(ctx, c.Executable, args...)
	stderr, err := cmd.StderrPipe()
	if err != nil {
		s.finish(ctx, "Cannot create notice pipe")
		return
	}
	cmd.Stdout = io.Discard
	if err = cmd.Start(); err != nil {
		s.finish(ctx, "Cannot start Psiphon client")
		return
	}
	s.set(Status{State: Connecting})
	scanner := bufio.NewScanner(stderr)
	scanner.Buffer(make([]byte, 4096), 2*1024*1024)
	port := 0
	tunnels := 0
	for scanner.Scan() {
		var n struct {
			Type string          `json:"noticeType"`
			Data json.RawMessage `json:"data"`
		}
		if json.Unmarshal(scanner.Bytes(), &n) != nil {
			continue
		}
		switch n.Type {
		case "ListeningSocksProxyPort":
			var d struct {
				Port int `json:"port"`
			}
			if json.Unmarshal(n.Data, &d) == nil && d.Port > 0 && d.Port <= 65535 {
				port = d.Port
			}
		case "Tunnels":
			var d struct {
				Count int `json:"count"`
			}
			if json.Unmarshal(n.Data, &d) == nil {
				tunnels = d.Count
			}
		case "SocksProxyPortInUse":
			port = 0
		}
		state := Connecting
		if tunnels > 0 && port > 0 {
			state = Connected
		}
		current := s.Status()
		next := Status{State: state, SocksPort: port}
		if current != next {
			s.set(next)
		}
	}
	scanErr := scanner.Err()
	// The child may block writing notices after a scanner error. Stop it before Wait.
	if scanErr != nil && cmd.Process != nil {
		_ = cmd.Process.Kill()
	}
	_ = cmd.Wait()
	s.finish(ctx, "Psiphon client stopped")
}

func (s *Session) finish(ctx context.Context, message string) {
	s.mu.Lock()
	if ctx.Err() != nil {
		s.state = Status{State: Stopped}
	} else {
		s.state = Status{State: Failed, Message: message}
	}
	status := s.state
	s.cancel = nil
	s.done = nil
	s.mu.Unlock()
	select {
	case s.updates <- status:
	default:
	}
}

// Stop waits until the child process is gone or ctx expires. On Windows the
// standard library kills the process on cancellation rather than sending SIGINT.
func (s *Session) Stop(ctx context.Context) error {
	s.mu.Lock()
	cancel, done := s.cancel, s.done
	s.mu.Unlock()
	if done == nil {
		return nil
	}
	cancel()
	select {
	case <-done:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}
