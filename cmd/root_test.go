package cmd

import "testing"

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
