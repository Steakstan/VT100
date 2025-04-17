package org.msv.vt100.OrderAutomation;

import javafx.application.Platform;
import org.apache.poi.ss.usermodel.Row;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.TerminalApp;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class OrderConfirmation {

    private final SSHManager sshConnector;
    private final Cursor cursor;
    private final TerminalApp terminalApp;
    private final ScreenTextDetector screenTextDetector;

    public OrderConfirmation(SSHManager sshConnector, Cursor cursor, TerminalApp terminalApp, ScreenTextDetector screenTextDetector) {
        System.out.println("Konstruktor OrderConfirmation aufgerufen: Initialisierung der benötigten Objekte.");
        this.sshConnector = sshConnector;
        this.cursor = cursor;
        this.terminalApp = terminalApp;
        this.screenTextDetector = screenTextDetector;
        System.out.println("Konstruktor abgeschlossen: Alle Objekte initialisiert.");
    }

    public void processOrders(Iterator<Row> rows) throws IOException, InterruptedException {
        System.out.println("Starte Verarbeitung aller Bestellungen aus dem Iterator.");
        while (rows.hasNext()) {
            if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                System.out.println("Verarbeitung gestoppt: Programm wurde abgebrochen oder Thread unterbrochen.");
                break;
            }
            terminalApp.checkForPause();
            Row currentRow = rows.next();
            System.out.println("Nächste Zeile im Excel-Sheet gelesen, beginne mit der Verarbeitung dieser Bestellung.");
            try {
                processOrder(currentRow);
            } catch (InterruptedException e) {
                System.out.println("Verarbeitung unterbrochen: Exception wurde ausgelöst, breche ab.");
                throw e;
            }
        }
        System.out.println("Verarbeitung aller Bestellungen abgeschlossen oder gestoppt.");
    }

    public void processOrder(Row row) throws IOException, InterruptedException {
        final String STARTUP_CURSOR = "3,11";
        String orderNumber = FileExtractor.extractCellValueAsString(row.getCell(0)).trim();
        String positionNumber = FileExtractor.extractCellValueAsString(row.getCell(1)).trim();
        String confirmationNumber = FileExtractor.extractCellValueAsString(row.getCell(2)).trim();
        String deliveryDate = FileExtractor.extractCellValueAsString(row.getCell(3)).trim();
        String screenText;
        String cursorPosition;

        System.out.println("Начало обработки заказа. Номер заказа: " + orderNumber);

        while (true) {
            if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                System.out.println("Обработка остановлена. Завершаю выполнение.");
                return;
            }
            terminalApp.checkForPause();
            cursorPosition = getCursorPosition();
            try {
                java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                screenText = (String) method.invoke(screenTextDetector);
            } catch (Exception e) {
                screenText = screenTextDetector.toString();
            }
            System.out.println("Проверка стартовой страницы: позиция курсора = " + cursorPosition);
            if (cursorPosition.equals("3,24") && screenText.contains("Programm - Nr.:")) {
                System.out.println("Найден 'Programm - Nr.:' на 3,24. Отправляю '5.0321'.");
                sendDataWithDelay("5.0321\r");
                Thread.sleep(200);
            } else if (cursorPosition.equals(STARTUP_CURSOR) && (screenText.contains("Auf-Nr.:") || screenText.contains("LB-Nr.:"))) {
                System.out.println("Стартовая страница достигнута. Позиция курсора: " + STARTUP_CURSOR);
                break;
            } else {
                System.out.println("Стартовая страница не обнаружена. Переключаю экран.");
                sendDataWithDelay("\u001BOS");
                sendDataWithDelay("\u001BOQ");
            }
        }

        try {
            java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
            screenText = (String) method.invoke(screenTextDetector);
        } catch (Exception e) {
            screenText = screenTextDetector.toString();
        }

        boolean isSpecialSix = false;
        if (orderNumber.length() == 5 && screenText.contains("Auf-Nr.:")) {
            System.out.println("Номер заказа из 5 символов. Отправляю 'L'.");
            sendDataWithDelay("L");
            sendDataWithDelay("\r");
            isSpecialSix = true;
        } else if (orderNumber.length() == 6 && screenText.contains("LB-Nr.:")) {
            System.out.println("Номер заказа из 6 символов. Отправляю 'K'.");
            sendDataWithDelay("K");
            sendDataWithDelay("\r");
            isSpecialSix = true;
        } else if (orderNumber.length() == 6 && screenText.contains("Auf-Nr.:")) {
            System.out.println("Номер заказа из 6 символов с 'Auf-Nr.:' обнаружен. Специальный режим.");
            isSpecialSix = true;
        } else {
            System.out.println("Условия для номера заказа не выполнены. Прерываю обработку.");
            return;
        }

        System.out.println("Отправляю номер заказа: " + orderNumber);
        sendDataWithDelay(orderNumber);
        sendDataWithDelay("\r");

        if (isSpecialSix) {
            System.out.println("Ожидание экрана ввода номера позиции (позиция 4,11 с 'Pos.   :').");
            while (true) {
                if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                    System.out.println("Остановка во время ожидания ввода номера позиции.");
                    return;
                }
                terminalApp.checkForPause();
                String currentCursor = getCursorPosition();
                try {
                    java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                    screenText = (String) method.invoke(screenTextDetector);
                } catch (Exception e) {
                    screenText = screenTextDetector.toString();
                }
                System.out.println("Ожидание ввода позиции: текущая позиция = " + currentCursor);
                if (currentCursor.equals("4,11") && screenText.contains("Pos.   :")) {
                    System.out.println("Экран ввода позиции обнаружен.");
                    break;
                }
                Thread.sleep(200);
            }
            System.out.println("Отправляю номер позиции: " + positionNumber);
            sendDataWithDelay(positionNumber);
            sendDataWithDelay("\r");

            while (true) {
                if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                    System.out.println("Остановка после отправки номера позиции.");
                    return;
                }
                terminalApp.checkForPause();
                String currentCursor = getCursorPosition();
                try {
                    java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                    screenText = (String) method.invoke(screenTextDetector);
                } catch (Exception e) {
                    screenText = screenTextDetector.toString();
                }
                System.out.println("Проверка после ввода позиции: текущая позиция = " + currentCursor);
                if (screenText.contains("Keine Bestellware!")) {
                    System.out.println("Обнаружена 'Keine Bestellware!'. Возвращаюсь на стартовую страницу.");
                    while (true) {
                        if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                            System.out.println("Остановка при возврате на стартовую страницу.");
                            return;
                        }
                        terminalApp.checkForPause();
                        cursorPosition = getCursorPosition();
                        try {
                            java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                            screenText = (String) method.invoke(screenTextDetector);
                        } catch (Exception e) {
                            screenText = screenTextDetector.toString();
                        }
                        System.out.println("Возврат на стартовую страницу: текущая позиция = " + cursorPosition);
                        if (cursorPosition.equals(STARTUP_CURSOR) && (screenText.contains("Auf-Nr.:") || screenText.contains("LB-Nr.:"))) {
                            System.out.println("Стартовая страница достигнута. Переход к следующему заказу.");
                            break;
                        }
                        sendDataWithDelay("\u001BOQ");
                        Thread.sleep(200);
                    }
                    return;
                } else if (currentCursor.equals("9,36") && screenText.contains("Vorgesehene WE-Filiale")) {
                    System.out.println("Достигнут экран для ввода даты поставки. Отправляю дату поставки: " + deliveryDate);
                    sendDataWithDelay(deliveryDate);
                    sendDataWithDelay("\r");
                    Thread.sleep(200);

                    /*String alertCursor;
                    String alertScreenText;
                    int counter = 0;
                    boolean alertDetected = false;
                    while (counter < 10) {  // максимум 10 итераций (~1 секунда ожидания)
                        if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                            System.out.println("Остановка во время ожидания специального сообщения.");
                            return;
                        }
                        alertCursor = getCursorPosition();
                        try {
                            java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                            alertScreenText = (String) method.invoke(screenTextDetector);
                        } catch (Exception e) {
                            alertScreenText = screenTextDetector.toString();
                        }
                        if (alertCursor.equals("23,48") && alertScreenText.contains("AB-Termin liegt vor Tagesdatum")) {
                            alertDetected = true;
                            break;
                        }
                        Thread.sleep(100);
                        counter++;
                    }
                    if (alertDetected) {
                        System.out.println("Обнаружено сообщение 'AB-Termin liegt vor Tagesdatum' на позиции 23,48. Отправляю 'J'.");
                        sendDataWithDelay("J");
                        sendDataWithDelay("\r");
                    }*/


                    sendDataWithDelay("\r");
                    System.out.println("Дата поставки отправлена. Переход к ожиданию ввода номера подтверждения.");
                    while (true) {
                        if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                            System.out.println("Остановка во время ожидания ввода номера подтверждения.");
                            return;
                        }
                        terminalApp.checkForPause();
                        String cur = getCursorPosition();
                        try {
                            java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                            screenText = (String) method.invoke(screenTextDetector);
                        } catch (Exception e) {
                            screenText = screenTextDetector.toString();
                        }
                        System.out.println("Ожидание ввода номера подтверждения: текущая позиция = " + cur);
                        if (cur.equals("14,31") && screenText.contains("Erfassen AB-Nummer")) {
                            System.out.println("Условия для ввода номера подтверждения выполнены.");
                            break;
                        }
                        Thread.sleep(200);
                    }
                    System.out.println("Отправляю номер подтверждения: " + confirmationNumber);
                    sendDataWithDelay(confirmationNumber);
                    sendDataWithDelay("\r");
                    while (true) {
                        if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                            System.out.println("Остановка во время ожидания возврата на стартовую страницу.");
                            return;
                        }
                        terminalApp.checkForPause();
                        String cur = getCursorPosition();
                        try {
                            java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                            screenText = (String) method.invoke(screenTextDetector);
                        } catch (Exception e) {
                            screenText = screenTextDetector.toString();
                        }
                        System.out.println("Ожидание возврата на стартовую страницу: текущая позиция = " + cur);

                        // Новая проверка: если курсор на 4,11 и на экране присутствует слово "Pos."
                        if (cur.equals("4,11") && screenText.contains("Pos.")) {
                            System.out.println("Курсор на 4,11 с 'Pos.' обнаружен. Пропущены условия стартовой страницы. Отправляю \"\\u001BOQ\" для возврата на шаг назад.");
                            sendDataWithDelay("\u001BOQ");
                            Thread.sleep(200);  // задержка для обновления экрана
                            continue; // переходим к следующей итерации цикла для повторной проверки
                        }

                        if (cur.equals(STARTUP_CURSOR) && (screenText.contains("Auf-Nr.:") || screenText.contains("LB-Nr.:"))) {
                            System.out.println("Стартовая страница достигнута. Переход к следующему заказу.");
                            break;
                        }
                        sendDataWithDelay("\r");
                        Thread.sleep(200);
                    }
                    return;
                } else if (screenText.contains("Bitte ausloesen !") && !(currentCursor.equals("9,36") && screenText.contains("Vorgesehene WE-Filiale"))) {
                    System.out.println("Обнаружена 'Bitte ausloesen !', но условия для ввода даты поставки не выполнены. Нажимаю Enter.");
                    sendDataWithDelay("\r");
                } else if (screenText.contains("OK (J/N/L/T/G)") && currentCursor.equals("13,74")) {
                    System.out.println("Обнаружена 'OK (J/N/L/T/G)' на позиции 13,74. Возвращаюсь на стартовую страницу.");
                    while (true) {
                        if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                            System.out.println("Остановка при возврате на стартовую страницу.");
                            return;
                        }
                        terminalApp.checkForPause();
                        cursorPosition = getCursorPosition();
                        try {
                            java.lang.reflect.Method method = screenTextDetector.getClass().getMethod("getScreenText");
                            screenText = (String) method.invoke(screenTextDetector);
                        } catch (Exception e) {
                            screenText = screenTextDetector.toString();
                        }
                        System.out.println("Возврат на стартовую страницу: текущая позиция = " + cursorPosition);
                        if (cursorPosition.equals(STARTUP_CURSOR) && (screenText.contains("Auf-Nr.:") || screenText.contains("LB-Nr.:"))) {
                            System.out.println("Стартовая страница достигнута. Переход к следующему заказу.");
                            break;
                        }
                        sendDataWithDelay("\u001BOQ");
                        Thread.sleep(200);
                    }
                    return;
                } else {
                    Thread.sleep(200);
                }
            }
        }
    }










    private String getCursorPosition() throws InterruptedException {
        System.out.println("Fordere Cursorposition vom JavaFX-Thread an.");
        final String[] cursorPosition = new String[1];
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            cursorPosition[0] = (cursor.getRow() + 1) + "," + (cursor.getColumn() + 1);
            latch.countDown();
        });
        latch.await();
        System.out.println("Empfangene Cursorposition: " + cursorPosition[0]);
        return cursorPosition[0];
    }

    private void sendDataWithDelay(String data) throws IOException, InterruptedException {
        System.out.println("Sende Daten an SSH: '" + data + "' mit Verzögerung.");
        sshConnector.send(data);
        int sleepTime = 200;
        int interval = 50;
        int elapsed = 0;
        while (elapsed < sleepTime) {
            if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                System.out.println("Unterbrechung oder Stop erkannt. Werfe InterruptedException.");
                throw new InterruptedException("Verarbeitung gestoppt");
            }
            Thread.sleep(interval);
            elapsed += interval;
        }
        System.out.println("Verzögerung abgeschlossen, kehre zurück zum Hauptablauf.");
    }
}
