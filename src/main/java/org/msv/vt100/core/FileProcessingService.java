package org.msv.vt100.core;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.msv.vt100.OrderAutomation.DeliveryDateProcessor;
import org.msv.vt100.OrderAutomation.OrderConfirmation;
import org.msv.vt100.OrderAutomation.ScreenTextDetector;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.TerminalApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileInputStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(FileProcessingService.class);

    private final SSHManager sshManager;
    private final Cursor cursor;
    private final TerminalApp terminalApp;
    private final ScreenTextDetector screenTextDetector;
    private final AtomicBoolean isPaused;
    private final AtomicBoolean isStopped;

    /**
     * Constructs a FileProcessingService.
     *
     * @param sshManager        the SSHManager instance for communication.
     * @param cursor            the terminal cursor.
     * @param terminalApp       the main TerminalApp instance.
     * @param screenTextDetector the screen text detector.
     * @param isPaused          a flag to pause processing.
     * @param isStopped         a flag to stop processing.
     */
    public FileProcessingService(SSHManager sshManager,
                                 Cursor cursor,
                                 TerminalApp terminalApp,
                                 ScreenTextDetector screenTextDetector,
                                 AtomicBoolean isPaused,
                                 AtomicBoolean isStopped) {
        this.sshManager = sshManager;
        this.cursor = cursor;
        this.terminalApp = terminalApp;
        this.screenTextDetector = screenTextDetector;
        this.isPaused = isPaused;
        this.isStopped = isStopped;
    }

    /**
     * Processes the Excel file based on the specified choice.
     *
     * @param choice        the type of processing to perform.
     * @param excelFilePath the path to the Excel file.
     * @throws InterruptedException if processing is interrupted.
     */
    public void processFile(int choice, String excelFilePath) throws InterruptedException {
        logger.info("Opening Excel file: {}", excelFilePath);
        isPaused.set(false);
        isStopped.set(false);

        try (FileInputStream fileInputStream = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fileInputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row firstRow = sheet.getRow(0);
            if (firstRow == null) {
                throw new IllegalStateException("Excel-Datei enthält keine Daten.");
            }

            int columnCount = firstRow.getLastCellNum();
            if (choice == 1 && columnCount < 3) {
                throw new IllegalArgumentException("Ungültiges Format für Bestellverarbeitung. Mindestens 3 Spalten erwartet.");
            } else if ((choice == 2 || choice == 3) && columnCount != 2) {
                throw new IllegalArgumentException("Ungültiges Format für Kommentarverarbeitung. Genau 2 Spalten erwartet.");
            } else if (choice == 4 && columnCount < 3) {
                throw new IllegalArgumentException("Ungültiges Format für Lieferterminverarbeitung. Mindestens 3 Spalten erwartet.");
            }

            if (choice == 1) {
                OrderConfirmation orderConfirmation = new OrderConfirmation(sshManager, cursor, terminalApp, screenTextDetector);
                orderConfirmation.processOrders(sheet.iterator());
                showInfo("Verarbeitung abgeschlossen.");
            } else if (choice == 4) {
                DeliveryDateProcessor deliveryDateProcessor = new DeliveryDateProcessor(sshManager, cursor, terminalApp, screenTextDetector);
                deliveryDateProcessor.processDeliveryDates(sheet);
                showInfo("Verarbeitung abgeschlossen.");
            } else {
                logger.warn("Verarbeitungstyp '{}' nicht implementiert.", choice);
            }

        } catch (Exception e) {
            logger.error("Fehler bei der Verarbeitung: {}", excelFilePath, e);
            String message = e.getMessage() != null ? e.getMessage() : "Unbekannter Fehler";
            showError("Verarbeitung fehlgeschlagen:\n" + message);
        }
    }

    private void showInfo(String msg) {
        Platform.runLater(() -> {
            new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK).showAndWait();
            terminalApp.hideProcessingButtons();
        });
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait();
            terminalApp.hideProcessingButtons();
        });
    }

}
