package org.msv.vt100.OrderAutomation;

import javafx.application.Platform;
import org.apache.poi.ss.usermodel.Row;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.TerminalApp;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

/**
 * OrderConfirmation handles the processing and confirmation of orders.
 * It reads order data from an Excel sheet, sends appropriate commands via SSH,
 * and adjusts the terminal cursor according to the screen content.
 */
public class OrderConfirmation {

    private final SSHManager sshConnector;
    private final Cursor cursor;
    private final TerminalApp terminalApp;
    private final ScreenTextDetector screenTextDetector;

    /**
     * Constructs an OrderConfirmation instance.
     *
     * @param sshConnector       the SSHManager for sending commands.
     * @param cursor             the terminal cursor.
     * @param terminalApp        the main TerminalApp.
     * @param screenTextDetector the detector to verify screen content.
     */
    public OrderConfirmation(SSHManager sshConnector, Cursor cursor, TerminalApp terminalApp, ScreenTextDetector screenTextDetector) {
        this.sshConnector = sshConnector;
        this.cursor = cursor;
        this.terminalApp = terminalApp;
        this.screenTextDetector = screenTextDetector;
    }

    /**
     * Processes orders from the given row iterator.
     *
     * @param rows an Iterator of Excel rows.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if processing is interrupted.
     */
    public void processOrders(Iterator<Row> rows) throws IOException, InterruptedException {
        while (rows.hasNext()) {
            if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                System.out.println("Verarbeitung gestoppt.");
                break;
            }

            terminalApp.checkForPause();

            Row currentRow = rows.next();
            try {
                processOrder(currentRow);
            } catch (InterruptedException e) {
                System.out.println("Verarbeitung unterbrochen.");
                throw e; // Rethrow the exception
            }
        }
    }

    /**
     * Processes a single order.
     *
     * @param row the Excel row containing order data.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if processing is interrupted.
     */
    public void processOrder(Row row) throws IOException, InterruptedException {
        String orderNumber = FileExtractor.extractCellValueAsString(row.getCell(0));
        String positionNumber = FileExtractor.extractCellValueAsString(row.getCell(1));
        String deliveryDate = FileExtractor.extractCellValueAsString(row.getCell(3));
        String confirmationNumber = FileExtractor.extractCellValueAsString(row.getCell(2));

        System.out.println("Verarbeitung der Bestellung: " + orderNumber);

        String cursorPosition = getCursorPosition();

        if (screenTextDetector.isAufNrDisplayed()) {
            if (orderNumber.length() == 5) {
                System.out.println("Auf dem Bildschirm 'Auf-Nr.:' und die Bestellnummer besteht aus 5 Ziffern. Sende 'L' an den Server.");
                sendDataWithDelay("L");
                sendDataWithDelay("\r");
            } else if (orderNumber.length() != 6) {
                System.out.println("Fehler: Die Bestellnummer muss 5 oder 6 Zeichen enthalten. Verarbeitung gestoppt.");
                return;
            }
        } else if (screenTextDetector.isLbNrDisplayed()) {
            if (orderNumber.length() == 6) {
                System.out.println("Auf dem Bildschirm 'LB-Nr.:' und die Bestellnummer besteht aus 6 Ziffern. Sende 'K' an den Server.");
                sendDataWithDelay("K");
                sendDataWithDelay("\r");
            } else if (orderNumber.length() != 5) {
                System.out.println("Fehler: Die Bestellnummer muss 5 oder 6 Zeichen enthalten. Verarbeitung gestoppt.");
                return;
            }
        } else {
            System.out.println("Es konnte nicht festgestellt werden, was auf dem Bildschirm angezeigt wird. Verarbeitung gestoppt.");
            return;
        }

        while (!cursorPosition.equals("311")) {
            System.out.println("Die Cursorposition ist nicht '311'. Bewege den Cursor nach links und überprüfe erneut.");
            sendDataWithDelay("\u001BOQ");

            System.out.println("Aktuelle Position: " + cursorPosition);

            if (cursorPosition.equals("2362")) {
                System.out.println("Der Cursor befindet sich immer noch in Position '2362'. Eingabe des Buchstabens 'L' und des Wertes '5.0321'.");
                sendDataWithDelay("L");
                sendDataWithDelay("\r");
                sendDataWithDelay("5.0321");
                sendDataWithDelay("\r");

                System.out.println("Erneute Verarbeitung der aktuellen Bestellung: " + orderNumber);
                processOrder(row);
                return;
            }

            cursorPosition = getCursorPosition();
        }

        if (screenTextDetector.isWareneingangDisplayed()) {
            sendDataWithDelay("\u001BOQ");
            return;
        }

        System.out.println("Der Cursor befindet sich an der richtigen Position. Bestellnummer eingeben: " + orderNumber);
        sendDataWithDelay(orderNumber);
        sendDataWithDelay("\r");

        System.out.println("Positionsnummer eingeben: " + positionNumber);
        sendDataWithDelay(positionNumber);
        sendDataWithDelay("\r");

        System.out.println("Überprüfung, ob die Bestellung nicht bereits geliefert wurde.");

        cursorPosition = getCursorPosition();
        if (cursorPosition.equals("1374")) {
            System.out.println("Der Cursor hat den Wert '1374'. Pfeiltaste zurück drücken und zur nächsten Bestellung wechseln.");
            sendDataWithDelay("\u001BOQ");
            return;
        }

        System.out.println("Überprüfung der Cursorposition vor der Eingabe des Lieferdatums.");

        cursorPosition = getCursorPosition();

        while (cursorPosition.equals("2480") || cursorPosition.equals("2443")) {
            System.out.println("Die Cursorposition ist '" + cursorPosition + "'. Drücke Enter und überprüfe erneut.");
            sendDataWithDelay("\r");
            cursorPosition = getCursorPosition();
        }

        if (cursorPosition.equals("936")) {
            System.out.println("Der Cursor befindet sich an der richtigen Position. Lieferdatum eingeben: " + deliveryDate);
            sendDataWithDelay(deliveryDate);
            sendDataWithDelay("\r");
            sendDataWithDelay("\r");
            Thread.sleep(1500);
        } else {
            System.out.println("Der Cursor befindet sich nicht an der richtigen Position für die Datumseingabe.");
            cursorPosition = getCursorPosition();
            sendDataWithDelay(deliveryDate);
            sendDataWithDelay("\r");
            sendDataWithDelay("\r");
            Thread.sleep(1500);
        }

        System.out.println("Eingabe der Bestätigungsnummer: " + confirmationNumber);
        sendDataWithDelay(confirmationNumber);

        while (!cursorPosition.equals("2375") && !cursorPosition.equals("2376") && !cursorPosition.equals("2377")) {
            sendDataWithDelay("\r");
            cursorPosition = getCursorPosition();

            switch (cursorPosition) {
                case "311", "411" -> {
                    System.out.println("Der Cursor hat den Wert " + cursorPosition + ". Schleife wird abgebrochen und zur nächsten Bestellung gewechselt.");
                    return;
                }
                case "221" -> {
                    System.out.println("Der Cursor hat den Wert '221'.");
                    sendDataWithDelay("\u001BOQ");
                }
            }
        }

        sendDataWithDelay("\r");
        cursorPosition = getCursorPosition();

        if (cursorPosition.equals("2440")) {
            System.out.println("Der Cursor hat den Wert '2440'. Drücke die Pfeiltaste nach links.");
            sendDataWithDelay("\r");
        }

        if (cursorPosition.equals("2362")) {
            System.out.println("Der Cursor hat den Wert '2362'. Drücke die Pfeiltaste nach links.");
            sendDataWithDelay("\u001BOQ");
        }

        System.out.println("Verzögerung vor der Verarbeitung der nächsten Bestellung.");
        if (terminalApp.isStopped()) {
            System.out.println("Verarbeitung gestoppt.");
            return;
        }
        terminalApp.checkForPause();
    }

    /**
     * Retrieves the current cursor position as a concatenated string of row and column.
     *
     * @return the cursor position string.
     * @throws InterruptedException if interrupted while waiting.
     */
    private String getCursorPosition() throws InterruptedException {
        final String[] cursorPosition = new String[1];
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            cursorPosition[0] = String.valueOf(cursor.getRow() + 1) + (cursor.getColumn() + 1);
            latch.countDown();
        });

        latch.await();
        return cursorPosition[0];
    }



    /**
     * Helper method to send data with a delay.
     *
     * @param data the data string to send.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if interrupted during delay.
     */
    private void sendDataWithDelay(String data) throws IOException, InterruptedException {
        sshConnector.send(data);
        int sleepTime = 300; // Delay in milliseconds
        int interval = 50;   // Check every 50 milliseconds
        int elapsed = 0;
        while (elapsed < sleepTime) {
            if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Verarbeitung gestoppt");
            }
            Thread.sleep(interval);
            elapsed += interval;
        }
    }
}
