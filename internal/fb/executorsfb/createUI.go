package executorsfb

import (
	"fmt"
	"os"
	"os/exec"
	"text/template"

	"github.com/shivasankaran18/STACKD/internal/fb/promptfb"
	"github.com/shivasankaran18/STACKD/internal/templates"
)

func CreateUI(dir string, ui promptfb.UIResponse, frontend promptfb.FrontEndResponse) {
	switch ui {
	case promptfb.TailwindCSS:
		CreateTailwindCSS(dir, frontend)
	case promptfb.ShadCN:
		//CreateShadCN(dir)
		break
	case promptfb.UI_None:
		return
	default:
		return
	}
}

func CreateTailwindCSS(dir string, frontend promptfb.FrontEndResponse) {
	path := dir + "/frontend"
	error := os.MkdirAll(path, os.ModePerm)
	if error != nil {
		fmt.Println("Error creating TailwindCSS directory:", error)
		os.Exit(1)
		return
	}
	command := exec.Command("npm", "install", "tailwindcss", "@tailwindcss/vite")
	command.Dir = path

	err := command.Run()
	if err != nil {
		fmt.Println("Error installing TailwindCSS:", err)
		os.Exit(1)
		return
	}

	if frontend == promptfb.ReactJS {
		viteTmpl, err := template.New("vite.config.js").Parse(templates.ViteConfigTemplates)
		if err != nil {
			fmt.Println("Error parsing vite.config.js template:", err)
			os.Exit(1)
			return
		}
		viteFile, err := os.Create(path + "/vite.config.js")
		if err != nil {
			fmt.Println("Error creating vite.config.js file:", err)
			os.Exit(1)
			return
		}
		defer viteFile.Close()

		err = viteTmpl.Execute(viteFile, nil)
		if err != nil {
			fmt.Println("Error executing vite.config.js template:", err)
			os.Exit(1)
			return
		}

		indexCssTmpl, err := template.New("index.css").Parse(templates.IndexCSSTemplates)
		if err != nil {
			fmt.Println("Error parsing index.css template:", err)
			os.Exit(1)
			return
		}
		indexCssFile, err := os.Create(path + "/src/index.css")
		if err != nil {
			fmt.Println("Error creating index.css file:", err)
			os.Exit(1)
			return
		}
		defer indexCssFile.Close()
		err = indexCssTmpl.Execute(indexCssFile, nil)
		if err != nil {
			fmt.Println("Error executing index.css template:", err)
			os.Exit(1)
			return
		}

	}
}
