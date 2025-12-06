#!/bin/bash

##
# Release script for Ogiri
#
# Creates a git tag and pushes it to GitHub to trigger automated release workflow.
#
# Usage:
#   ./scripts/release.sh              # Use version from .ogiri-version
#   ./scripts/release.sh 1.0.3        # Use specific version
#
# The release workflow will:
#   1. Extract version from tag
#   2. Import GPG key for signing
#   3. Publish to Maven Central
#   4. Create GitHub release
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

# Determine version
if [ -n "$1" ]; then
  VERSION="$1"
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
echo "  Project: $PROJECT_ROOT"
echo ""

# Check if tag already exists
if git -C "$PROJECT_ROOT" rev-parse "$TAG" >/dev/null 2>&1; then
  echo -e "${RED}Error: Tag '$TAG' already exists${NC}"
  exit 1
fi

# Confirm with user
echo -e "${YELLOW}This will:${NC}"
echo "  1. Create git tag: $TAG"
echo "  2. Push tag to GitHub"
echo "  3. Trigger automated release workflow"
echo ""
read -p "Continue? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo -e "${YELLOW}Release cancelled${NC}"
  exit 0
fi

# Create and push tag
echo -e "${YELLOW}Creating tag...${NC}"
git -C "$PROJECT_ROOT" tag -a "$TAG" -m "Release $VERSION"
echo -e "${GREEN}✓ Tag created: $TAG${NC}"

echo -e "${YELLOW}Pushing tag to GitHub...${NC}"
git -C "$PROJECT_ROOT" push origin "$TAG"
echo -e "${GREEN}✓ Tag pushed${NC}"

echo ""
echo -e "${GREEN}Release initiated successfully!${NC}"
echo ""
echo "Release details:"
echo "  GitHub: https://github.com/mosobande/ogiri/releases/tag/$TAG"
echo "  Workflow: https://github.com/mosobande/ogiri/actions/workflows/release.yml"
echo ""
echo "The automated release workflow will:"
echo "  - Build and sign artifacts with GPG"
echo "  - Publish to Maven Central"
echo "  - Create GitHub release with artifacts"
echo ""
echo "Check the workflow status at the link above."
