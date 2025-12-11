# GitHub Pages Setup Guide

This guide explains how the ogiri documentation is deployed to GitHub Pages with versioning support.

## Overview

Documentation is automatically deployed to GitHub Pages on every push to `main` or `ori` branches. Multiple versions are supported, allowing users to view docs for different ogiri releases.

**Live Site:** https://mosobande.github.io/ogiri

## Automatic Deployment

### Trigger Events

The `docs.yml` workflow deploys documentation when:

1. Push to `main` or `ori` branches
2. Changes to:
   - `docs/**` directory
   - `mkdocs.yml` file
   - `.github/workflows/docs.yml` file

### Workflow Steps

1. **Build** - Compiles mkdocs site
2. **Deploy** - Pushes to `gh-pages` branch

**Duration:** ~30 seconds

## Manual Deployment

Deploy docs manually for specific versions:

```bash
# Install dependencies (one-time)
pip install mkdocs-material mkdocs-mike

# Deploy using current version from .ogiri-version
./scripts/publish-docs.sh

# Deploy specific version
./scripts/publish-docs.sh 1.1.0

# Deploy as development version
./scripts/publish-docs.sh dev
```

The `publish-docs.sh` script:
- Reads version from `.ogiri-version` by default
- Builds documentation with mkdocs
- Deploys using mike (multi-version support)
- Sets version as latest automatically

## Version Management

### How Versioning Works

**Version Source:** `.ogiri-version` file

**Deployment with Mike:**
```bash
mike deploy 1.1.0 latest
```

This creates two deployments:
- `1.1.0/` - Specific version archive
- `latest/` - Alias pointing to latest version

### Accessing Versions

**Latest (root):**
```
https://mosobande.github.io/ogiri/
```

**Specific version:**
```
https://mosobande.github.io/ogiri/1.1.0/
```

**Version switcher:** Users can select versions from dropdown (Material theme)

## GitHub Repository Configuration

### Enable GitHub Pages

1. Go to repository **Settings**
2. Navigate to **Pages**
3. Select:
   - **Source:** Deploy from a branch
   - **Branch:** `gh-pages`
   - **Folder:** `/ (root)`
4. Save

The `docs.yml` workflow automatically creates the `gh-pages` branch.

### Access Control

Repository secrets (if using private GPG keys for other workflows):

- `GITHUB_TOKEN` - Auto-provisioned by GitHub Actions
- Uses GITHUB_TOKEN for docs.yml (no secrets needed)

## Workflow File: docs.yml

Location: `.github/workflows/docs.yml`

```yaml
name: Deploy Documentation
on:
  push:
    branches: [main, ori]
    paths:
      - 'docs/**'
      - 'mkdocs.yml'
      - '.github/workflows/docs.yml'
```

### Key Features

- **Python 3.12** runtime
- **Dependency caching** - Faster builds
- **Conditional deployment** - Only on main/ori pushes
- **Pages artifact** - Standard GitHub Pages format
- **Concurrency control** - Prevents race conditions

## Configuration: mkdocs.yml

Key sections for documentation deployment:

```yaml
site_name: Ògiri Security
site_url: https://mosobande.github.io/ogiri
repo_url: https://github.com/mosobande/ogiri

extra:
  version:
    provider: mike  # Multi-version support
```

## Troubleshooting

### Docs not deploying

1. Check **Actions** tab for `docs.yml` workflow status
2. Verify GitHub Pages is enabled (Settings → Pages)
3. Ensure `gh-pages` branch exists
4. Check workflow permissions (read/write)

### Version not showing in dropdown

1. Run publish script: `./scripts/publish-docs.sh`
2. Check mike installation: `pip show mkdocs-mike`
3. Verify `.ogiri-version` file contains correct version

### Build fails locally

```bash
# Install exact dependencies
pip install mkdocs-material==9.5.0 mkdocs-mike==2.0.0

# Test build
mkdocs build

# Test serve
mkdocs serve  # Visit http://localhost:8000
```

### 404 on gh-pages branch

The `gh-pages` branch is created automatically by the workflow. If missing:

1. Delete branch: `git push origin --delete gh-pages`
2. Re-run docs workflow (push to main/ori)
3. Wait for workflow completion

## Local Testing

### Preview documentation locally

```bash
# Install dependencies
pip install mkdocs-material

# Start development server
mkdocs serve

# Access at http://localhost:8000
```

### Test version deployment locally

```bash
# Install mike
pip install mkdocs-mike

# Build and stage deployment
mkdocs build

# (Don't run mike locally - requires git origin setup)
```

## Release Process

### When releasing a new version

1. Update `.ogiri-version` file:
   ```bash
   echo "1.2.0" > .ogiri-version
   ```

2. Commit and push:
   ```bash
   git add .ogiri-version
   git commit -m "chore: bump version to 1.2.0"
   git push origin main
   ```

3. Documentation automatically deploys with new version

4. Or manually deploy:
   ```bash
   ./scripts/publish-docs.sh 1.2.0
   ```

### Version Aliases

The `latest` alias always points to the most recent deployed version. Set manually:

```bash
mike alias latest 1.2.0
```

## Dependencies

### Required (CI/CD)

- `mkdocs-material` - Material theme
- `mkdocs-mike` - Multi-version support

### Optional (Local)

- `mkdocs` - Static site generator
- `pymdown-extensions` - Markdown extensions

### Installation

```bash
pip install mkdocs-material mkdocs-mike
```

## Monitoring

### View deployment history

1. Repository → **Actions** → **Deploy Documentation**
2. Check workflow runs
3. Click run to see detailed logs

### Monitor site health

- Visit: https://mosobande.github.io/ogiri
- Check latest version loads
- Verify dropdown shows all versions
- Test navigation between pages

## FAQ

**Q: Can I deploy from a different branch?**
A: Update `docs.yml` to include your branch in `on.push.branches`

**Q: How do I delete an old version?**
A: Use mike: `mike delete 1.0.0 && mike deploy --push`

**Q: Can I deploy docs on release tags?**
A: Yes, modify `docs.yml` to trigger on tags: `on: [push: {tags: ['v*']}]`

**Q: How long are old versions kept?**
A: As long as `gh-pages` branch exists (no automatic cleanup)

**Q: Can I customize the version selector?**
A: Yes, edit `mkdocs.yml` `extra.version` section

## Resources

- [Material for MkDocs Docs](https://squidfunk.github.io/mkdocs-material/)
- [Mike Documentation](https://github.com/jimporter/mike)
- [GitHub Pages Docs](https://docs.github.com/en/pages)
