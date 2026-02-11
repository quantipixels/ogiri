#!/bin/bash
# Usage: ./scripts/publish-docs.sh [version]
# If no version provided, uses version from .ogiri-version

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

VERSION="${1:-.}"
if [ "$VERSION" = "." ]; then
  VERSION=$(cat .ogiri-version)
fi

VERSION=$(echo "$VERSION" | xargs)

echo -e "${BLUE}Publishing documentation for version: $VERSION${NC}"

if ! command -v mike &> /dev/null; then
  echo -e "${RED}Error: 'mike' is not installed${NC}"
  echo "Install with: pip install mkdocs-mike"
  exit 1
fi

echo -e "${BLUE}Building documentation...${NC}"
export RELEASE_VERSION="$VERSION"
mkdocs build -q

echo -e "${BLUE}Deploying version $VERSION to GitHub Pages...${NC}"
mike deploy --push --update-aliases "$VERSION" latest

echo -e "${GREEN}✓ Documentation deployed successfully!${NC}"
echo -e "${GREEN}✓ Access at: https://quantipixels.github.io/ogiri${NC}"
