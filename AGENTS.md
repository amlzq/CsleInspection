# AGENTS.md

Guidance for AI agents working in this repository.

## Project

IntelliJ Platform plugin "Chinese Expression Inspection" (id `com.amlzq.csle.inspection`): flags Chinese
characters in string literals and doc comments and converts them to the regional Chinese usage
(Simplified / Taiwan / Hong Kong / Traditional) selected in the settings.

- Kotlin only, JVM toolchain 21, Gradle wrapper, IntelliJ Platform Gradle Plugin 2.x
- Target platform: `intellijIdea("2025.3.6.1")`, `sinceBuild = 253`, no `untilBuild`
- Only runtime dependency: `opencc4j`
- Version lives in `gradle.properties`; user-facing release notes in `changeNotes.md`
- Text files uploaded to the Marketplace: `pluginDescription.md` (plugin page) and
  `gettingStarted.md` (turned into HTML by `generateGettingStarted`); `README.md` and
  `README.zh-CN.md` are the repository front pages
- Signing and publishing read `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` and
  `PUBLISH_TOKEN` from the environment; never hard-code or commit them

## Build, test, run

```bash
./gradlew test                 # JVM + platform tests, no IDE instance needed in advance
./gradlew test --tests "com.amlzq.csle.inspection.lang.CsleInspectionTest"
./gradlew buildPlugin          # zip in build/distributions
./gradlew runIde               # sandbox IDE with the plugin installed
./gradlew verifyPlugin         # JetBrains plugin verifier
./gradlew generateGettingStarted  # gettingStarted.md -> build/gettingStarted.html
```

The first build downloads the IDE distribution and can take several minutes.

## Architecture

```
src/main/kotlin/com/amlzq/csle/inspection/
  CsleLocalInspectionTool.kt      shared base inspection: entry point, cleaning, descriptor creation
  CsleLocalQuickFix.kt            shared quick fix base
  CsleAddToDictionary*.kt         quick fix + editor action for the custom dictionary
  lang/                           one inspection class per language, plus the HTML embedded-entry point
  scan/                           whole-project scan: scanner, runner, tool window, batch fixer
  settings/                       persistent state, configurable UI, chips input, glyphs
  util/                           bundle access, context, dictionary, helpers
src/main/resources/META-INF/
  plugin.xml                      base descriptor, hard dependency on com.intellij.modules.platform only
  com.amlzq.csle.inspection-with<Plugin>.xml   one descriptor per optional plugin
src/main/resources/messages/      CsleBundle.properties + zh, zh_HK, zh_MO, zh_TW
src/main/resources/inspectionDescriptions/
  Csle<Lang>.html                  description shown in Settings | Editor | Inspections, no locale suffix = en
  Csle<Lang>_<locale>.html         translated copies (same set of locales as the bundle)
  metaInformation.json             lists every inspection id with its codeQualityCategory
src/test/kotlin/                  BasePlatformTestCase tests mirroring the main packages
```

## Architecture rules

- Code that depends only on optional plugins must live exclusively in the inspection class for the
  corresponding language.
- Consequently, nothing outside `lang/Csle<Lang>Inspection.kt` may reference an optional plugin's API
  (`PyFile`, `KtFile`, `JSFile`, PHP/Dart PSI classes, …). Shared code in `CsleLocalInspectionTool`,
  `scan/`, `settings/` and `util/` must compile against the base platform only.
- Each optional plugin is declared in three places that must stay in sync: `build.gradle.kts`
  (`bundledPlugin`/`compatiblePlugin`), `plugin.xml` (`<depends optional="true" config-file=…>`), and
  its own `with<Plugin>.xml` descriptor. Never add a non-optional `<depends>` on a language plugin.
- Per-language inspections are registered only in the matching `with<Plugin>.xml`; keys come from the
  bundle.
- HTML `<script>` / `<style>` handling goes through the `com.amlzq.csle.inspection.embeddedInspector`
  extension point: `CsleMarkupInspection` stays plugin-agnostic and implementations are registered in
  `withJS.xml` / `withCSS.xml`.

## Adding support for a language

1. `build.gradle.kts`: add `bundledPlugin(...)` or `compatiblePlugin(...)`.
2. `plugin.xml`: add `<depends optional="true" config-file="com.amlzq.csle.inspection-with<Plugin>.xml">`.
3. New `META-INF/com.amlzq.csle.inspection-with<Plugin>.xml` with the `localInspection` entry.
4. New `lang/Csle<Lang>Inspection.kt` extending `CsleLocalInspectionTool`; keep plugin types inside it.
5. Settings: `excluded<Lang>` field in `CsleSettings` plus the `loadState` fallback to `excluded`,
   an `ExcludedTarget` entry in `CsleSettingsConfigurable`, and the UI wiring.
6. Bundle keys in all five locale files: inspection display name, inspection group, language target label.
7. `inspectionDescriptions/Csle<Lang>.html` plus one copy per locale; the file name must equal the
   `shortName` used in the `localInspection` entry, and the id is added to `metaInformation.json`.
8. Tests for highlight and quick fix; entry in `changeNotes.md`.

## Conventions

- Code style follows the existing files: 4-space indent, KDoc on classes and non-obvious members.
  Comments and KDoc in this codebase are written in Chinese — match the file you are editing.
- All user-visible text goes through `CsleBundle.message("key")`; add every new key to all
  `messages/CsleBundle*.properties` files (en, zh, zh_HK, zh_MO, zh_TW), not just the English one.
- Likewise update `inspectionDescriptions/Csle<Lang>*.html` for every locale when the wording of an
  inspection description changes.
- Read settings inside inspection code with `CsleSettings.effective()`, never `CsleSettings.instance`,
  so the project scheme is honoured.
- Do not introduce deprecated IntelliJ Platform APIs. Kotlin uses `JvmDefaultMode.NO_COMPATIBILITY`
  on purpose so interface bridge methods do not trip the plugin verifier — keep that setting.
- Avoid new runtime dependencies; the conversion itself is expected to go through `opencc4j`.
- Public inspection messages must keep working in batch mode (`isOnTheFly = false`).

## Tests

- Platform tests extend `com.amlzq.csle.inspection.CsleBasePlatformTest`, which resets the
  application-level settings before and after each case; keep new settings fields in that reset.
  Only pure-logic tests may be plain JUnit.
- Mirror the main source package layout under `src/test/kotlin`.

## Docs and releases

- Add user-facing changes under `# Unreleased` in `changeNotes.md` using `**FEATURE**`,
  `**IMPROVEMENT**` or `**BUGFIX**`; bump `version` in `gradle.properties` when releasing.
- Keep `README.md` and `README.zh-CN.md` in sync when features change.
- Commit messages and PR titles follow Conventional Commits: `type(scope): summary`, with types
  `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert` and
  scopes `lang`, `scan`, `settings`, `quickfix`, `dictionary`, `l10n`. Contributor-facing details
  (PR checklist, issue reporting) live in `CONTRIBUTING.md`; `CLAUDE.md` only points at this file.

## Do not

- Do not commit build output or caches (`build/`, `.gradle/`, `.intellijPlatform/`, `.kotlin/`, `.run/`).
- Do not restructure `lang/` shared logic into a new common class if it would reference optional plugins.
