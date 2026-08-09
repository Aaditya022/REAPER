package cmd

import (
	"bytes"
	"io"
	"os"
	"strings"
	"testing"
)

func TestBareLaunchRunsCreateFlow(t *testing.T) {
	if rootCmd.Run == nil {
		t.Fatal("bare 'reaper' launch should run the create flow so the REAPER banner is displayed")
	}
	if createCmd.Run == nil {
		t.Fatal("'reaper create' should run the create flow")
	}
}

func TestHelpDoesNotRunCreateFlow(t *testing.T) {
	rootCmd.SetArgs([]string{"--help"})
	if err := rootCmd.Execute(); err != nil {
		t.Fatalf("'reaper --help' returned an error: %v", err)
	}
	createCmd.SetArgs([]string{"create", "--help"})
	if err := createCmd.Execute(); err != nil {
		t.Fatalf("'reaper create --help' returned an error: %v", err)
	}
}

func TestHelpShowsBanner(t *testing.T) {
	old := os.Stdout
	r, w, _ := os.Pipe()
	os.Stdout = w
	rootCmd.SetArgs([]string{"--help"})
	if err := rootCmd.Execute(); err != nil {
		os.Stdout = old
		t.Fatalf("'reaper --help' returned an error: %v", err)
	}
	w.Close()
	var buf bytes.Buffer
	io.Copy(&buf, r)
	os.Stdout = old
	out := buf.String()
	if !strings.Contains(out, "█") {
		t.Fatalf("'reaper --help' did not print the REAPER ASCII banner")
	}
	if strings.Contains(out, "shiva") {
		t.Fatalf("'reaper --help' still contains the placeholder description text")
	}
}
