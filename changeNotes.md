# Next
- **FEATURE** add Tools | Chinese Expression Inspection | Inspect All Files: scan the whole project, review the results in a tool window, and convert every checked occurrence with Accept All (one Undo reverts the batch).
- **IMPROVEMENT** inspections now also report in batch mode, so Code | Inspect Code lists the occurrences.

# 0.6.0
- **IMPROVEMENT** share one implementation for the XML and HTML inspections.
- **BUGFIX** stop reporting Chinese text from comments in HTML and XML.
- **BUGFIX** inspect and fix Chinese text inside HTML `<script>` and `<style>` blocks.
- **BUGFIX** keep the CSS, JSON and markup quick fixes from corrupting the document.
- **BUGFIX** make doc-comment quick fixes work in JavaScript, PHP and Dart, and recognize Dart `///` comments.
- **BUGFIX** keep the original syntax in literal quick fixes (template literals, char literals, Python prefixes, multi-line JSX).
- **BUGFIX** inspect JSX attribute values again.
- **BUGFIX** honor the literal expression switch in the JavaScript, Python, PHP and Dart inspections.
- **BUGFIX** show the stored default target when resetting the settings page.
- **IMPROVEMENT** drive Python docstrings by the string-literal switch, since Python has no doc-comment syntax.
- **IMPROVEMENT** explain each inspection target on the settings page and let the excluded list resize with the panel.
- **IMPROVEMENT** name both glyphs and point to the inspection settings in the problem description.
- **IMPROVEMENT** translate inspection names and settings group labels.
- **BREAKING** require IntelliJ IDEA 2025.3 or later.

# 0.5.0
- **FEATURE** add doc-comment inspection and fixes (option)
- **FEATURE** add support for HTML/CSS, XML, JSON
- **REFACTOR** per-language excluded lists and target selector

# 0.4.0
- Re-run the inspection after modifying the glyph configuration.
- Support checking and converting tag attribute values in JSX files.
- Upgrading third-party dependencies.

# 0.3.1
- Added Traditional Chinese (Hong Kong) support.

# 0.3.0
- Add support for Python languages

# 0.2.1
- Fix dart multi-line string.

# 0.2.0
- Add support for PHP languages

# 0.1.0
- Add support for JavaScript and TypeScript languages

# 0.0.5
- sinceBuild 243

# 0.0.4
- Change compatibility range

# 0.0.3
- Fix compatibility error.

# 0.0.2
- Fix compatibility error.

# 0.0.1
- Basic features of plugins: inspection, quick fix, and setting.