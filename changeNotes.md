# Unreleased
- **FEATURE** add the "Add to custom dictionary" quick fix: fill in the target phrase and save it to the custom dictionary of the Quick fix target in the active settings scheme.
- **FEATURE** add "Add to Custom Dictionary" to the editor context menu for the selected text.
- **FEATURE** switch between the Quick fix target dictionaries inside the custom dictionary dialog, without changing the setting outside.
- **FEATURE** search the entries of the custom dictionary dialog.
- **IMPROVEMENT** the custom dictionary dialog offers Import, Export, Download template and Clear from a menu next to the search field.
- **IMPROVEMENT** importing a dictionary file with more than 20,000 entries is refused.

# 0.8.0
- **FEATURE** settings can now be stored per project (Project) or shared by all projects (Default (IDE)); switch between them with the Scheme drop-down on the settings page.
- **IMPROVEMENT** the Inspect and Quick fix glyphs now follow the IDE display language until you choose your own.
- **IMPROVEMENT** the Inspect and Quick fix glyphs may now be the same.
- **FEATURE** add your own phrase dictionary for each Quick fix target; its entries take priority over the built-in dictionary.
- **IMPROVEMENT** the excluded list is now a chip input that is quicker to add to, remove from and paste into.

# 0.7.0
- **FEATURE** add Tools | Chinese Expression Inspection | Inspect All Files: scan the whole project, review the results in a tool window, and convert every checked occurrence with Accept All (one Undo reverts the batch).
- **FEATURE** optionally scan the whole project automatically after it opens (Settings | Tools | Chinese Expression Inspection | Scan, off by default); a notification links to the results.
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