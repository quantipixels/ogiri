#!/bin/bash

##
# Release script for Ògiri
#
# Creates a git tag and pushes it to GitHub to trigger automated release workflow.
#
# Usage:
#   ./scripts/release.sh              # Use version from .ogiri-version
#   ./scripts/release.sh 1.0.3        # Use specific version
#   ./scripts/release.sh -f 1.0.3     # Force reuse existing tag
#   ./scripts/release.sh --force      # Force reuse with version from .ogiri-version
#
# The release workflow will:
#   1. Create GitHub release from tag
#   2. Trigger docs.yml to build and deploy versioned documentation
#   3. Maven deploy workflows run independently (not blocked by Maven failures)
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Parse arguments
FORCE_REUSE=false
VERSION=""

while [[ $# -gt 0 ]]; do
  case $1 in
    -f|--force)
      FORCE_REUSE=true
      shift
      ;;
    -*)
      echo -e "${RED}Error: Unknown option '$1'${NC}"
      exit 1
      ;;
    *)
      VERSION="$1"
      shift
      ;;
  esac
done

# Determine version
if [ -n "$VERSION" ]; then
  : # VERSION already set from arguments
else
  # Read from .ogiri-version file
  VERSION_FILE="$PROJECT_ROOT/.ogiri-version"
  if [ ! -f "$VERSION_FILE" ]; then
    echo -e "${RED}Error: .ogiri-version file not found${NC}"
    exit 1
  fi
  VERSION=$(cat "$VERSION_FILE" | tr -d '\n' | tr -d ' ')
fi

# Validate version format
if ! [[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo -e "${RED}Error: Invalid version format '$VERSION'. Expected: X.Y.Z${NC}"
  exit 1
fi

TAG="v$VERSION"

echo -e "${YELLOW}Release Configuration:${NC}"
echo "  Version: $VERSION"
echo "  Tag: $TAG"
echo "  Force reuse: $FORCE_REUSE"
echo "  Project: $PROJECT_ROOT"
echo ""

# Check if tag already exists
if git -C "$PROJECT_ROOT" rev-parse "$TAG" >/dev/null 2>&1; then
  if [ "$FORCE_REUSE" = false ]; then
    echo -e "${RED}Error: Tag '$TAG' already exists${NC}"
    echo -e "${YELLOW}Use -f or --force to reuse/overwrite the tag${NC}"
    exit 1
  fi
fi

# Confirm with user
echo -e "${YELLOW}This will:${NC}"
if [ "$FORCE_REUSE" = true ]; then
  echo "  1. Force update git tag: $TAG"
  echo "  2. Force push tag to GitHub"
else
  echo "  1. Create git tag: $TAG"
  echo "  2. Push tag to GitHub"
fi
echo "  3. Trigger automated release workflow"
echo ""
read -p "Continue? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo -e "${YELLOW}Release cancelled${NC}"
  exit 0
fi

# Create and push tag
echo -e "${YELLOW}Creating/updating tag...${NC}"
if [ "$FORCE_REUSE" = true ]; then
  git -C "$PROJECT_ROOT" tag -f -a "$TAG" -m "Release $VERSION"
  echo -e "${GREEN}✓ Tag updated: $TAG${NC}"
else
  git -C "$PROJECT_ROOT" tag -a "$TAG" -m "Release $VERSION"
  echo -e "${GREEN}✓ Tag created: $TAG${NC}"
fi

echo -e "${YELLOW}Pushing tag to GitHub...${NC}"
# A1: Use 'origin' remote (standard convention) instead of 'github'
REMOTE="${GIT_REMOTE:-origin}"
if [ "$FORCE_REUSE" = true ]; then
  git -C "$PROJECT_ROOT" push -f "$REMOTE" "$TAG"
  echo -e "${GREEN}✓ Tag force-pushed to $REMOTE${NC}"
else
  git -C "$PROJECT_ROOT" push "$REMOTE" "$TAG"
  echo -e "${GREEN}✓ Tag pushed to $REMOTE${NC}"
fi

echo ""
echo -e "${GREEN}Release initiated successfully!${NC}"
echo ""
echo "Release details:"
echo "  GitHub: https://github.com/quantipixels/ogiri/releases/tag/$TAG"
echo "  Workflow: https://github.com/quantipixels/ogiri/actions/workflows/release.yml"
echo ""
echo "The automated workflows will:"
echo "  - release.yml: Create GitHub release"
echo "  - release.yml (publish-npm): Publish ogiri-security-client to npm"
echo "  - docs.yml: Build and deploy versioned documentation"
echo "  - Maven workflows: Deploy independently (not blocked by each other)"
echo ""
echo "Check workflow status at: https://github.com/quantipixels/ogiri/actions"
