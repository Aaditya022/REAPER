/*
Copyright © 2025 NAME HERE <EMAIL ADDRESS>
*/
package cmd

import (
	"github.com/spf13/cobra"
	"os"
)

var rootCmd = &cobra.Command{
	Use:   "reaper",
	Short: "REAPER - Full Stack Project Generator",
	Long:  "REAPER is a CLI tool to scaffold full stack projects with various configurations.It supports multiple front-end and back-end frameworks and  database configurations shiva poda",
}

func Execute() {
	err := rootCmd.Execute()
	if err != nil {
		os.Exit(1)
	}
}

func init() {

	rootCmd.Flags().BoolP("toggle", "t", false, "Help message for toggle")
}
