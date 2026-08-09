# Releasing

## Auto flow (commit a version bump → publish)

`tag-on-version-change.yml` watches `main` for changes to `universal_printer_flutter/pubspec.yaml`.
When the `version:` has no matching `vX.Y.Z` tag, it creates + pushes that tag, which triggers
`publish.yml` → publishes to pub.dev (OIDC).

**So: bump `version:` in a commit, push to `main` → auto-tag → auto-publish.**

Two one-time prerequisites (both manual, only you can do them):

1. **pub.dev** → package → **Admin → Automated publishing** → enable GitHub Actions,
   repo `vikramvikraanth/-universal_printer_flutter`, tag pattern `v{{version}}`.
2. **GitHub** → repo → **Settings → Secrets and variables → Actions** → add secret **`RELEASE_PAT`**
   (a fine-grained PAT with *Contents: read/write* on this repo). Required because a tag pushed by the
   default `GITHUB_TOKEN` cannot trigger `publish.yml` (GitHub anti-recursion). Without it, the tag is
   still created but you must publish manually.

## Manual bump button (alternative)

Two GitHub Actions, both **manual/deliberate** — nothing lands on `main` or publishes on its own.

## 1. Bump the version — `bump.yml` (manual button)

GitHub → **Actions** → **Bump version (manual)** → **Run workflow** → pick `patch` / `minor` / `major`.

It:
1. creates a branch `release/vX.Y.Z`,
2. bumps `version:` in `universal_printer_flutter/pubspec.yaml` and prepends a `CHANGELOG.md` entry,
3. pushes the branch and **opens a PR to `main`**.

You review the PR (fill in the CHANGELOG), then merge it. Nothing is published yet.

## 2. Publish — `publish.yml` (tag-triggered)

After merging, publish by pushing the tag:

```bash
git checkout main && git pull
git tag v0.0.5 && git push origin v0.0.5
```

The tag triggers a publish to **pub.dev** via OIDC (no stored secrets).

### One-time pub.dev setup (before the first automated publish)

pub.dev → package `universal_printer_flutter` → **Admin → Automated publishing**:
- Enable **Publishing from GitHub Actions**
- Repository: `vikramvikraanth/-universal_printer_flutter`
- Tag pattern: `v{{version}}`

Until this is set, the publish job fails auth (bump/PR still work).

## Fully manual alternative

Skip both workflows entirely: bump `version:` yourself, then
`cd universal_printer_flutter && flutter pub publish`.
