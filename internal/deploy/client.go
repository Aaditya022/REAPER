package deploy

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"
)

// DefaultHTTPTimeout bounds a single HTTP request to the engine.
const DefaultHTTPTimeout = 10 * time.Second

// EngineClient is a thin HTTP client for the STACKD Ignition engine.
// It knows nothing about Zerops internals.
type EngineClient struct {
	baseURL string
	client  *http.Client
}

// NewEngineClient builds a client for the given engine base URL.
func NewEngineClient(engineURL string, timeout time.Duration) *EngineClient {
	if engineURL == "" {
		engineURL = "http://localhost:8080"
	}
	if timeout <= 0 {
		timeout = DefaultHTTPTimeout
	}
	return &EngineClient{
		baseURL: strings.TrimRight(engineURL, "/"),
		client:  &http.Client{Timeout: timeout},
	}
}

// EngineURL returns the configured engine base URL from STACKD_IGNITION_URL,
// defaulting to http://localhost:8080.
func EngineURL() string {
	if v := strings.TrimSpace(os.Getenv("STACKD_IGNITION_URL")); v != "" {
		return v
	}
	return "http://localhost:8080"
}

// CreateDeploymentRequest is the body sent to POST /api/v1/deployments.
type CreateDeploymentRequest struct {
	ProjectPath     string `json:"projectPath"`
	ZeropsProjectID string `json:"zeropsProjectId"`
}

// DeploymentStatusResponse mirrors the engine's deployment status DTO.
// errorCode and liveUrl may be absent because Spring omits null fields.
type DeploymentStatusResponse struct {
	DeploymentID string `json:"deploymentId"`
	Status       string `json:"status"`
	Message      string `json:"message"`
	ErrorCode    string `json:"errorCode,omitempty"`
	LiveURL      string `json:"liveUrl,omitempty"`
}

// EngineError mirrors the engine's synchronous error DTO.
type EngineError struct {
	Error   bool   `json:"error"`
	Code    string `json:"code"`
	Message string `json:"message"`
}

// APIError is a non-2xx response from the engine.
type APIError struct {
	StatusCode int
	Code       string
	Message    string
}

func (e *APIError) Error() string {
	if e.Code != "" {
		return fmt.Sprintf("engine returned %d (%s): %s", e.StatusCode, e.Code, e.Message)
	}
	return fmt.Sprintf("engine returned status %d", e.StatusCode)
}

// Health checks GET /api/v1/health on the engine.
func (c *EngineClient) Health(ctx context.Context) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+"/api/v1/health", nil)
	if err != nil {
		return err
	}
	resp, err := c.client.Do(req)
	if err != nil {
		return fmt.Errorf("engine health check failed: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return decodeAPIError(resp)
	}
	return nil
}

// CreateDeployment posts a new deployment and returns the deployment id.
func (c *EngineClient) CreateDeployment(ctx context.Context, projectPath, projectID string) (string, error) {
	body, err := json.Marshal(CreateDeploymentRequest{ProjectPath: projectPath, ZeropsProjectID: projectID})
	if err != nil {
		return "", fmt.Errorf("could not encode deployment request: %w", err)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+"/api/v1/deployments", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := c.client.Do(req)
	if err != nil {
		return "", fmt.Errorf("could not start deployment: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusAccepted {
		return "", decodeAPIError(resp)
	}
	var status DeploymentStatusResponse
	if err := json.NewDecoder(resp.Body).Decode(&status); err != nil {
		return "", fmt.Errorf("could not parse engine response: %w", err)
	}
	if status.DeploymentID == "" {
		return "", fmt.Errorf("engine response did not include a deploymentId")
	}
	return status.DeploymentID, nil
}

// DeploymentStatus fetches the current status of a deployment.
func (c *EngineClient) DeploymentStatus(ctx context.Context, deploymentID string) (DeploymentStatusResponse, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+"/api/v1/deployments/"+deploymentID+"/status", nil)
	if err != nil {
		return DeploymentStatusResponse{}, err
	}
	resp, err := c.client.Do(req)
	if err != nil {
		return DeploymentStatusResponse{}, fmt.Errorf("could not fetch deployment status: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return DeploymentStatusResponse{}, decodeAPIError(resp)
	}
	var status DeploymentStatusResponse
	if err := json.NewDecoder(resp.Body).Decode(&status); err != nil {
		return DeploymentStatusResponse{}, fmt.Errorf("could not parse engine response: %w", err)
	}
	return status, nil
}

// decodeAPIError turns a non-2xx response into an *APIError.
func decodeAPIError(resp *http.Response) error {
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	var e EngineError
	if err := json.Unmarshal(body, &e); err == nil && e.Code != "" {
		return &APIError{StatusCode: resp.StatusCode, Code: e.Code, Message: e.Message}
	}
	return &APIError{StatusCode: resp.StatusCode, Message: strings.TrimSpace(string(body))}
}
