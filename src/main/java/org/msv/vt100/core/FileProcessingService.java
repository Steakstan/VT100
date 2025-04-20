package org.msv.vt100.core;

import javafx.application.Platform;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.msv.vt100.OrderAutomation.DeliveryDateProcessor;
import org.msv.vt100.OrderAutomation.ScreenTextDetector;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.ui.TerminalDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileInputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileProcessingService {
private static final Logger logger = LoggerFactory.getLogger(FileProcessingService.class);

private final SSHManager sshManager;
private final Cursor cursor;
private final TerminalApp terminalApp;
private final ScreenTextDetector screenTextDetector;
private final AtomicBoolean isPaused;
private final AtomicBoolean isStopped;

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

public void processFile(int choice, String excelFilePath) throws InterruptedException {
    logger.info("Opening Excel file: {}", excelFilePath);
    isPaused.set(false);
    isStopped.set(false);

    try (FileInputStream fileInputStream = new FileInputStream(excelFilePath);
         Workbook workbook = new XSSFWorkbook(fileInputStream)) {

        Sheet sheet = workbook.getSheetAt(0);
        Row firstRow = sheet.getRow(0);
        if (firstRow == null) {
            showError("Excel-Datei enthält keine Daten.");
            return;
        }

        int columnCount = firstRow.getLastCellNum();

        if (choice == 2 && columnCount != 2) {
            showError("Ungültiges Format für Kommentarverarbeitung. Genau 2 Spalten erwartet.");
            return;
        } else if (choice == 4 && columnCount < 3) {
            showError("Ungültiges Format für Lieferterminverarbeitung. Mindestens 3 Spalten erwartet.");
            return;
        }

        if (choice == 4) {
            DeliveryDateProcessor deliveryDateProcessor = new DeliveryDateProcessor(sshManager, cursor, terminalApp, screenTextDetector);
            deliveryDateProcessor.processDeliveryDates(sheet);
            showInfo();
        } else {
            logger.warn("Verarbeitungstyp '{}' nicht implementiert.", choice);
            showError("Verarbeitungstyp nicht implementiert: " + choice);
        }

    } catch (Exception e) {
        logger.error("Fehler bei der Verarbeitung: {}", excelFilePath, e);
        String message = e.getMessage() != null ? e.getMessage() : "Unbekannter Fehler";
        showError("Verarbeitung fehlgeschlagen:\n" + message);
    }
}

private void showInfo() {
    Platform.runLater(() -> {
        TerminalDialog.showInfo("Verarbeitung abgeschlossen.", terminalApp.getUIController().getPrimaryStage());
        terminalApp.hideProcessingButtons();
    });
}

private void showError(String msg) {
    Platform.runLater(() -> {
        TerminalDialog.showError(msg, terminalApp.getUIController().getPrimaryStage());
        terminalApp.hideProcessingButtons();
    });
}
}
