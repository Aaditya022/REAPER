package deploy

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"path/filepath"
	"time"
)

// DeploymentStatus mirrors the engine's deployment status enum.
type DeploymentStatus string

const (
	StatusPending        DeploymentStatus = "PENDING"
	StatusAnalyzing      DeploymentStatus = "ANALYZING"
	StatusConfiguring    DeploymentStatus = "CONFIGURING"
	StatusDeploying      DeploymentStatus = "DEPLOYING"
	StatusHealthChecking DeploymentStatus = "HEALTH_CHECKING"
	StatusHealthy        DeploymentStatus = "HEALTHY"
	StatusFailed         DeploymentStatus = "FAILED"
)

// IsTerminal reports whether the status ends the deployment lifecycle.
func IsTerminal(s DeploymentStatus) bool {
	return s == StatusHealthy || s == StatusFailed
}

// Default polling settings.
const (
	DefaultPollInterval = 2 * time.Second
	DefaultPollTimeout  = 10 * time.Minute
)

// PollStatus polls the engine until a terminal status, a timeout, or an error.
// onChange is invoked only when the status value changes from the previous poll.
func PollStatus(ctx context.Context, client *EngineClient, deploymentID string, interval, timeout time.Duration, onChange func(DeploymentStatusResponse)) (DeploymentStatusResponse, error) {
	if interval <= 0 {
		interval = DefaultPollInterval
	}
	if timeout <= 0 {
		timeout = DefaultPollTimeout
	}
	if onChange == nil {
		onChange = func(DeploymentStatusResponse) {}
	}

	timeoutTimer := time.NewTimer(timeout)
	defer timeoutTimer.Stop()
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	var lastStatus string
	for {
		resp, err := client.DeploymentStatus(ctx, deploymentID)
		if err != nil {
			return DeploymentStatusResponse{}, err
		}
		if resp.Status != lastStatus {
			onChange(resp)
			lastStatus = resp.Status
		}
		if IsTerminal(DeploymentStatus(resp.Status)) {
			return resp, nil
		}

		select {
		case <-ctx.Done():
			return DeploymentStatusResponse{}, ctx.Err()
		case <-timeoutTimer.C:
			return DeploymentStatusResponse{}, fmt.Errorf("deployment polling timed out after %s", timeout)
		case <-ticker.C:
		}
	}
}

// MaybeDeploy asks whether to deploy and, if confirmed, deploys the project.
// A "no" answer (or cancel) returns nil and leaves the scaffold untouched.
func MaybeDeploy(projectDir string) error {
	proceed, err := ConfirmDeploy()
	if err != nil {
		return err
	}
	if !proceed {
		return nil
	}
	return DeployProject(projectDir)
}

// DeployProject runs the deployment flow through the engine.
func DeployProject(projectDir string) error {
	ctx := context.Background()
	client := NewEngineClient(EngineURL(), 0)

	if err := client.Health(ctx); err != nil {
		return fmt.Errorf("REAPER Ignition is not running.\nStart it with:\njava -jar stackd-ignition.jar")
	}

	projectID, err := ProjectID()
	if err != nil {
		return err
	}
	if projectID == "" {
		return nil
	}

	absPath, err := filepath.Abs(projectDir)
	if err != nil {
		return fmt.Errorf("could not resolve project path %q: %w", projectDir, err)
	}

	PrintDeployStart()
	deploymentID, err := client.CreateDeployment(ctx, absPath, projectID)
	if err != nil {
		var apiErr *APIError
		if errors.As(err, &apiErr) && apiErr.StatusCode == http.StatusConflict && apiErr.Code == "DEPLOYMENT_ALREADY_IN_PROGRESS" {
			return fmt.Errorf("a deployment for %s is already in progress.\nWait for it to finish, then try again.", projectDir)
		}
		return err
	}

	final, err := PollStatus(ctx, client, deploymentID, DefaultPollInterval, DefaultPollTimeout, ShowStatus)
	if err != nil {
		var apiErr *APIError
		if errors.As(err, &apiErr) && apiErr.StatusCode == http.StatusNotFound {
			return fmt.Errorf("deployment %q was not found on the engine.\nThe REAPER Ignition engine may have restarted and lost its in-memory deployment state.\nNo new deployment was started.", deploymentID)
		}
		return err
	}

	if final.Status == string(StatusHealthy) {
		PrintFinalHealthy(final.LiveURL)
		return nil
	}

	msg := final.Message
	if final.ErrorCode != "" {
		msg = fmt.Sprintf("%s (%s)", msg, final.ErrorCode)
	}
	return fmt.Errorf("deployment %q failed: %s", deploymentID, msg)
}
