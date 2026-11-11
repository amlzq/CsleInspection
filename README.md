Language: English | [简体中文](./README_zh.md)

Inspects string literal expressions for Chinese characters and quickly converts them to the wording and characters used in another Chinese-speaking region (Mainland China, Taiwan, Hong Kong), e.g. 程序 ⇄ 程式.

### Features

- **Inspect as you type**: Chinese characters in string literals (and, optionally, doc comments) are highlighted in the editor, with a quick fix that converts them to the usage you chose. Code | Inspect Code reports them too.
- **Choose your regions**: set which regional Chinese usage to inspect and which to convert to (Simplified Chinese, Chinese (Taiwan), Chinese (Hong Kong) or Traditional Chinese). They default to the IDE display language, and may be the same.
- **Exclude what you don't want checked**: per language, skip calls, tags, properties or keys such as `print`, `console.log` or `script`, using a chip input that is quick to add to, remove from and paste into.
- **Custom dictionary**: add your own phrase mappings for each quick fix target. They take priority over the built-in dictionary. Edit, search, import and export them from the settings page (up to 20,000 entries).
- **Inspect the whole project**: choose **Tools | Chinese Expression Inspection | Inspect All Files**. Results appear in the *Chinese Expression Inspection* tool window, grouped by file; double-click an entry to jump to the code. Uncheck anything you want to keep, then click **Accept All** to convert every checked occurrence. A single Undo reverts the whole batch.
- **Inspect on open**: turn on **Settings | Tools | Chinese Expression Inspection | Project inspection** to run the same inspection in the background after a project opens (off by default). If anything is found, a notification offers a **View** link to the results; the tool window is not opened for you.
- **Per-project or shared settings**: store settings per project or share them across all projects with the Scheme drop-down.

### Supported languages

- Dart
- Java
- Kotlin
- JavaScript and TypeScript
- PHP
- Python
- HTML/CSS
- XML
- JSON

If you want more supported languages, new an [issue](https://github.com/amlzq/CsleInspection/issues).

### Installation

[IntelliJ plugin](https://plugins.jetbrains.com/plugin/26834-chinese-expression-inspection)

[VSCode extension](https://marketplace.visualstudio.com/items?itemName=amlzq.csle-diagnostic)

### Thanks

[Opencc4j](https://github.com/houbb/opencc4j)
