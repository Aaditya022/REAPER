package deploy

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestHealthOK(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/health" {
			t.Errorf("path = %q, want /api/v1/health", r.URL.Path)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		io.WriteString(w, `{"status":"ok"}`)
	}))
	defer srv.Close()

	client := NewEngineClient(srv.URL, 0)
	if err := client.Health(context.Background()); err != nil {
		t.Fatalf("Health() = %v, want nil", err)
	}
}

func TestHealthUnavailable(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {}))
	url := srv.URL
	srv.Close()

	client := NewEngineClient(url, time.Second)
	if err := client.Health(context.Background()); err == nil {
		t.Fatal("Health() = nil, want connection error")
	}
}

func TestEngineUnavailableMessage(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {}))
	url := srv.URL
	srv.Close()

	t.Setenv("STACKD_IGNITION_URL", url)
	t.Setenv("ZEROPS_PROJECT_ID", "proj-1")

	err := DeployProject("some-dir")
	if err == nil {
		t.Fatal("DeployProject() = nil, want engine-not-running error")
	}
	for _, want := range []string{"REAPER Ignition is not running", "java -jar stackd-ignition.jar"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("DeployProject() error = %q, want it to contain %q", err, want)
		}
	}
}

func TestCreateDeploymentRequestAnd202(t *testing.T) {
	var gotBody CreateDeploymentRequest
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			t.Errorf("method = %s, want POST", r.Method)
		}
		if r.URL.Path != "/api/v1/deployments" {
			t.Errorf("path = %q, want /api/v1/deployments", r.URL.Path)
		}
		if ct := r.Header.Get("Content-Type"); !strings.Contains(ct, "application/json") {
			t.Errorf("Content-Type = %q, want application/json", ct)
		}
		if err := json.NewDecoder(r.Body).Decode(&gotBody); err != nil {
			t.Fatalf("decode request body: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusAccepted)
		io.WriteString(w, `{"deploymentId":"dep-42","status":"PENDING","message":"Deployment created"}`)
	}))
	defer srv.Close()

	client := NewEngineClient(srv.URL, 0)
	id, err := client.CreateDeployment(context.Background(), "/abs/path/app", "proj-1")
	if err != nil {
		t.Fatalf("CreateDeployment() = %v", err)
	}
	if id != "dep-42" {
		t.Errorf("deploymentId = %q, want dep-42", id)
	}
	if gotBody.ProjectPath != "/abs/path/app" {
		t.Errorf("projectPath = %q, want /abs/path/app", gotBody.ProjectPath)
	}
	if gotBody.ZeropsProjectID != "proj-1" {
		t.Errorf("zeropsProjectId = %q, want proj-1", gotBody.ZeropsProjectID)
	}
}

func TestDeployProjectSendsAbsolutePath(t *testing.T) {
	var gotBody CreateDeploymentRequest
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/health":
			io.WriteString(w, `{"status":"ok"}`)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/deployments":
			if err := json.NewDecoder(r.Body).Decode(&gotBody); err != nil {
				t.Fatalf("decode request body: %v", err)
			}
			w.WriteHeader(http.StatusAccepted)
			io.WriteString(w, `{"deploymentId":"dep-1","status":"HEALTHY","message":"Deployment healthy"}`)
		case r.Method == http.MethodGet && strings.HasPrefix(r.URL.Path, "/api/v1/deployments/"):
			io.WriteString(w, `{"deploymentId":"dep-1","status":"HEALTHY","message":"Deployment healthy","liveUrl":"https://app.zerops.io"}`)
		default:
			http.NotFound(w, r)
		}
	}))
	defer srv.Close()

	t.Setenv("STACKD_IGNITION_URL", srv.URL)
	t.Setenv("ZEROPS_PROJECT_ID", "proj-1")

	if err := DeployProject("./app"); err != nil {
		t.Fatalf("DeployProject() = %v", err)
	}
	if !filepath.IsAbs(gotBody.ProjectPath) {
		t.Errorf("projectPath = %q, want an absolute path", gotBody.ProjectPath)
	}
	if filepath.Base(gotBody.ProjectPath) != "app" {
		t.Errorf("projectPath base = %q, want %q", filepath.Base(gotBody.ProjectPath), "app")
	}
	if gotBody.ZeropsProjectID != "proj-1" {
		t.Errorf("zeropsProjectId = %q, want proj-1", gotBody.ZeropsProjectID)
	}
}

func TestCreateDeploymentMalformedResponse(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusAccepted)
		io.WriteString(w, `not-json`)
	}))
	defer srv.Close()

	client := NewEngineClient(srv.URL, 0)
	if _, err := client.CreateDeployment(context.Background(), "/x", "p"); err == nil {
		t.Fatal("CreateDeployment() = nil, want parse error")
	}
}

func TestCreateDeploymentConflict(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusConflict)
		io.WriteString(w, `{"error":true,"code":"DEPLOYMENT_ALREADY_IN_PROGRESS","message":"already in progress"}`)
	}))
	defer srv.Close()

	client := NewEngineClient(srv.URL, 0)
	_, err := client.CreateDeployment(context.Background(), "/x", "p")
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("CreateDeployment() err = %v, want *APIError", err)
	}
	if apiErr.StatusCode != http.StatusConflict || apiErr.Code != "DEPLOYMENT_ALREADY_IN_PROGRESS" {
		t.Errorf("APIError = %+v, want 409 DEPLOYMENT_ALREADY_IN_PROGRESS", apiErr)
	}
}

func TestDeployProjectAlreadyInProgress(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/health":
			io.WriteString(w, `{"status":"ok"}`)
		default:
			w.WriteHeader(http.StatusConflict)
			io.WriteString(w, `{"error":true,"code":"DEPLOYMENT_ALREADY_IN_PROGRESS","message":"already in progress"}`)
		}
	}))
	defer srv.Close()

	t.Setenv("STACKD_IGNITION_URL", srv.URL)
	t.Setenv("ZEROPS_PROJECT_ID", "proj-1")

	err := DeployProject("app")
	if err == nil || !strings.Contains(err.Error(), "already in progress") {
		t.Fatalf("DeployProject() = %v, want 'already in progress'", err)
	}
}

func TestDeploymentStatusParsing(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, `{"deploymentId":"dep-1","status":"DEPLOYING","message":"Deploying to Zerops"}`)
	}))
	defer srv.Close()

	client := NewEngineClient(srv.URL, 0)
	st, err := client.DeploymentStatus(context.Background(), "dep-1")
	if err != nil {
		t.Fatalf("DeploymentStatus() = %v", err)
	}
	if st.DeploymentID != "dep-1" || st.Status != "DEPLOYING" {
		t.Errorf("got %+v", st)
	}
	// Spring omits null properties, so errorCode and liveUrl must decode as empty.
	if st.ErrorCode != "" || st.LiveURL != "" {
		t.Errorf("omitted fields must decode empty, got %+v", st)
	}
}

func TestDeploymentStatusWithLiveURL(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, `{"deploymentId":"dep-1","status":"HEALTHY","message":"Deployment healthy","liveUrl":"https://app.zerops.io"}`)
	}))
	defer srv.Close()

	client := NewEngineClient(srv.URL, 0)
	st, err := client.DeploymentStatus(context.Background(), "dep-1")
	if err != nil {
		t.Fatalf("DeploymentStatus() = %v", err)
	}
	if st.LiveURL != "https://app.zerops.io" {
		t.Errorf("liveUrl = %q, want https://app.zerops.io", st.LiveURL)
	}
}
