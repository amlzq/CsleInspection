语言: 简体中文 | [English](./README.md)

检查字符串字面量表达式是否包含中文汉字，并可以在不同中文地区（中国大陆、台湾、香港）的用字和习惯用语之间快速转换，例如 程序 ⇄ 程式。

### 功能

- **边写边检查**：在编辑器中高亮字符串字面量（以及可选的文档注释）里的中文，并提供快速修复，转换为您选择的地区用法。Code | Inspect Code 也会报告这些结果。
- **自选地区用法**：设置要检查的中文地区用法和要转换成的用法（简体中文、中文（台湾）、中文（香港）、繁体中文）。默认跟随 IDE 显示语言，两者也可以相同。
- **排除不想检查的位置**：按语言排除调用、标签、属性或键，例如 `print`、`console.log`、`script`；使用标签式输入，添加、删除和粘贴都很方便。
- **自定义词典**：为每个快速修复目标添加自己的短语映射，优先于内置词典。可在设置页编辑、搜索、导入和导出（最多 20,000 条）。
- **检查所有文件**：通过 **Tools | 中文表达式检查 | 检查所有文件** 扫描整个项目。结果按文件分组显示在「中文表达式检查」工具窗口中，双击条目可跳转到代码。取消勾选想保留的条目，然后点击 **全部接受** 转换所有已勾选的条目。一次撤销即可还原整批改动。
- **打开项目后自动检查**：在 **Settings | Tools | 中文表达式检查 | 项目检查** 中开启（默认关闭），项目打开、索引完成后会在后台执行同样的检查。发现结果时弹出通知，点击「查看」打开结果窗口，不会自动展开工具窗口。
- **按项目或共享设置**：通过 Scheme 下拉框，设置可以按项目保存，也可以在所有项目间共享。

### 支持语言

- Dart
- Java
- Kotlin
- JavaScript and TypeScript
- PHP
- Python
- HTML/CSS
- XML
- JSON

如果你想更多支持的语言，可以创建 [issue](https://github.com/amlzq/CsleInspection/issues)。

### 安装

[IntelliJ 插件](https://plugins.jetbrains.com/plugin/26834-chinese-expression-inspection)

[VSCode 扩展](https://marketplace.visualstudio.com/items?itemName=amlzq.csle-diagnostic)

### 感谢

[Opencc4j](https://github.com/houbb/opencc4j)
