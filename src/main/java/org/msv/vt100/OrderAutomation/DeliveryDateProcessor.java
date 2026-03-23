package org.msv.vt100.OrderAutomation;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.util.CellValueExtractor;
import org.msv.vt100.util.ExcelOrderData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import static org.msv.vt100.util.Waiter.waitUntil;

public class DeliveryDateProcessor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDateProcessor.class);

    private final SSHManager sshConnector;
    private final Cursor cursor;
    private final TerminalApp terminalApp;
    private final ScreenTextDetector screenTextDetector;

    private volatile long lastStartReachedAtNs = -1L;
    private volatile long lastBackToStartCmdAtNs = -1L;
    private volatile String lastBackToStartCmdLabel = "";

    public DeliveryDateProcessor(SSHManager sshConnector, Cursor cursor, TerminalApp terminalApp, ScreenTextDetector screenTextDetector) {
        this.sshConnector = sshConnector;
        this.cursor = cursor;
        this.terminalApp = terminalApp;
        this.screenTextDetector = screenTextDetector;
    }

    public void processDeliveryDate(Row row, ExcelOrderData.ColumnIndices indices) throws InterruptedException, IOException {
        log.info("-----------------------------------------------------");
        log.info("START processDeliveryDate: starting order processing from Excel.");

        ExcelOrderData orderData = ExcelOrderData.fromExcelRow(row, indices);
        String orderNumber        = orderData.orderNumber();
        String positionNumber     = orderData.positionNumber();
        String deliveryDate       = orderData.deliveryDate();
        String confirmationNumber = orderData.confirmationNumber();
        boolean hasConfirmationCol = indices.confirmationCol() >= 0;

        log.info("Aus Excel extrahiert: {}", orderData);
        log.info("AB-Nummer (Bestätigung): {}", confirmationNumber);

        long t0 = System.nanoTime();
        boolean moved = navigateToStartPage();
        if (moved) {
            waitForStartPageStable();
        }
        long t1 = System.nanoTime();
        log.debug("Timing: toStart={}ms", (t1 - t0) / 1_000_000);

        ensureOrderFieldSmart(orderNumber);

        log.info("Sende Bestellnummer: {}", orderNumber);
        if (lastStartReachedAtNs > 0) {
            long ms = (System.nanoTime() - lastStartReachedAtNs) / 1_000_000;
            log.info("Start→Auf-Nr: {} ms", ms);
        }
        sshConnector.send(orderNumber + "\r");

        if (!waitForPositionPromptFast()) {
            throw new IOException("Timeout beim Warten auf Positionsprompt");
        }

        log.info("Sende Positionsnummer: {}", positionNumber);
        sshConnector.send(positionNumber + "\r");

        boolean postPosSeen = waitUntil("Post-Position state",
                () -> {
                    terminalApp.checkForPause();
                    String c = cursor.getCursorPosition();
                    String s = getScreenText();
                    return s.contains("Keine Bestellware")
                            || isBitteAusloesen()
                            || (c.equals("9,36")  && s.contains("Vorgesehene WE-Filiale"))
                            || screenTextDetector.isWareneingangDisplayed()
                            || s.contains("Eingangsrechnung")
                            || (c.equals("13,74") && s.contains("OK (J/N/L/T/G)"));
                });
        if (!postPosSeen) {
            throw new IOException("Timeout nach Eingabe der Positionsnummer (kein sinnvolles Zustand)");
        }

        if (getScreenText().contains("Keine Bestellware")) {
            log.info("INFO: 'Keine Bestellware' erkannt – warte bis die Meldung verschwindet.");
            waitUntil("'Keine Bestellware' verschwindet",
                    () -> {
                        terminalApp.checkForPause();
                        return !getScreenText().contains("Keine Bestellware");
                    });
            log.info("INFO: 'Keine Bestellware' verschwunden – breche Verarbeitung dieses Auftrags ab.");
            navigateToStartPage();
            return;
        }

        if (resolveBitteAusloesenIfPresent()) {
            log.info("'Bitte ausloesen' wurde bereinigt (Helper).");
        }
        String afterPos = getScreenText();
        if (screenTextDetector.isWareneingangDisplayed() || afterPos.contains("Eingangsrechnung")) {
            log.info("INFO: Bestellung wurde bereits geliefert. Verarbeitung wird abgebrochen.");
            navigateToStartPage();
            return;
        }

        if (cursor.getCursorPosition().equals("13,74") && getScreenText().contains("OK (J/N/L/T/G)")) {
            if (!waitForOkPromptAndCompareDate(deliveryDate)) {
                return;
            }
        }

        waitForDeliveryDateInputPrompt(deliveryDate);

        waitForBestellTerminWarningsToDisappear();

        handleFinalInputSequence(deliveryDate, confirmationNumber, hasConfirmationCol);

        log.info("-----------------------------------------------------");
    }

    private void ensureOrderFieldSmart(String orderNumber) throws IOException, InterruptedException {
        if (orderNumber == null || orderNumber.isEmpty()) return;

        String screenText = getScreenText();
        boolean is5 = orderNumber.length() == 5;
        boolean is6 = orderNumber.length() == 6;
        boolean hasAufNr = screenText.contains("Auf-Nr");
        boolean hasLbNr  = screenText.contains("LB-Nr");

        if (is5 && hasAufNr) {
            log.info("Umschalte auf Lagerbestellung (L)...");
            sendDataWithDelay("L\r");
            waitForStartPageStable();
        } else if (is6 && hasLbNr) {
            log.info("Umschalte auf Kaufauftrag (K)...");
            sendDataWithDelay("K\r");
            waitForStartPageStable();
        } else {
            log.info("Der Modus entspricht bereits der Bestellung – kein Umschalten erforderlich.");
        }
    }

    private void waitForStartPageStable() throws InterruptedException {
        final long deadline = System.nanoTime() + 300_000_000L;
        String prev = captureStartAnchors();
        String prevCur = cursor.getCursorPosition();

        while (System.nanoTime() < deadline) {
            terminalApp.checkForPause();
            Thread.sleep(80);

            String curSnap = captureStartAnchors();
            String curCur  = cursor.getCursorPosition();
            String text    = getScreenText();

            if (isStartPage(text, curCur) && curCur.equals(prevCur) && curSnap.equals(prev)) {
                log.debug("Startseite stabil bestätigt (anchors).");
                return;
            }
            prev = curSnap;
            prevCur = curCur;
        }
        log.debug("Die Anker am Startplatz haben kein Doppelspiel ergeben - wir machen ohne zusätzliche Wartezeit weiter.");
    }

    private boolean waitForOkPromptAndCompareDate(String deliveryDate) throws IOException, InterruptedException {
        log.info("Warte auf Bedingung: 'OK (J/N/L/T/G)' bei Cursor 13,74.");

        boolean success = waitUntil("Cursor = 13,74 & Text enthält 'OK (J/N/L/T/G)'", () -> {
            terminalApp.checkForPause();
            String currCursor = cursor.getCursorPosition();
            String screenText = getScreenText();
            log.debug("[DEBUG] Cursor bei OK-Bedingung: {}, sampleTextLen={}", currCursor, screenText.length());
            return currCursor.equals("13,74") && screenText.contains("OK (J/N/L/T/G)");
        });

        if (!success) {
            log.warn("[WARNUNG] Timeout beim Warten auf OK-Prompt.");
            return false;
        }

        final boolean force = isForceExcelDeliveryDateOverride();
        if (force) {

            log.info("FORCE_KW aktiv: Excel-Lieferdatum wird IMMER gesetzt (auch wenn identisch).");
        } else {






            
            String existingRaw = CellValueExtractor.extractCells(terminalApp.getScreenBuffer(), 9, 37, 38, 39, 40).trim();
            String existingDigits = existingRaw.replaceAll("[^0-9]", "");
            boolean dateMissing = existingDigits.length() < 2;

            if (dateMissing) {
                log.info("INFO: Kein vorhandenes Datum – verarbeite Auftrag.");
            } else {
                int existingWeek = Integer.parseInt(existingDigits.substring(0, 2));
                int excelWeek = Integer.parseInt(deliveryDate.substring(0, 2));
                log.info("Vergleiche Kalenderwochen: vorhanden ({}), Excel ({}).", existingWeek, excelWeek);

                if (excelWeek <= existingWeek) {
                    log.info("INFO: Excel-KW ≤ vorhandene KW. Verarbeitung wird übersprungen (Toggle aus).");
                    navigateToStartPage();
                    return false;
                }
                log.info("Excel-KW > vorhandene KW – verarbeite Auftrag.");
            }
        }

        log.info("Bedingung erfüllt. Sende 'N'.");
        sendDataWithDelay("N\r");

        boolean updated = waitUntil("Bildschirm ändert sich nach 'N'", () -> {
            terminalApp.checkForPause();
            return !getScreenText().contains("OK (J/N/L/T/G)");
        });

        if (!updated) {
            log.warn("[WARNUNG] Bildschirm hat sich nach 'N' nicht sichtbar verändert.");
        }

        return true;
    }




    private boolean resolveBitteAusloesenIfPresent() throws InterruptedException, IOException {
        terminalApp.checkForPause();

        if (!isBitteAusloesen()) {
            log.info("INFO: 'Bitte ausloesen' nicht gefunden.");
            return false;
        }

        final long hardDeadline = System.nanoTime() + 180_000_000_000L;
        int noProgressStreak = 0;

        while (System.nanoTime() < hardDeadline) {
            String snapBefore = captureProgressSnapshot();
            String curBefore  = cursor.getCursorPosition();

            sshConnector.send("\r");

            boolean reacted = waitUntil("ENTER reaction bei 'Bitte ausloesen'", () -> {
                terminalApp.checkForPause();
                String s = getScreenText();
                String c = cursor.getCursorPosition();
                String snapAfter = captureProgressSnapshot();

                if ((c.equals("9,36")  && s.contains("Vorgesehene WE-Filiale")) ||
                        (c.equals("13,74") && s.contains("OK (J/N/L/T/G)")) ||
                        (s.contains("Eingaben OK") &&
                                (c.equals("23,75") || c.equals("23,76") || c.equals("23,77") || c.equals("23,78"))) ||
                        (c.equals("22,2")  && s.contains("Interner Text")) ||
                        screenTextDetector.isWareneingangDisplayed() ||
                        s.contains("Eingangsrechnung")) {
                    return true;
                }

                if (!isBitteAusloesen()) return true;

                return !snapAfter.equals(snapBefore) || !c.equals(curBefore);
            });
            if (!reacted) break;

            String s = getScreenText();
            String c = cursor.getCursorPosition();
            if ((c.equals("9,36")  && s.contains("Vorgesehene WE-Filiale")) ||
                    (c.equals("13,74") && s.contains("OK (J/N/L/T/G)")) ||
                    (s.contains("Eingaben OK") &&
                            (c.equals("23,75") || c.equals("23,76") || c.equals("23,77") || c.equals("23,78"))) ||
                    (c.equals("22,2")  && s.contains("Interner Text")) ||
                    screenTextDetector.isWareneingangDisplayed() ||
                    s.contains("Eingangsrechnung") ||
                    !isBitteAusloesen()) {
                return true;
            }

            String snapAfter = captureProgressSnapshot();
            String curAfter  = cursor.getCursorPosition();
            boolean progressed = !snapAfter.equals(snapBefore) || !curAfter.equals(curBefore);

            if (progressed) {
                noProgressStreak = 0;
            } else if (++noProgressStreak >= 2) {
                break;
            }
        }

        return !isBitteAusloesen();
    }

    private boolean isBitteAusloesen() {
        String c = cursor.getCursorPosition();
        String s = getScreenText();
        return c.startsWith("24,") && s.contains("Bitte ausloesen");
    }

    private String captureProgressSnapshot() {
        var buf = terminalApp.getScreenBuffer();
        int rows = 24, cols = 80;
        StringBuilder sb = new StringBuilder(rows * cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                sb.append(buf.getCell(r, c).character());
            }
        }
        return sb.toString();
    }

    private void waitForDeliveryDateInputPrompt(String deliveryDate) throws IOException, InterruptedException {
        log.info("Warte auf 'Vorgesehene WE-Filiale' oder 'Bitte ausloesen'…");

        waitUntil("Cursor=24,xx & 'Bitte ausloesen' OR Cursor=9,36 & 'Vorgesehene WE-Filiale'", () -> {
            terminalApp.checkForPause();
            String c = cursor.getCursorPosition();
            String s = getScreenText();
            return isBitteAusloesen() || (c.equals("9,36") && s.contains("Vorgesehene WE-Filiale"));
        });

        resolveBitteAusloesenIfPresent();

        boolean success = waitUntil("Cursor = 9,36 & Text enthält 'Vorgesehene WE-Filiale'", () -> {
            terminalApp.checkForPause();
            String currCursor = cursor.getCursorPosition();
            String screenText = getScreenText();
            log.debug("[DEBUG] Cursor bei WE-Filiale: {};", currCursor);
            return currCursor.equals("9,36") && screenText.contains("Vorgesehene WE-Filiale");
        });
        if (!success) {
            throw new IOException("Timeout beim Warten auf Eingabefeld für Lieferdatum");
        }

        log.info("WE-Filiale erreicht. Sende Lieferdatum: {}", deliveryDate);
        sendDataWithDelay(deliveryDate);
        sshConnector.send("\r");
    }

    private void waitForBestellTerminWarningsToDisappear() throws InterruptedException {
        log.info("Überprüfe, ob Meldungen 'Bestell-Termin um ' und 'ueberschritten!' angezeigt werden.");

        boolean success = waitUntil("Warnung 'Bestell-Termin um ... ueberschritten!'", () -> {
            terminalApp.checkForPause();
            String screenText = getScreenText();
            log.debug("[DEBUG] Während 'Bestell-Termin'-Prüfung: {}", screenText);
            return !(screenText.contains("Bestell-Termin um ") && screenText.contains("ueberschritten!"));
        });

        if (success) {
            log.info("Keine kritischen Meldungen mehr.");
        } else {
            log.warn("[WARNUNG] Timeout beim Warten auf das Verschwinden der Bestell-Termin-Warnung.");
        }
    }

    private void handleFinalInputSequence(String deliveryDate, String confirmationNumber, boolean hasConfirmationCol) throws IOException, InterruptedException {
        sendDataWithDelay("T");
        sshConnector.send("\r");

        waitForErfassenAbNummer(confirmationNumber, hasConfirmationCol);
        waitForCursorAt960();
        resolveBitteAusloesenIfPresent();

        if (shouldWriteComment()) {
            waitForEingabenOkPromptAndSendZ();
            waitForInternerTextAndComment(deliveryDate);
            waitForTextKZandOQSequence();
        } else {
            log.info("Kommentar deaktiviert – überspringe Eingabe und sende nur Enter.");
            finalEingabenOkEnter();
            return;
        }

        finalEingabenOkEnter();
    }

    private void waitForErfassenAbNummer(String confirmationNumber, boolean hasConfirmationCol) throws IOException, InterruptedException {
        log.info("Warte auf 'Erfassen AB-Nummer' bei Cursor 14,31.");

        boolean success = waitUntil("Cursor = 14,31 & Text enthält 'Erfassen AB-Nummer'", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            String screenText = getScreenText();
            log.debug("[DEBUG] Cursor = {}, Text = {}", cursorPosition, screenText);
            return cursorPosition.equals("14,31") && screenText.contains("Erfassen AB-Nummer");
        });

        if (!success) throw new IOException("Timeout beim Warten auf Eingabe 'Erfassen AB-Nummer'");

        log.info("'Erfassen AB-Nummer' erkannt.");
        if (hasConfirmationCol && confirmationNumber != null && !confirmationNumber.isEmpty()) {
            log.info("Sende AB-Nummer: {}", confirmationNumber);
            sendDataWithDelay(confirmationNumber);
            sshConnector.send("\r");
        } else {
            log.info("Keine AB-Nummer vorhanden oder nicht erforderlich. Sende Enter.");
            sshConnector.send("\r");
        }
    }

    private void waitForCursorAt960() throws IOException, InterruptedException {
        log.info("Warte auf Cursorposition 9,60.");

        boolean success = waitUntil("Cursor = 9,60", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            return cursorPosition.equals("9,60");
        });

        if (!success) throw new IOException("Timeout beim Warten auf Cursor 9,60");

        log.info("Cursor erkannt. Sende Enter.");
        sshConnector.send("\r");
    }

    private void waitForEingabenOkPromptAndSendZ() throws IOException, InterruptedException {
        log.info("Warte auf 'Eingaben OK' (mit Abfang 'Bitte ausloesen').");

        while (true) {
            boolean seen = waitUntil(
                    "Eingaben OK ODER Bitte ausloesen ODER Interner Text",
                    () -> {
                        terminalApp.checkForPause();
                        String c = cursor.getCursorPosition();
                        String s = getScreenText();
                        boolean eingabenOk = s.contains("Eingaben OK") &&
                                (c.equals("23,75") || c.equals("23,76") || c.equals("23,77") || c.equals("23,78"));
                        boolean ausloesen  = isBitteAusloesen();
                        boolean interner   = isCursorAt22x(c) && norm(s).contains("Interner Text");
                        return eingabenOk || ausloesen || interner;
                    }
            );

            if (!seen) {
                throw new IOException("Timeout beim Warten auf 'Eingaben OK' / 'Bitte ausloesen' / 'Interner Text'");
            }

            String c = cursor.getCursorPosition();
            String s = getScreenText();

            if (isBitteAusloesen()) {
                log.info("'Bitte ausloesen' vor 'Z' erkannt — bereinige...");
                resolveBitteAusloesenIfPresent();
                continue;
            }

            if (isCursorAt22x(c) && norm(s).contains("Interner Text")) {
                log.info("„Interner Text“ kam vor „Z“ – das Senden von „Z“ wird übersprungen.");
                return;
            }

            if (s.contains("Eingaben OK") &&
                    (c.equals("23,75") || c.equals("23,76") || c.equals("23,77") || c.equals("23,78"))) {
                log.info("Bedingung erfüllt. Sende 'Z'.");
                sendDataWithDelay("Z");
                sshConnector.send("\r");

                barrierAfterZ();
                return;
            }

            log.debug("Unerwarteter Zustand vor 'Z': c={}, text sample len={}", c, s.length());
        }
    }


    private void waitForInternerTextAndComment(String deliveryDate) throws IOException, InterruptedException {
        log.info("Wir warten auf „Internet Text“ mit der Abfangung von „Bitte einlösen“ und ohne automatisches Drücken der Eingabetaste auf „EINGABEN_OK“.");

        while (true) {
            AusloeserStatus st = waitForAusloeserOderAlternative();

            if (st == AusloeserStatus.INTERNE_EINGABE) {
                log.info("'Interner Text' wurde erkannt – warten Sie auf einen stabilen Frame und drücken Sie die Eingabetaste.");
                final String[] prev = {captureProgressSnapshot()};
                final int[] stable = {0};
                waitUntil("Stabiler Rahmen 'Interner Text' (2 in einer Reihe)", () -> {
                    terminalApp.checkForPause();
                    String snap = captureProgressSnapshot();
                    if (snap.equals(prev[0])) stable[0]++; else stable[0] = 0;
                    prev[0] = snap;
                    return stable[0] >= 1;
                });
                sendEnterAndWaitForChange();
                break;
            }

            if (st == AusloeserStatus.BITTE_AUSLOESEN) {
                log.info("'Bitte auslösen' vor 'Interner Text' - Eingabe mit Reaktionssperre.");
                sendEnterAndWaitForChange();
                continue;
            }

            if (st == AusloeserStatus.EINGABEN_OK) {
                log.debug("EINGABEN_OK nach Z – NICHT die Eingabetaste drücken, sondern auf Interner Text warten.");
                continue;
            }

            throw new IOException("Weder 'Bitte ausloesen' noch 'Interner Text' erschienen.");
        }

        boolean nummerErkannt = waitUntil("Dreistellige Zahl in Zeile 22 erkannt", () -> {
            terminalApp.checkForPause();
            String numberText = CellValueExtractor.extractCells(terminalApp.getScreenBuffer(), 22,  2, 3, 4);
            log.info("Extrahierte Zahl: {}", numberText);
            return numberText.matches("\\d{3}");
        });
        if (!nummerErkannt) throw new IOException("Timeout beim Warten auf dreistellige Nummer für Kommentar");

        String kw = extractWeekFromDeliveryDate(deliveryDate);
        String template = getUserCommentTemplate();
        String comment = template.replace("**", kw);
        log.info("Sende Kommentar: {}", comment);

        sendDataWithDelay(comment);
        sshConnector.send("\r");

        boolean echoed = waitUntil("Kommentar erschien", () -> {
            terminalApp.checkForPause();
            String line = CellValueExtractor.extractCells(terminalApp.getScreenBuffer(), 22, 12, 22, 70);
            String cmp = comment.replaceAll("\\s+","");
            String sample = line.replaceAll("\\s+","");
            return sample.length() >= Math.min(2, cmp.length());
        });

        if (!echoed) {
            log.warn("Der Kommentar wurde nicht angezeigt - ich wiederhole die Eingabe einmal.");
            sendDataWithDelay(comment);
            sshConnector.send("\r");
            boolean echoed2 = waitUntil("Kommentar angezeigt (Wiederholung)", () -> {
                terminalApp.checkForPause();
                String line = CellValueExtractor.extractCells(terminalApp.getScreenBuffer(), 22, 12, 22, 70);
                String cmp = comment.replaceAll("\\s+","");
                String sample = line.replaceAll("\\s+","");
                return sample.length() >= Math.min(2, cmp.length());
            });
            if (!echoed2) {
                throw new IOException("Kommentar wurde nach zwei Versuchen nicht übernommen.");
            }
        }
    }



    private void waitForTextKZandOQSequence() throws IOException, InterruptedException {
        log.info("Warten Sie auf „Text-KZ“ – zuerst irgendwo auf dem Bildschirm, dann richten Sie den Cursor aus und führen Sie OQ aus.");

        boolean textKZAppeared = waitUntil("„Text-KZ“ erschien auf dem Bildschirm", () -> {
            terminalApp.checkForPause();
            return norm(getScreenText()).contains("Text-KZ");
        });
        if (!textKZAppeared) throw new IOException("Timeout: „Text-KZ“ wurde nicht angezeigt.");

        boolean cursorRightSide = waitUntil("Cursor 22.73–78 für „Text-KZ“", () -> {
            terminalApp.checkForPause();
            String c = cursor.getCursorPosition();
            return c.equals("22,73") || c.equals("22,74") || c.equals("22,75") ||
                    c.equals("22,76") || c.equals("22,77") || c.equals("22,78");
        });
        if (!cursorRightSide) throw new IOException("Timeout bei der ersten 'Text-KZ'-Eingabe");

        log.info("Rechts 'Text-KZ' erreicht - mit Enter (smart) bestätigen.");
        sendEnterAndWaitForChange();

        boolean textKZBei22_2 = waitUntil("Cursor = 22,2 & Text enthält 'Text-KZ'", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            String screenText = getScreenText();
            return cursorPosition.equals("22,2") && norm(screenText).contains("Text-KZ");
        });
        if (!textKZBei22_2) throw new IOException("Timeout bei der zweiten 'Text-KZ'-Eingabe");

        log.info("Sende ‚\\u001BOQ‘ (ESC OQ) und warte auf Cursor 23,75–23,78.");
        sshConnector.send("\u001BOQ");

        boolean zielCursorErreicht = waitUntil("Endcursor 23.75-23.78 nach OQ", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            return cursorPosition.equals("23,75") || cursorPosition.equals("23,76") ||
                    cursorPosition.equals("23,77") || cursorPosition.equals("23,78");
        });
        if (!zielCursorErreicht) throw new IOException("Timeout beim Warten auf Zielcursor nach OQ");

        log.info("Die Endposition ist erreicht.");
    }


    private void finalEingabenOkEnter() throws IOException, InterruptedException {
        log.info("Warte auf 'Eingaben OK' bei Cursorposition 23,75–23,78 ODER 'Bitte ausloesen'.");

        while (true) {
            boolean gesehen = waitUntil("'Eingaben OK' oder 'Bitte ausloesen'", () -> {
                terminalApp.checkForPause();
                String s = getScreenText();
                String c = cursor.getCursorPosition();
                boolean ok = s.contains("Eingaben OK") &&
                        (c.equals("23,75") || c.equals("23,76") || c.equals("23,77") || c.equals("23,78"));
                boolean ausloesen = isBitteAusloesen();
                return ok || ausloesen;
            });

            if (!gesehen) throw new IOException("Timeout beim Warten auf finalen Eingaben-OK-Prompt oder 'Bitte ausloesen'");

            String s = getScreenText();
            String c = cursor.getCursorPosition();

            if (isBitteAusloesen()) {
                log.info("'Bitte ausloesen' vor finalem OK erkannt — bereinige...");
                resolveBitteAusloesenIfPresent();
                continue;
            }

            log.info("Erkannt. Sende Enter.");
            sshConnector.send("\r");
            handlePossiblePostOkPositionPrompt();
            return;
        }
    }

    private void handlePossiblePostOkPositionPrompt() throws IOException, InterruptedException {
        log.info("Überprüfung auf nachträgliche 'Pos-Nr.:' bei Cursor 23,62 oder Rückkehr zur Startseite.");

        boolean erkannt = waitUntil("Cursor bei 23,62 und 'Pos-Nr.:' sichtbar ODER Startseite", () -> {
            terminalApp.checkForPause();
            String cursorPosition = cursor.getCursorPosition();
            String screenText = getScreenText();

            boolean posNrPrompt = cursorPosition.equals("23,62") && screenText.contains("Pos-Nr.:");
            boolean backToStart = cursorPosition.equals("3,11") || cursorPosition.equals("3,24");

            return posNrPrompt || backToStart;
        });

        if (!erkannt) {
            log.info("Kein weiteres Verhalten erkannt (kein 'Pos-Nr.:' oder Startseite).");
            return;
        }

        String cursorPosition = cursor.getCursorPosition();
        String screenText = getScreenText();

        if (cursorPosition.equals("23,62") && screenText.contains("Pos-Nr.:")) {
            log.info("Zusätzliche 'Pos-Nr.:' erkannt bei 23,62 – sende einmal Enter.");
            sshConnector.send("\r");
        } else {
            log.info("Startseite erkannt – keine weitere Eingabe notwendig.");
        }
    }

    private String getScreenText() {
        try {
            java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
            return (String) method.invoke(screenTextDetector);
        } catch (Exception e) {
            log.warn("Fehler beim Aufruf von getScreenText(), fallback auf toString(). Grund: {}", e.getMessage());
            return screenTextDetector.toString();
        }
    }

    private boolean waitForPositionPromptFast() throws InterruptedException, IOException {
        log.info("Warte auf Bildschirm für Positionsnummer-Eingabe (fast).");

        final long tStart      = System.nanoTime();
        final long earlyNudge  = tStart + 120_000_000L;
        final long deadline    = tStart + 320_000_000L;
        boolean nudged = false;

        while (System.nanoTime() < deadline) {
            terminalApp.checkForPause();

            String cur = cursor.getCursorPosition();
            String txt = getScreenText();

            if (isPosPrompt(txt, cur)) {
                long ms = (System.nanoTime() - tStart) / 1_000_000;
                log.info("Eingabeaufforderung für Positionsnummer erkannt ({}ms).", ms);
                return true;
            }

            if (!nudged && System.nanoTime() >= earlyNudge && isStartPage(txt, cur)) {
                log.debug("Still Startseite after ~120ms → Early Nudge Enter.");
                sshConnector.send("\r");
                nudged = true;
            }

            if (!nudged && ("3,11".equals(cur) || "3,16".equals(cur) || "3,24".equals(cur))) {
                log.debug("Zwischenzustand ({}). Instant Nudge Enter.", cur);
                sshConnector.send("\r");
                nudged = true;
            }
        }

        log.warn("Fast-Path miss. Mini-Fallback Enter...");
        sshConnector.send("\r");
        return waitUntil("Pos-Prompt (Mini-Fallback)", () -> {
            terminalApp.checkForPause();
            return isPosPrompt(getScreenText(), cursor.getCursorPosition());
        });
    }

    private boolean isPosPrompt(String text, String cursorPos) {
        if (!"4,11".equals(cursorPos)) return false;
        String norm = text.replace('\u00A0',' ').replaceAll("\\s+", " ");

        return norm.matches("(?s).*\\bPos\\.?(-?Nr\\.)?:?.*")
                || norm.contains("Pos-Nr")
                || norm.contains("Pos");
    }

    private boolean isStartPage(String text, String cursorPos) {
        if (!(cursorPos.equals("3,11") || cursorPos.equals("3,24"))) return false;
        String norm = text.replace('\u00A0',' ').replaceAll("\\s+", " ");
        return norm.matches("(?s).*\\bAuf-Nr\\.?\\s*:.*")
                || norm.matches("(?s).*\\bLB-Nr\\.?\\s*:.*");
    }

    private boolean navigateToStartPage() throws IOException, InterruptedException {
        log.info("Navigiere zur Startseite...");
        boolean movedAtLeastOnce = false;

        while (true) {
            terminalApp.checkForPause();
            String cursorBefore = cursor.getCursorPosition();
            String screenBefore = getScreenText();

            if (isStartPage(screenBefore, cursorBefore)) {
                lastStartReachedAtNs = System.nanoTime();
                if (lastBackToStartCmdAtNs > 0) {
                    long ms = (lastStartReachedAtNs - lastBackToStartCmdAtNs) / 1_000_000;
                    log.info("Startseite erreicht. BackCmd→Start: {} ms (cmd={})", ms, lastBackToStartCmdLabel);
                    lastBackToStartCmdAtNs = -1L; lastBackToStartCmdLabel = "";
                } else {
                    log.info("Startseite erreicht.");
                }
                break;
            }

            if (cursorBefore.equals("3,24") && screenBefore.contains("Programm - Nr.:")) {
                log.info("Navigation: Bildschirm zeigt 'Programm - Nr.:'. Sende '5.0321'.");
                String snapProg = captureRelevantScreenPart();
                lastBackToStartCmdAtNs = System.nanoTime();
                lastBackToStartCmdLabel = "5.0321";
                sendDataWithDelay("5.0321\r");
                movedAtLeastOnce = true;

                boolean moved = waitUntil("Bildschirm/Cursor ändern sich nach '5.0321'", () -> {
                    terminalApp.checkForPause();
                    String afterText = getScreenText();
                    String afterCur  = cursor.getCursorPosition();
                    String afterSnap = captureRelevantScreenPart();
                    return !afterSnap.equals(snapProg)
                            || !afterCur.equals(cursorBefore)
                            || isStartPage(afterText, afterCur);
                });
                if (moved) continue;
                log.debug("Keine Änderung nach '5.0321'. Warte auf manuelle Änderung...");
            }

            String snapshotBefore = captureRelevantScreenPart();
            log.info("BACK_NAV_BEFORE — Cursor={}", cursorBefore);
            lastBackToStartCmdAtNs = System.nanoTime();
            lastBackToStartCmdLabel = "ESC O Q";
            sshConnector.send("\u001BOQ");
            log.info("BACK_NAV_SENT — Rücksprungbefehl (ESC O Q) gesendet.");
            movedAtLeastOnce = true;

            boolean changed = waitUntil("Bildschirm/Cursor ändern sich nach OQ", () -> {
                terminalApp.checkForPause();
                String afterText = getScreenText();
                String afterCur  = cursor.getCursorPosition();
                String afterSnap = captureRelevantScreenPart();
                return !afterSnap.equals(snapshotBefore)
                        || !afterCur.equals(cursorBefore)
                        || isStartPage(afterText, afterCur);
            });

            if (!changed) {
                if (isStartPage(getScreenText(), cursor.getCursorPosition())) {
                    log.info("BACK_NAV_AFTER — bereits Startseite. Kein weiterer Rücksprung nötig.");
                    lastStartReachedAtNs = System.nanoTime();
                    if (lastBackToStartCmdAtNs > 0) {
                        long ms = (lastStartReachedAtNs - lastBackToStartCmdAtNs) / 1_000_000;
                        log.info("Startseite erreicht. BackCmd→Start: {} ms (cmd={})", ms, lastBackToStartCmdLabel);
                        lastBackToStartCmdAtNs = -1L; lastBackToStartCmdLabel = "";
                    } else {
                        log.info("Startseite erreicht.");
                    }
                    break;
                }
                log.warn("Keine Änderung nach OQ. Warte auf manuelles Eingreifen...");
                boolean weiter = waitUntil("Startseite erscheint nach manuellem Eingreifen",
                        () -> isStartPage(getScreenText(), cursor.getCursorPosition()));
                if (weiter) {
                    log.info("Startseite manuell erreicht.");
                    lastStartReachedAtNs = System.nanoTime();
                    if (lastBackToStartCmdAtNs > 0) {
                        long ms = (lastStartReachedAtNs - lastBackToStartCmdAtNs) / 1_000_000;
                        log.info("Startseite erreicht. BackCmd→Start: {} ms (cmd={})", ms, lastBackToStartCmdLabel);
                        lastBackToStartCmdAtNs = -1L; lastBackToStartCmdLabel = "";
                    } else {
                        log.info("Startseite erreicht.");
                    }
                    break;
                }
            } else {
                log.info("BACK_NAV_AFTER — Cursor={}", cursor.getCursorPosition());
            }
        }
        return movedAtLeastOnce;
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

    private String extractWeekFromDeliveryDate(String deliveryDate) {
        log.info("Extrahiere Kalenderwoche aus Lieferdatum: {}", deliveryDate);
        if (deliveryDate != null && deliveryDate.length() >= 2) {
            String week = deliveryDate.substring(0, 2);
            log.info("KW: {}", week);
            return week;
        }
        return "??";
    }

    private void sendDataWithDelay(String data) throws IOException, InterruptedException {
        log.info("Sende Daten: '{}'", data.trim());
        sshConnector.send(data);
        int sleepTime = 10;
        int interval  = 10;
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
        log.info("Starte Verarbeitung mehrerer Bestellungen...");

        ExcelOrderData.ColumnIndices indices = ExcelOrderData.detectAllColumns(sheet, terminalApp);
        if (indices == null) {
            log.warn("Spaltenerkennung fehlgeschlagen – Verarbeitung wird abgebrochen.");
            return;
        }

        Iterator<Row> rows = sheet.iterator();
        if (rows.hasNext()) rows.next();

        while (rows.hasNext()) {
            if (terminalApp.isStopped()) {
                log.info("Verarbeitung gestoppt.");
                break;
            }
            terminalApp.checkForPause();
            Row row = rows.next();
            log.info("Verarbeite nächste Zeile.");
            processDeliveryDate(row, indices);
        }

        log.info("Verarbeitung aller Bestellungen abgeschlossen.");
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
        log.info("Warte auf „Internettext“ / „Bereit zum Lesen“ / „Lesen OK“ (stabil, mit Internettext-Priorität).");

        final AtomicReference<AusloeserStatus> status = new AtomicReference<>(AusloeserStatus.NICHTS);
        final int[] stableInterner = {0};

        boolean erkannt = waitUntil("Interner Text|Bitte ausloesen|Eingaben OK", () -> {
            terminalApp.checkForPause();

            String c = cursor.getCursorPosition();
            String s = norm(getScreenText());

            if (isCursorAt22x(c) && s.contains("Interner Text")) {
                if (++stableInterner[0] >= 2) {
                    status.set(AusloeserStatus.INTERNE_EINGABE);
                    return true;
                }
                return false;
            } else {
                stableInterner[0] = 0;
            }

            if (isBitteAusloesen()) {
                status.set(AusloeserStatus.BITTE_AUSLOESEN);
                return true;
            }

            if (s.contains("Eingaben OK") &&
                    (c.equals("23,75") || c.equals("23,76") || c.equals("23,77") || c.equals("23,78"))) {
                status.set(AusloeserStatus.EINGABEN_OK);
                return true;
            }

            return false;
        });

        return erkannt ? status.get() : AusloeserStatus.NICHTS;
    }


    private String captureStartAnchors() {
        StringBuilder sb = new StringBuilder(256);
        for (int col = 8; col <= 30; col++) {
            sb.append(terminalApp.getScreenBuffer().getCell(3, col).character());
        }
        for (int col = 5; col <= 40; col++) {
            sb.append(terminalApp.getScreenBuffer().getCell(1, col).character());
        }
        for (int col = 31; col <= 40; col++) {
            sb.append(terminalApp.getScreenBuffer().getCell(3, col).character());
        }
        return sb.toString();
    }

    private String norm(String s) {
        if (s == null) return "";
        return s.replace('\u00A0',' ').replaceAll("\\s+"," ");
    }

    private boolean isCursorAt22x(String c) {
        return "22,1".equals(c) || "22,2".equals(c) || "22,3".equals(c) || "22,4".equals(c);
    }

    private void sendEnterAndWaitForChange() throws InterruptedException, IOException {
        String beforeSnap = captureProgressSnapshot();
        String beforeCur  = cursor.getCursorPosition();
        sshConnector.send("\r");
        boolean changed = waitUntil("Bildschirm/Cursor geändert nach Enter", () -> {
            terminalApp.checkForPause();
            String afterSnap = captureProgressSnapshot();
            String afterCur  = cursor.getCursorPosition();
            return !afterSnap.equals(beforeSnap) || !afterCur.equals(beforeCur);
        });
        if (!changed) {
            log.warn("Auf die Eingabetaste gibt es keine offensichtliche Reaktion (weder auf dem Bildschirm noch auf dem Cursor). Gehen Sie vorsichtig vor.");
        }
    }

    private void barrierAfterZ() throws InterruptedException {
        String before = captureProgressSnapshot();
        waitUntil("Der Bildschirm änderte sich nach „Z“", () -> {
            terminalApp.checkForPause();
            return !captureProgressSnapshot().equals(before);
        });
    }

    private boolean isForceExcelDeliveryDateOverride() {
        // 1) weiche Kopplung an TerminalApp (kein Compile-Zwang):
        try {
            var m = terminalApp.getClass().getMethod("isForceDeliveryDateOverride");
            Object v = m.invoke(terminalApp);
            if (v instanceof Boolean && (Boolean) v) return true;
        } catch (Exception ignored) { /* optional */ }

        // 2) System-Property / Env
        String sys = System.getProperty("msv.forceKw", "");
        if ("true".equalsIgnoreCase(sys)) return true;
        String env = System.getenv("MSV_FORCE_KW");
        if ("true".equalsIgnoreCase(env)) return true;

        // 3) Tag im User-Template (leicht "taggen"/"enttaggen"):
        String tpl = getUserCommentTemplate();
        if (tpl != null && tpl.toUpperCase().contains("[FORCE_KW]")) return true;

        return false;
    }

}
