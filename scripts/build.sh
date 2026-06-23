#!/usr/bin/env bash
#
# Build the ConfigRadar CLI into an executable fat jar.
#
# Run from anywhere; it locates the repository root itself:
#
#   ./scripts/build.sh                 # build + run tests, jar copied to dist/
#   ./scripts/build.sh --skip-tests    # faster build, tests skipped
#   ./scripts/build.sh --help
#
# Output: dist/config-radar-cli.jar (a self-contained executable jar).
#
# Requirements: JDK 21+ and Maven 3.9+. The script checks for them and
# exits with a clear message if either is missing or too old.

set -euo pipefail

# --- locate repository root (directory containing this script's parent pom.xml) ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ ! -f "$REPO_ROOT/pom.xml" ]]; then
  echo "error: could not locate repository root (no pom.xml above $SCRIPT_DIR)" >&2
  exit 1
fi

usage() {
  cat <<EOF
Usage: scripts/build.sh [--skip-tests] [--help]

Build the ConfigRadar CLI executable jar.

Options:
  --skip-tests   build without running the test suite (faster)
  --help         show this help and exit

Output:
  dist/config-radar-cli.jar   self-contained executable jar

Requirements:
  JDK 21+ and Maven 3.9+
EOF
}

SKIP_TESTS=0
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=1 ;;
    --help|-h) usage; exit 0 ;;
    *) echo "error: unknown option '$arg'" >&2; usage >&2; exit 2 ;;
  esac
done

# --- requirement checks ------------------------------------------------------

require_java_version() {
  # Needs JDK 21+. java -version prints to stderr in most distributions.
  if ! command -v java >/dev/null 2>&1; then
    echo "error: 'java' not found on PATH. ConfigRadar requires JDK 21+." >&2
    echo "       Install a JDK 21 (or newer) and ensure 'java' is on your PATH." >&2
    exit 1
  fi
  local major
  # java -version prints e.g. 'openjdk version "21.0.11"' or '"1.8.0_341"'.
  major="$(java -version 2>&1 | awk -F\" 'NR==1 {print $2}' | cut -d. -f1)"
  if [[ "$major" =~ ^[0-9]+$ ]] && (( major < 21 )); then
    echo "error: ConfigRadar requires JDK 21+, but found Java $major." >&2
    echo "       Current java: $(java -version 2>&1 | head -1)" >&2
    echo "       Set JAVA_HOME to a JDK 21+ install or upgrade your default JDK." >&2
    exit 1
  fi
}

require_maven_version() {
  # Needs Maven 3.9+.
  if ! command -v mvn >/dev/null 2>&1; then
    echo "error: 'mvn' not found on PATH. ConfigRadar requires Maven 3.9+." >&2
    echo "       Install Maven 3.9+ (or use './mvnw' if a wrapper is added later)." >&2
    exit 1
  fi
  local ver major minor
  # mvn -version first line: 'Apache Maven 3.9.9 (...)'.
  ver="$(mvn -version 2>&1 | awk 'NR==1 {print $3}')"
  major="$(echo "$ver" | cut -d. -f1)"
  minor="$(echo "$ver" | cut -d. -f2)"
  if [[ "$major" =~ ^[0-9]+$ && "$minor" =~ ^[0-9]+$ ]]; then
    if (( major < 3 || (major == 3 && minor < 9) )); then
      echo "error: ConfigRadar requires Maven 3.9+, but found Maven $ver." >&2
      exit 1
    fi
  fi
}

require_java_version
require_maven_version

cd "$REPO_ROOT"

# --- build -------------------------------------------------------------------

echo "==> Building ConfigRadar CLI ($(date '+%H:%M:%S'))"
echo "    repo:    $REPO_ROOT"
echo "    java:    $(java -version 2>&1 | head -1)"
echo "    maven:   $(mvn -version 2>&1 | head -1)"
[[ "$SKIP_TESTS" -eq 1 ]] && echo "    tests:   skipped (--skip-tests)" || echo "    tests:   enabled"

MVN_ARGS=( -pl config-radar-cli -am package )
if [[ "$SKIP_TESTS" -eq 1 ]]; then
  MVN_ARGS+=( -DskipTests )
fi

mvn -q "${MVN_ARGS[@]}"

BUILT_JAR="$REPO_ROOT/config-radar-cli/target/config-radar-cli.jar"
if [[ ! -f "$BUILT_JAR" ]]; then
  echo "error: expected jar not found at $BUILT_JAR" >&2
  echo "       the build may have failed silently; re-run without the build script for details." >&2
  exit 1
fi

# --- stage to dist/ for a stable, distributable path -------------------------

DIST_DIR="$REPO_ROOT/dist"
mkdir -p "$DIST_DIR"
cp -f "$BUILT_JAR" "$DIST_DIR/config-radar-cli.jar"

# --- verify the jar is runnable ---------------------------------------------

# picocli's --help always prints usage; if the jar is malformed or the main
# class is missing, this fails fast with a non-zero exit and an error.
if ! java -jar "$DIST_DIR/config-radar-cli.jar" --help >/dev/null 2>&1; then
  echo "error: built jar failed to start (java -jar ... --help returned non-zero)" >&2
  echo "       jar: $DIST_DIR/config-radar-cli.jar" >&2
  exit 1
fi

echo ""
echo "==> Build successful"
echo "    executable jar: $DIST_DIR/config-radar-cli.jar ($(du -h "$DIST_DIR/config-radar-cli.jar" | cut -f1))"
echo ""
echo "    Run it with:"
echo "      java -jar dist/config-radar-cli.jar inventory <project> -o config-inventory.yaml"
echo "      java -jar dist/config-radar-cli.jar diff --base before.yaml --head after.yaml -o diff.yaml"
echo ""
echo "    See skills/config-radar/SKILL.md for full usage and use cases."
