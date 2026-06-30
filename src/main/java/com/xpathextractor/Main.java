package com.xpathextractor;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * Entry point for the Web Element XPath Extractor.
 *
 * Usage:
 *   java -jar web-xpath-extractor-1.0.0.jar [url] [maxElements] [outputFile]
 *
 * All arguments are optional — the program will prompt interactively if omitted.
 *
 * Examples:
 *   java -jar web-xpath-extractor-1.0.0.jar
 *   java -jar web-xpath-extractor-1.0.0.jar https://example.com
 *   java -jar web-xpath-extractor-1.0.0.jar https://example.com 200 output.xlsx
 */
public class Main {

    private static final int DEFAULT_MAX_ELEMENTS = 500;

    public static void main(String[] args) {
        printBanner();

        String url         = null;
        int    maxElements = DEFAULT_MAX_ELEMENTS;
        String outputFile  = null;

        // Parse CLI arguments
        if (args.length >= 1) url         = args[0].trim();
        if (args.length >= 2) { try { maxElements = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {} }
        if (args.length >= 3) outputFile  = args[2].trim();

        // Interactive prompts for missing arguments
        Scanner scanner = new Scanner(System.in);

        if (url == null || url.isEmpty()) {
            System.out.print("Enter URL to extract (e.g. https://example.com): ");
            url = scanner.nextLine().trim();
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        System.out.printf("Max elements to extract [%d, 0 = unlimited]: ", DEFAULT_MAX_ELEMENTS);
        String maxInput = scanner.nextLine().trim();
        if (!maxInput.isEmpty()) {
            try { maxElements = Integer.parseInt(maxInput); } catch (NumberFormatException ignored) {}
        }

        if (outputFile == null || outputFile.isEmpty()) {
            String defaultName = XPathExtractor.hostnameFromUrl(url) + "_elements.xlsx";
            System.out.printf("Output file name [%s]: ", defaultName);
            String nameInput = scanner.nextLine().trim();
            outputFile = nameInput.isEmpty() ? defaultName : nameInput;
        }

        if (!outputFile.toLowerCase().endsWith(".xlsx")) {
            outputFile += ".xlsx";
        }

        // Run the extraction
        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Target  : " + url);
        System.out.println("Limit   : " + (maxElements <= 0 ? "unlimited" : maxElements));
        System.out.println("Output  : " + outputFile);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        try {
            XPathExtractor extractor = new XPathExtractor();
            List<WebElement> elements = extractor.extract(url, maxElements);

            if (elements.isEmpty()) {
                System.out.println("No elements found. The page may be empty or behind authentication.");
                System.exit(1);
            }

            System.out.println();
            printSummary(elements);

            ExcelExporter exporter = new ExcelExporter();
            exporter.export(elements, outputFile);

            System.out.println();
            System.out.println("✓ Done! Open '" + outputFile + "' to see the results.");
            System.out.println("  Sheet 1 – Elements : full list with XPaths");
            System.out.println("  Sheet 2 – Summary  : tag frequency breakdown");

        } catch (IOException e) {
            System.err.println();
            System.err.println("✗ Error: " + e.getMessage());
            System.err.println("  Check that the URL is reachable and you have write permission for the output file.");
            System.exit(1);
        }
    }

    private static void printSummary(List<WebElement> elements) {
        long withText  = elements.stream().filter(e -> !e.getTextContent().isEmpty()).count();
        long withAttrs = elements.stream().filter(e -> !e.getAttributes().isEmpty()).count();
        long uniqueTags = elements.stream().map(WebElement::getTag).distinct().count();

        System.out.println("Extraction complete:");
        System.out.printf("  %-22s %d%n", "Total elements",  elements.size());
        System.out.printf("  %-22s %d%n", "Unique tag types", uniqueTags);
        System.out.printf("  %-22s %d%n", "Elements with text", withText);
        System.out.printf("  %-22s %d%n", "Elements with attrs", withAttrs);
        System.out.println();
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║       Web Element XPath Extractor  v1.0          ║");
        System.out.println("║  Extracts HTML elements + relative XPaths         ║");
        System.out.println("║  and exports them to a formatted Excel file.      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();
    }
}
