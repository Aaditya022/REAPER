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
	Long:  "REAPER is a CLI tool for scaffolding full-stack projects with configurable frontend, backend, and database stacks. Choose from React, Next.js, Django, Express, Prisma, Drizzle, PostgreSQL, MySQL, and MongoDB, then deploy straight to Zerops.",
	Args:  cobra.NoArgs,
	Run:   runCreate,
}

func Execute() {
	err := rootCmd.Execute()
	if err != nil {
		os.Exit(1)
	}
}

func init() {
	helpFunc := rootCmd.HelpFunc()
	rootCmd.SetHelpFunc(func(cmd *cobra.Command, args []string) {
		printBanner()
		helpFunc(cmd, args)
	})
}
