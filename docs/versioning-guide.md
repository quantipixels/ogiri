# Documentation Versioning Guide

This guide explains how ogiri uses **mike** to manage versioned documentation on GitHub Pages.

## Overview

**Mike** (Multi-version docs for MkDocs) allows serving multiple documentation versions from the same site with a version selector dropdown.

**Current Configuration:**

- Version source: `.ogiri-version` file (currently: 1.1.0)
- Provider: `mike`
- Default version: `latest` (alias to current version)
- GitHub Pages: https://mosobande.github.io/ogiri

## How Versioning Works

### Version Management Flow

```text
1. Update .ogiri-version file
         ↓
2. Commit and push to main
         ↓
3. GitHub Actions docs.yml workflow triggers
         ↓
4. Build docs with mkdocs
         ↓
5. Deploy with mike (creates version directory)
         ↓
6. Update "latest" alias to point to new version
         ↓
7. Available at https://mosobande.github.io/ogiri
```

### Version Directory Structure

Mike creates a directory structure like:

```text
gh-pages (branch)
├── index.html              # Latest version (from "latest" alias)
├── 1.1.0/                  # Version 1.1.0
│   ├── index.html
│   └── ...
├── 1.0.0/                  # Previous version
│   ├── index.html
│   └── ...
└── versions.json           # Version metadata (used by version selector)
```

## Configuration Files

### 1. mkdocs.yml

Version provider configured in `extra` section:

```yaml
extra:
  version:
    provider: mike
    default: latest
```

**What this does:**

- `provider: mike` - Enable mike version selector
- `default: latest` - Show "latest" as default option

### 2. .ogiri-version

Single line file containing the current version:

```text
1.1.0
```

This is read by:

- Build scripts
- GitHub Actions workflow
- Version management scripts

### 3. .github/workflows/docs.yml

Three jobs configured:

**Job 1: build**

- Installs mkdocs and mike
- Reads version from `.ogiri-version`
- Builds static site with mkdocs

**Job 2: deploy**

- Uploads site to GitHub Pages artifact
- Only runs on main/ori branch pushes

**Job 3: version** (NEW)

- Runs after build and deploy complete
- Uses mike to deploy versioned docs
- Command: `mike deploy --push --update-aliases "$VERSION" latest`
- Updates "latest" alias to new version

## Usage

### Release a New Version

1. **Update version file:**

   ```bash
   echo "1.2.0" > .ogiri-version
   ```

2. **Commit and push:**

   ```bash
   git add .ogiri-version
   git commit -m "chore: bump version to 1.2.0"
   git push origin main
   ```

3. **Automatic deployment:**
   - Workflow triggers automatically
   - Docs built and deployed to https://mosobande.github.io/ogiri/1.2.0
   - "latest" alias updated to point to 1.2.0

### Manual Deployment

Deploy a version manually without pushing to main:

```bash
# Install dependencies
pip install mkdocs-material mkdocs-mike

# Build documentation
mkdocs build

# Deploy with mike
mike deploy 1.2.0 latest

# Or use the helper script
./scripts/publish-docs.sh 1.2.0
```

### View Version Metadata

Mike creates a `versions.json` file on the gh-pages branch:

```bash
git checkout gh-pages
cat versions.json
```

Shows available versions and their aliases.

## Mike Commands

### Deploy a version

```bash
mike deploy 1.2.0 latest
```

Deploys documentation as version 1.2.0 and updates the "latest" alias.

### Deploy with message

```bash
mike deploy 1.2.0 latest --title "Release 1.2.0"
```

### List versions

```bash
mike list
```

Shows all deployed versions and their aliases.

### Delete a version

```bash
mike delete 1.0.0
```

Removes version 1.0.0 from the site.

### Set aliases

```bash
mike alias latest 1.2.0
mike alias stable 1.1.0
```

Create custom version aliases.

### Push to remote

```bash
mike deploy --push 1.2.0 latest
```

Automatically commits and pushes changes to gh-pages branch.

## Version Selector (Material Theme)

The version dropdown appears in the top-right corner of the Material theme.

**Features:**

- Shows all available versions
- Shows version aliases
- Clicking a version navigates to that version's docs
- Built from `versions.json` on gh-pages branch

**Content:**

```json
[
  {
    "version": "1.2.0",
    "title": "1.2.0",
    "aliases": ["latest"]
  },
  {
    "version": "1.1.0",
    "title": "1.1.0",
    "aliases": []
  }
]
```

## GitHub Pages Integration

### Required Permissions

The `version` job requires:

```yaml
permissions:
  contents: write # Write to gh-pages branch
  pages: write # Write to GitHub Pages
  id-token: write # For GitHub Pages deployment
```

### Branch Configuration

GitHub Pages must be set to deploy from the `gh-pages` branch:

1. Settings → Pages
2. Source: Deploy from a branch
3. Branch: `gh-pages`
4. Folder: `/ (root)`

The `gh-pages` branch is created automatically by the workflow on first deployment.

### Automatic Deployment

The workflow only deploys on pushes to `main` or `ori` branches:

```yaml
if: github.event_name == 'push' && github.ref == 'refs/heads/main'
```

Pull requests only trigger the build job (validation).

## Troubleshooting

### Version doesn't appear in dropdown

1. Check versions.json exists on gh-pages branch:

   ```bash
   git checkout gh-pages
   ls versions.json
   ```

2. Verify mike command ran successfully:

   - Check Actions workflow logs
   - Look for "Deploy versioned docs" step

3. Clear browser cache and reload

### Docs not deploying

1. Check workflow permissions (Settings → Actions → General)
2. Verify gh-pages branch exists
3. Check Actions tab for workflow errors

### Can't deploy manually

1. Install mike: `pip install mkdocs-mike`
2. Build first: `mkdocs build`
3. Check git is configured: `git config user.email`
4. Run with push flag: `mike deploy --push 1.2.0 latest`

### Wrong version showing as latest

Run alias command explicitly:

```bash
mike alias latest 1.2.0
```

Or update in workflow and redeploy.

## Best Practices

1. **Keep .ogiri-version in sync with release version**

   - Update before releasing
   - One source of truth

2. **Use semantic versioning**

   - 1.2.0 format (MAJOR.MINOR.PATCH)
   - Makes version ordering clear

3. **Tag releases**

   ```bash
   git tag v1.2.0
   git push origin v1.2.0
   ```

4. **Update docs before release**

   - Update CHANGELOG.md
   - Update configuration examples
   - Verify all docs build locally

5. **Keep old versions**

   - Users may still run older versions
   - Keep documentation accessible
   - Delete only if necessary

6. **Monitor versions.json**
   - Ensures versions are properly indexed
   - Check occasionally for consistency

## Advanced Configuration

### Custom version aliases

Create multiple aliases for a version:

```bash
mike deploy --push 1.2.0 latest stable production
```

This creates three ways to access version 1.2.0:

- `/{version}/` - Latest (current release)
- `/stable/` - Stable version
- `/production/` - Production docs

### Development version

Deploy development docs separately:

```bash
mike deploy --push dev development --title "Development (unstable)"
```

Makes dev docs available at `/dev/` without affecting stable versions.

### Scheduled deployments

Create separate workflow to update docs on schedule:

```yaml
on:
  schedule:
    - cron: '0 12 * * 0'  # Weekly
```

Useful for updating docs from a separate "docs" branch.

## Integration with Release Process

### Release workflow

1. Update version: `echo "1.2.0" > .ogiri-version`
2. Commit: `git commit -m "chore: bump version to 1.2.0"`
3. Tag: `git tag v1.2.0`
4. Push: `git push origin main v1.2.0`
5. Docs auto-deploy with version 1.2.0
6. Create GitHub release linked to tag

### Rollback

To rollback to a previous version:

1. Update .ogiri-version to previous version
2. Update latest alias:
   ```bash
   mike alias latest 1.1.0
   ```

## Resources

- [Mike Documentation](https://github.com/jimporter/mike)
- [Material Theme Versioning](https://squidfunk.github.io/mkdocs-material/setup/setting-up-versioning/)
- [GitHub Pages with MkDocs](https://squidfunk.github.io/mkdocs-material/publishing-your-site/#github-pages)
