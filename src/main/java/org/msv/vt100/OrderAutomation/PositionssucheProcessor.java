package org.msv.vt100.OrderAutomation;

import javafx.application.Platform;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.ui.TerminalDialog;
import org.msv.vt100.util.CellValueExtractor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class PositionssucheProcessor {

    private final String orderFilePath;
    private final String outputFilePath;
    private final String userNumber;
    private final TerminalApp terminalApp;
    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;
    private final SSHManager sshManager;

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
                Platform.runLater(() -> {
                    terminalApp.hideProcessingButtons();
                    TerminalDialog.showError("Fehler bei der Positionssuche: " + ex.getMessage(), terminalApp.getUIController().getPrimaryStage());
                });
            }
        }, "PositionssucheProcessorThread").start();
    }

    public void search() throws Exception {
        List<String> orders = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(orderFilePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                String order = FileExtractor.extractCellValueAsString(row.getCell(0));
                if (order != null && !order.isEmpty()) {
                    orders.add(order.trim());
                }
            }
        }

        XSSFWorkbook resultWorkbook = new XSSFWorkbook();
        Sheet resultSheet = resultWorkbook.createSheet("Results");

        CellStyle headerCellStyle = resultWorkbook.createCellStyle();
        Font headerFont = resultWorkbook.createFont();
        headerFont.setBold(true);
        headerCellStyle.setFont(headerFont);
        headerCellStyle.setAlignment(HorizontalAlignment.CENTER);
        headerCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerCellStyle.setBorderTop(BorderStyle.THIN);
        headerCellStyle.setBorderBottom(BorderStyle.THIN);
        headerCellStyle.setBorderLeft(BorderStyle.THIN);
        headerCellStyle.setBorderRight(BorderStyle.THIN);

        CellStyle defaultCellStyle = resultWorkbook.createCellStyle();
        defaultCellStyle.setAlignment(HorizontalAlignment.CENTER);
        defaultCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        defaultCellStyle.setBorderTop(BorderStyle.THIN);
        defaultCellStyle.setBorderBottom(BorderStyle.THIN);
        defaultCellStyle.setBorderLeft(BorderStyle.THIN);
        defaultCellStyle.setBorderRight(BorderStyle.THIN);

        int resultRowIndex = 0;
        Row header = resultSheet.createRow(resultRowIndex++);
        Cell cell0 = header.createCell(0);
        cell0.setCellValue("Unternehmensnummer");
        cell0.setCellStyle(headerCellStyle);
        Cell cell1 = header.createCell(1);
        cell1.setCellValue("Bestellungsnummer");
        cell1.setCellStyle(headerCellStyle);
        Cell cell2 = header.createCell(2);
        cell2.setCellValue("Positionsnummer");
        cell2.setCellStyle(headerCellStyle);
        Cell cell3 = header.createCell(3);
        cell3.setCellValue("Modellbeschreibung");
        cell3.setCellStyle(headerCellStyle);
        Cell cell4 = header.createCell(4);
        cell4.setCellValue("Modellnummer");
        cell4.setCellStyle(headerCellStyle);
        Cell cell5 = header.createCell(5);
        cell5.setCellValue("Lieferdatum");
        cell5.setCellStyle(headerCellStyle);
        Cell cell6 = header.createCell(6);
        cell6.setCellValue("AB-Liefertermin");
        cell6.setCellStyle(headerCellStyle);

        String[] firmNumbers = userNumber.split(",");
        for (int i = 0; i < firmNumbers.length; i++) {
            firmNumbers[i] = firmNumbers[i].trim();
        }

        for (String order : orders) {
            Map<String, Row> processedRowsForOrder = new HashMap<>();
            if (terminalApp.isStopped()) break;
            terminalApp.checkForPause();
            while (!getCursorPosition().equals("3,13")) {
                if (terminalApp.isStopped()) break;
                terminalApp.checkForPause();
                waitForStableScreenSnapshot(Duration.ofSeconds(1), Duration.ofMillis(50));
            }
            if (terminalApp.isStopped()) break;
            sendDataWithDelay(order);
            Thread.sleep(70);
            ScreenTextDetector detector = new ScreenTextDetector(screenBuffer);
            while (detector.isAchtungDisplayed()) {
                waitForStableScreenSnapshot(Duration.ofSeconds(1), Duration.ofMillis(50));
            }
            boolean finished = false;
            while (!finished) {
                terminalApp.checkForPause();
                if (terminalApp.isStopped()) return;
                resultRowIndex = scanLinesAndWriteMatches(screenBuffer, resultSheet, firmNumbers, order, resultRowIndex, defaultCellStyle, processedRowsForOrder);
                sendDataWithDelay("\r");
                Thread.sleep(70);
                String currentCursor = getCursorPosition();
                if (currentCursor.equals("23,10")) {
                    resultRowIndex = scanLinesAndWriteMatches(screenBuffer, resultSheet, firmNumbers, order, resultRowIndex, defaultCellStyle, processedRowsForOrder);
                    sendDataWithDelay("\u001BOQ");
                    finished = true;
                }
            }
        }

        for (int i = 0; i < 7; i++) {
            resultSheet.autoSizeColumn(i);
        }

        String correctedOutputFilePath = outputFilePath;
        if (!correctedOutputFilePath.toLowerCase().endsWith(".xlsx")) {
            correctedOutputFilePath += ".xlsx";
        }
        try (FileOutputStream fos = new FileOutputStream(correctedOutputFilePath)) {
            resultWorkbook.write(fos);
        }
        resultWorkbook.close();
    }

    private String getCursorPosition() throws InterruptedException {
        final String[] cursorPosition = new String[1];
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            cursorPosition[0] = (cursor.getRow() + 1) + "," + (cursor.getColumn() + 1);
            latch.countDown();
        });
        latch.await();
        return cursorPosition[0];
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

                            if (line == 22) {
                                writeRow22(newRow, buffer, defaultCellStyle, firm, order, position);
                                sendDataWithDelay("\r");
                                Thread.sleep(70);
                                String modelNumber = CellValueExtractor.extractCells(screenBuffer, 7, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41);
                                Cell cellModel = newRow.createCell(4);
                                cellModel.setCellValue(modelNumber);
                                cellModel.setCellStyle(defaultCellStyle);
                                pageTransitioned = true;
                                break;
                            } else {
                                writeNormalRow(newRow, buffer, line, defaultCellStyle, firm, order, position);
                            }
                        } else {
                            // обновление существующей строки
                            if (line == 22) {
                                updateRow22(existingRow, buffer, defaultCellStyle);
                            } else {
                                updateNormalRow(existingRow, buffer, line, defaultCellStyle);
                            }
                        }

                    }
                }

                if (pageTransitioned) break; // выйти из for, чтобы заново начать с новой страницы
            }

            if (!pageTransitioned) {
                // Если не перешли страницу — проверяем курсор и переходим вручную, если возможно
                String currentCursor = getCursorPosition();
                if (currentCursor.equals("23,10")) {
                    sendDataWithDelay("\u001BOQ"); // Taste F2 — nächste Seite
                    Thread.sleep(70);
                } else {
                    pageHasMore = false; // больше страниц нет — выходим
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


    private void sendDataWithDelay(String data) throws Exception {
        sshManager.send(data);
        Thread.sleep(70);
    }
    private void waitForStableScreenSnapshot(Duration timeout, Duration interval) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        String last = screenBuffer.toStringVisible();

        while (System.currentTimeMillis() - startTime < timeout.toMillis()) {
            Thread.sleep(interval.toMillis());
            terminalApp.checkForPause();

            String current = screenBuffer.toStringVisible();
            if (current.equals(last)) {
                return; // экран стабилен
            }
            last = current;
        }
    }

}