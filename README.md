Language: English | [简体中文](./README_zh.md)

csle, short for "Chinese String Literal Expression", inspect whether the string literal expression contains Chinese characters, and can quickly fix them between simplified or traditional glyphs.

In the plugin settings view, you can exclude strings in certain locations, such as print, if you don't want to inspect them.

In the plugin settings view, you can modify the Chinese glyphs you want to inspect and the glyphs to quickly fix.

### Inspect all files

Choose **Tools | Chinese Expression Inspection | Inspect All Files** to scan the whole project. The results appear in the *Chinese Expression Inspection* tool window, grouped by file; double-click an entry to jump to the code. Uncheck anything you want to keep, then click **Accept All** to convert every checked occurrence. A single Undo reverts the whole batch.

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
