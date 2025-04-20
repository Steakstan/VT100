package org.msv.vt100.OrderAutomation;

import javafx.application.Platform;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.ui.TerminalDialog;
import org.msv.vt100.util.CellValueExtractor;
import org.msv.vt100.util.Waiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.msv.vt100.util.Waiter.waitUntil;

public class PositionssucheProcessor {

    private final String orderFilePath;
    private final String outputFilePath;
    private final String userNumber;
    private final TerminalApp terminalApp;
    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;
    private final SSHManager sshManager;
    private static final Logger log = LoggerFactory.getLogger(PositionssucheProcessor.class);

    public PositionssucheProcessor(String orderFilePath, String outputFilePath, String userNumber,
                                   TerminalApp terminalApp, ScreenBuffer screenBuffer, Cursor cursor) {
        this.orderFilePath = orderFilePath;
        this.outputFilePath = outputFilePath;
        this.userNumber = userNumber;
        this.terminalApp = terminalApp;
        this.screenBuffer = screenBuffer;
        this.cursor = cursor;
        this.sshManager = terminalApp.getSSHManager();
    }

    public void startSearch(Runnable onCompletion) {
        new Thread(() -> {
            try {
                Platform.runLater(terminalApp::showProcessingButtons);
                search();
                Platform.runLater(() -> {
                    terminalApp.hideProcessingButtons();
                    onCompletion.run();
                });
            } catch (Exception ex) {
                log.error("Fehler bei der Positionssuche", ex);
                Platform.runLater(() -> {
                    terminalApp.hideProcessingButtons();
                    TerminalDialog.showError("Fehler bei der Positionssuche: " + ex.getMessage(), terminalApp.getUIController().getPrimaryStage());
                });
            }
        }, "PositionssucheProcessorThread").start();
    }

    public void search() throws Exception {
        List<String> orders = loadOrders();
        XSSFWorkbook resultWorkbook = new XSSFWorkbook();
        Sheet resultSheet = resultWorkbook.createSheet("Results");
        CellStyle headerCellStyle = createHeaderCellStyle(resultWorkbook);
        CellStyle defaultCellStyle = createDefaultCellStyle(resultWorkbook);
        int resultRowIndex = createHeaderRow(resultSheet, headerCellStyle);
        String[] firmNumbers = userNumber.split(",");

        for (String order : orders) {
            if (order.length() == 5) {
                System.out.println("⏭️ Bestellnummer '" + order + "' hat nur 5 Zeichen – übersprungen.");
                continue;
            }

            Map<String, Row> processedRows = new HashMap<>();
            waitUntil("Warte auf Cursor 3,13", () -> cursor.getCursorPosition().equals("3,13"));
            System.out.println("📤 Sende Bestellnummer: " + order);
            sendDataWithDelay(order);
            sendDataWithDelay("\r");

            System.out.println("⏳ Warte auf Bildschirmwechsel nach Auftragseingabe...");
            String screenBefore = screenBuffer.toString();
            String cursorBefore = cursor.getCursorPosition();

            waitUntil("Bildschirmwechsel nach Auftrag", () -> {
                terminalApp.checkForPause();
                String currentScreen = screenBuffer.toString();
                String currentCursor = cursor.getCursorPosition();
                return !currentScreen.equals(screenBefore) || !currentCursor.equals(cursorBefore);
            });
            Waiter.waitFor(() -> true, Duration.ofMillis(100), Duration.ofMillis(100)).get();

            ScreenTextDetector detector = new ScreenTextDetector(screenBuffer);
            while (detector.isAchtungDisplayed()) {
                System.out.println("⚠️ 'Achtung' erkannt. Warte bis verschwunden...");
                waitUntil("Achtung verschwunden", () -> !new ScreenTextDetector(screenBuffer).isAchtungDisplayed());
            }

            boolean finished = false;
            boolean firstEnterAfterOrder = true;
            String previousLine7 = null;
            String previousLine8 = null;

            while (!finished) {
                terminalApp.checkForPause();
                if (terminalApp.isStopped()) return;

                resultRowIndex = scanLinesAndWriteMatches(
                        screenBuffer, resultSheet, firmNumbers, order,
                        resultRowIndex, defaultCellStyle, processedRows
                );

                String cursorPos = cursor.getCursorPosition();
                String screenText = screenBuffer.toString();

                if (cursorPos.equals("3,13") && screenText.contains("Auftr-Nr.")) {
                    System.out.println("🏁 Zurück auf der Startseite. Verarbeitung dieses Auftrags abgeschlossen.");
                    break;
                }

                if (cursorPos.equals("23,10") && screenText.contains("Pos/XX")) {
                    System.out.println("↩️ 'Pos/XX' erkannt. Sende Zurück und beende Auftrag.");
                    sendDataWithDelay("\u001BOQ");
                    break;
                }

                if (firstEnterAfterOrder && cursorPos.equals("24,73") && screenText.contains("Bitte ausloesen")) {
                    System.out.println("🟨 Initialer Enter nach 'Bitte ausloesen' (Cursor 24,73) wird gesendet...");
                    previousLine7 = extractLineData(7);
                    previousLine8 = extractLineData(8);
                    sendDataWithDelay("\r");
                    firstEnterAfterOrder = false;
                    continue;
                }

                if (!firstEnterAfterOrder && cursorPos.equals("24,73") && screenText.contains("Bitte ausloesen")) {
                    String currentLine7 = extractLineData(7);
                    String currentLine8 = extractLineData(8);
                    boolean changed7 = !currentLine7.equals(previousLine7);
                    boolean changed8 = !currentLine8.equals(previousLine8);

                    if (changed7 || changed8) {
                        System.out.println("✅ Änderung in Zeilen 7 & 8 erkannt. Enter wird erneut gesendet.");
                        sendDataWithDelay("\r");
                        previousLine7 = currentLine7;
                        previousLine8 = currentLine8;
                    } else {
                        System.out.println("⏸ Noch keine vollständige Änderung in Zeilen 7 & 8...");
                    }
                } else {
                    System.out.println("⏸ Bedingung für Enter nicht erfüllt (Cursor: " + cursorPos + ").");
                }
            }
        }

        for (int i = 0; i < 7; i++) {
            resultSheet.autoSizeColumn(i);
        }

        try (FileOutputStream fos = new FileOutputStream(
                outputFilePath.endsWith(".xlsx") ? outputFilePath : outputFilePath + ".xlsx")) {
            resultWorkbook.write(fos);
        }
        resultWorkbook.close();
    }


    private String extractLineData(int row) {
        int[] cols = new int[79];
        for (int i = 0; i < 79; i++) cols[i] = i;
        return CellValueExtractor.extractCells(screenBuffer, row, cols);
    }





    private List<String> loadOrders() throws Exception {
        List<String> orders = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(orderFilePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                String order = FileExtractor.extractCellValueAsString(row.getCell(0));
                if (order != null && !order.isEmpty()) orders.add(order.trim());
            }
        }
        return orders;
    }

    private CellStyle createHeaderCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDefaultCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private int createHeaderRow(Sheet sheet, CellStyle style) {
        Row row = sheet.createRow(0);
        String[] headers = {
                "Unternehmensnummer", "Bestellungsnummer", "Positionsnummer",
                "Modellbeschreibung", "Modellnummer", "Lieferdatum", "AB-Liefertermin"
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
        return 1;
    }


    private void sendDataWithDelay(String data) throws Exception {
        log.info("Sende Daten: '{}'", data.replace("\r", "<Enter>"));
        sshManager.send(data);
        Waiter.waitFor(() -> true, Duration.ofMillis(60), Duration.ofMillis(30)).get();
    }



    private int scanLinesAndWriteMatches(ScreenBuffer buffer, Sheet resultSheet, String[] firmNumbers, String order,
                                         int resultRowIndex, CellStyle defaultCellStyle, Map<String, Row> processedRows) throws Exception {
        boolean pageHasMore = true;

        while (pageHasMore) {
            terminalApp.checkForPause();
            if (terminalApp.isStopped()) return resultRowIndex;

            boolean pageTransitioned = false;

            for (int line = 7; line <= 22; line++) {
                terminalApp.checkForPause();
                if (terminalApp.isStopped()) return resultRowIndex;

                String cellFirm = CellValueExtractor.extractCells(buffer, line, 4, 5, 6, 7);
                for (String firm : firmNumbers) {
                    if (cellFirm.equals(firm)) {
                        String position = CellValueExtractor.extractCells(buffer, line, 0, 1, 2, 3);
                        String key = firm + "_" + position;
                        Row existingRow = processedRows.get(key);

                        if (existingRow == null) {
                            Row newRow = resultSheet.createRow(resultRowIndex++);
                            processedRows.put(key, newRow);

                            int finalLine = line;
                            CompletableFuture<String> descFuture = CompletableFuture.supplyAsync(() ->
                                    CellValueExtractor.extractCells(buffer, finalLine, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41));

                            int finalLine1 = line;
                            CompletableFuture<String> modelFuture = CompletableFuture.supplyAsync(() ->
                                    CellValueExtractor.extractCells(buffer, finalLine1 + 1, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41));

                            int finalLine2 = line;
                            CompletableFuture<String> deliveryFuture = CompletableFuture.supplyAsync(() ->
                                    CellValueExtractor.extractCells(buffer, finalLine2, 63, 64, 65, 66));

                            int finalLine3 = line;
                            CompletableFuture<String> abFuture = CompletableFuture.supplyAsync(() ->
                                    CellValueExtractor.extractCells(buffer, finalLine3, 55, 56, 57, 58));

                            try {
                                CompletableFuture.allOf(descFuture, modelFuture, deliveryFuture, abFuture).join();

                                newRow.createCell(0).setCellValue(firm);
                                newRow.getCell(0).setCellStyle(defaultCellStyle);
                                newRow.createCell(1).setCellValue(order);
                                newRow.getCell(1).setCellStyle(defaultCellStyle);
                                newRow.createCell(2).setCellValue(position);
                                newRow.getCell(2).setCellStyle(defaultCellStyle);
                                newRow.createCell(3).setCellValue(descFuture.get());
                                newRow.getCell(3).setCellStyle(defaultCellStyle);
                                newRow.createCell(4).setCellValue(modelFuture.get());
                                newRow.getCell(4).setCellStyle(defaultCellStyle);
                                newRow.createCell(5).setCellValue(deliveryFuture.get());
                                newRow.getCell(5).setCellStyle(defaultCellStyle);
                                newRow.createCell(6).setCellValue(abFuture.get());
                                newRow.getCell(6).setCellStyle(defaultCellStyle);

                            } catch (Exception e) {
                                System.err.println("❌ Fehler beim parallelen Extrahieren: " + e.getMessage());
                                Thread.currentThread().interrupt();
                            }

                            if (line == 22) {
                                sendDataWithDelay("\r");
                                Thread.sleep(200);
                                pageTransitioned = true;
                                break;
                            }
                        } else {
                            System.out.println("✍️  Zeile bereits vorhanden – Firma: " + firm + ", Position: " + position);
                            if (line == 22) {
                                updateRow22(existingRow, buffer, defaultCellStyle);
                            } else {
                                updateNormalRow(existingRow, buffer, line, defaultCellStyle);
                            }
                        }
                    }
                }

                if (pageTransitioned) break;
            }

            if (!pageTransitioned) {
                String currentCursor = cursor.getCursorPosition();
                if (currentCursor.equals("23,10")) {
                    sendDataWithDelay("\u001BOQ");
                    Thread.sleep(150);
                } else {
                    pageHasMore = false;
                }
            }
        }

        return resultRowIndex;
    }

    private void writeRow22(Row row, ScreenBuffer buffer, CellStyle defaultCellStyle, String firm, String order, String position) {
        Cell cellFirm = row.createCell(0);
        cellFirm.setCellValue(firm);
        cellFirm.setCellStyle(defaultCellStyle);
        Cell cellOrder = row.createCell(1);
        cellOrder.setCellValue(order);
        cellOrder.setCellStyle(defaultCellStyle);
        Cell cellPosition = row.createCell(2);
        cellPosition.setCellValue(position);
        cellPosition.setCellStyle(defaultCellStyle);
        String modelDescription = CellValueExtractor.extractCells(buffer, 22, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41);
        String deliveryDate = CellValueExtractor.extractCells(buffer, 22, 63, 64, 65, 66);
        String abLiefertermin = CellValueExtractor.extractCells(buffer, 22, 55, 56, 57, 58);
        Cell cellDesc = row.createCell(3);
        cellDesc.setCellValue(modelDescription);
        cellDesc.setCellStyle(defaultCellStyle);
        Cell cellDelivery = row.createCell(5);
        cellDelivery.setCellValue(deliveryDate);
        cellDelivery.setCellStyle(defaultCellStyle);
        Cell cellAB = row.createCell(6);
        cellAB.setCellValue(abLiefertermin);
        cellAB.setCellStyle(defaultCellStyle);
    }

    private void writeNormalRow(Row row, ScreenBuffer buffer, int line, CellStyle defaultCellStyle, String firm, String order, String position) {
        Cell cellFirm = row.createCell(0);
        cellFirm.setCellValue(firm);
        cellFirm.setCellStyle(defaultCellStyle);
        Cell cellOrder = row.createCell(1);
        cellOrder.setCellValue(order);
        cellOrder.setCellStyle(defaultCellStyle);
        Cell cellPosition = row.createCell(2);
        cellPosition.setCellValue(position);
        cellPosition.setCellStyle(defaultCellStyle);
        String modelDescription = CellValueExtractor.extractCells(buffer, line, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41);
        String modelNumber = CellValueExtractor.extractCells(buffer, line + 1, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41);
        String deliveryDate = CellValueExtractor.extractCells(buffer, line, 63, 64, 65, 66);
        String abLiefertermin = CellValueExtractor.extractCells(buffer, line, 55, 56, 57, 58);
        Cell cellDesc = row.createCell(3);
        cellDesc.setCellValue(modelDescription);
        cellDesc.setCellStyle(defaultCellStyle);
        Cell cellModel = row.createCell(4);
        cellModel.setCellValue(modelNumber);
        cellModel.setCellStyle(defaultCellStyle);
        Cell cellDelivery = row.createCell(5);
        cellDelivery.setCellValue(deliveryDate);
        cellDelivery.setCellStyle(defaultCellStyle);
        Cell cellAB = row.createCell(6);
        cellAB.setCellValue(abLiefertermin);
        cellAB.setCellStyle(defaultCellStyle);
    }
    private void updateRow22(Row row, ScreenBuffer buffer, CellStyle defaultCellStyle) {
        String modelDescription = CellValueExtractor.extractCells(buffer, 22, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41);
        String deliveryDate = CellValueExtractor.extractCells(buffer, 22, 63, 64, 65, 66);
        String abLiefertermin = CellValueExtractor.extractCells(buffer, 22, 55, 56, 57, 58);
        Cell cellDesc = row.getCell(3);
        if (cellDesc == null) {
            cellDesc = row.createCell(3);
        }
        cellDesc.setCellValue(modelDescription);
        cellDesc.setCellStyle(defaultCellStyle);
        Cell cellDelivery = row.getCell(5);
        if (cellDelivery == null) {
            cellDelivery = row.createCell(5);
        }
        cellDelivery.setCellValue(deliveryDate);
        cellDelivery.setCellStyle(defaultCellStyle);
        Cell cellAB = row.getCell(6);
        if (cellAB == null) {
            cellAB = row.createCell(6);
        }
        cellAB.setCellValue(abLiefertermin);
        cellAB.setCellStyle(defaultCellStyle);
    }

    private void updateNormalRow(Row row, ScreenBuffer buffer, int line, CellStyle defaultCellStyle) {
        String modelDescription = CellValueExtractor.extractCells(buffer, line, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41);
        String modelNumber = CellValueExtractor.extractCells(buffer, line + 1, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41);
        String deliveryDate = CellValueExtractor.extractCells(buffer, line, 63, 64, 65, 66);
        String abLiefertermin = CellValueExtractor.extractCells(buffer, line, 55, 56, 57, 58);
        Cell cellDesc = row.getCell(3);
        if (cellDesc == null) {
            cellDesc = row.createCell(3);
        }
        cellDesc.setCellValue(modelDescription);
        cellDesc.setCellStyle(defaultCellStyle);
        Cell cellModel = row.getCell(4);
        if (cellModel == null) {
            cellModel = row.createCell(4);
        }
        cellModel.setCellValue(modelNumber);
        cellModel.setCellStyle(defaultCellStyle);
        Cell cellDelivery = row.getCell(5);
        if (cellDelivery == null) {
            cellDelivery = row.createCell(5);
        }
        cellDelivery.setCellValue(deliveryDate);
        cellDelivery.setCellStyle(defaultCellStyle);
        Cell cellAB = row.getCell(6);
        if (cellAB == null) {
            cellAB = row.createCell(6);
        }
        cellAB.setCellValue(abLiefertermin);
        cellAB.setCellStyle(defaultCellStyle);
    }

}
