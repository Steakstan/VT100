package org.msv.vt100.OrderAutomation;

import javafx.application.Platform;
import org.apache.poi.ss.usermodel.Row;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.util.CellValueExtractor;

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
        System.out.println("-----------------------------------------------------");
        System.out.println("START processDeliveryDate: Начало обработки заказа из Excel.");

        // Извлечение данных заказа из Excel
        String orderNumber = FileExtractor.extractCellValueAsString(row.getCell(0)).trim();
        String positionNumber = FileExtractor.extractCellValueAsString(row.getCell(1)).trim();
        String deliveryDate = FileExtractor.extractCellValueAsString(row.getCell(2)).trim();
        System.out.println("Aus Excel extrahiert: Bestellnummer = " + orderNumber + ", Positionsnummer = " + positionNumber + ", Lieferdatum = " + deliveryDate);

        // Получаем текущее содержимое экрана через screenTextDetector
        String screenText;
        try {
            java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
            screenText = (String) method.invoke(screenTextDetector);
            System.out.println("Initialer Bildschirmtext: " + screenText);
        } catch (Exception e) {
            screenText = screenTextDetector.toString();
            System.out.println("Fehler beim Abruf des Bildschirmtextes, benutze toString(): " + screenText);
        }

        final String STARTUP_CURSOR = "3,11";

        // Переход на стартовую страницу: убеждаемся, что терминал находится на экране для ввода номера заказа
        System.out.println("Übergang zur Startseite wird gestartet.");
        while (true) {
            String cursorPosition = getCursorPosition();
            System.out.println("[DEBUG] Aktuelle Cursorposition während Startseitenprüfung: " + cursorPosition);
            try {
                java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                screenText = (String) method.invoke(screenTextDetector);
            } catch (Exception e) {
                screenText = screenTextDetector.toString();
            }
            System.out.println("[DEBUG] Bildschirmtext während Startseitenprüfung: " + screenText);
            if (cursorPosition.equals("3,24") && screenText.contains("Programm - Nr.:")) {
                System.out.println("Startseitenprüfung: Bildschirm zeigt 'Programm - Nr.:'. Sende '5.0321'.");
                sendDataWithDelay("5.0321\r");
                Thread.sleep(100);
            } else if (cursorPosition.equals(STARTUP_CURSOR) &&
                    (screenText.contains("Auf-Nr.:") || screenText.contains("LB-Nr.:"))) {
                System.out.println("Startseite erreicht: Cursorposition " + STARTUP_CURSOR + " und relevante Texte gefunden.");
                break;
            } else {
                System.out.println("Startseite nicht erreicht, Schalte Bildschirm um (Sende ESC-Sequenz).");
                sendDataWithDelay("\u001BOS");
                sendDataWithDelay("\u001BOQ");
            }
        }

        // Определение управляющего символа по формату номера заказа
        boolean isSpecialSix = false;
        if (orderNumber.length() == 5 && screenText.contains("Auf-Nr.:")) {
            System.out.println("Bestellnummer hat 5 Zeichen und 'Auf-Nr.:' ist vorhanden. Sende 'L'.");
            sendDataWithDelay("L");
            sendDataWithDelay("\r");
        } else if (orderNumber.length() == 6 && screenText.contains("LB-Nr.:")) {
            System.out.println("Bestellnummer hat 6 Zeichen und 'LB-Nr.:' ist vorhanden. Sende 'K'.");
            sendDataWithDelay("K");
            sendDataWithDelay("\r");
        } else if (orderNumber.length() == 6 && screenText.contains("Auf-Nr.:")) {
            System.out.println("Spezialfall erkannt: 6-stellige Bestellnummer mit 'Auf-Nr.:'.");
            isSpecialSix = true;
        } else {
            System.out.println("FEHLER: Bestellnummer muss 5 oder 6 Zeichen haben. Verarbeitung wird gestoppt.");
            return;
        }

        // Ввод номера заказа
        System.out.println("Sende Bestellnummer: " + orderNumber);
        sendDataWithDelay(orderNumber);
        sendDataWithDelay("\r");

        // Если специальный случай – ожидание экрана для ввода номера позиции
        if (isSpecialSix) {
            System.out.println("Warte auf Bildschirm für Positionsnummer-Eingabe.");
            while (true) {
                String currentCursor = getCursorPosition();
                try {
                    java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                    screenText = (String) method.invoke(screenTextDetector);
                } catch (Exception e) {
                    screenText = screenTextDetector.toString();
                }
                System.out.println("[DEBUG] Aktueller Cursor bei Positions-Eingabe: " + currentCursor);
                if (currentCursor.equals("4,11") && screenText.contains("Pos.")) {
                    System.out.println("Eingabeaufforderung für Positionsnummer erkannt.");
                    break;
                }
                Thread.sleep(100);
            }
            System.out.println("Sende Positionsnummer: " + positionNumber);
            sendDataWithDelay(positionNumber);
            sendDataWithDelay("\r");

            // Если после ввода позиции появляется "Position wurde storniert!"
            try {
                java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                screenText = (String) method.invoke(screenTextDetector);
            } catch (Exception e) {
                screenText = screenTextDetector.toString();
            }
            if (screenText.contains("Position wurde storniert!")) {
                System.out.println("WARNUNG: 'Position wurde storniert!' erkannt. Zurück zur Startseite.");
                navigateToStartPage();
                return;
            }
        }

        // Если поставка уже получена, то прекращаем обработку
        if (screenTextDetector.isWareneingangDisplayed()) {
            System.out.println("INFO: Bestellung wurde bereits geliefert. Verarbeitung wird abgebrochen.");
            navigateToStartPage();
            return;
        }



        // Блок 1: Если на экране появляется "OK (J/N/L/T/G)" при Cursor "13,74", ждем и отправляем "N"
        System.out.println("Warte auf Bedingung: 'OK (J/N/L/T/G)' bei Cursor 13,74.");
        while (true) {
            terminalApp.checkForPause();
            String currCursor = getCursorPosition();
            try {
                java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                screenText = (String) method.invoke(screenTextDetector);
            } catch (Exception e) {
                screenText = screenTextDetector.toString();
            }
            System.out.println("[DEBUG] Cursor bei OK-Bedingung: " + currCursor + "; Bildschirmtext: " + screenText);
            if (currCursor.equals("13,74") && screenText.contains("OK (J/N/L/T/G)")) {
                // Проверка существующего Lieferdatum на экране (из ячеек 37-40 строки 9)
                terminalApp.checkForPause();
                String existingDeliveryDate = CellValueExtractor.extractCells(terminalApp.getScreenBuffer(), 9, 37, 38, 39, 40);
                System.out.println("Vergleiche vorhandenes Lieferdatum (" + existingDeliveryDate + ") mit dem Excel-Datum (" + deliveryDate + ").");
                if (existingDeliveryDate.equals(deliveryDate)) {
                    System.out.println("INFO: Lieferdatum ist bereits gesetzt. Verarbeitung wird übersprungen.");
                    navigateToStartPage();
                    return;
                }
                System.out.println("Bedingung bei Cursor 13,74 erfüllt. Sende 'N'.");
                sendDataWithDelay("N");
                sendDataWithDelay("\r");
                break;
            }
            Thread.sleep(100);
        }

        // Блок 2: Если на экране появляется "Bitte ausloesen !" bei Cursor "24,80", отправляем Enter до исчезновения этой фразы
        System.out.println("Überprüfe, ob 'Bitte ausloesen !' bei Cursor 24,80 angezeigt wird.");
        while (true) {
            terminalApp.checkForPause();
            String currCursor = getCursorPosition();
            try {
                java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                screenText = (String) method.invoke(screenTextDetector);
            } catch (Exception e) {
                screenText = screenTextDetector.toString();
            }
            System.out.println("[DEBUG] Cursor bei 'Bitte ausloesen !'-Prüfung: " + currCursor + "; Bildschirmtext: " + screenText);
            if (currCursor.equals("24,80") && screenText.contains("Bitte ausloesen !")) {
                System.out.println("Meldung 'Bitte ausloesen !' erkannt bei Cursor 24,80. Sende Enter.");
                sendDataWithDelay("\r");
                Thread.sleep(100);
            } else if (!screenText.contains("Bitte ausloesen !")) {
                System.out.println("'Bitte ausloesen !' ist nicht mehr vorhanden. Fahre fort.");
                break;
            } else {
                Thread.sleep(100);
            }
        }

        // Блок 3: Ожидание появления фразы "Vorgesehene WE-Filiale" bei Cursor "9,36" и отправка Lieferdatum
        System.out.println("Warte auf 'Vorgesehene WE-Filiale' bei Cursor 9,36, um das Lieferdatum zu senden.");
        while (true) {
            terminalApp.checkForPause();
            String currCursor = getCursorPosition();
            try {
                java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                screenText = (String) method.invoke(screenTextDetector);
            } catch (Exception e) {
                screenText = screenTextDetector.toString();
            }
            System.out.println("[DEBUG] Cursor bei WE-Filiale: " + currCursor + "; Bildschirmtext: " + screenText);
            if (currCursor.equals("9,36") && screenText.contains("Vorgesehene WE-Filiale")) {
                System.out.println("Bedingung 'Vorgesehene WE-Filiale' bei Cursor 9,36 erfüllt. Sende Lieferdatum: " + deliveryDate);
                sendDataWithDelay(deliveryDate);
                sendDataWithDelay("\r");
                break;
            }
            Thread.sleep(100);
        }

        // Блок 4: После отправки даты поставки – ждем, если на экране отображаются "Bestell-Termin um " и "ueberschritten!"
        System.out.println("Überprüfe, ob Meldungen 'Bestell-Termin um ' und 'ueberschritten!' angezeigt werden.");
        while (true) {
            terminalApp.checkForPause();
            try {
                java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                screenText = (String) method.invoke(screenTextDetector);
            } catch (Exception e) {
                screenText = screenTextDetector.toString();
            }
            System.out.println("[DEBUG] Während 'Bestell-Termin'-Prüfung: " + screenText);
            if (screenText.contains("Bestell-Termin um ") && screenText.contains("ueberschritten!")) {
                System.out.println("Meldungen 'Bestell-Termin um ' und 'ueberschritten!' erkannt. Warte auf ihr Verschwinden.");
                Thread.sleep(100);
            } else {
                System.out.println("Keine kritischen Meldungen mehr. Fahre fort.");
                break;
            }
        }

        // Блок 5: Отправка "T" и ожидание изменения курсора с подробной проверкой
        System.out.println("Sende 'T' und beginne mit dem Drücken von Enter, bis alle Bedingungen erfüllt sind.");
        sendDataWithDelay("T");
        sendDataWithDelay("\r");
        Thread.sleep(100);
        while (true) {
            terminalApp.checkForPause();
            // Сохраняем позицию до отправки Enter
            String beforePosition = getCursorPosition();
            sendDataWithDelay("\r"); // Отправка Enter
            // Ожидаем, пока позиция не изменится
            String afterPosition = getCursorPosition();
            while (afterPosition.equals(beforePosition)) {
                terminalApp.checkForPause();
                Thread.sleep(100);
                afterPosition = getCursorPosition();
            }
            // Новая позиция курсора
            String cursorPosition = afterPosition;
            try {
                java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                screenText = (String) method.invoke(screenTextDetector);
            } catch (Exception e) {
                screenText = screenTextDetector.toString();
            }
            System.out.println("Drücke Enter. Neue Cursorposition: " + cursorPosition + "; Bildschirmtext: " + screenText);

            // Если курсор на "24,80" и на экране "Bitte ausloesen !" – повторная отправка Enter, пока фраза не исчезнет
            if (cursorPosition.equals("24,80") && screenText.contains("Bitte ausloesen !")) {
                System.out.println("Meldung 'Bitte ausloesen !' erkannt bei Cursor 24,80. Sende Enter bis die Meldung verschwindet.");
                while (cursorPosition.equals("24,80") && screenText.contains("Bitte ausloesen !")) {
                    sendDataWithDelay("\r");
                    Thread.sleep(100);
                    cursorPosition = getCursorPosition();
                    try {
                        java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                        screenText = (String) method.invoke(screenTextDetector);
                    } catch (Exception e) {
                        screenText = screenTextDetector.toString();
                    }
                    System.out.println("Aktualisierte Cursorposition: " + cursorPosition + "; Bildschirmtext: " + screenText);
                }
            }

            // Проверка условия "Eingaben OK"
            if (screenText.contains("Eingaben OK")) {
                if (cursorPosition.equals("23,75") || cursorPosition.equals("23,76") ||
                        cursorPosition.equals("23,77") || cursorPosition.equals("23,78")) {
                    System.out.println("Bedingung 'Eingaben OK' und Cursor im erlaubten Bereich erfüllt.");
                    break;
                } else {
                    // Если "Eingaben OK" есть, но курсор не в диапазоне:
                    if (screenText.contains("Pos-Nr.:") && cursorPosition.equals("23,62")) {
                        System.out.println("Erkennung von 'Pos-Nr.:' bei Cursor 23,62. Sende Zurück-Navigation (\\u001BOQ).");
                        sendDataWithDelay("\u001BOQ");
                    } else {
                        System.out.println("Eingaben OK vorhanden, aber falsche Cursorposition und 'Pos-Nr.:' nicht gefunden. Warten...");
                    }
                }
            }

            // Дополнительные проверки курсорных позиций
            if (cursorPosition.equals("23,62")) {
                System.out.println("Cursor in Position 23,62 erkannt. Sende 'L' und '5.0321' und starte die Verarbeitung neu.");
                sendDataWithDelay("L");
                sendDataWithDelay("\r");
                sendDataWithDelay("5.0321");
                sendDataWithDelay("\r");
                processDeliveryDate(row);  // Рекурсивный вызов для повторной обработки
                return;
            } else if (cursorPosition.equals("411")) {
                System.out.println("Cursorposition 411 festgestellt. Zurück zur Startseite.");
                navigateToStartPage();
                return;
            }
            Thread.sleep(100);
        }

        // Блок 6: Отправка "Z" и ожидание, пока курсор не окажется на позиции "22,12" или "22,11"
        System.out.println("Sende 'Z' und drücke Enter, bis der Cursor auf '22,12' oder '22,11' steht.");
        sendDataWithDelay("Z");
        sendDataWithDelay("\r");
        String cursorPos = getCursorPosition();
        int samePosCount = 0; // Счётчик последовательных опросов с одинаковой позицией
        while (!cursorPos.equals("22,12") && !cursorPos.equals("22,11")) {
            terminalApp.checkForPause();
            // Сохраняем позицию до отправки Enter
            String beforePos = getCursorPosition();
            sendDataWithDelay("\r");
            // Получаем позицию после отправки
            String afterPos = getCursorPosition();
            if (afterPos.equals(beforePos)) {
                samePosCount++;
                System.out.println("Cursorposition unverändert: " + beforePos + " (Wiederholung " + samePosCount + ")");
                // Если позиция не менялась 5 раз подряд, отправляем Enter еще раз
                if (samePosCount >= 5) {
                    System.out.println("Fünfmal gleiche Position erkannt. Sende zusätzlich Enter.");
                    sendDataWithDelay("\r");
                    samePosCount = 0; // Сброс счетчика после экстренной команды
                }
            } else {
                samePosCount = 0; // Если позиция изменилась, сбрасываем счетчик
            }
            cursorPos = afterPos;
            Thread.sleep(100); // Небольшая задержка перед следующей итерацией
        }
        System.out.println("Finale Cursorposition erreicht: " + cursorPos);


        // Блок 7: Ввод комментария – извлечение календарной недели (KW) из Lieferdatum
        String kwWeek = extractWeekFromDeliveryDate(deliveryDate);
        String comment = "DEM HST NACH WIRD DIE WARE IN KW " + kwWeek + " ZUGESTELLT";
        System.out.println("Sende Kommentar: " + comment);
        sendDataWithDelay(comment);
        sendDataWithDelay("\r");
        Thread.sleep(100);

// Блок 8: Дополнительная проверка позиции курсора через F1
        System.out.println("Überprüfung der Cursorposition (F1).");
        String checkCursor = getCursorPosition();
        System.out.println("[DEBUG] Cursorposition bei F1-Prüfung: " + checkCursor);
        if (checkCursor.equals("22,74") || checkCursor.equals("22,73") || checkCursor.equals("22,78")) {
            int samePosCount1 = 0;
            String beforePos = getCursorPosition();
            sendDataWithDelay("\r");
            Thread.sleep(100);
            String afterPos = getCursorPosition();
            while (afterPos.equals(beforePos)) {
                samePosCount1++;
                System.out.println("F1-Prüfung: Cursor unverändert: " + beforePos + " (" + samePosCount1 + " Mal)");
                // Вывод содержимого буфера
                System.out.println("[DEBUG] Buffer content: " + screenText);
                if (samePosCount1 >= 5) {
                    System.out.println("F1-Prüfung: Fünfmal gleiche Position festgestellt, sende Enter erneut.");
                    sendDataWithDelay("\r");
                    samePosCount1 = 0;
                }
                Thread.sleep(100);
                beforePos = getCursorPosition();
                sendDataWithDelay("\r");
                afterPos = getCursorPosition();
            }
            System.out.println("F1-Prüfung: Cursor hat sich geändert.");
        } else {
            System.out.println("Cursor nicht im erlaubten Bereich (nicht 22,74, 22,73 oder 22,78). Warte auf Änderung...");
            String currentPos = getCursorPosition();
            while (true) {
                Thread.sleep(100);
                String newPos = getCursorPosition();
                System.out.println("[DEBUG] Aktuelle Position: " + newPos + " | Buffer: " + screenText);
                if (!newPos.equals(currentPos)) {
                    System.out.println("Cursor hat sich geändert von " + currentPos + " zu " + newPos);
                    break;
                } else {
                    System.out.println("Cursor unverändert (" + currentPos + "), warte weiter...");
                }
            }
        }

// Блок 9: Если курсор находится на позиции "22,2", то с помощью команды "\u001BOQ" переходим
// к диапазону "23,75"–"23,78"
        System.out.println("Überprüfung der Position 22,2.");
        String curPos = getCursorPosition();
        if (curPos.equals("22,2")) {
            System.out.println("Cursor bei 22,2 erkannt. Navigiere mittels '\\u001BOQ' zur Position 23,75 bis 23,78.");
            while (!curPos.equals("23,75") && !curPos.equals("23,76") &&
                    !curPos.equals("23,77") && !curPos.equals("23,78")) {
                int samePosCount2 = 0;
                String beforePos = getCursorPosition();
                sendDataWithDelay("\u001BOQ");
                Thread.sleep(100);
                String afterPos = getCursorPosition();
                while (afterPos.equals(beforePos)) {
                    samePosCount2++;
                    System.out.println("Navigation: Cursor unverändert: " + beforePos + " (" + samePosCount2 + " Mal)");
                    // Вывод содержимого буфера
                    System.out.println("[DEBUG] Buffer content: " + screenText);
                    if (samePosCount2 >= 5) {
                        System.out.println("Navigation: Fünfmal gleiche Position festgestellt, sende Enter erneut.");
                        sendDataWithDelay("\r");
                        samePosCount2 = 0;
                    }
                    Thread.sleep(100);
                    beforePos = getCursorPosition();
                    sendDataWithDelay("\u001BOQ");
                    afterPos = getCursorPosition();
                }
                curPos = afterPos;
            }
            System.out.println("Zielposition erreicht, sende Enter.");
            sendDataWithDelay("\r");
            Thread.sleep(100);
        }


        // Блок 10: Если отображается экран с позицией заказа, и курсор равен "23,62", отправить Enter
        curPos = getCursorPosition();
        if (screenTextDetector.isPosNrDisplayed() && curPos.equals("23,62")) {
            System.out.println("Erkannte Positionsanzeige und Cursor bei 23,62. Sende Enter.");
            terminalApp.checkForPause();
            sendDataWithDelay("\r");
        }

        // Блок 11: Дополнительная проверка позиции 23,62: если она сохраняется, то отправляем "\u001BOQ"
        System.out.println("Überprüfung der Position 23,62.");
        curPos = getCursorPosition();
        if (curPos.equals("23,62")) {
            System.out.println("Cursorposition 23,62 erkannt. Sende '\\u001BOQ' zur Korrektur.");
            terminalApp.checkForPause();
            sendDataWithDelay("\u001BOQ");
        }

        System.out.println("Verzögerung vor der Verarbeitung der nächsten Bestellung. Ende der Verarbeitung dieses Auftrags.");
        // Weitere Aktionen oder finales Delay können hier eingefügt werden.
        System.out.println("-----------------------------------------------------");
    }



    /**
     * Hilfsmethode zur Navigation auf die Startseite, analog zu OrderConfirmation.
     * (Implementierung kann variieren – hier ein Beispiel, das ähnlich wie in OrderConfirmation arbeitet.)
     */
    private void navigateToStartPage() throws InterruptedException, IOException {
        System.out.println("Navigiere zur Startseite...");
        final String STARTUP_CURSOR = "3,11";
        String screenText;
        while (true) {
            String cursorPosition = getCursorPosition();
            try {
                java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                screenText = (String) method.invoke(screenTextDetector);
            } catch (Exception e) {
                screenText = screenTextDetector.toString();
            }
            System.out.println("[DEBUG] Navigation - Cursor: " + cursorPosition + "; Bildschirmtext: " + screenText);
            if (cursorPosition.equals("3,24") && screenText.contains("Programm - Nr.:")) {
                System.out.println("Navigation: Bildschirm zeigt 'Programm - Nr.:'. Sende '5.0321'.");
                sendDataWithDelay("5.0321\r");
                Thread.sleep(100);
            } else if (cursorPosition.equals(STARTUP_CURSOR) &&
                    (screenText.contains("Auf-Nr.:") || screenText.contains("LB-Nr.:"))) {
                System.out.println("Startseite erreicht.");
                break;
            } else {
                System.out.println("Navigation: Bildschirm umschalten.");
                sendDataWithDelay("\u001BOS");
                sendDataWithDelay("\u001BOQ");
            }
        }
    }
    /**
     * Extracts the first two characters from the delivery date for use in a comment.
     * @param deliveryDate the delivery date string.
     * @return the extracted week value or "??" if the date is invalid.
     */
    private String extractWeekFromDeliveryDate(String deliveryDate) {
        System.out.println("Extrahiere Kalenderwoche aus dem Lieferdatum: " + deliveryDate);
        if (deliveryDate != null && deliveryDate.length() >= 2) {
            String week = deliveryDate.substring(0, 2);
            System.out.println("Extrahierte KW: " + week);
            return week;
        }
        System.out.println("Ungültiges Lieferdatum, setze KW auf '??'.");
        return "??";
    }

    /**
     * Processes delivery dates for multiple orders from the given row iterator.
     * @param rows an Iterator of Excel rows.
     * @throws IOException if an I/O error occurs.
     * @throws InterruptedException if processing is interrupted.
     */
    public void processDeliveryDates(Iterator<Row> rows) throws IOException, InterruptedException {
        System.out.println("Starte Verarbeitung mehrerer Bestellungen...");
        while (rows.hasNext()) {
            if (terminalApp.isStopped()) {
                System.out.println("Verarbeitung gestoppt.");
                break;
            }
            terminalApp.checkForPause();
            Row currentRow = rows.next();
            System.out.println("Verarbeite nächste Zeile aus Excel.");
            processDeliveryDate(currentRow);
        }
        System.out.println("Verarbeitung aller Bestellungen abgeschlossen.");
    }

    /**
     * Retrieves the current cursor position as a concatenated string of row and column.
     * @return the cursor position string.
     * @throws InterruptedException if interrupted while waiting.
     */
    private String getCursorPosition() throws InterruptedException {
        System.out.println("Fordere aktuelle Cursorposition vom JavaFX-Thread an...");
        final String[] cursorPosition = new String[1];
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            cursorPosition[0] = (cursor.getRow() + 1) + "," + (cursor.getColumn() + 1);
            System.out.println("Berechnete Cursorposition: " + cursorPosition[0]);
            latch.countDown();
        });
        latch.await();
        System.out.println("Empfangene Cursorposition: " + cursorPosition[0]);
        return cursorPosition[0];
    }

    /**
     * Helper method for sending data with a delay.
     * @param data the data string to send.
     * @throws IOException if an I/O error occurs.
     * @throws InterruptedException if interrupted during the delay.
     */
    private void sendDataWithDelay(String data) throws IOException, InterruptedException {
        System.out.println("Sende Daten: '" + data.trim() + "'");
        sshConnector.send(data);
        int sleepTime = 150; // Gesamtdauer der Verzögerung in Millisekunden
        int interval = 50;   // Интервал проверки
        int elapsed = 0;
        while (elapsed < sleepTime) {
            if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Verarbeitung gestoppt");
            }
            Thread.sleep(interval);
            elapsed += interval;
        }
        System.out.println("Verzögerung beendet nach " + elapsed + " ms.");
    }
}