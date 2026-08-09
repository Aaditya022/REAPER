package deploy

import (
	"fmt"
	"os"
	"strings"

	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// confirmModel is a bubbletea yes/no selector. The cursor starts on "No" so
// pressing enter without thinking never triggers a deployment.
type confirmModel struct {
	cursor   int
	choices  []string
	selected bool
	result   string
	cancel   bool
}

func (m confirmModel) Init() tea.Cmd { return nil }

func (m confirmModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "ctrl+c", "q", "esc":
			m.cancel = true
			return m, tea.Quit
		case "up", "k":
			if m.cursor > 0 {
				m.cursor--
			}
		case "down", "j":
			if m.cursor < len(m.choices)-1 {
				m.cursor++
			}
		case "enter":
			m.selected = true
			m.result = m.choices[m.cursor]
			return m, tea.Quit
		}
	}
	return m, nil
}

func (m confirmModel) View() string {
	labelStyle := lipgloss.NewStyle().Foreground(lipgloss.Color("12")).Bold(true).Padding(0, 1)
	optionStyle := lipgloss.NewStyle().Foreground(lipgloss.Color("7")).Padding(0, 2)
	selectedStyle := lipgloss.NewStyle().Foreground(lipgloss.Color("15")).Background(lipgloss.Color("6")).Bold(true).Padding(0, 2).Border(lipgloss.RoundedBorder(), true).BorderForeground(lipgloss.Color("6"))
	borderStyle := lipgloss.NewStyle().Border(lipgloss.RoundedBorder()).BorderForeground(lipgloss.Color("12")).Padding(1, 2)

	out := labelStyle.Render("? Deploy this to Zerops now?") + "\n\n"
	var options string
	for i, choice := range m.choices {
		cursor := "  "
		style := optionStyle
		if m.cursor == i {
			cursor = "> "
			style = selectedStyle
		}
		options += style.Render(cursor+choice) + "\n"
	}
	out += borderStyle.Render(options)
	return out
}

// ConfirmDeploy asks whether to deploy. Cancel counts as "no".
func ConfirmDeploy() (bool, error) {
	m := confirmModel{choices: []string{"Yes", "No"}, cursor: 1}
	final, err := tea.NewProgram(m).Run()
	if err != nil {
		return false, fmt.Errorf("prompt failed: %w", err)
	}
	mod := final.(confirmModel)
	if mod.cancel || !mod.selected {
		return false, nil
	}
	return mod.result == "Yes", nil
}

// projectIDModel is a bubbletea text input for the Zerops project id.
type projectIDModel struct {
	input  textinput.Model
	cancel bool
}

func initialProjectIDModel() projectIDModel {
	ti := textinput.New()
	ti.Placeholder = "e.g. my-project"
	ti.Focus()
	ti.CharLimit = 128
	ti.Width = 30
	return projectIDModel{input: ti}
}

func (m projectIDModel) Init() tea.Cmd { return textinput.Blink }

func (m projectIDModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.String() {
		case "ctrl+c", "esc":
			m.cancel = true
			return m, tea.Quit
		case "enter":
			return m, tea.Quit
		}
	}
	var cmd tea.Cmd
	m.input, cmd = m.input.Update(msg)
	return m, cmd
}

func (m projectIDModel) View() string {
	title := lipgloss.NewStyle().Foreground(lipgloss.Color("#FFD700")).Bold(true).Render("🔑 Enter Zerops project ID")
	inputBox := lipgloss.NewStyle().Foreground(lipgloss.Color("#00FA9A")).Border(lipgloss.RoundedBorder()).Padding(0, 1).MarginBottom(1).Render(m.input.View())
	return fmt.Sprintf("%s\n\n%s", title, inputBox)
}

// ProjectID returns the Zerops project id from ZEROPS_PROJECT_ID, or asks
// interactively. A blank result (env missing and prompt cancelled/empty)
// skips deployment.
func ProjectID() (string, error) {
	if v := strings.TrimSpace(os.Getenv("ZEROPS_PROJECT_ID")); v != "" {
		return v, nil
	}
	m := initialProjectIDModel()
	final, err := tea.NewProgram(m).Run()
	if err != nil {
		return "", fmt.Errorf("prompt failed: %w", err)
	}
	mod := final.(projectIDModel)
	if mod.cancel {
		return "", nil
	}
	return strings.TrimSpace(mod.input.Value()), nil
}
