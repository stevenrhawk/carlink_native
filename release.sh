#!/bin/bash
#
# release.sh — Bump version, build signed AAB, upload to Play Store internal testing
#
# Usage:
#   ./release.sh              # Auto-bump versionCode, keep versionName
#   ./release.sh 1.1.0        # Auto-bump versionCode, set versionName to 1.1.0
#   ./release.sh --dry-run    # Bump + build only, skip upload
#
# Prerequisites:
#   - Keystore at D:\android-dev\android-keystore
#   - .env with KEYSTORE_PASSWORD at D:\android-dev\carplay-exploartion\.env
#   - Service account JSON at D:\android-dev\carplay-exploartion\trimline-fire-*.json
#   - App must have been manually uploaded to Play Store at least once
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_GRADLE="$SCRIPT_DIR/app/build.gradle.kts"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Ensure JAVA_HOME is set (Windows bash doesn't inherit it reliably)
# Resolve user home — $HOME may differ between Git Bash, MSYS2, WSL
USER_HOME="${HOME:-}"
if [[ -z "$USER_HOME" || ! -d "$USER_HOME" ]]; then
    # Fall back to USERPROFILE (Windows env var) converted to Unix path
    USER_HOME="$(cygpath -u "${USERPROFILE:-C:\\Users\\stevenrhawk}" 2>/dev/null || echo "/c/Users/stevenrhawk")"
fi

find_jdk() {
    local candidates=(
        # WSL paths (/mnt/c/...)
        "/mnt/c/Users/stevenrhawk/.gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2"
        "/mnt/c/Program Files/Java/jdk-21"
        "/mnt/c/Program Files/Java/jdk-17"
        # Git Bash / MSYS2 paths (/c/...)
        "/c/Users/stevenrhawk/.gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2"
        "/c/Program Files/Java/jdk-21"
        "/c/Program Files/Java/jdk-17"
        # $HOME-relative
        "$USER_HOME/.gradle/jdks/jetbrains_s_r_o_-21-amd64-windows.2"
    )
    for candidate in "${candidates[@]}"; do
        echo "  Checking: $candidate (exists=$(test -d "$candidate" && echo yes || echo no))" >&2
        if [[ -f "$candidate/bin/java" || -f "$candidate/bin/java.exe" ]]; then
            echo "$candidate"
            return 0
        fi
    done
    return 1
}

echo "DEBUG: HOME=$HOME USER_HOME=$USER_HOME JAVA_HOME=${JAVA_HOME:-unset}" >&2
if [[ -z "${JAVA_HOME:-}" ]] || ! "${JAVA_HOME}/bin/java" -version &>/dev/null; then
    echo "DEBUG: Searching for JDK..." >&2
    JAVA_HOME="$(find_jdk)" || {
        echo -e "${RED}ERROR: No JDK 17+ found. Set JAVA_HOME.${NC}"
        exit 1
    }
    export JAVA_HOME
fi

# Gradle runs as a Windows process — it needs a Windows-style JAVA_HOME.
# Convert /mnt/c/... (WSL) to C:\... for the gradlew wrapper.
if [[ "$JAVA_HOME" == /mnt/* ]]; then
    # WSL: convert /mnt/c/foo to C:/foo for gradlew (Windows process)
    JAVA_HOME_WIN="$(echo "$JAVA_HOME" | sed 's|^/mnt/\(.\)|\U\1:|; s|/|\\|g')"
    export JAVA_HOME="$JAVA_HOME_WIN"
fi

export PATH="$JAVA_HOME/bin:$PATH"
echo -e "${GREEN}Using JDK: $JAVA_HOME${NC}"

# Parse args
DRY_RUN=false
NEW_VERSION_NAME=""

for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        --help|-h)
            echo "Usage: ./release.sh [VERSION_NAME] [--dry-run]"
            echo ""
            echo "  VERSION_NAME   Optional new versionName (e.g., 1.1.0)"
            echo "  --dry-run      Build only, don't upload to Play Store"
            exit 0
            ;;
        *) NEW_VERSION_NAME="$arg" ;;
    esac
done

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  Carlink Native — Release Pipeline${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

# ── Step 1: Read current version ──────────────────────────────────────

CURRENT_CODE=$(grep -oP 'versionCode = \K[0-9]+' "$BUILD_GRADLE")
CURRENT_NAME=$(grep -oP 'versionName = "\K[^"]+' "$BUILD_GRADLE")

if [[ -z "$CURRENT_CODE" || -z "$CURRENT_NAME" ]]; then
    echo -e "${RED}ERROR: Could not parse versionCode/versionName from build.gradle.kts${NC}"
    exit 1
fi

echo -e "Current version: ${YELLOW}${CURRENT_NAME}${NC} (code ${YELLOW}${CURRENT_CODE}${NC})"

# ── Step 2: Bump version ─────────────────────────────────────────────

NEW_CODE=$((CURRENT_CODE + 1))
FINAL_NAME="${NEW_VERSION_NAME:-$CURRENT_NAME}"

echo -e "New version:     ${GREEN}${FINAL_NAME}${NC} (code ${GREEN}${NEW_CODE}${NC})"
echo ""

# Update versionCode
sed -i "s/versionCode = ${CURRENT_CODE}/versionCode = ${NEW_CODE}/" "$BUILD_GRADLE"

# Update versionName if provided
if [[ -n "$NEW_VERSION_NAME" ]]; then
    sed -i "s/versionName = \"${CURRENT_NAME}\"/versionName = \"${NEW_VERSION_NAME}\"/" "$BUILD_GRADLE"
fi

# Verify the bump worked
VERIFY_CODE=$(grep -oP 'versionCode = \K[0-9]+' "$BUILD_GRADLE")
VERIFY_NAME=$(grep -oP 'versionName = "\K[^"]+' "$BUILD_GRADLE")

if [[ "$VERIFY_CODE" != "$NEW_CODE" ]]; then
    echo -e "${RED}ERROR: versionCode bump failed (expected $NEW_CODE, got $VERIFY_CODE)${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Version bumped in build.gradle.kts${NC}"

# ── Step 3: Build signed release AAB ──────────────────────────────────

echo ""
echo -e "${CYAN}Building signed release AAB...${NC}"
echo ""

cd "$SCRIPT_DIR"
bash ./gradlew clean bundleRelease 2>&1 | tail -20

AAB_PATH="$SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"

if [[ ! -f "$AAB_PATH" ]]; then
    echo -e "${RED}ERROR: AAB not found at $AAB_PATH${NC}"
    echo "Build may have failed. Check output above."
    exit 1
fi

AAB_SIZE=$(du -h "$AAB_PATH" | cut -f1)
echo ""
echo -e "${GREEN}✓ AAB built: ${AAB_PATH} (${AAB_SIZE})${NC}"

# ── Step 4: Upload to Play Store ──────────────────────────────────────

if [[ "$DRY_RUN" == true ]]; then
    echo ""
    echo -e "${YELLOW}DRY RUN — skipping Play Store upload${NC}"
    echo -e "To upload manually: ${CYAN}./gradlew publishReleaseBundle${NC}"
else
    echo ""
    echo -e "${CYAN}Uploading to Play Store (internal testing track)...${NC}"
    echo ""

    bash ./gradlew publishReleaseBundle 2>&1 | tail -20

    echo ""
    echo -e "${GREEN}✓ Uploaded to Play Store internal testing${NC}"
fi

# ── Summary ───────────────────────────────────────────────────────────

echo ""
echo -e "${CYAN}========================================${NC}"
echo -e "${GREEN}  Release complete!${NC}"
echo -e "  Version: ${FINAL_NAME} (${NEW_CODE})"
if [[ "$DRY_RUN" == true ]]; then
    echo -e "  ${YELLOW}Upload skipped (dry run)${NC}"
else
    echo -e "  Track: internal testing"
fi
echo -e "${CYAN}========================================${NC}"
