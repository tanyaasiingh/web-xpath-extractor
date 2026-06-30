package com.xpathextractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fetches a URL with Jsoup and extracts:
 *   (a) every element holding meaningful VISIBLE TEXT, and
 *   (b) every ICON element (svg, <i> icon-fonts, icon images, icon-buttons)
 * — each with a robust, document-verified-unique relative XPath.
 *
 * Elements that are hidden (display:none, visibility:hidden, hidden attr,
 * aria-hidden="true", type="hidden") are skipped, since they're not
 * actually visible on the rendered page.
 *
 * XPath generation strategy (in priority order — first match wins):
 *   1. //tag[@id='x']                     — only if unique in the document
 *   2. //tag[@data-testid='x' | data-test | data-qa | data-cy]  — only if unique
 *   3. //tag[@name='x']                   — only if unique
 *   4. //tag[@aria-label='x']             — only if unique (great for icon buttons)
 *   5. //tag[@title='x']                  — only if unique
 *   6. //tag[@placeholder='x']            — only if unique
 *   7. //tag[@alt='x']                    — only if unique (icon/img alt text)
 *   8. //tag[text()='exact text']         — only if unique, short visible text
 *   9. //tag[contains(@class,'x')]        — one distinctive (non-utility) class, unique
 *  10. Full positional path from <body>, anchored to nearest unique-id ancestor.
 *
 * Every candidate is verified against the WHOLE document before being
 * accepted, so the generated locator is guaranteed to resolve to exactly
 * one node wherever a unique strategy exists.
 */
public class XPathExtractor {

    // Tags that never render visible content on their own
    private static final Set<String> SKIP_TAGS = new HashSet<>(Arrays.asList(
            "script", "style", "meta", "link", "head", "#root", "html",
            "noscript", "template", "defs", "clippath", "lineargradient",
            "radialgradient", "br", "hr", "wbr", "title"
    ));

    // Tags that commonly render an ICON (graphical glyph, not text)
    private static final Set<String> ICON_TAGS = new HashSet<>(Arrays.asList(
            "svg", "path", "use", "i", "icon"
    ));

    // Class-name / attribute fragments that strongly indicate an icon-font glyph
    private static final List<String> ICON_CLASS_HINTS = Arrays.asList(
            "icon", "fa-", "material-icons", "glyphicon", "bi-", "feather",
            "iconfont", "ico-", "svg-icon"
    );

    // Attributes worth capturing in the report (display only)
    private static final List<String> INTERESTING_ATTRS = Arrays.asList(
            "id", "class", "name", "type", "href", "src",
            "alt", "placeholder", "role", "aria-label", "title", "value",
            "data-testid"
    );

    // Locator-priority attributes used when BUILDING the xpath (order matters)
    private static final List<String> LOCATOR_ATTRS = Arrays.asList(
            "data-testid", "data-test", "data-qa", "data-cy",
            "name", "aria-label", "title", "placeholder", "alt", "for"
    );

    private Document doc; // kept so we can run document-wide uniqueness checks

    /**
     * Download the page at {@code url} and extract visible-text and icon elements.
     *
     * @param url         Target URL (must include scheme, e.g. https://)
     * @param maxElements Maximum number of elements to return (0 = unlimited)
     * @return Ordered list of extracted WebElement objects
     */
    public List<WebElement> extract(String url, int maxElements) throws IOException {
        System.out.println("Fetching: " + url);

        doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .timeout(20_000)
                .followRedirects(true)
                .maxBodySize(0)
                .get();

        System.out.println("Page title: " + doc.title());

        List<WebElement> results = new ArrayList<>();
        int[] counter = {0};

        traverseElement(doc.body(), results, counter, maxElements);

        System.out.println("Extracted " + results.size() + " visible text/icon elements.");
        return results;
    }

    private void traverseElement(Element el, List<WebElement> results,
                                  int[] counter, int maxElements) {
        if (el == null) return;
        if (maxElements > 0 && counter[0] >= maxElements) return;

        String tag = el.tagName().toLowerCase();

        if (!SKIP_TAGS.contains(tag) && !isHidden(el)) {
            ElementKind kind = classify(el, tag);
            if (kind != ElementKind.NONE) {
                counter[0]++;
                String attrs = buildAttributeString(el);
                String text  = kind == ElementKind.ICON ? "[icon]" : extractText(el);
                String xpath = buildBestXPath(el);
                String tagLabel = kind == ElementKind.ICON ? tag + " (icon)" : tag;

                results.add(new WebElement(counter[0], tagLabel, attrs, text, xpath));
            }
        }

        // Don't descend into icon glyph internals (svg's <path>/<use> children are noise)
        if (tag.equals("svg")) return;

        for (Element child : el.children()) {
            if (maxElements > 0 && counter[0] >= maxElements) break;
            traverseElement(child, results, counter, maxElements);
        }
    }

    private enum ElementKind { TEXT, ICON, NONE }

    /**
     * Classifies an element as carrying meaningful visible TEXT, being an ICON,
     * or neither (pure layout wrapper — skip it).
     */
    private ElementKind classify(Element el, String tag) {
        // 1. Icon detection
        if (isIcon(el, tag)) return ElementKind.ICON;

        // 2. Visible text detection — direct own text only (avoids duplicate
        //    reporting of the same sentence at every ancestor level)
        String ownText = el.ownText().trim();
        if (!ownText.isEmpty()) return ElementKind.TEXT;

        // 3. Element with no own text but no children at all and no icon signal — skip
        return ElementKind.NONE;
    }

    /**
     * Detects icons: <svg>, icon-font glyphs (<i class="fa-...">, etc.),
     * small decorative <img>, or any element whose class/attrs scream "icon"
     * even if it has zero text content (icon buttons often have only an
     * aria-label, no visible text).
     */
    private boolean isIcon(Element el, String tag) {
        if (tag.equals("svg")) return true;

        if (tag.equals("img")) {
            String alt = el.attr("alt").toLowerCase();
            String src = el.attr("src").toLowerCase();
            String cls = el.attr("class").toLowerCase();
            if (alt.contains("icon") || src.contains("icon") || classHasIconHint(cls)) return true;
            // Small images (explicit width/height <= 32) are usually icons too
            String w = el.attr("width"), h = el.attr("height");
            if (isSmallDimension(w) || isSmallDimension(h)) return true;
            return false;
        }

        if (tag.equals("i")) {
            // <i> tags with no text are almost always icon-font glyphs
            return el.ownText().trim().isEmpty();
        }

        String cls = el.attr("class").toLowerCase();
        if (classHasIconHint(cls) && el.ownText().trim().isEmpty()) return true;

        // role="img" with aria-label and no text = icon
        if ("img".equalsIgnoreCase(el.attr("role")) && el.ownText().trim().isEmpty()) return true;

        return false;
    }

    private boolean classHasIconHint(String cls) {
        for (String hint : ICON_CLASS_HINTS) {
            if (cls.contains(hint)) return true;
        }
        return false;
    }

    private boolean isSmallDimension(String dim) {
        try {
            return !dim.isEmpty() && Integer.parseInt(dim.replaceAll("[^0-9]", "")) <= 32;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns true if the element is not actually visible on the rendered page,
     * based on static HTML signals (Jsoup can't run CSS/JS, so this catches
     * the common inline/attribute cases — not stylesheet-driven hiding).
     */
    private boolean isHidden(Element el) {
        if (el.hasAttr("hidden")) return true;
        if ("true".equalsIgnoreCase(el.attr("aria-hidden"))) return true;
        if ("hidden".equalsIgnoreCase(el.attr("type"))) return true;

        String style = el.attr("style").toLowerCase().replaceAll("\\s+", "");
        if (style.contains("display:none") || style.contains("visibility:hidden")
                || style.contains("opacity:0")) return true;

        return false;
    }

    /**
     * Builds a concise string of the element's most useful attributes.
     */
    private String buildAttributeString(Element el) {
        StringBuilder sb = new StringBuilder();
        for (String attrName : INTERESTING_ATTRS) {
            String val = el.attr(attrName);
            if (val != null && !val.isEmpty()) {
                if (sb.length() > 0) sb.append("  ");
                if ("class".equals(attrName) && val.length() > 60) {
                    val = val.substring(0, 57) + "...";
                }
                sb.append(attrName).append("='").append(val).append("'");
            }
        }
        for (Attribute attr : el.attributes()) {
            String name = attr.getKey();
            if (name.startsWith("data-") && !INTERESTING_ATTRS.contains(name)) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(name).append("='").append(attr.getValue()).append("'");
                if (sb.length() > 120) break;
            }
        }
        String result = sb.toString();
        return result.length() > 150 ? result.substring(0, 147) + "..." : result;
    }

    private String extractText(Element el) {
        String ownText = el.ownText().trim();
        if (ownText.isEmpty()) return "";
        return ownText.length() > 100 ? ownText.substring(0, 97) + "..." : ownText;
    }

    // ── XPath generation ─────────────────────────────────────────────────────

    /**
     * Tries a prioritized list of strategies and returns the FIRST one that
     * resolves to exactly one element in the whole document. Falls back to a
     * full positional path if nothing unique is found.
     */
    private String buildBestXPath(Element el) {
        String tag = el.tagName();

        // 1. id
        String id = el.attr("id");
        if (!id.isEmpty() && isUniqueById(id)) {
            return "//" + tag + "[@id='" + escapeXPathLiteral(id) + "']";
        }

        // 2-7. other strong locator attributes, in priority order
        for (String attrName : LOCATOR_ATTRS) {
            String val = el.attr(attrName);
            if (val == null || val.isEmpty() || val.length() > 80) continue;
            if (isUniqueByAttr(tag, attrName, val)) {
                return "//" + tag + "[@" + attrName + "='" + escapeXPathLiteral(val) + "']";
            }
        }

        // 8. exact short visible text (best for text elements; also catches
        //    icon-font ligature text e.g. material-icons that render a word)
        String ownText = el.ownText().trim();
        if (!ownText.isEmpty() && ownText.length() <= 80 && !ownText.contains("'")) {
            if (isUniqueByText(tag, ownText)) {
                return "//" + tag + "[text()='" + ownText + "']";
            }
            // Not globally unique by exact text — try contains() combined with
            // tag, which still resolves to one node in many real cases
            if (isUniqueByContainsText(tag, ownText)) {
                return "//" + tag + "[contains(text(),'" + escapeXPathLiteral(ownText) + "')]";
            }
        }

        // 9. single distinctive class (skip noisy utility-class soups)
        String classAttr = el.attr("class");
        if (!classAttr.isEmpty()) {
            String[] classes = classAttr.trim().split("\\s+");
            for (String cls : classes) {
                if (cls.length() < 3 || isLikelyUtilityClass(cls)) continue;
                if (isUniqueByClass(tag, cls)) {
                    return "//" + tag + "[contains(@class,'" + escapeXPathLiteral(cls) + "')]";
                }
            }
        }

        // 10. fallback — full positional path from <body>, shortened by
        //     anchoring to the nearest ancestor that has a unique id
        return buildPositionalXPath(el);
    }

    private String buildPositionalXPath(Element el) {
        List<String> parts = new ArrayList<>();
        Element current = el;

        while (current != null
                && !current.tagName().equalsIgnoreCase("body")
                && !current.tagName().equalsIgnoreCase("html")
                && !current.tagName().equalsIgnoreCase("#root")
                && !current.tagName().equalsIgnoreCase("[document]")) {

            String tag  = current.tagName();
            String elId = current.attr("id");

            if (!elId.isEmpty() && isUniqueById(elId) && current != el) {
                parts.add(0, tag + "[@id='" + escapeXPathLiteral(elId) + "']");
                return "//" + String.join("/", parts);
            }

            int pos = siblingPosition(current);
            parts.add(0, pos > 1 ? tag + "[" + pos + "]" : tag);
            current = current.parent();
        }

        return "//" + String.join("/", parts);
    }

    private int siblingPosition(Element el) {
        Element parent = el.parent();
        if (parent == null) return 1;

        String tag = el.tagName();
        Elements siblings = parent.children().stream()
                .filter(s -> s.tagName().equals(tag))
                .collect(Collectors.toCollection(Elements::new));

        if (siblings.size() == 1) return 1;
        return siblings.indexOf(el) + 1;
    }

    // ── Uniqueness checks against the whole document ────────────────────────

    private boolean isUniqueById(String id) {
        Elements matches = doc.select("[id='" + cssEscape(id) + "']");
        return matches.size() == 1;
    }

    private boolean isUniqueByAttr(String tag, String attrName, String value) {
        Elements matches = doc.select(tag + "[" + attrName + "='" + cssEscape(value) + "']");
        return matches.size() == 1;
    }

    private boolean isUniqueByText(String tag, String text) {
        Elements candidates = doc.select(tag);
        int count = 0;
        for (Element c : candidates) {
            if (c.ownText().trim().equals(text)) {
                count++;
                if (count > 1) return false;
            }
        }
        return count == 1;
    }

    private boolean isUniqueByContainsText(String tag, String text) {
        Elements candidates = doc.select(tag);
        int count = 0;
        for (Element c : candidates) {
            if (c.ownText().trim().contains(text)) {
                count++;
                if (count > 1) return false;
            }
        }
        return count == 1;
    }

    private boolean isUniqueByClass(String tag, String cls) {
        Elements matches = doc.select(tag + "." + cssEscape(cls));
        return matches.size() == 1;
    }

    /** Filters out common utility/responsive classes (Tailwind, Bootstrap grid, etc.) that are never stable locators. */
    private boolean isLikelyUtilityClass(String cls) {
        return cls.matches("^(col|row|p|m|px|py|mx|my|pt|pb|pl|pr|mt|mb|ml|mr|w|h|d|flex|justify|items|text|bg|border|rounded|gap|grid|space)-.*")
                || cls.matches("^(sm|md|lg|xl|2xl):.*")
                || cls.equals("active") || cls.equals("disabled") || cls.equals("hidden")
                || cls.equals("show") || cls.equals("hide") || cls.equals("clearfix");
    }

    /** Escapes single quotes for safe embedding inside an XPath string literal. */
    private String escapeXPathLiteral(String value) {
        return value.replace("'", "\\'");
    }

    /** Minimal CSS-selector escaping for Jsoup's CSS-style select(). */
    private String cssEscape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    /**
     * Simple utility: extract just the hostname from a URL string,
     * safe for use as a filename component.
     */
    public static String hostnameFromUrl(String url) {
        try {
            return new URL(url).getHost().replace(".", "_");
        } catch (Exception e) {
            return "page";
        }
    }
}
