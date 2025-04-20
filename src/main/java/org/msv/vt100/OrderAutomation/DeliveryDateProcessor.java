package org.msv.vt100.OrderAutomation;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.util.CellValueExtractor;
import org.msv.vt100.util.ExcelOrderData;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import static org.msv.vt100.util.Waiter.waitUntil;

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
        boolean hasConfirmationCol = indices.confirmationCol() >= 0;

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

            Thread.sleep(70); // небольшая пауза перед проверкой
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

            String cursorPosition = cursor.getCursorPosition();
            weFilialeScreenDetected = cursorPosition.equals("9,36") && screenText.contains("Vorgesehene WE-Filiale");
            bitteAusloesenDetected = cursorPosition.equals("24,80") && screenText.contains("Bitte ausloesen !");
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

        boolean success = waitUntil("Cursor = 13,74 & Text enthält 'OK (J/N/L/T/G)'", () -> {
            terminalApp.checkForPause();
            String currCursor = cursor.getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Cursor bei OK-Bedingung: " + currCursor + "; Bildschirmtext: " + screenText);
            return currCursor.equals("13,74") && screenText.contains("OK (J/N/L/T/G)");
        });

        if (!success) {
            System.out.println("[WARNUNG] Timeout beim Warten auf OK-Prompt.");
            return false;
        }

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

        return true;
    }






    private void handleBitteAusloesenLoop() throws IOException, InterruptedException {
        System.out.println("Starte Schleife für 'Bitte ausloesen !'.");

        boolean erkannt = waitUntil("Cursor = 24,80 & Text enthält 'Bitte ausloesen !'", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Cursor bei 'Bitte ausloesen !'-Prüfung: " + cursorPosition + "; Bildschirmtext: " + screenText);
            return cursorPosition.equals("24,80") && screenText.contains("Bitte ausloesen !");
        });

        if (!erkannt) {
            System.out.println("INFO: 'Bitte ausloesen !' nicht erkannt.");
            return;
        }

        while (true) {
            terminalApp.checkForPause();
            String before = getScreenText();
            String cursorBefore = cursor.getCursorPosition();

            if (cursorBefore.equals("24,80") && before.contains("Bitte ausloesen !")) {
                System.out.println("Sende Enter (ausloesen-Schleife).");
                sendDataWithDelay("\r");

                // Warten, bis Bildschirm sich ändert
                boolean changed = waitUntil("Bildschirm ändert sich nach Enter", () -> {
                    terminalApp.checkForPause();
                    return !getScreenText().equals(before);
                });

                if (!changed) {
                    System.out.println("Bildschirm hat sich nicht geändert. Breche aus Schleife aus.");
                    break;
                }

                // Warten auf vollständiges Verschwinden von 'Bitte ausloesen!'
                boolean verschwunden = waitUntil("'Bitte ausloesen !' verschwunden", () -> {
                    terminalApp.checkForPause();
                    String after = getScreenText();
                    String currentCursor = cursor.getCursorPosition();
                    return !after.contains("Bitte ausloesen !") || !currentCursor.equals("24,80");
                });

                if (verschwunden) {
                    System.out.println("'Bitte ausloesen !' verschwunden.");
                    break;
                }
            } else {
                System.out.println("'Bitte ausloesen !' nicht mehr sichtbar.");
                break;
            }
        }
    }



    private void waitForDeliveryDateInputPrompt(String deliveryDate) throws IOException, InterruptedException {
        System.out.println("Warte auf 'Vorgesehene WE-Filiale' bei Cursor 9,36, um das Lieferdatum zu senden.");

        // 👉 Direkt prüfen: ggf. zuerst 'Bitte ausloesen!' verarbeiten
        if (cursor.getCursorPosition().equals("24,80") && getScreenText().contains("Bitte ausloesen !")) {
            System.out.println("'Bitte ausloesen !' erkannt – starte Schleife vor Lieferdatum.");
            handleBitteAusloesenLoop();
        }

        boolean success = waitUntil("Cursor = 9,36 & Text enthält 'Vorgesehene WE-Filiale'", () -> {
            terminalApp.checkForPause();
            String currCursor = cursor.getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Cursor bei WE-Filiale: " + currCursor + "; Bildschirmtext: " + screenText);
            return currCursor.equals("9,36") && screenText.contains("Vorgesehene WE-Filiale");
        });

        if (!success) throw new IOException("Timeout beim Warten auf Eingabefeld für Lieferdatum");

        System.out.println("Bedingung erfüllt. Sende Lieferdatum: " + deliveryDate);
        sendDataWithDelay(deliveryDate);
        sendDataWithDelay("\r");
    }




    private void waitForBestellTerminWarningsToDisappear() throws InterruptedException {
        System.out.println("Überprüfe, ob Meldungen 'Bestell-Termin um ' und 'ueberschritten!' angezeigt werden.");

        boolean success = waitUntil("Warnung 'Bestell-Termin um ... ueberschritten!'", () -> {
            terminalApp.checkForPause();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Während 'Bestell-Termin'-Prüfung: " + screenText);
            return !(screenText.contains("Bestell-Termin um ") && screenText.contains("ueberschritten!"));
        });

        if (success) {
            System.out.println("Keine kritischen Meldungen mehr.");
        } else {
            System.out.println("[WARNUNG] Timeout beim Warten auf das Verschwinden der Bestell-Termin-Warnung.");
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

        boolean success = waitUntil("Cursor = 14,31 & Text enthält 'Erfassen AB-Nummer'", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Cursor = " + cursorPosition + ", Text = " + screenText);
            return cursorPosition.equals("14,31") && screenText.contains("Erfassen AB-Nummer");
        });

        if (!success) throw new IOException("Timeout beim Warten auf Eingabe 'Erfassen AB-Nummer'");

        System.out.println("'Erfassen AB-Nummer' erkannt.");
        if (hasConfirmationCol && confirmationNumber != null && !confirmationNumber.isEmpty()) {
            System.out.println("Sende AB-Nummer: " + confirmationNumber);
            sendDataWithDelay(confirmationNumber);
            sendDataWithDelay("\r");
        } else {
            System.out.println("Keine AB-Nummer vorhanden oder nicht erforderlich. Sende Enter.");
            sendDataWithDelay("\r");
        }
    }






    private void waitForCursorAt960() throws IOException, InterruptedException {
        System.out.println("Warte auf Cursorposition 9,60.");

        boolean success = waitUntil("Cursor = 9,60", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            return cursorPosition.equals("9,60");
        });

        if (!success) throw new IOException("Timeout beim Warten auf Cursor 9,60");

        System.out.println("Cursor erkannt. Sende Enter.");
        sendDataWithDelay("\r");
    }



    private void checkForBitteAusloesenNach960() throws IOException, InterruptedException {
        System.out.println("Prüfe nach 9,60, ob 'Bitte ausloesen !' erscheint.");

        AusloeserStatus status = waitForAusloeserOderAlternative();

        if (status == AusloeserStatus.BITTE_AUSLOESEN) {
            System.out.println("'Bitte ausloesen !' erkannt. Beginne Enter-Schleife.");

            while (true) {
                terminalApp.checkForPause();
                String before = getScreenText();
                String cursorBefore = cursor.getCursorPosition();

                if (cursorBefore.equals("24,80") && before.contains("Bitte ausloesen !")) {
                    System.out.println("Sende Enter (ausloesen-Schleife).");
                    sendDataWithDelay("\r");

                    boolean changed = waitUntil("Bildschirm nach Enter verändert sich", () -> {
                        terminalApp.checkForPause();
                        return !getScreenText().equals(before);
                    });

                    if (!changed) {
                        System.out.println("Keine Änderung erkannt. Schleife beendet.");
                        break;
                    }

                    boolean verschwunden = waitUntil("'Bitte ausloesen !' verschwunden", () -> {
                        terminalApp.checkForPause();
                        String after = getScreenText();
                        String currentCursor = cursor.getCursorPosition();
                        return !after.contains("Bitte ausloesen !") || !currentCursor.equals("24,80");
                    });

                    if (verschwunden) {
                        System.out.println("'Bitte ausloesen !' verschwunden.");
                        break;
                    }
                } else {
                    System.out.println("'Bitte ausloesen !' nicht mehr sichtbar.");
                    break;
                }
            }

        } else {
            System.out.println("'Bitte ausloesen !' nicht erkannt oder andere Eingabe erkannt: " + status);
        }
    }



    private void waitForEingabenOkPromptAndSendZ() throws IOException, InterruptedException {
        System.out.println("Warte auf 'Eingaben OK' und Cursorposition 23,75–23,78.");

        boolean success = waitUntil("Eingaben OK & Cursor zwischen 23,75–23,78", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Cursor: " + cursorPosition + "; Bildschirmtext: " + screenText);
            return screenText.contains("Eingaben OK") &&
                    (cursorPosition.equals("23,75") || cursorPosition.equals("23,76") || cursorPosition.equals("23,77") || cursorPosition.equals("23,78"));
        });

        if (!success) throw new IOException("Timeout beim Warten auf Eingaben OK für 'Z'");

        System.out.println("Bedingung erfüllt. Sende 'Z'.");
        sendDataWithDelay("Z");
        sendDataWithDelay("\r");
    }



    private void waitForInternerTextAndComment(String deliveryDate) throws IOException, InterruptedException {
        System.out.println("Warte auf 'Interner Text' oder 'Bitte ausloesen !'...");

        AusloeserStatus status = waitForAusloeserOderAlternative();

        // 🔁 Wenn 'Bitte ausloesen!' zuerst erscheint → Enter-Schleife bis verschwunden
        while (status == AusloeserStatus.BITTE_AUSLOESEN) {
            System.out.println("'Bitte ausloesen !' erkannt. Sende Enter und warte erneut...");
            sendDataWithDelay("\r");

            status = waitForAusloeserOderAlternative();
        }

        if (status != AusloeserStatus.INTERNE_EINGABE) {
            throw new IOException("Weder 'Bitte ausloesen !' noch 'Interner Text' erschienen.");
        }

        System.out.println("'Interner Text' erkannt. Sende Enter.");
        sendDataWithDelay("\r");

        // ⌛ Warten auf dreistellige Zahl
        boolean nummerErkannt = waitUntil("Dreistellige Nummer in Zeile 22 erkannt", () -> {
            terminalApp.checkForPause();
            String numberText = CellValueExtractor.extractCells(terminalApp.getScreenBuffer(), 22, 1, 2, 3);
            System.out.println("Extrahierte Zahl: " + numberText);
            return numberText.matches("\\d{3}");
        });

        if (!nummerErkannt) throw new IOException("Timeout beim Warten auf dreistellige Nummer für Kommentar");

        // 💬 Kommentar erzeugen und senden
        String kw = extractWeekFromDeliveryDate(deliveryDate);
        String template = getUserCommentTemplate();
        String comment = template.replace("**", kw);
        System.out.println("Sende Kommentar: " + comment);
        sendDataWithDelay(comment);
        sendDataWithDelay("\r");
    }



    private void waitForTextKZandOQSequence() throws IOException, InterruptedException {
        System.out.println("Warte auf 'Text-KZ' bei Cursorposition 22,74–22,78.");

        boolean textKZPrompt = waitUntil("Cursor 22,74–78 & 'Text-KZ' erkannt", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            String screenText = getScreenText();
            return (cursorPosition.equals("22,74") || cursorPosition.equals("22,73") || cursorPosition.equals("22,78")) &&
                    screenText.contains("Text-KZ");
        });

        if (!textKZPrompt) throw new IOException("Timeout bei der ersten 'Text-KZ'-Eingabe");

        System.out.println("Erkannt. Sende Enter.");
        sendDataWithDelay("\r");

        System.out.println("Überprüfung der Position 22,2.");

        boolean textKZBei22_2 = waitUntil("Cursor = 22,2 & Text enthält 'Text-KZ'", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            String screenText = getScreenText();
            return cursorPosition.equals("22,2") && screenText.contains("Text-KZ");
        });

        if (!textKZBei22_2) throw new IOException("Timeout bei der zweiten 'Text-KZ'-Eingabe");

        System.out.println("Sende '\u001BOQ' und warte auf Cursor 23,75–23,78.");
        sendDataWithDelay("\u001BOQ");

        boolean zielCursorErreicht = waitUntil("Zielcursor bei 23,75–23,78 erreicht", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            return cursorPosition.equals("23,75") || cursorPosition.equals("23,76") || cursorPosition.equals("23,77") || cursorPosition.equals("23,78");
        });

        if (!zielCursorErreicht) throw new IOException("Timeout beim Warten auf Zielcursor nach OQ");
        System.out.println("Zielposition erreicht.");
    }


    private void finalEingabenOkEnter() throws IOException, InterruptedException {
        System.out.println("Warte auf 'Eingaben OK' bei Cursorposition 23,75–23,78.");

        boolean okErkannt = waitUntil("'Eingaben OK' + Cursor bei 23,75–23,78", () -> {
            terminalApp.checkForPause();
            String screenText = getScreenText();
            String cursorPosition = cursor.getCursorPosition();
            return screenText.contains("Eingaben OK") &&
                    (cursorPosition.equals("23,75") || cursorPosition.equals("23,76") || cursorPosition.equals("23,77") || cursorPosition.equals("23,78"));
        });

        if (!okErkannt) throw new IOException("Timeout beim Warten auf finalen Eingaben-OK-Prompt");

        System.out.println("Erkannt. Sende Enter.");
        sendDataWithDelay("\r");
    }


    private void finalPosNrEnter() throws IOException, InterruptedException {
        System.out.println("Warte auf Rückkehr zur Startseite oder auf 'Pos-Nr.:' bei 23,62.");

        boolean zielErreicht = waitUntil("Startseite (3,11 / 3,24) oder 'Pos-Nr.:' bei 23,62", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            String screenText = getScreenText();

            boolean startseite = cursorPosition.equals("3,11") || cursorPosition.equals("3,24");
            boolean posNrErkannt = screenTextDetector.isPosNrDisplayed()
                    && cursorPosition.equals("23,62")
                    && screenText.contains("Pos-Nr.:");

            return startseite || posNrErkannt;
        });

        if (!zielErreicht) throw new IOException("Timeout beim Warten auf Rückkehr oder 'Pos-Nr.:'");

        String cursorPosition = cursor.getCursorPosition();
        getScreenText();

        if (cursorPosition.equals("3,11") || cursorPosition.equals("3,24")) {
            System.out.println("Zurück auf der Startseite erkannt. Überspringe.");
        } else {
            System.out.println("Erkannte Positionsanzeige. Sende Enter.");
            sendDataWithDelay("\r");
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

        boolean erkannt = waitUntil("Cursor = 4,11 & Text enthält 'Pos.'", () -> {
            terminalApp.checkForPause();
            String currentCursor = cursor.getCursorPosition();
            String screenText = getScreenText();
            System.out.println("[DEBUG] Aktueller Cursor bei Positions-Eingabe: " + currentCursor);
            return currentCursor.equals("4,11") && screenText.contains("Pos.");
        });

        if (erkannt) {
            System.out.println("Eingabeaufforderung für Positionsnummer erkannt.");
            return true;
        } else {
            System.out.println("Timeout beim Warten auf Positions-Eingabe.");
            return false;
        }
    }



    private void navigateToStartPage() throws IOException, InterruptedException {
        System.out.println("Navigiere zur Startseite...");
        final String STARTUP_CURSOR = "3,11";
        int noChangeCounter = 0;
        final int maxAttempts = 2;

        while (true) {
            terminalApp.checkForPause();
            String cursorBefore = cursor.getCursorPosition();
            String screenBefore = getScreenText();

            if (cursorBefore.equals(STARTUP_CURSOR) &&
                    (screenBefore.contains("Auf-Nr.:") || screenBefore.contains("LB-Nr.:"))) {
                System.out.println("Startseite erreicht.");
                break;
            }

            // Sonderfall: direkt auf "Programm - Nr." Bildschirm
            if (cursorBefore.equals("3,24") && screenBefore.contains("Programm - Nr.:")) {
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

            // Schritt: OS senden
            String snapshotBeforeOS = captureRelevantScreenPart();
            System.out.println("Sende '\u001BOQ'");
            sendDataWithDelay("\u001BOQ");

            if (hasRelevantScreenChanged(snapshotBeforeOS, cursorBefore)) {
                noChangeCounter = 0;
                continue;
            } else {
                System.out.println("[DEBUG] Keine Änderung nach OS. Versuch: " + (noChangeCounter + 1));
                noChangeCounter++;
            }

            if (noChangeCounter >= maxAttempts) {
                System.out.println("[WARNUNG] Keine Reaktion nach " + maxAttempts + " Versuchen. Warte auf manuelle Änderung...");

                boolean weiter = waitUntil("Startseite erscheint nach manuellem Eingreifen", () -> {
                    String cursorPosition = cursor.getCursorPosition();
                    String screen = getScreenText();
                    return cursorPosition.equals(STARTUP_CURSOR) && (screen.contains("Auf-Nr.:") || screen.contains("LB-Nr.:"));
                });

                if (weiter) {
                    System.out.println("Startseite manuell erreicht.");
                    break;
                }

                noChangeCounter = 0;
            }
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
        String currentCursor = cursor.getCursorPosition();
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

    private enum AusloeserStatus {
        BITTE_AUSLOESEN,
        EINGABEN_OK,
        INTERNE_EINGABE,
        NICHTS
    }

    private AusloeserStatus waitForAusloeserOderAlternative() throws InterruptedException {
        System.out.println("Warte auf 'Bitte ausloesen !' oder alternative Zustände...");

        final AtomicReference<AusloeserStatus> status = new AtomicReference<>(AusloeserStatus.NICHTS);

        boolean erkannt = waitUntil("Bitte ausloesen oder Alternativen", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            String screenText = getScreenText();

            if (cursorPosition.equals("24,80") && screenText.contains("Bitte ausloesen !")) {
                status.set(AusloeserStatus.BITTE_AUSLOESEN);
                return true;
            }

            if (screenText.contains("Eingaben OK") &&
                    (cursorPosition.equals("23,75") || cursorPosition.equals("23,76") || cursorPosition.equals("23,77") || cursorPosition.equals("23,78"))) {
                status.set(AusloeserStatus.EINGABEN_OK);
                return true;
            }

            if (cursorPosition.equals("22,2") && screenText.contains("Interner Text")) {
                status.set(AusloeserStatus.INTERNE_EINGABE);
                return true;
            }

            return false;
        });

        return erkannt ? status.get() : AusloeserStatus.NICHTS;
    }






}
