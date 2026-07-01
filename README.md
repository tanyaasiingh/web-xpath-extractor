# Web Element XPath Extractor

A Java command-line tool that crawls a webpage, identifies its meaningful **visible text elements** and **icons**, generates a robust, document-verified-unique **relative XPath** for each one, and exports everything to a formatted Excel (`.xlsx`) report.

Built for QA engineers, test automation developers, and anyone who needs a fast XPath inventory of a page without manually inspecting it element by element in DevTools.

---

## Features

- 🔍 **Smart element detection** — reports only elements that matter: visible text nodes and icons (SVGs, icon-fonts like Font Awesome/Material Icons, small icon images, icon buttons)
- 🚫 **Hidden-element filtering** — skips elements marked `hidden`, `aria-hidden="true"`, `display:none`, `visibility:hidden`, or `opacity:0`
- 🎯 **Best-possible XPath generation** — tries a prioritized cascade of locator strategies and verifies each candidate is unique across the whole document:
  1. `id`
  2. `data-testid` / `data-test` / `data-qa` / `data-cy`
  3. `name`
  4. `aria-label`
  5. `title`
  6. `placeholder`
  7. `alt`
  8. `for`
  9. Exact visible text — `text()=`
  10. Partial visible text — `contains(text(), ...)`
  11. A single distinctive CSS class
  12. Full positional path from `<body>`, anchored to nearest unique-id ancestor
- 📊 **Formatted Excel output** — two sheets:
  - **Elements** — `#`, `Tag`, `Attributes`, `Text Content`, `Relative XPath`
  - **Summary** — tag frequency breakdown with counts and percentages
- ⚙️ **Configurable element cap** — limit how many elements to extract (default 500, or unlimited)
- 💻 **Interactive or scriptable** — run with prompts, or pass arguments for automation/CI use

---

## Requirements

| Tool | Minimum Version |
|---|---|
| [Java (JDK)](https://adoptium.net) | 11+ |
| [Maven](https://maven.apache.org/download.cgi) | 3.6+ |

Verify both are installed:

```bash
java -version
mvn -version
```

---

## Project Structure

```
xpath-extractor/
├── pom.xml
└── src/main/java/com/xpathextractor/
    ├── Main.java            # CLI entry point
    ├── XPathExtractor.java  # Fetches the page and generates XPaths
    ├── ExcelExporter.java   # Writes the styled .xlsx report
    └── WebElement.java      # Data model for one extracted element
```

### Dependencies

| Library | Purpose |
|---|---|
| [Jsoup](https://jsoup.org/) | Fetches and parses HTML, traverses the DOM |
| [Apache POI](https://poi.apache.org/) | Generates the styled `.xlsx` output |
| [Jackson](https://github.com/FasterXML/jackson) | JSON support |

---

## Build

```bash
mvn clean package
```

Produces a runnable JAR at `target/web-xpath-extractor-1.0.0.jar`.

---

## Usage

### Interactive mode

```bash
java -jar target/web-xpath-extractor-1.0.0.jar
```

You'll be prompted for:
1. **URL** to extract (e.g. `https://example.com`)
2. **Max elements** (press Enter for default 500, or `0` for unlimited)
3. **Output filename** (press Enter for auto-generated name)

### Non-interactive mode

```bash
java -jar target/web-xpath-extractor-1.0.0.jar <url> [maxElements] [outputFile]
```

Examples:

```bash
java -jar target/web-xpath-extractor-1.0.0.jar https://example.com
java -jar target/web-xpath-extractor-1.0.0.jar https://example.com 200 report.xlsx
```

---

## Output

### Sheet 1 — Elements

| # | Tag | Attributes | Text Content | Relative XPath |
|---|---|---|---|---|
| 1 | `button` | `id='submit-btn'` | `Submit` | `//button[@id='submit-btn']` |
| 2 | `svg (icon)` | `class='icon-search'` | `[icon]` | `//svg[contains(@class,'icon-search')]` |
| 3 | `a` | `href='/about'` | `About Us` | `//a[text()='About Us']` |

Icon rows are flagged with `(icon)` in the Tag column and `[icon]` in Text Content for easy filtering.

### Sheet 2 — Summary

Tag frequency table sorted by count, with percentages and overall stats.

---

## How It Works

1. **Fetch** — Jsoup downloads the page's static HTML and parses the DOM
2. **Classify** — Each element is checked for visibility, then classified as `TEXT`, `ICON`, or skipped
3. **Generate XPath** — Tries each locator strategy in priority order, verifying uniqueness against the whole document before accepting it
4. **Export** — Results written into a styled two-sheet Excel workbook

---

## Known Limitations

- **Static HTML only** — JavaScript-rendered content won't be visible. For SPAs, consider extending with Selenium or Playwright to fetch the rendered DOM
- **Stylesheet-driven hiding not detected** — only inline styles and HTML attributes are checked, not external CSS rules
- **Login-gated or bot-protected pages** may return incomplete content

---

## Customization

Easy to tune in `XPathExtractor.java`:

- `LOCATOR_ATTRS` — reorder or extend locator attribute priority
- `ICON_CLASS_HINTS` — add more icon-font class naming conventions
- `isLikelyUtilityClass()` — adjust utility-class filtering regex
- `DEFAULT_MAX_ELEMENTS` in `Main.java` — change the default element cap

---

## License

This project is provided as-is for internal/personal use. Adapt freely.
