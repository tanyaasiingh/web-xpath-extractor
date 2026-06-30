package com.xpathextractor;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Writes a list of WebElements to an .xlsx file using Apache POI.
 *
 * Output structure:
 *  Sheet 1 – "Elements"   : full element list with XPaths
 *  Sheet 2 – "Summary"    : tag frequency counts
 */
public class ExcelExporter {

    // Column widths in units of 1/256th of a character (POI convention)
    private static final int[] COL_WIDTHS = {
        4 * 256,   // #
        14 * 256,  // Tag
        38 * 256,  // Attributes
        30 * 256,  // Text Content
        60 * 256   // Relative XPath
    };

    /**
     * Exports elements to the given file path.
     *
     * @param elements  List of elements to write
     * @param filePath  Destination .xlsx path
     */
    public void export(List<WebElement> elements, String filePath) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(filePath)) {

            writeElementsSheet(wb, elements);
            writeSummarySheet(wb, elements);

            wb.write(fos);
        }
        System.out.println("Excel file written to: " + filePath);
    }

    // ── Sheet 1: Elements ────────────────────────────────────────────────────

    private void writeElementsSheet(XSSFWorkbook wb, List<WebElement> elements) {
        XSSFSheet sheet = wb.createSheet("Elements");

        // Styles
        CellStyle headerStyle = makeHeaderStyle(wb);
        CellStyle tagStyle    = makeTagStyle(wb);
        CellStyle xpathStyle  = makeXpathStyle(wb);
        CellStyle bodyStyle   = makeBodyStyle(wb);
        CellStyle altStyle    = makeAltBodyStyle(wb);

        // Header row
        String[] headers = {"#", "Tag", "Attributes", "Text Content", "Relative XPath"};
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(18);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, COL_WIDTHS[i]);
        }

        // Freeze the header row
        sheet.createFreezePane(0, 1);

        // Auto-filter on the header row
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, elements.size(), 0, headers.length - 1));

        // Data rows
        for (int i = 0; i < elements.size(); i++) {
            WebElement el = elements.get(i);
            Row row = sheet.createRow(i + 1);
            row.setHeightInPoints(15);

            CellStyle even = (i % 2 == 0) ? bodyStyle : altStyle;

            setCell(row, 0, String.valueOf(el.getIndex()), even);
            setCell(row, 1, el.getTag(), tagStyle);
            setCell(row, 2, el.getAttributes(), even);
            setCell(row, 3, el.getTextContent(), even);
            setCell(row, 4, el.getXpath(), xpathStyle);
        }
    }

    // ── Sheet 2: Summary ─────────────────────────────────────────────────────

    private void writeSummarySheet(XSSFWorkbook wb, List<WebElement> elements) {
        XSSFSheet sheet = wb.createSheet("Summary");

        CellStyle headerStyle = makeHeaderStyle(wb);
        CellStyle bodyStyle   = makeBodyStyle(wb);
        CellStyle altStyle    = makeAltBodyStyle(wb);
        CellStyle numStyle    = makeNumStyle(wb);

        // Count tags
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (WebElement el : elements) {
            counts.merge(el.getTag(), 1, Integer::sum);
        }
        // Sort descending
        List<Map.Entry<String, Integer>> sorted = counts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(java.util.stream.Collectors.toList());

        // Header
        Row h = sheet.createRow(0);
        h.setHeightInPoints(18);
        setCell(h, 0, "Tag", headerStyle);
        setCell(h, 1, "Count", headerStyle);
        setCell(h, 2, "% of Total", headerStyle);
        sheet.setColumnWidth(0, 18 * 256);
        sheet.setColumnWidth(1, 12 * 256);
        sheet.setColumnWidth(2, 14 * 256);

        // Stats header
        Row statsHeader = sheet.createRow(sorted.size() + 2);
        setCell(statsHeader, 0, "Statistic", headerStyle);
        setCell(statsHeader, 1, "Value", headerStyle);

        // Data rows
        int total = elements.size();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            Row row = sheet.createRow(i + 1);
            CellStyle cs = (i % 2 == 0) ? bodyStyle : altStyle;
            setCell(row, 0, entry.getKey(), cs);

            Cell countCell = row.createCell(1);
            countCell.setCellValue(entry.getValue());
            countCell.setCellStyle(numStyle);

            Cell pctCell = row.createCell(2);
            pctCell.setCellValue((double) entry.getValue() / total);
            CellStyle pctStyle = wb.createCellStyle();
            pctStyle.cloneStyleFrom(numStyle);
            DataFormat fmt = wb.createDataFormat();
            pctStyle.setDataFormat(fmt.getFormat("0.0%"));
            pctCell.setCellStyle(pctStyle);
        }

        // Overall stats block
        int statsRow = sorted.size() + 3;
        writeStatRow(sheet, statsRow++, "Total elements", String.valueOf(total), bodyStyle, numStyle);
        writeStatRow(sheet, statsRow++, "Unique tags", String.valueOf(sorted.size()), bodyStyle, numStyle);
        writeStatRow(sheet, statsRow, "Elements with text",
                String.valueOf(elements.stream().filter(e -> !e.getTextContent().isEmpty()).count()),
                bodyStyle, numStyle);
    }

    private void writeStatRow(XSSFSheet sheet, int rowIdx, String label, String value,
                               CellStyle labelStyle, CellStyle valStyle) {
        Row row = sheet.createRow(rowIdx);
        setCell(row, 0, label, labelStyle);
        Cell c = row.createCell(1);
        c.setCellValue(value);
        c.setCellStyle(valStyle);
    }

    // ── Style factories ──────────────────────────────────────────────────────

    private CellStyle makeHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x18, (byte)0x5F, (byte)0xA5}, null)); // #185FA5
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        font.setFontName("Arial");
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(style, BorderStyle.THIN);
        return style;
    }

    private CellStyle makeBodyStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Arial");
        style.setFont(font);
        style.setWrapText(false);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(style, BorderStyle.HAIR);
        return style;
    }

    private CellStyle makeAltBodyStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        style.cloneStyleFrom(makeBodyStyle(wb));
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xF0, (byte)0xF4, (byte)0xFB}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle makeTagStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Arial");
        font.setColor(new XSSFColor(new byte[]{(byte)0x0C, (byte)0x44, (byte)0x7C}, null).getIndex());
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(style, BorderStyle.HAIR);
        return style;
    }

    private CellStyle makeXpathStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontName("Courier New");
        font.setFontHeightInPoints((short) 9);
        font.setColor(new XSSFColor(new byte[]{(byte)0x3B, (byte)0x6D, (byte)0x11}, null).getIndex());
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(style, BorderStyle.HAIR);
        return style;
    }

    private CellStyle makeNumStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setFontName("Arial");
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(style, BorderStyle.HAIR);
        return style;
    }

    private void setBorder(CellStyle style, BorderStyle bs) {
        style.setBorderTop(bs);
        style.setBorderBottom(bs);
        style.setBorderLeft(bs);
        style.setBorderRight(bs);
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }
}
