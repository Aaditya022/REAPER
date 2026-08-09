package deploy

import (
	"fmt"

	"github.com/charmbracelet/lipgloss"
)

var (
	titleStyle   = lipgloss.NewStyle().Foreground(lipgloss.Color("#FFD700")).Bold(true)
	accentStyle  = lipgloss.NewStyle().Foreground(lipgloss.Color("#00FA9A")).Bold(true)
	dividerStyle = lipgloss.NewStyle().Foreground(lipgloss.Color("#00CED1")).Bold(true)
	errorStyle   = lipgloss.NewStyle().Foreground(lipgloss.Color("1")).Bold(true)
)

// PrintDeployStart is printed once the deployment has been accepted.
func PrintDeployStart() {
	fmt.Println(dividerStyle.Render("\nDeploying to Zerops"))
}

// ShowStatus prints a line whenever the deployment status changes.
func ShowStatus(s DeploymentStatusResponse) {
	if s.Status == string(StatusFailed) {
		line := errorStyle.Render("✗ Deployment failed")
		if s.ErrorCode != "" {
			line += " [" + s.ErrorCode + "]"
		}
		if s.Message != "" {
			line += ": " + s.Message
		}
		fmt.Println(line)
		return
	}
	label := statusLabel(s.Status)
	if s.Message != "" {
		label = s.Message
	}
	fmt.Println(accentStyle.Render("●") + " " + label)
}

// PrintFinalHealthy is printed when the deployment reaches HEALTHY.
func PrintFinalHealthy(liveURL string) {
	if liveURL != "" {
		fmt.Println(accentStyle.Render("✓ Deployed successfully: ") + titleStyle.Render(liveURL))
	} else {
		fmt.Println(accentStyle.Render("✓ Deployed successfully"))
	}
}

func statusLabel(s string) string {
	switch s {
	case string(StatusPending):
		return "Pending"
	case string(StatusAnalyzing):
		return "Analyzing project stack"
	case string(StatusConfiguring):
		return "Preparing environment & config"
	case string(StatusDeploying):
		return "Deploying to Zerops"
	case string(StatusHealthChecking):
		return "Checking live URL"
	case string(StatusHealthy):
		return "Deployment healthy"
	case string(StatusFailed):
		return "Deployment failed"
	default:
		return s
	}
}
