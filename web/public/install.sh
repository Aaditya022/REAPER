#!/usr/bin/env bash
set -e

REAPER_REPO_URL="https://github.com/Aaditya022/REAPER.git"
REAPER_DIR="${REAPER_DIR:-${HOME:-$PWD}/.reaper}"
REAPER_BIN_DIR="${REAPER_BIN_DIR:-${HOME:-$PWD}/.local/bin}"

log()  { printf '[REAPER] %s\n' "$*"; }
warn() { printf '[REAPER] WARNING: %s\n' "$*"; }
die()  { printf '[REAPER] ERROR: %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1; }

log "Starting installation..."
log "Repository: $REAPER_REPO_URL"
log "Install directory: $REAPER_DIR"
log "Binary directory: $REAPER_BIN_DIR"

log "Checking dependencies..."

log "Detecting operating system..."
case "$(uname -s)" in
  Darwin) OS_NAME="macOS" ;;
  Linux)  OS_NAME="Linux" ;;
  *)      die "unsupported operating system '$(uname -s)'. REAPER supports macOS and Linux." ;;
esac
log "Detected $OS_NAME."

need git || die "git is required but was not found. Install Git (https://git-scm.com), then re-run this script."
need go  || die "Go is required but was not found. Install Go 1.24+ (https://go.dev/dl), then re-run this script."

GO_VERSION="$(go version | sed -nE 's/.*go([0-9]+)\.([0-9]+).*/\1.\2/p' | head -n1)"
if [ -n "$GO_VERSION" ]; then
  GO_MAJOR="${GO_VERSION%%.*}"
  GO_MINOR="${GO_VERSION#*.}"
  GO_MINOR="${GO_MINOR%%.*}"
  if [ "$GO_MAJOR" -lt 1 ] || { [ "$GO_MAJOR" -eq 1 ] && [ "$GO_MINOR" -lt 24 ]; }; then
    warn "detected Go $GO_VERSION, but REAPER requires Go 1.24+; the build may fail."
  fi
fi

log "git: $(git --version)"
log "go: $(go version)"

if [ -d "$REAPER_DIR/.git" ]; then
  log "Updating existing REAPER installation at $REAPER_DIR..."
  (cd "$REAPER_DIR" && git pull --ff-only origin main) \
    || die "failed to update the existing REAPER checkout at $REAPER_DIR. Run 'git pull' there manually to see the error."
elif [ -e "$REAPER_DIR" ]; then
  die "$REAPER_DIR already exists and is not a REAPER checkout. Move or remove it, or set REAPER_DIR to a different location, then re-run this script."
else
  log "Downloading REAPER from $REAPER_REPO_URL..."
  mkdir -p "$(dirname "$REAPER_DIR")"
  git clone --depth 1 "$REAPER_REPO_URL" "$REAPER_DIR" \
    || die "failed to download REAPER from $REAPER_REPO_URL. Check your network connection and try again."
fi

log "Installing dependencies (Go modules)..."
(cd "$REAPER_DIR" && go mod download) \
  || die "failed to download Go module dependencies. Run 'go mod download' in $REAPER_DIR manually to see the error."

log "Building the REAPER CLI..."
(cd "$REAPER_DIR" && go build -trimpath -o reaper .) \
  || die "failed to build the REAPER CLI. Run 'go build -o reaper .' in $REAPER_DIR manually to see the error."

log "Installing the REAPER CLI to $REAPER_BIN_DIR..."
mkdir -p "$REAPER_BIN_DIR"
cp "$REAPER_DIR/reaper" "$REAPER_BIN_DIR/reaper"
chmod +x "$REAPER_BIN_DIR/reaper"

case ":$PATH:" in
  *":$REAPER_BIN_DIR:"*) : ;;
  *)
    warn "$REAPER_BIN_DIR is not on your PATH."
    printf '[REAPER] To use reaper in this session, run:\n'
    printf '        export PATH="$HOME/.local/bin:$PATH"\n'
    printf '[REAPER] To make it permanent, add that line to your shell profile (~/.zshrc or ~/.bashrc).\n'
    ;;
esac

if need java && need mvn; then
  log "Building the REAPER deploy engine (Java + Maven found)..."
  (cd "$REAPER_DIR/zerops-deploy-engine" && mvn -q clean package -DskipTests) \
    || warn "deploy engine build failed; the CLI is installed, but the engine jar was not produced."
  log "Deploy engine built: $REAPER_DIR/zerops-deploy-engine/target/stackd-ignition-0.1.0-SNAPSHOT.jar"
else
  warn "Java or Maven not found; skipping the REAPER deploy engine (requires Java 17+ and Maven 3.9+). The CLI works without it."
fi

if ! need node; then
  warn "Node.js not found; the CLI can scaffold projects, but installing generated dependencies requires Node.js 22+."
fi

if [ -x "$REAPER_BIN_DIR/reaper" ]; then
  log "Verifying installation..."
  if "$REAPER_BIN_DIR/reaper" --help >/dev/null 2>&1; then
    log "Installation complete."
    printf '\n[REAPER] Next steps:\n'
    printf '  reaper --help   see available commands\n'
    printf '  reaper create   scaffold a new full-stack project\n'
    printf '\n[REAPER] REAPER CLI installed to %s. Happy building.\n' "$REAPER_BIN_DIR/reaper"
  else
    warn "the binary was installed, but 'reaper --help' exited non-zero. Run '$REAPER_BIN_DIR/reaper --help' manually."
  fi
fi
