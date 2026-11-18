# Contributing

Thanks for your interest in contributing to Chinese Expression Inspection!

## Getting started

```bash
git clone https://github.com/amlzq/CsleInspection.git
cd CsleInspection
./gradlew buildPlugin
```

Sources are in `src/main/kotlin/` and `src/main/resources/`; tests mirror the same package layout
under `src/test/kotlin/`. User-visible text lives in `src/main/resources/messages/`
(`CsleBundle.properties` plus the `zh`, `zh_HK`, `zh_MO` and `zh_TW` variants).

The first build downloads the IntelliJ Platform distribution (`intellijIdea("2025.3.6.1")`) and can
take several minutes.

Run a sandbox IDE with the plugin installed:

```bash
./gradlew runIde
```

## Before opening a PR

```bash
./gradlew test
./gradlew verifyPlugin
```

`./gradlew test` runs the platform tests without an IDE instance; `./gradlew verifyPlugin` checks
the built plugin against the JetBrains plugin verifier.

Please add or update tests for behavior changes, keep all bundle locale files in sync, and add an
entry to `changeNotes.md` under `Unreleased` (`**FEATURE**`, `**IMPROVEMENT**` or `**BUGFIX**`).

## Architecture rules

- Per-language inspections live exclusively in `lang/Csle<Lang>Inspection.kt`; nothing outside those
  files may reference an optional plugin's API (`KtFile`, `PyFile`, `JSFile`, PHP/Dart PSI classes, …).
- Each optional plugin is declared in `build.gradle.kts`, in `plugin.xml` as
  `<depends optional="true" config-file=…>`, and in its own `com.amlzq.csle.inspection-with<Plugin>.xml`.
- Read settings with `CsleSettings.effective()`, never `CsleSettings.instance`.
- Do not add runtime dependencies; conversion goes through `opencc4j`.

See [AGENTS.md](./AGENTS.md) for the full guidance.

## Commit and PR title convention

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
type(scope): short summary
```

- **type**: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`
- **scope**: `lang`, `scan`, `settings`, `quickfix`, `dictionary`, `l10n`
  (optional when a change spans several areas)
- Append `!` for breaking changes, e.g. `refactor(settings)!: drop the shared scheme`

PR titles follow the same convention.

## Reporting issues

Open an issue at <https://github.com/amlzq/CsleInspection/issues> with the IDE version and build
number, the plugin version, and the language of the file being inspected.
