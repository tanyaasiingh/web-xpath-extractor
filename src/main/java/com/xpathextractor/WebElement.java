package com.xpathextractor;

/**
 * Represents a single web element extracted from a page,
 * along with its computed relative XPath.
 */
public class WebElement {

    private final int index;
    private final String tag;
    private final String attributes;
    private final String textContent;
    private final String xpath;

    public WebElement(int index, String tag, String attributes, String textContent, String xpath) {
        this.index = index;
        this.tag = tag;
        this.attributes = attributes;
        this.textContent = textContent;
        this.xpath = xpath;
    }

    public int getIndex()        { return index; }
    public String getTag()       { return tag; }
    public String getAttributes(){ return attributes; }
    public String getTextContent(){ return textContent; }
    public String getXpath()     { return xpath; }

    @Override
    public String toString() {
        return String.format("[%d] <%s> %s | %s | %s", index, tag, attributes, textContent, xpath);
    }
}
