# Branching Strategy

This repository follows a structured branching model to ensure quality and streamline releases.

## Branch Overview

### `master` (Production/Release)
- **Purpose:** Production-ready code only
- **Who merges:** Code owners via PR after review
- **Protection:** CI/CD must pass before merge
- **Auto-actions:** 
  - On successful build: Create versioned release
  - Attach APK artifact to GitHub Release
  - Auto-update README.md with download link

### `dev` (Development)
- **Purpose:** Active development and integration
- **Who pushes:** All developers
- **CI/CD:** Build APK, run tests, upload artifact (no release)
- **Workflow:** This is where all features and fixes land first

## Workflow

```
Feature/Fix Branch
      ↓
   (develop locally)
      ↓
   Pull Request to `dev`
      ↓
   Code Review + CI Check
      ↓
   Merge to `dev`
      ↓
   Integration Testing (optional)
      ↓
   Pull Request to `master`
      ↓
   Final Review + CI Check
      ↓
   Merge to `master`
      ↓
   🎉 Auto-Release!
      ↓
   APK uploaded + README updated
```

## Naming Conventions

- **Feature branches:** `feature/feature-name`
- **Bug fixes:** `bugfix/bug-name`
- **Hotfixes:** `hotfix/critical-issue`

Example: `feature/voice-recording-quality`

## Commit Messages

Use clear, descriptive messages:
- `feat: add voice compression` (for features)
- `fix: resolve audio sync issue` (for bug fixes)
- `docs: update README` (for documentation)
- `chore: update dependencies` (for maintenance)

## Release Management

- **Versioning:** `vX.Y.Z` (semantic versioning)
- **Release notes:** Generated automatically with commit history
- **APK attachment:** Automatic on `master` merge
- **README update:** Automatic with latest download link

## CI/CD Triggers

| Branch | Trigger | Actions |
|--------|---------|---------|
| `dev` | Push | Build APK, Upload artifact |
| `master` | Push | Build APK, Create Release, Upload APK, Update README |

For questions or process improvements, open an issue or discuss with team leads.
