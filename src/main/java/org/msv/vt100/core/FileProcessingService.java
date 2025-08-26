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
        logger.info("Excel-Datei wird geöffnet: {}", excelFilePath);
        isPaused.set(false);
        isStopped.set(false);

        try (FileInputStream fileInputStream = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fileInputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row firstRow = sheet.getRow(0);
            if (firstRow == null) {
                throw new IllegalArgumentException("Excel-Datei enthält keine Daten.");
            }

            int columnCount = firstRow.getLastCellNum();

            if (choice == 2 && columnCount != 2) {
                throw new IllegalArgumentException("Ungültiges Format für Kommentarverarbeitung. Genau 2 Spalten erwartet.");
            } else if (choice == 4 && columnCount < 3) {
                throw new IllegalArgumentException("Ungültiges Format für Lieferterminverarbeitung. Mindestens 3 Spalten erwartet.");
            }


            checkPauseStop();

            if (choice == 4) {
                DeliveryDateProcessor deliveryDateProcessor =
                        new DeliveryDateProcessor(sshManager, cursor, terminalApp, screenTextDetector);

                deliveryDateProcessor.processDeliveryDates(sheet);

            } else {
                throw new UnsupportedOperationException("Verarbeitungstyp nicht implementiert: " + choice);
            }

        } catch (InterruptedException ie) {
            logger.warn("Verarbeitung unterbrochen (Stop/Pause).");
            Thread.currentThread().interrupt(); // Status wahren
            throw ie; // an UI propagieren -> genau ein Dialog
        } catch (Exception e) {
            logger.error("Fehler bei der Verarbeitung: {}", excelFilePath, e);
            String message = e.getMessage() != null ? e.getMessage() : "Unbekannter Fehler";
            throw new RuntimeException(message, e); // UI zeigt Fehler
        }
    }


    public void checkPauseStop() throws InterruptedException {
        // harter Stop per Flag
        if (isStopped.get()) {
            throw new InterruptedException("Verarbeitung vom Benutzer gestoppt.");
        }
        // harter Stop per Thread-Interrupt
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Thread unterbrochen.");
        }
        // Pause kooperativ
        while (isPaused.get()) {
            if (isStopped.get() || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Verarbeitung während Pause gestoppt.");
            }
            Thread.sleep(100);
        }
    }

    public void shutdown() {
        try { isStopped.set(true); isPaused.set(false); } catch (Throwable ignore) {}
    }

}
