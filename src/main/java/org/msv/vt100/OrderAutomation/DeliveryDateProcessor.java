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
 * DeliveryDateProcessor handles the processing of delivery dates for orders.
 * It reads order and delivery data from an Excel sheet, sends commands via SSH,
 * and interacts with the terminal based on the current screen content.
 */
public class DeliveryDateProcessor {
    private final SSHManager sshConnector;
    private final Cursor cursor;
    private final TerminalApp terminalApp;
    private final ScreenTextDetector screenTextDetector;

    /**
     * Constructs a DeliveryDateProcessor.
     *
     * @param sshConnector       the SSHManager for sending commands.
     * @param cursor             the terminal cursor.
     * @param terminalApp        the main TerminalApp.
     * @param screenTextDetector the detector for screen content.
     */
    public DeliveryDateProcessor(SSHManager sshConnector, Cursor cursor, TerminalApp terminalApp, ScreenTextDetector screenTextDetector) {
        this.sshConnector = sshConnector;
        this.cursor = cursor;
        this.terminalApp = terminalApp;
        this.screenTextDetector = screenTextDetector;
    }

    /**
     * Processes the delivery date for a single order.
     *
     * @param row the Excel row containing order and delivery data.
     * @throws InterruptedException if processing is interrupted.
     * @throws IOException          if an I/O error occurs.
     */
    public void processDeliveryDate(Row row) throws InterruptedException, IOException {
        String orderNumber = FileExtractor.extractCellValueAsString(row.getCell(0));
        String positionNumber = FileExtractor.extractCellValueAsString(row.getCell(1));
        String deliveryDate = FileExtractor.extractCellValueAsString(row.getCell(2));
        if (screenTextDetector.isAufNrDisplayed()) {
            if (orderNumber.length() == 5) {
                System.out.println("Auf dem Bildschirm 'Auf-Nr.:' und die Bestellnummer besteht aus 5 Ziffern. Sende 'L' an den Server.");
                terminalApp.checkForStop();
                sendDataWithDelay("L");
                sendDataWithDelay("\r");
            } else if (orderNumber.length() != 6) {
                System.out.println("Fehler: Die Bestellnummer muss 5 oder 6 Zeichen enthalten. Verarbeitung gestoppt.");
                return;
            }
        } else if (screenTextDetector.isLbNrDisplayed()) {
            if (orderNumber.length() == 6) {
                System.out.println("Auf dem Bildschirm 'LB-Nr.:' und die Bestellnummer besteht aus 6 Ziffern. Sende 'K' an den Server.");
                terminalApp.checkForStop();
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

        // Label for returning to the start of order processing
        startProcessing:

        while (true) {
            System.out.println("Verarbeitung des Lieferdatums für Bestellung: " + orderNumber);

            String cursorPosition = getCursorPosition();

            while (!cursorPosition.equals("311")) {
                System.out.println("Die Cursorposition ist nicht '311'. Bewege den Cursor nach links und überprüfe erneut.");
                terminalApp.checkForStop();
                sendDataWithDelay("\u001BOQ");

                // Recheck cursor position
                cursorPosition = getCursorPosition();

                if (cursorPosition.equals("2362")) {
                    System.out.println("Der Cursor befindet sich in Position '2362'. Eingabe des Buchstabens 'L' und des Wertes '5.0321'.");
                    terminalApp.checkForStop();
                    sendDataWithDelay("L");
                    sendDataWithDelay("\r");
                    terminalApp.checkForStop();
                    sendDataWithDelay("5.0321");
                    sendDataWithDelay("\r");

                    System.out.println("Erneute Verarbeitung der aktuellen Bestellung: " + orderNumber);
                    processDeliveryDate(row); // Recursively process the current order again
                    return; // End current processing
                }

                // Move the cursor left until it is at position 311
                while (!cursorPosition.equals("311")) {
                    terminalApp.checkForStop();
                    sendDataWithDelay("\u001BOQ");
                    cursorPosition = getCursorPosition();
                }

                System.out.println("Der Cursor wurde auf Position 311 gesetzt. Erneuter Versuch der Auftragsverarbeitung.");

                // Repeat order input
                processDeliveryDate(row);
                return;
            }

            System.out.println("Der Cursor wurde auf Position 311 gesetzt.");

            // Input order number and position
            System.out.println("Bestellnummer eingeben: " + orderNumber);
            terminalApp.checkForStop();
            sendDataWithDelay(orderNumber);
            sendDataWithDelay("\r");

            System.out.println("Positionsnummer eingeben: " + positionNumber);
            terminalApp.checkForStop();
            sendDataWithDelay(positionNumber);
            sendDataWithDelay("\r");

            System.out.println("Überprüfung, ob die Bestellung bereits geliefert wurde.");
            if (screenTextDetector.isWareneingangDisplayed()) {
                return;
            }

            cursorPosition = getCursorPosition();
            if (!cursorPosition.equals("1374")) {
                System.out.println("Der Cursor ist nicht auf Position 1374. Wechsle zur nächsten Bestellung. Aktuelle Position: " + cursorPosition);
                return; // Abort processing of the current order
            }

            // If the cursor is at position 1374
            System.out.println("Der Cursor wurde auf Position 1374 gesetzt. Eingabe des Buchstabens 'N' und Drücken von Enter.");
            terminalApp.checkForStop();
            sendDataWithDelay("N");
            sendDataWithDelay("\r");
            cursorPosition = getCursorPosition();

            // Continue pressing Enter until position 936 is reached
            System.out.println("Drücke Enter weiter, bis Position 936 erreicht ist.");
            // For storing the previous cursor position
            int consecutive411Count = 0; // Counter for repeated appearance of position 411

            while (!cursorPosition.equals("936")) {
                terminalApp.checkForStop();
                sendDataWithDelay("\r");
                cursorPosition = getCursorPosition();
                System.out.println("Drücke Enter. Cursorposition: " + cursorPosition);

                // Check for repeated position 411
                if (cursorPosition.equals("411")) {
                    terminalApp.checkForStop();
                    consecutive411Count++;
                    System.out.println("Der Cursor ist auf Position 411. Wiederholungscount: " + consecutive411Count);
                    if (consecutive411Count == 2) {
                        System.out.println("Der Cursor war zweimal hintereinander auf Position 411. Auftragsverarbeitung zurückgesetzt.");
                        return; // Return to the beginning of the processing for the same order
                    }
                } else {
                    consecutive411Count = 0; // Reset the counter if the position is different from 411
                }
            }
            System.out.println("Der Cursor wurde auf Position 936 gesetzt.");

            // Input delivery date
            System.out.println("Lieferdatum eingeben: " + deliveryDate);
            terminalApp.checkForStop();
            sendDataWithDelay(deliveryDate);
            sendDataWithDelay("\r");

            // Input letter "T" and press Enter until position 2375 is reached
            System.out.println("Eingabe des Buchstabens 'T' und Drücken von Enter, bis Position 2375 erreicht ist.");
            terminalApp.checkForStop();
            sendDataWithDelay("T");
            sendDataWithDelay("\r");
            Thread.sleep(1500);
            label:
            while (true) {
                terminalApp.checkForStop();
                sendDataWithDelay("\r");
                cursorPosition = getCursorPosition();
                System.out.println("Drücke Enter: " + cursorPosition);

                switch (cursorPosition) {
                    case "2375":
                        terminalApp.checkForStop();
                        System.out.println("Der Cursor wurde auf Position 2375 gesetzt.");
                        break label;
                    case "2376":
                        terminalApp.checkForStop();
                        System.out.println("Der Cursor wurde auf Position 2376 gesetzt.");
                        break label;
                    case "2377":
                        terminalApp.checkForStop();
                        System.out.println("Der Cursor wurde auf Position 2377 gesetzt.");
                        break label;
                    case "2378":
                        terminalApp.checkForStop();
                        System.out.println("Der Cursor wurde auf Position 2378 gesetzt.");
                        break label;
                    case "2362":
                        terminalApp.checkForStop();
                        System.out.println("Der Cursor befindet sich in Position '2362'. Eingabe des Buchstabens 'L' und des Wertes '5.0321'.");
                        sendDataWithDelay("L");
                        sendDataWithDelay("\r");
                        sendDataWithDelay("5.0321");
                        sendDataWithDelay("\r");

                        System.out.println("Erneute Verarbeitung der aktuellen Bestellung: " + orderNumber);
                        processDeliveryDate(row); // Recursive call for reprocessing the current order
                        return;
                    case "411":
                        terminalApp.checkForStop();
                        System.out.println("Der Cursor wurde auf Position 411 gesetzt. Schleife wird abgebrochen und der Auftrag erneut verarbeitet.");
                        continue startProcessing;
                }
            }

            // Input "Z" and press Enter until position 2212 is reached
            System.out.println("Eingabe des Buchstabens 'Z' und Drücken von Enter, bis Position 2212 erreicht ist.");
            terminalApp.checkForStop();
            sendDataWithDelay("Z");
            sendDataWithDelay("\r");
            cursorPosition = getCursorPosition();
            while (!cursorPosition.equals("2212") && !cursorPosition.equals("2211")) {
                terminalApp.checkForStop();
                sendDataWithDelay("\r");
                cursorPosition = getCursorPosition();
            }
            System.out.println("Der Cursor wurde auf Position " + cursorPosition + " gesetzt.");

            // Extract the first two characters from the delivery date for the comment
            String kwWeek = extractWeekFromDeliveryDate(deliveryDate);

            // Insert a comment using the delivery date
            String comment = "DEM HST NACH WIRD DIE WARE IN KW " + kwWeek + " ZUGESTELLT";
            System.out.println("Kommentar eingeben: " + comment);
            terminalApp.checkForStop();
            sendDataWithDelay(comment);
            sendDataWithDelay("\r");

            // Check cursor position via F1
            System.out.println("Überprüfung der Cursorposition über F1.");
            cursorPosition = getCursorPosition();
            if (cursorPosition.equals("2274") || cursorPosition.equals("2273") || cursorPosition.equals("2278")) {
                terminalApp.checkForStop();
                System.out.println("Der Cursor wurde auf Position " + cursorPosition + " gesetzt. Drücke Enter.");
                sendDataWithDelay("\r");
            }

            // Check position 222
            System.out.println("Überprüfung der Position 222.");
            cursorPosition = getCursorPosition();
            if (cursorPosition.equals("222")) {
                System.out.println("Der Cursor wurde auf Position 222 gesetzt.");
                // Return to position 2375
                System.out.println("Rückkehr zur Position 2375 durch Drücken der linken Pfeiltaste.");
                while (!cursorPosition.equals("2375") && !cursorPosition.equals("2376") && !cursorPosition.equals("2377") && !cursorPosition.equals("2378")) {
                    terminalApp.checkForStop();
                    sendDataWithDelay("\u001BOQ");
                    cursorPosition = getCursorPosition();
                }
                sendDataWithDelay("\r");
            }

            cursorPosition = getCursorPosition();

            if (screenTextDetector.isPosNrDisplayed() && cursorPosition.equals("2362")) {
                terminalApp.checkForStop();
                sendDataWithDelay("\r");
            }

            // Check for position 2362 for finishing up
            System.out.println("Überprüfung der Position 2362.");
            cursorPosition = getCursorPosition();
            if (cursorPosition.equals("2362")) {
                terminalApp.checkForStop();
                System.out.println("Der Cursor wurde auf Position 2362 gesetzt. Drücke die linke Pfeiltaste.");
                sendDataWithDelay("\u001BOQ");
            }

            System.out.println("Verzögerung vor der Verarbeitung der nächsten Bestellung.");

            break; // Exit the loop after successfully processing the order
        }
    }

    /**
     * Extracts the first two characters from the delivery date for use in a comment.
     *
     * @param deliveryDate the delivery date string.
     * @return the extracted week value or "??" if the date is invalid.
     */
    private String extractWeekFromDeliveryDate(String deliveryDate) {
        // It is assumed that deliveryDate is in the format "dd.MM.yyyy" or similar.
        if (deliveryDate != null && deliveryDate.length() >= 2) {
            return deliveryDate.substring(0, 2);  // Extract the first two characters
        }
        return "??";  // Return default value if date is invalid
    }

    /**
     * Processes delivery dates for multiple orders from the given row iterator.
     *
     * @param rows an Iterator of Excel rows.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if processing is interrupted.
     */
    public void processDeliveryDates(Iterator<Row> rows) throws IOException, InterruptedException {
        while (rows.hasNext()) {
            if (terminalApp.isStopped()) {
                System.out.println("Verarbeitung gestoppt.");
                break;
            }

            terminalApp.checkForPause();

            Row currentRow = rows.next();
            processDeliveryDate(currentRow);
        }
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
     * Helper method for sending data with a delay.
     *
     * @param data the data string to send.
     * @throws IOException          if an I/O error occurs.
     * @throws InterruptedException if interrupted during the delay.
     */
    private void sendDataWithDelay(String data) throws IOException, InterruptedException {
        sshConnector.send(data);
        int sleepTime = 500; // Delay in 500 ms
        int interval = 50;   // Check every 50 ms
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
