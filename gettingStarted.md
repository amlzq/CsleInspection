### 1. Install and restart

Go to **Settings | Plugins | Marketplace**, search for **Chinese Expression Inspection**, click **Install**, then restart the IDE.

### 2. See what it finds

Open any Java, Kotlin, JavaScript/TypeScript, PHP, Python, Dart, HTML/CSS, XML or JSON file. Chinese characters in string literals are highlighted by the inspection.

### 3. Convert with a quick fix

Put the caret on a highlighted string and press **Alt+Enter** (**Option+Return** on macOS), then choose the quick fix to convert it to the usage you want.

### 4. Choose your regions

Open **Settings | Tools | Chinese Expression Inspection**. Under **Inspect contains**, pick the regional Chinese usage you want to find, and under **quick fix to**, the usage you want to convert to (Simplified Chinese, Chinese (Taiwan), Chinese (Hong Kong) or Traditional Chinese).

### 5. Skip what you don't want checked

In **Excluded items**, pick a language and type the calls, tags or keys to skip, such as `print` or `console.log`, then press **Enter**.

### 6. Fix the whole project at once

Choose **Tools | Chinese Expression Inspection | Inspect All Files**. Review the results in the *Chinese Expression Inspection* tool window, uncheck anything you want to keep, then click **Accept All**. A single Undo reverts the whole batch.

### 7. Teach it your own wording

Select a phrase in the editor, right-click, and choose **Add to Custom Dictionary**. Your entries take priority over the built-in dictionary, and you can edit, search, import and export them under **Settings | Tools | Chinese Expression Inspection | Custom dictionary**.

---

### 1. 安装并重启

打开 **Settings | Plugins | Marketplace**，搜索 **Chinese Expression Inspection**，点击 **Install**，然后重启 IDE。

### 2. 查看检查结果

打开任意 Java、Kotlin、JavaScript/TypeScript、PHP、Python、Dart、HTML/CSS、XML 或 JSON 文件，字符串字面量里的中文会被高亮。

### 3. 用快速修复转换

把光标放在高亮的字符串上，按 **Alt+Enter**（macOS 为 **Option+Return**），选择快速修复，即可转换为您想要的地区用法。

### 4. 选择地区用法

打开 **Settings | Tools | 中文表达式检查**。在「检查包含」中选择要找出的中文地区用法，在「并快速修复为」中选择要转换成的用法（简体中文、中文（台湾）、中文（香港）、繁体中文）。

### 5. 排除不想检查的位置

在「排除项」中选择语言，输入要跳过的调用、标签或键，例如 `print`、`console.log`，按 **Enter** 添加。

### 6. 一次修复整个项目

选择 **Tools | 中文表达式检查 | 检查所有文件**，在「中文表达式检查」工具窗口中查看结果，取消勾选想保留的条目，然后点击「全部接受」。一次撤销即可还原整批改动。

### 7. 加入自己的用词

在编辑器中选中一个短语，右键选择「添加到自定义词典」。您添加的条目优先于内置词典，可以在 **Settings | Tools | 中文表达式检查 | 自定义词典** 中编辑、搜索、导入和导出。
