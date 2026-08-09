package executorsfb

import (
	"fmt"
	"os"
	"os/exec"

	"github.com/shivasankaran18/STACKD/internal/fb/promptfb"
)

func CreateFrontend(dir string, frontend promptfb.FrontEndResponse) {

	switch frontend {
	case promptfb.ReactJS:
		CreateReactJS(dir)
	case promptfb.ReactTS:
		CreateReactTS(dir)
	case promptfb.Frontend_None:
		return
	default:
		return
	}
}

// createViteCommand builds the Vite scaffold command. The destination must
// never be passed to create-vite as an absolute path: npm/npx strips the
// leading "/", resolving it against the process cwd and leaving a partial
// project. Instead the command runs with its working directory set to the
// project directory and targets only the "frontend" basename, which resolves
// correctly for both absolute and relative project directories.
func createViteCommand(dir string, template string) *exec.Cmd {
	cmd := exec.Command("npx", "--yes", "create-vite@latest", "frontend", "--template", template)
	cmd.Dir = dir
	return cmd
}

func CreateReactJS(dir string) {
	path := dir + "/frontend"
	if err := os.MkdirAll(path, os.ModePerm); err != nil {
		fmt.Println("Error creating frontend directory:", err)
		os.Exit(1)
	}
	command := createViteCommand(dir, "react")

	err := command.Run()
	if err != nil {
		fmt.Println("Error creating ReactJS project:", err)
		os.Exit(1)
	}

}
func CreateReactTS(dir string) {
	command := createViteCommand(dir, "react-ts")

	err := command.Run()
	if err != nil {
		fmt.Println("Error creating ReactTS project:", err)
		os.Exit(1)
	}

}
