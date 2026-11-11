Inspects string literal expressions for Chinese characters and quickly converts them to the wording and characters used in another Chinese-speaking region (Mainland China, Taiwan, Hong Kong), e.g. 程序 ⇄ 程式.

### Features

- **Inspect as you type**: Chinese characters in string literals (and, optionally, doc comments) are highlighted in the editor, with a quick fix that converts them to the usage you chose. Code | Inspect Code reports them too.
- **Choose your regions**: set which regional Chinese usage to inspect and which to convert to (Simplified Chinese, Chinese (Taiwan), Chinese (Hong Kong) or Traditional Chinese). They default to the IDE display language.
- **Exclude what you don't want checked**: per language, skip calls, tags, properties or keys such as `print`, `console.log` or `script`.
- **Custom dictionary**: add your own phrase mappings for each quick fix target; they take priority over the built-in dictionary. Edit, search, import and export them from the settings page.
- **Inspect the whole project**: choose Tools | Chinese Expression Inspection | Inspect All Files, review the results grouped by file, and convert everything at once with Accept All. A single Undo reverts the whole batch.
- **Inspect on open**: optionally run the same inspection in the background after a project opens (off by default), with a notification that links to the results.
- **Per-project or shared settings**: store settings per project or share them across all projects.

### Supported languages

Dart, Java, Kotlin, JavaScript and TypeScript, PHP, Python, HTML/CSS, XML and JSON.

Need another language? Open an [issue](https://github.com/amlzq/CsleInspection/issues).

---

检查字符串字面量表达式是否包含中文汉字，并可以在不同中文地区（中国大陆、台湾、香港）的用字和习惯用语之间快速转换，例如 程序 ⇄ 程式。

### 功能

- **边写边检查**：在编辑器中高亮字符串字面量（以及可选的文档注释）里的中文，并提供快速修复，转换为您选择的地区用法。Code | Inspect Code 也会报告这些结果。
- **自选地区用法**：设置要检查的中文地区用法和要转换成的用法（简体中文、中文（台湾）、中文（香港）、繁体中文）。默认跟随 IDE 显示语言。
- **排除不想检查的位置**：按语言排除调用、标签、属性或键，例如 `print`、`console.log`、`script`。
- **自定义词典**：为每个快速修复目标添加自己的短语映射，优先于内置词典。可在设置页编辑、搜索、导入和导出。
- **检查所有文件**：通过 Tools | 中文表达式检查 | 检查所有文件 扫描整个项目，按文件分组查看结果，用「全部接受」一次转换所有已勾选的条目。一次撤销即可还原整批改动。
- **打开项目后自动检查**：可选在项目打开后于后台执行同样的检查（默认关闭），发现结果时弹出通知并链接到结果窗口。
- **按项目或共享设置**：设置可以按项目保存，也可以在所有项目间共享。

### 支持语言

Dart、Java、Kotlin、JavaScript 和 TypeScript、PHP、Python、HTML/CSS、XML、JSON。

需要其他语言？请创建 [issue](https://github.com/amlzq/CsleInspection/issues)。
