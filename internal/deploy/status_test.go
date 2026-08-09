package deploy

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestPollStatusProgression(t *testing.T) {
	statuses := []string{"PENDING", "ANALYZING", "CONFIGURING", "DEPLOYING", "HEALTH_CHECKING", "HEALTHY"}
	var calls int64
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		i := int(atomic.AddInt64(&calls, 1) - 1)
		if i >= len(statuses) {
			i = len(statuses) - 1
		}
		fmt.Fprintf(w, `{"deploymentId":"dep-1","status":"%s","message":"%s"}`, statuses[i], statuses[i])
	}))
	defer srv.Close()

	client := NewEngineClient(srv.URL, 0)
	var seen []string
	onChange := func(s DeploymentStatusResponse) { seen = append(seen, s.Status) }

	final, err := PollStatus(context.Background(), client, "dep-1", 5*time.Millisecond, 5*time.Second, onChange)
	if err != nil {
		t.Fatalf("PollStatus() = %v", err)
	}
	if final.Status != string(StatusHealthy) {
		t.Errorf("final status = %q, want HEALTHY", final.Status)
	}
	if strings.Join(seen, ",") != strings.Join(statuses, ",") {
		t.Errorf("seen statuses = %v, want %v", seen, statuses)
	}
}

func TestPollStatusFailed(t *testing.T) {
	statuses := []string{"DEPLOYING", "FAILED"}
	var calls int64
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		i := int(atomic.AddInt64(&calls, 1) - 1)
		if i >= len(statuses) {
			i = len(statuses) - 1
		}
		fmt.Fprintf(w, `{"deploymentId":"dep-1","status":"%s","message":"env missing","errorCode":"MISSING_REQUIRED_ENV_VARS"}`, statuses[i])
	}))
	defer srv.Close()

	client := NewEngineClient(srv.URL, 0)
	final, err := PollStatus(context.Background(), client, "dep-1", 5*time.Millisecond, 5*time.Second, nil)
	if err != nil {
		t.Fatalf("PollStatus() = %v", err)
	}
	if final.Status != string(StatusFailed) || final.ErrorCode != "MISSING_REQUIRED_ENV_VARS" {
		t.Errorf("final = %+v, want FAILED with errorCode", final)
	}
}

func TestPollStatusTimeout(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, `{"deploymentId":"dep-1","status":"PENDING","message":"Deployment created"}`)
	}))
	defer srv.Close()

	client := NewEngineClient(srv.URL, 0)
	start := time.Now()
	_, err := PollStatus(context.Background(), client, "dep-1", 5*time.Millisecond, 50*time.Millisecond, nil)
	if err == nil {
		t.Fatal("PollStatus() = nil, want timeout error")
	}
	if !strings.Contains(err.Error(), "timed out") {
		t.Errorf("error = %q, want 'timed out'", err)
	}
	if elapsed := time.Since(start); elapsed > time.Second {
		t.Errorf("poll took too long: %s", elapsed)
	}
}

func TestPollStatusConnectionFailure(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {}))
	url := srv.URL
	srv.Close()

	client := NewEngineClient(url, 200*time.Millisecond)
	if _, err := PollStatus(context.Background(), client, "dep-1", time.Millisecond, time.Second, nil); err == nil {
		t.Fatal("PollStatus() = nil, want connection error")
	}
}

func TestPollStatusNotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		io.WriteString(w, `{"error":true,"code":"DEPLOYMENT_NOT_FOUND","message":"not found"}`)
	}))
	defer srv.Close()

	client := NewEngineClient(srv.URL, 0)
	_, err := PollStatus(context.Background(), client, "dep-1", time.Millisecond, time.Second, nil)
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.StatusCode != http.StatusNotFound {
		t.Fatalf("PollStatus() = %v, want 404", err)
	}
}

func TestDeployProjectPolling404(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/health":
			io.WriteString(w, `{"status":"ok"}`)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/deployments":
			w.WriteHeader(http.StatusAccepted)
			io.WriteString(w, `{"deploymentId":"dep-1","status":"PENDING","message":"Deployment created"}`)
		default:
			w.WriteHeader(http.StatusNotFound)
			io.WriteString(w, `{"error":true,"code":"DEPLOYMENT_NOT_FOUND","message":"not found"}`)
		}
	}))
	defer srv.Close()

	t.Setenv("STACKD_IGNITION_URL", srv.URL)
	t.Setenv("ZEROPS_PROJECT_ID", "proj-1")

	err := DeployProject("app")
	if err == nil || !strings.Contains(err.Error(), "may have restarted") {
		t.Fatalf("DeployProject() = %v, want engine-restart explanation", err)
	}
}

func TestDeployProjectFailedTerminal(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case r.Method == http.MethodGet && r.URL.Path == "/api/v1/health":
			io.WriteString(w, `{"status":"ok"}`)
		case r.Method == http.MethodPost && r.URL.Path == "/api/v1/deployments":
			w.WriteHeader(http.StatusAccepted)
			io.WriteString(w, `{"deploymentId":"dep-1","status":"DEPLOYING","message":"Deploying to Zerops"}`)
		default:
			io.WriteString(w, `{"deploymentId":"dep-1","status":"FAILED","message":"env missing","errorCode":"MISSING_REQUIRED_ENV_VARS"}`)
		}
	}))
	defer srv.Close()

	t.Setenv("STACKD_IGNITION_URL", srv.URL)
	t.Setenv("ZEROPS_PROJECT_ID", "proj-1")

	err := DeployProject("app")
	if err == nil || !strings.Contains(err.Error(), "MISSING_REQUIRED_ENV_VARS") {
		t.Fatalf("DeployProject() = %v, want FAILED errorCode", err)
	}
}

func TestIsTerminal(t *testing.T) {
	for _, tc := range []struct {
		status DeploymentStatus
		want   bool
	}{
		{StatusPending, false},
		{StatusAnalyzing, false},
		{StatusConfiguring, false},
		{StatusDeploying, false},
		{StatusHealthChecking, false},
		{StatusHealthy, true},
		{StatusFailed, true},
	} {
		if got := IsTerminal(tc.status); got != tc.want {
			t.Errorf("IsTerminal(%s) = %v, want %v", tc.status, got, tc.want)
		}
	}
}
