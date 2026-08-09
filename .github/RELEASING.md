# Automated releases

Two GitHub Actions run the release pipeline for `universal_printer_flutter`:

1. **`bump-and-tag.yml`** — on every push to `main` that touches `universal_printer_flutter/**`,
   it bumps the **patch** version in `pubspec.yaml`, prepends a `CHANGELOG.md` entry, commits back to
   `main` (`chore: release vX.Y.Z [skip ci]`), and pushes a `vX.Y.Z` tag.
2. **`publish.yml`** — the tag triggers a publish to **pub.dev** via pub.dev's OIDC automated
   publishing (no stored secrets).

Flow: **merge to `main` → auto bump + tag → auto publish**.

## One-time setup (required before the first automated publish)

On **pub.dev** → package `universal_printer_flutter` → **Admin** → **Automated publishing**:

- Enable **Publishing from GitHub Actions**
- **Repository:** `vikramvikraanth/-universal_printer_flutter`
- **Tag pattern:** `v{{version}}`

(You must be an uploader/admin of the package to do this.)

## Caveats

- **Patch-only bump.** Every qualifying push to `main` increments the patch (`0.0.4 → 0.0.5`). For a
  minor/major release, bump `version:` manually in the same commit — the workflow will tag whatever it
  finds.
- **Branch protection.** If `main` is protected, allow `github-actions[bot]` to push (or the bump push
  is rejected). Alternatively require the workflow to open a PR instead of pushing to `main`.
- **Every merge publishes.** Because publish is tag-triggered and the bump auto-tags, every qualifying
  merge ships a public, permanent pub.dev version. Keep unfinished work off `main` (feature branches +
  reviewed PRs) so you don't publish half-done changes.
- **Docs-only / non-package changes** under other paths won't trigger a release (path filter).

## Manual escape hatch

To skip a release on a given push to `main`, include `[skip ci]` in the commit subject.
To publish manually instead of via CI: `cd universal_printer_flutter && flutter pub publish`.
