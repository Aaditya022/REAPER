package executorsfb

import (
	"path/filepath"
	"strings"
	"testing"
)

// TestCreateViteCommandAbsoluteProjectDir is a regression test for a release
// blocker: passing the destination as an absolute path to npm/npx create-vite
// strips the leading "/", scaffolding into cwd+path and leaving a partial
// project (the CLI then exits 1). The command must instead run with its
// working directory set to the project directory and target only the
// "frontend" basename, which resolves correctly for both absolute and relative
// project directories.
func TestCreateViteCommandAbsoluteProjectDir(t *testing.T) {
	abs := filepath.Join(t.TempDir(), "my-abs-project")

	for _, template := range []string{"react", "react-ts"} {
		cmd := createViteCommand(abs, template)

		if cmd.Dir != abs {
			t.Fatalf("template %q: working dir = %q, want %q", template, cmd.Dir, abs)
		}
		if len(cmd.Args) != 6 {
			t.Fatalf("template %q: unexpected args: %v", template, cmd.Args)
		}
		if cmd.Args[0] != "npx" || !strings.Contains(cmd.Args[2], "create-vite") {
			t.Fatalf("template %q: expected npx create-vite invocation, got args: %v", template, cmd.Args)
		}
		if cmd.Args[3] != "frontend" {
			t.Fatalf("template %q: expected relative target \"frontend\", got %q", template, cmd.Args[3])
		}
	}
}

// TestCreateViteCommandRelativeProjectDir guards against regressions for the
// still-supported relative directory form.
func TestCreateViteCommandRelativeProjectDir(t *testing.T) {
	cmd := createViteCommand("./some-demo", "react")

	if cmd.Dir != "./some-demo" {
		t.Fatalf("working dir = %q, want %q", cmd.Dir, "./some-demo")
	}
	if cmd.Args[3] != "frontend" {
		t.Fatalf("expected relative target \"frontend\", got %q", cmd.Args[3])
	}
}
