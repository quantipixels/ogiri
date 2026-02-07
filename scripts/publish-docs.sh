#!/bin/bash
# Script to publish versioned documentation to GitHub Pages using mike
# Usage: ./scripts/publish-docs.sh [version]
# If no version provided, uses version from .ogiri-version

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Get version from argument or .ogiri-version file
VERSION="${1:-.}"
if [ "$VERSION" = "." ]; then
  VERSION=$(cat .ogiri-version)
fi

# Trim whitespace
VERSION=$(echo "$VERSION" | xargs)

echo -e "${BLUE}Publishing documentation for version: $VERSION${NC}"

# Check if mike is installed
if ! command -v mike &> /dev/null; then
  echo -e "${RED}Error: 'mike' is not installed${NC}"
  echo "Install with: pip install mkdocs-mike"
  exit 1
fi

# Build and deploy with mike
echo -e "${BLUE}Building documentation...${NC}"
export RELEASE_VERSION="$VERSION"
mkdocs build -q

echo -e "${BLUE}Deploying version $VERSION to GitHub Pages...${NC}"
mike deploy --push --update-aliases "$VERSION" latest

echo -e "${GREEN}✓ Documentation deployed successfully!${NC}"
echo -e "${GREEN}✓ Access at: https://quantipixels.github.io/ogiri${NC}"
