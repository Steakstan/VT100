package org.msv.vt100.OrderAutomation;

import javafx.application.Platform;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.util.CellValueExtractor;
import org.msv.vt100.util.ExcelOrderData;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class DeliveryDateProcessor {
    private final SSHManager sshConnector;
    private final Cursor cursor;
    private final TerminalApp terminalApp;
    private final ScreenTextDetector screenTextDetector;

    public DeliveryDateProcessor(SSHManager sshConnector, Cursor cursor, TerminalApp terminalApp, ScreenTextDetector screenTextDetector) {

        this.sshConnector = sshConnector;
        this.cursor = cursor;
        this.terminalApp = terminalApp;
        this.screenTextDetector = screenTextDetector;
    }

    public void processDeliveryDate(Row row, ExcelOrderData.ColumnIndices indices) throws InterruptedException, IOException {
        System.out.println("-----------------------------------------------------");
        System.out.println("START processDeliveryDate: Начало обработки заказа aus Excel.");

        ExcelOrderData orderData = ExcelOrderData.fromExcelRow(row, indices);
        String orderNumber = orderData.orderNumber();
        String positionNumber = orderData.positionNumber();
        String deliveryDate = orderData.deliveryDate();
        String confirmationNumber = orderData.confirmationNumber();
        boolean hasConfirmationCol = indices.confirmationCol >= 0;

        System.out.println("Aus Excel extrahiert: " + orderData);
        System.out.println("AB-Nummer (Bestätigung): " + confirmationNumber);

        getInitialScreenText();
        String screenText;
        navigateToStartPage();
        screenText = getScreenText();

        if (orderNumber.length() == 5 && screenText.contains("Auf-Nr.:")) {
            System.out.println("5-stellige Bestellnummer erkannt. Sende 'L'.");
            sendDataWithDelay("L");
            sendDataWithDelay("\r");
        } else if (orderNumber.length() == 6 && screenText.contains("LB-Nr.:")) {
            System.out.println("6-stellige Bestellnummer mit 'LB-Nr.:' erkannt. Sende 'K'.");
            sendDataWithDelay("K");
            sendDataWithDelay("\r");
        }

        System.out.println("Sende Bestellnummer: " + orderNumber);
        sendDataWithDelay(orderNumber);
        sendDataWithDelay("\r");

        boolean weFilialeScreenDetected = false;
        boolean bitteAusloesenDetected = false;

        if (waitForPositionPrompt()) {
            System.out.println("Sende Positionsnummer: " + positionNumber);
            sendDataWithDelay(positionNumber);
            sendDataWithDelay("\r");

            Thread.sleep(200); // небольшая пауза перед проверкой
            String screenTextAfterPosition = getScreenText();
            if (screenTextAfterPosition.contains("Keine Bestellware!")) {
                System.out.println("WARNUNG: 'Keine Bestellware!' erkannt. Zurück zur Startseite.");
                navigateToStartPage();
                return;
            }

            screenText = getScreenText();
            if (screenText.contains("Position wurde storniert!")) {
                System.out.println("WARNUNG: 'Position wurde storniert!' erkannt. Zurück zur Startseite.");
                navigateToStartPage();
                return;
            }

            String cursor = getCursorPosition();
            weFilialeScreenDetected = cursor.equals("9,36") && screenText.contains("Vorgesehene WE-Filiale");
            bitteAusloesenDetected = cursor.equals("24,80") && screenText.contains("Bitte ausloesen !");
        }

        if (screenTextDetector.isWareneingangDisplayed()) {
            System.out.println("INFO: Bestellung wurde bereits geliefert. Verarbeitung wird abgebrochen.");
            navigateToStartPage();
            return;
        }

        if (!(weFilialeScreenDetected || bitteAusloesenDetected)) {
            if (!waitForOkPromptAndCompareDate(deliveryDate, hasConfirmationCol)) {
                return;
            }
        }

        if (bitteAusloesenDetected) {
            handleBitteAusloesenLoop();
        }

        waitForDeliveryDateInputPrompt(deliveryDate);
        waitForBestellTerminWarningsToDisappear();

        handleFinalInputSequence(deliveryDate, confirmationNumber, hasConfirmationCol);

        System.out.println("Verzögerung vor der Verarbeitung der nächsten Bestellung. Ende der Verarbeitung dieses Auftrags.");
        System.out.println("-----------------------------------------------------");
    }




    private boolean waitForOkPromptAndCompareDate(String deliveryDate, boolean hasConfirmationColumn) throws IOException, InterruptedException {
        System.out.println("Warte auf Bedingung: 'OK (J/N/L/T/G)' bei Cursor 13,74.");
        while (true) {
            terminalApp.checkForPause();
            String currCursor = getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Cursor bei OK-Bedingung: " + currCursor + "; Bildschirmtext: " + screenText);
            if (currCursor.equals("13,74") && screenText.contains("OK (J/N/L/T/G)")) {
                String existingDate = CellValueExtractor.extractCells(terminalApp.getScreenBuffer(), 9, 37, 38, 39, 40);
                String ab1 = CellValueExtractor.extractCells(terminalApp.getScreenBuffer(), 9, 50);
                String ab2 = CellValueExtractor.extractCells(terminalApp.getScreenBuffer(), 9, 51);
                boolean confirmationMissing = (ab1 + ab2).trim().isEmpty();

                System.out.println("Vergleiche vorhandenes Lieferdatum (" + existingDate + ") mit dem Excel-Datum (" + deliveryDate + ").");
                System.out.println("Besteht eine AB-Spalte in Excel? " + hasConfirmationColumn + ", und ist AB-Feld leer? " + confirmationMissing);

                if (existingDate.equals(deliveryDate) && !(hasConfirmationColumn && confirmationMissing)) {
                    System.out.println("INFO: Lieferdatum ist bereits gesetzt und AB vorhanden. Verarbeitung wird übersprungen.");
                    navigateToStartPage();
                    return false;
                }
                System.out.println("Bedingung erfüllt. Sende 'N'.");
                sendDataWithDelay("N");
                sendDataWithDelay("\r");

                // 🔽 Neue Bedingung: sofort prüfen, ob 'Bitte ausloesen !' erscheint
                Thread.sleep(100);
                String postNscreen = getScreenText();
                String postNcursor = getCursorPosition();
                if (postNcursor.equals("24,80") && postNscreen.contains("Bitte ausloesen !")) {
                    System.out.println("Nach 'N': 'Bitte ausloesen !' erkannt. Starte entsprechende Verarbeitung.");
                    handleBitteAusloesenLoop();
                }

                break;
            }
            Thread.sleep(50);
        }
        return true;
    }



    private void handleBitteAusloesenLoop() throws IOException, InterruptedException {
        System.out.println("Überprüfe, ob 'Bitte ausloesen !' bei Cursor 24,80 angezeigt wird.");
        while (true) {
            terminalApp.checkForPause();
            String currCursor = getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Cursor bei 'Bitte ausloesen !'-Prüfung: " + currCursor + "; Bildschirmtext: " + screenText);
            if (currCursor.equals("24,80") && screenText.contains("Bitte ausloesen !")) {
                System.out.println("Meldung erkannt. Sende Enter.");
                sendDataWithDelay("\r");
                Thread.sleep(50);
            } else if (!screenText.contains("Bitte ausloesen !")) {
                System.out.println("'Bitte ausloesen !' ist nicht mehr vorhanden.");
                break;
            } else {
                Thread.sleep(50);
            }
        }
    }

    private void waitForDeliveryDateInputPrompt(String deliveryDate) throws IOException, InterruptedException {
        System.out.println("Warte auf 'Vorgesehene WE-Filiale' bei Cursor 9,36, um das Lieferdatum zu senden.");
        while (true) {
            terminalApp.checkForPause();
            String currCursor = getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Cursor bei WE-Filiale: " + currCursor + "; Bildschirmtext: " + screenText);
            if (currCursor.equals("9,36") && screenText.contains("Vorgesehene WE-Filiale")) {
                System.out.println("Bedingung erfüllt. Sende Lieferdatum: " + deliveryDate);
                sendDataWithDelay(deliveryDate);
                sendDataWithDelay("\r");
                break;
            }
            Thread.sleep(50);
        }
    }

    private void waitForBestellTerminWarningsToDisappear() throws InterruptedException {
        System.out.println("Überprüfe, ob Meldungen 'Bestell-Termin um ' und 'ueberschritten!' angezeigt werden.");
        while (true) {
            terminalApp.checkForPause();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Während 'Bestell-Termin'-Prüfung: " + screenText);
            if (screenText.contains("Bestell-Termin um ") && screenText.contains("ueberschritten!")) {
                System.out.println("Meldungen erkannt. Warte auf ihr Verschwinden.");
                Thread.sleep(50);
            } else {
                System.out.println("Keine kritischen Meldungen mehr.");
                break;
            }
        }
    }
    private void handleFinalInputSequence(String deliveryDate, String confirmationNumber, boolean hasConfirmationCol) throws IOException, InterruptedException {
        sendDataWithDelay("T");
        sendDataWithDelay("\r");

        waitForErfassenAbNummer(confirmationNumber, hasConfirmationCol);
        waitForCursorAt960();
        checkForBitteAusloesenNach960();

        if (shouldWriteComment()) {
            waitForEingabenOkPromptAndSendZ();            // Sende 'Z'
            waitForInternerTextAndComment(deliveryDate);  // Kommentar eingeben
            waitForTextKZandOQSequence();                 // OQ + Eingabe OK
        } else {
            System.out.println("Kommentar deaktiviert – überspringe Eingabe und sende nur Enter.");
            finalEingabenOkEnter();
            return;
        }

        finalEingabenOkEnter();
        finalPosNrEnter();
    }


    private void waitForErfassenAbNummer(String confirmationNumber, boolean hasConfirmationCol) throws IOException, InterruptedException {
        System.out.println("Warte auf 'Erfassen AB-Nummer' bei Cursor 14,31.");
        while (true) {
            terminalApp.checkForPause();
            String cursor = getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Cursor = " + cursor + ", Text = " + screenText);
            if (cursor.equals("14,31") && screenText.contains("Erfassen AB-Nummer")) {
                System.out.println("'Erfassen AB-Nummer' erkannt.");
                if (hasConfirmationCol && confirmationNumber != null && !confirmationNumber.isEmpty()) {
                    System.out.println("Sende AB-Nummer: " + confirmationNumber);
                    sendDataWithDelay(confirmationNumber);
                    sendDataWithDelay("\r");
                } else {
                    System.out.println("Keine AB-Nummer vorhanden oder nicht erforderlich. Sende Enter.");
                    sendDataWithDelay("\r");
                }
                break;
            }
            Thread.sleep(50);
        }
    }



    private void waitForCursorAt960() throws IOException, InterruptedException {
        System.out.println("Warte auf Cursorposition 9,60.");
        while (true) {
            terminalApp.checkForPause();
            String cursor = getCursorPosition();
            if (cursor.equals("9,60")) {
                System.out.println("Cursor erkannt. Sende Enter.");
                sendDataWithDelay("\r");
                break;
            }
            Thread.sleep(50);
        }
    }

    private void checkForBitteAusloesenNach960() throws IOException, InterruptedException {
        System.out.println("Prüfe nach 9,60, ob 'Bitte ausloesen !' erscheint.");
        long start = System.currentTimeMillis();
        boolean erkannt = false;
        while (System.currentTimeMillis() - start < 500) {
            terminalApp.checkForPause();
            String screenText = getScreenText();
            String cursor = getCursorPosition();
            if (cursor.equals("24,80") && screenText.contains("Bitte ausloesen !")) {
                erkannt = true;
                System.out.println("'Bitte ausloesen !' erkannt. Beginne Enter-Schleife.");
                break;
            }
            if (screenText.contains("Eingaben OK") &&
                    (cursor.equals("23,75") || cursor.equals("23,76") || cursor.equals("23,77") || cursor.equals("23,78"))) {
                System.out.println("'Eingaben OK' erkannt. Kein Auslösen notwendig.");
                break;
            }
            Thread.sleep(50);
        }

        if (erkannt) {
            while (true) {
                terminalApp.checkForPause();
                String before = getScreenText();
                String beforeCursor = getCursorPosition();

                if (beforeCursor.equals("24,80") && before.contains("Bitte ausloesen !")) {
                    System.out.println("Sende Enter (ausloesen-Schleife).");
                    sendDataWithDelay("\r");
                    Thread.sleep(50);
                    String after = getScreenText();
                    if (!after.equals(before)) {
                        continue;
                    }
                    while (true) {
                        terminalApp.checkForPause();
                        Thread.sleep(50);
                        after = getScreenText();
                        String currentCursor = getCursorPosition();
                        if (!after.equals(before)) break;
                        if (!after.contains("Bitte ausloesen !") || !currentCursor.equals("24,80")) {
                            System.out.println("'Bitte ausloesen !' verschwunden.");
                            return;
                        }
                    }
                } else {
                    System.out.println("'Bitte ausloesen !' nicht mehr sichtbar.");
                    break;
                }
            }
        }
    }

    private void waitForEingabenOkPromptAndSendZ() throws IOException, InterruptedException {
        System.out.println("Warte auf 'Eingaben OK' und Cursorposition 23,75–23,78.");
        while (true) {
            terminalApp.checkForPause();
            String cursor = getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Cursor: " + cursor + "; Bildschirmtext: " + screenText);
            if (screenText.contains("Eingaben OK") &&
                    (cursor.equals("23,75") || cursor.equals("23,76") || cursor.equals("23,77") || cursor.equals("23,78"))) {
                System.out.println("Bedingung erfüllt. Sende 'Z'.");
                sendDataWithDelay("Z");
                sendDataWithDelay("\r");
                break;
            }
            Thread.sleep(100);
        }
    }

    private void waitForInternerTextAndComment(String deliveryDate) throws IOException, InterruptedException {
        System.out.println("Prüfe auf Cursorposition 22,2 und Text 'Interner Text'.");
        while (true) {
            terminalApp.checkForPause();
            String cursor = getCursorPosition();
            String screenText = getScreenText();

            if (cursor.equals("24,80") && screenText.contains("Bitte ausloesen !")) {
                while (cursor.equals("24,80") && screenText.contains("Bitte ausloesen !")) {
                    sendDataWithDelay("\r");
                    Thread.sleep(50);
                    cursor = getCursorPosition();
                    screenText = getScreenText();
                }
            }

            if (cursor.equals("22,2") && screenText.contains("Interner Text")) {
                System.out.println("'Interner Text' erkannt. Sende Enter.");
                sendDataWithDelay("\r");
                break;
            }
            Thread.sleep(200);
        }

        while (true) {
            terminalApp.checkForPause();
            String numberText = CellValueExtractor.extractCells(terminalApp.getScreenBuffer(), 22, 1, 2, 3);
            System.out.println("Extrahierte Zahl: " + numberText);
            if (numberText.matches("\\d{3}")) {
                String kw = extractWeekFromDeliveryDate(deliveryDate);
                String template = getUserCommentTemplate();
                String comment = template.replace("**", kw);
                System.out.println("Sende Kommentar: " + comment);
                sendDataWithDelay(comment);
                sendDataWithDelay("\r");
                break;
            }
            Thread.sleep(50);
        }
    }

    private void waitForTextKZandOQSequence() throws IOException, InterruptedException {
        System.out.println("Warte auf 'Text-KZ' bei Cursorposition 22,74–22,78.");
        while (true) {
            terminalApp.checkForPause();
            String cursor = getCursorPosition();
            String screenText = getScreenText();
            if ((cursor.equals("22,74") || cursor.equals("22,73") || cursor.equals("22,78")) &&
                    screenText.contains("Text-KZ")) {
                System.out.println("Erkannt. Sende Enter.");
                sendDataWithDelay("\r");
                break;
            }
            Thread.sleep(1000);
        }

        System.out.println("Überprüfung der Position 22,2.");
        while (true) {
            terminalApp.checkForPause();
            String cursor = getCursorPosition();
            String screenText = getScreenText();
            if (cursor.equals("22,2") && screenText.contains("Text-KZ")) {
                System.out.println("Sende '\u001BOQ' und warte auf Cursor 23,75–23,78.");
                sendDataWithDelay("\u001BOQ");
                Thread.sleep(50);
                while (true) {
                    terminalApp.checkForPause();
                    cursor = getCursorPosition();
                    if (cursor.equals("23,75") || cursor.equals("23,76") || cursor.equals("23,77") || cursor.equals("23,78")) {
                        System.out.println("Zielposition erreicht: " + cursor);
                        break;
                    }
                    Thread.sleep(200);
                }
                break;
            }
            Thread.sleep(1000);
        }
    }

    private void finalEingabenOkEnter() throws IOException, InterruptedException {
        System.out.println("Warte auf 'Eingaben OK' bei Cursorposition 23,75–23,78.");
        while (true) {
            terminalApp.checkForPause();
            String screenText = getScreenText();
            String cursor = getCursorPosition();
            if (screenText.contains("Eingaben OK") &&
                    (cursor.equals("23,75") || cursor.equals("23,76") || cursor.equals("23,77") || cursor.equals("23,78"))) {
                System.out.println("Erkannt. Sende Enter.");
                sendDataWithDelay("\r");
                break;
            }
            Thread.sleep(1000);
        }
    }

    private void finalPosNrEnter() throws IOException, InterruptedException {
        while (true) {
            terminalApp.checkForPause();
            String cursor = getCursorPosition();
            String screenText = getScreenText();
            if (cursor.equals("3,11") || cursor.equals("3,24")) {
                System.out.println("Zurück auf der Startseite erkannt. Überspringe.");
                break;
            }
            if (screenTextDetector.isPosNrDisplayed() &&
                    cursor.equals("23,62") && screenText.contains("Pos-Nr.:")) {
                System.out.println("Erkannte Positionsanzeige. Sende Enter.");
                sendDataWithDelay("\r");
                break;
            }
            Thread.sleep(1000);
        }
    }
    private String getScreenText() {
        try {
            java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
            return (String) method.invoke(screenTextDetector);
        } catch (Exception e) {
            System.out.println("Fehler beim Aufruf von getScreenText(), fallback auf toString().");
            return screenTextDetector.toString();
        }
    }

    private boolean waitForPositionPrompt() throws InterruptedException {
        System.out.println("Warte auf Bildschirm für Positionsnummer-Eingabe.");
        while (true) {
            terminalApp.checkForPause();
            String currentCursor = getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Aktueller Cursor bei Positions-Eingabe: " + currentCursor);
            if (currentCursor.equals("4,11") && screenText.contains("Pos.")) {
                System.out.println("Eingabeaufforderung für Positionsnummer erkannt.");
                return true;
            }
            Thread.sleep(50);
        }
    }


    private void navigateToStartPage() throws IOException, InterruptedException {
        System.out.println("Navigiere zur Startseite...");
        final String STARTUP_CURSOR = "3,11";
        int noChangeCounter = 0;
        final int maxAttempts = 2;

        while (true) {
            String cursorBefore = getCursorPosition();

            if (cursorBefore.equals(STARTUP_CURSOR) &&
                    (getScreenText().contains("Auf-Nr.:") || getScreenText().contains("LB-Nr.:"))) {
                System.out.println("Startseite erreicht.");
                break;
            }

            // Sonderfall: direkt auf "Programm - Nr." Bildschirm
            if (cursorBefore.equals("3,24") && getScreenText().contains("Programm - Nr.:")) {
                System.out.println("Navigation: Bildschirm zeigt 'Programm - Nr.:'. Sende '5.0321'.");
                String snapshotBefore = captureRelevantScreenPart();
                sendDataWithDelay("5.0321\r");
                if (hasRelevantScreenChanged(snapshotBefore, cursorBefore)) {
                    noChangeCounter = 0;
                    continue;
                } else {
                    System.out.println("[DEBUG] Keine Änderung nach '5.0321'.");
                    noChangeCounter++;
                }
            }

            // Schritt 1: OS senden
            String snapshotBeforeOS = captureRelevantScreenPart();
            System.out.println("Sende '\u001BOQ'");
            sendDataWithDelay("\u001BOQ");
            boolean changedAfterOS = hasRelevantScreenChanged(snapshotBeforeOS, cursorBefore);
            if (changedAfterOS) {
                noChangeCounter = 0;
                continue;
            } else {
                System.out.println("[DEBUG] Keine Änderung nach OS. Versuch: " + (noChangeCounter + 1));
                noChangeCounter++;
            }

            // Только если OS вызвало изменения — пробуем OQ

            if (noChangeCounter >= maxAttempts) {
                System.out.println("[WARNUNG] Keine Reaktion nach " + maxAttempts + " Versuchen. Warte auf manuelle Änderung...");
                Thread.sleep(1000);
                noChangeCounter = 0;
            }

            Thread.sleep(50);
        }
    }



    private String captureRelevantScreenPart() {
        StringBuilder snapshot = new StringBuilder();
        for (int col = 35; col <= 68; col++) {
            snapshot.append(terminalApp.getScreenBuffer().getCell(8, col).character());
        }
        for (int col = 39; col <= 59; col++) {
            snapshot.append(terminalApp.getScreenBuffer().getCell(22, col).character());
        }
        return snapshot.toString();
    }

    private boolean hasRelevantScreenChanged(String previousSnapshot, String previousCursor) throws InterruptedException {
        String currentSnapshot = captureRelevantScreenPart();
        String currentCursor = getCursorPosition();
        boolean changed = !previousSnapshot.equals(currentSnapshot) && !previousCursor.equals(currentCursor);
        System.out.println("[DEBUG] Snapshot geändert? " + !previousSnapshot.equals(currentSnapshot) +
                " | Cursor geändert? " + !previousCursor.equals(currentCursor));
        return changed;
    }



    private String extractWeekFromDeliveryDate(String deliveryDate) {
        System.out.println("Extrahiere Kalenderwoche aus Lieferdatum: " + deliveryDate);
        if (deliveryDate != null && deliveryDate.length() >= 2) {
            String week = deliveryDate.substring(0, 2);
            System.out.println("KW: " + week);
            return week;
        }
        return "??";
    }

    private String getCursorPosition() throws InterruptedException {
        final String[] pos = new String[1];
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            pos[0] = (cursor.getRow() + 1) + "," + (cursor.getColumn() + 1);
            latch.countDown();
        });
        latch.await();
        return pos[0];
    }

    private void sendDataWithDelay(String data) throws IOException, InterruptedException {
        System.out.println("Sende Daten: '" + data.trim() + "'");
        sshConnector.send(data);
        int sleepTime = 60;
        int interval = 30;
        int elapsed = 0;
        while (elapsed < sleepTime) {
            if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Verarbeitung gestoppt");
            }
            Thread.sleep(interval);
            elapsed += interval;
        }
    }

    public void processDeliveryDates(Sheet sheet) throws IOException, InterruptedException {
        System.out.println("Starte Verarbeitung mehrerer Bestellungen...");

        ExcelOrderData.ColumnIndices indices = ExcelOrderData.detectAllColumns(sheet, terminalApp);

        Iterator<Row> rows = sheet.iterator();
        // Пропускаем заголовок
        if (rows.hasNext()) rows.next();

        while (rows.hasNext()) {
            if (terminalApp.isStopped()) {
                System.out.println("Verarbeitung gestoppt.");
                break;
            }
            terminalApp.checkForPause();
            Row row = rows.next();
            System.out.println("Verarbeite nächste Zeile.");
            processDeliveryDate( row, indices);  // ✅ теперь передаём sheet и индексы
        }

        System.out.println("Verarbeitung aller Bestellungen abgeschlossen.");
    }



    private void getInitialScreenText() {
        try {
            java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
            String screenText = (String) method.invoke(screenTextDetector);
            System.out.println("Initialer Bildschirmtext: " + screenText);
        } catch (Exception e) {
            String fallback = screenTextDetector.toString();
            System.out.println("Fehler beim Abruf des Bildschirmtextes, fallback auf toString(): " + fallback);
        }
    }
    private boolean shouldWriteComment() {
        return terminalApp.isShouldWriteComment();
    }

    private String getUserCommentTemplate() {
        return terminalApp.getCommentText();
    }

}
