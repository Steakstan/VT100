package org.msv.vt100.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.msv.vt100.OrderAutomation.FileExtractor;

import java.util.*;

public record ExcelOrderData(String orderNumber, String positionNumber, String deliveryDate, String confirmationNumber) {

    @Override
    public String toString() {
        return "Bestellnummer = " + orderNumber + ", Positionsnummer = " + positionNumber + ", Lieferdatum = " + deliveryDate + ", AB-Nummer = " + confirmationNumber;
    }

    public static class ColumnIndices {
        public final int orderCol;
        public final int posCol;
        public final int dateCol;
        public final int confirmationCol;

        public ColumnIndices(int orderCol, int posCol, int dateCol, int confirmationCol) {
            this.orderCol = orderCol;
            this.posCol = posCol;
            this.dateCol = dateCol;
            this.confirmationCol = confirmationCol;
        }
    }

    public static ColumnIndices detectAllColumns(Sheet sheet) {
        int orderCol = detectOrderColumn(sheet);
        int posCol = detectPositionColumn(sheet, orderCol);
        int dateCol = detectDeliveryDateColumn(sheet, posCol);
        int confCol = detectConfirmationColumn(sheet, posCol, dateCol);

        StringBuilder messageBuilder = new StringBuilder();
        if (orderCol == -1) {
            messageBuilder.append("\n- Spalte mit Bestellnummer nicht gefunden.");
        }
        if (posCol == -1) {
            messageBuilder.append("\n- Spalte mit Positionsnummer nicht gefunden.");
        }
        if (dateCol == -1) {
            messageBuilder.append("\n- Spalte mit Lieferdatum nicht gefunden.");
        }
        if ((orderCol == -1 || posCol == -1 || dateCol == -1)) {
            throw new IllegalStateException("Struktur der Excel-Tabelle unvollständig:" + messageBuilder);
        }

        if (dateCol - posCol > 1 && confCol == -1) {
            throw new IllegalStateException("Zwischen Positionsnummer und Lieferdatum befindet sich eine Spalte, aber keine gültige AB-Nummer erkannt.");
        }

        return new ColumnIndices(orderCol, posCol, dateCol, confCol);
    }

    public static ExcelOrderData fromExcelRow(Row row, ColumnIndices indices) {
        return fromExcelRow(row, indices.orderCol, indices.posCol, indices.dateCol, indices.confirmationCol);
    }

    public static ExcelOrderData fromExcelRow(Row row, int orderCol, int posCol, int dateCol, int confCol) {
        String orderNumber = FileExtractor.extractCellValueAsString(row.getCell(orderCol)).trim();
        String positionNumber = FileExtractor.extractCellValueAsString(row.getCell(posCol)).trim();
        String rawDate = FileExtractor.extractCellValueAsString(row.getCell(dateCol)).trim();
        String deliveryDate = normalizeDeliveryDate(rawDate);
        String confirmationNumber = confCol >= 0 ? FileExtractor.extractCellValueAsString(row.getCell(confCol)).trim() : "";
        return new ExcelOrderData(orderNumber, positionNumber, deliveryDate, confirmationNumber);
    }

    public static int detectOrderColumn(Sheet sheet) {
        List<String> knownPrefixes = Arrays.asList(BranchNumbers.BRANCH_NUMBERS);
        Row header = sheet.getRow(0);
        int numColumns = header.getLastCellNum();

        for (int i = 0; i < numColumns; i++) {
            Cell cell = header.getCell(i);
            if (cell != null) {
                String value = FileExtractor.extractCellValueAsString(cell).toLowerCase(Locale.ROOT);
                if (value.contains("bestellnummer") || value.contains("auftragsnummer") || value.contains("nummer")) {
                    return i;
                }
            }
        }

        int bestGuess = -1;
        int rowLimit = Math.min(20, sheet.getLastRowNum());
        for (int col = 0; col < numColumns; col++) {
            int validCount = 0;
            for (int rowIdx = 1; rowIdx <= rowLimit; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                Cell cell = row.getCell(col);
                if (cell == null) continue;
                String val = FileExtractor.extractCellValueAsString(cell).trim();
                if (val.length() >= 5 && val.length() <= 6 && val.matches("[A-Z0-9]+")) {
                    String prefix = val.length() >= 2 ? val.substring(0, 2) : "";
                    if (knownPrefixes.contains(prefix) || prefix.matches("[A-Z0-9]{2}")) {
                        validCount++;
                    }
                }
            }
            if (validCount > rowLimit / 2) {
                bestGuess = col;
                break;
            }
        }
        return bestGuess;
    }

    public static int detectPositionColumn(Sheet sheet, int orderColumn) {
        int numColumns = sheet.getRow(0).getLastCellNum();
        Row header = sheet.getRow(0);
        String[] possibleHeaders = {"positionsnummer", "position", "pos."};

        for (int i = orderColumn + 1; i < numColumns; i++) {
            Cell cell = header.getCell(i);
            if (cell != null) {
                String value = FileExtractor.extractCellValueAsString(cell).toLowerCase(Locale.ROOT);
                for (String headerName : possibleHeaders) {
                    if (value.contains(headerName)) {
                        return i;
                    }
                }
            }
        }

        int rowLimit = Math.min(20, sheet.getLastRowNum());
        for (int col = orderColumn + 1; col < numColumns; col++) {
            int validCount = 0;
            for (int rowIdx = 1; rowIdx <= rowLimit; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                Cell cell = row.getCell(col);
                if (cell == null) continue;
                String val = FileExtractor.extractCellValueAsString(cell).trim();
                if (val.matches("[1-9][0-9]{0,2}")) {
                    validCount++;
                }
            }
            if (validCount > rowLimit / 2) {
                return col;
            }
        }
        return -1;
    }

    public static int detectDeliveryDateColumn(Sheet sheet, int posColumn) {
        Row header = sheet.getRow(0);
        int numColumns = header.getLastCellNum();
        String[] keywords = {"lieferdatum", "we-datum"};

        for (int i = posColumn + 1; i < numColumns; i++) {
            Cell cell = header.getCell(i);
            if (cell != null) {
                String value = FileExtractor.extractCellValueAsString(cell).toLowerCase(Locale.ROOT);
                for (String keyword : keywords) {
                    if (value.contains(keyword)) {
                        return i;
                    }
                }
            }
        }

        int rowLimit = Math.min(20, sheet.getLastRowNum());
        for (int col = posColumn + 1; col < numColumns; col++) {
            int validCount = 0;
            for (int rowIdx = 1; rowIdx <= rowLimit; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                Cell cell = row.getCell(col);
                if (cell == null) continue;
                String raw = FileExtractor.extractCellValueAsString(cell).trim().toLowerCase();
                raw = raw.replace("kw", "").replaceAll("[^0-9./]", "").trim();
                if (raw.matches("\\d{4}") || raw.matches("\\d{2}\\.\\d{4}") || raw.matches("\\d{4}/\\d{2}") || raw.matches("\\d{2}")) {
                    validCount++;
                }
            }
            if (validCount > rowLimit / 2) {
                return col;
            }
        }
        return -1;
    }

    public static int detectConfirmationColumn(Sheet sheet, int posCol, int dateCol) {
        if (posCol >= 0 && dateCol > posCol + 1) {
            Row header = sheet.getRow(0);
            for (int i = posCol + 1; i < dateCol; i++) {
                Cell cell = header.getCell(i);
                if (cell != null) {
                    String value = FileExtractor.extractCellValueAsString(cell).toLowerCase(Locale.ROOT);
                    if (value.contains("ab-nummer") || value.equals("ab")) {
                        return i;
                    }
                }
            }
            if (dateCol - posCol == 2) {
                return posCol + 1; // единственная колонка между позицией и датой
            }
        }
        return -1;
    }

    public static String normalizeDeliveryDate(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        raw = raw.replace("KW", "").replace("kw", "").replaceAll("[^0-9./]", "").trim();
        if (raw.matches("\\d{4}")) {
            return raw;
        } else if (raw.matches("\\d{2}/\\d{4}")) {
            String[] parts = raw.split("/");
            return String.format("%02d%s", Integer.parseInt(parts[0]), parts[1].substring(2));
        } else if (raw.matches("\\d{2}\\.\\d{4}")) {
            String[] parts = raw.split("\\.");
            return String.format("%02d%s", Integer.parseInt(parts[0]), parts[1].substring(2));
        } else if (raw.matches("\\d{2}")) {
            Calendar now = Calendar.getInstance();
            int year = now.get(Calendar.YEAR) % 100;
            return String.format("%02d%02d", Integer.parseInt(raw), year);
        }
        return raw;
    }

    public static List<ExcelOrderData> extractAllFromSheet(Sheet sheet, int orderCol, int posCol, int dateCol, int confCol) {
        List<ExcelOrderData> list = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            ExcelOrderData data = fromExcelRow(row, orderCol, posCol, dateCol, confCol);
            if (!data.orderNumber().isEmpty() && !data.positionNumber().isEmpty() && !data.deliveryDate().isEmpty()) {
                list.add(data);
            }
        }
        return list;
    }
}