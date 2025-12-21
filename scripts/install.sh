#!/bin/bash
# Install git hooks for development workflow
# This script sets up pre-commit and pre-push hooks to enforce code quality

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
GIT_HOOKS_DIR="$PROJECT_ROOT/.git/hooks"
SCRIPTS_HOOKS_DIR="$SCRIPT_DIR/scripts/git-hooks"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Installing git hooks...${NC}"
echo ""

# Check if .git directory exists
if [ ! -d "$PROJECT_ROOT/.git" ]; then
  echo -e "${RED}❌ Error: Not a git repository!${NC}"
  echo "Please run this script from the project root directory."
  exit 1
fi

# Function to install a hook
install_hook() {
  local hook_name=$1
  local hook_src="$SCRIPTS_HOOKS_DIR/$hook_name"
  local hook_dest="$GIT_HOOKS_DIR/$hook_name"

  if [ ! -f "$hook_src" ]; then
    echo -e "${RED}❌ Error: Hook script not found at $hook_src${NC}"
    exit 1
  fi

  # Remove existing hook if it's a symlink or regular file
  if [ -L "$hook_dest" ] || [ -f "$hook_dest" ]; then
    rm -f "$hook_dest"
  fi

  # Create symlink to the hook script
  ln -s "$hook_src" "$hook_dest"
  chmod +x "$hook_src"

  echo -e "${GREEN}✅ Installed $hook_name${NC}"
}

# Install each hook
install_hook "pre-commit"
install_hook "pre-push"

echo ""
echo -e "${GREEN}✅ All git hooks installed successfully!${NC}"
echo ""
echo "Installed hooks:"
echo "  • pre-commit  - Runs spotlessCheck before committing"
echo "  • pre-push    - Runs full build and tests before pushing"
echo ""
echo "To uninstall hooks, run:"
echo "  rm .git/hooks/pre-commit .git/hooks/pre-push"
echo ""
