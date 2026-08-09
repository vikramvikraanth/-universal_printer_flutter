# Releasing

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
