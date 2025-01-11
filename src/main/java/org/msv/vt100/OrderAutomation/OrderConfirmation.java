package org.msv.vt100.OrderAutomation;

import javafx.application.Platform;
import org.apache.poi.ss.usermodel.Row;
import org.msv.vt100.Cursor;
import org.msv.vt100.SSHConnector;
import org.msv.vt100.TerminalApp;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class OrderConfirmation {

    private final SSHConnector sshConnector;
    private final Cursor cursor;
    private final TerminalApp terminalApp;
    private final ScreenTextDetector screenTextDetector;

    public OrderConfirmation(SSHConnector sshConnector, Cursor cursor, TerminalApp terminalApp, ScreenTextDetector screenTextDetector) {
        this.sshConnector = sshConnector;
        this.cursor = cursor;
        this.terminalApp = terminalApp;
        this.screenTextDetector = screenTextDetector;
    }

    public void processOrders(Iterator<Row> rows) throws IOException, InterruptedException {
        while (rows.hasNext()) {
            if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                System.out.println("Processing stopped.");
                break;
            }

            terminalApp.checkForPause();

            Row currentRow = rows.next();
            try {
                processOrder(currentRow);
            } catch (InterruptedException e) {
                System.out.println("Processing interrupted.");
                throw e; // Пробрасываем исключение выше
            }
        }
    }

    public void processOrder(Row row) throws IOException, InterruptedException {
        String orderNumber = FileExtractor.extractCellValueAsString(row.getCell(0));
        String positionNumber = FileExtractor.extractCellValueAsString(row.getCell(1));
        String deliveryDate = FileExtractor.extractCellValueAsString(row.getCell(3));
        String confirmationNumber = FileExtractor.extractCellValueAsString(row.getCell(2));

        System.out.println("Обработка заказа: " + orderNumber);

        String cursorPosition = getCursorPosition();

        if (screenTextDetector.isAufNrDisplayed()) {
            if (orderNumber.length() == 5) {
                System.out.println("На экране 'Auf-Nr.:' и номер заказа состоит из 5 цифр. Отправляем 'L' на сервер.");
                sendDataWithDelay("L");
                sendDataWithDelay("\r");
            } else if (orderNumber.length() != 6) {
                System.out.println("Ошибка: Номер заказа должен содержать 5 или 6 символов. Остановлена обработка.");
                return;
            }
        } else if (screenTextDetector.isLbNrDisplayed()) {
            if (orderNumber.length() == 6) {
                System.out.println("На экране 'LB-Nr.:' и номер заказа состоит из 6 цифр. Отправляем 'K' на сервер.");
                sendDataWithDelay("K");
                sendDataWithDelay("\r");
            } else if (orderNumber.length() != 5) {
                System.out.println("Ошибка: Номер заказа должен содержать 5 или 6 символов. Остановлена обработка.");
                return;
            }
        } else {
            System.out.println("Не удалось определить, что отображается на экране. Обработка остановлена.");
            return;
        }

        while (!cursorPosition.equals("311")) {
            System.out.println("Значение позиции курсора не равно '311'. Перемещение курсора влево и повторная проверка.");
            sendDataWithDelay("\u001BOQ");

            System.out.println("Текущая позиция " + cursorPosition);

            if (cursorPosition.equals("2362")) {
                System.out.println("Курсор все еще в положении '2362'. Ввод буквы 'L' и значений '5.0321'.");
                sendDataWithDelay("L");
                sendDataWithDelay("\r");
                sendDataWithDelay("5.0321");
                sendDataWithDelay("\r");

                System.out.println("Повторная обработка текущего заказа: " + orderNumber);
                processOrder(row);
                return;
            }

            cursorPosition = getCursorPosition();
        }

        if (screenTextDetector.isWareneingangDisplayed()) {
            sendDataWithDelay("\u001BOQ");
            return;
        }

        System.out.println("Курсор на правильной позиции. Ввод номера заказа: " + orderNumber);
        sendDataWithDelay(orderNumber);
        sendDataWithDelay("\r");

        System.out.println("Ввод номера позиции: " + positionNumber);
        sendDataWithDelay(positionNumber);
        sendDataWithDelay("\r");

        System.out.println("Проверка, не доставлен ли заказ.");

        cursorPosition = getCursorPosition();
        if (cursorPosition.equals("1374")) {
            System.out.println("Курсор имеет значение '1374'. Нажатие стрелки назад и переход к следующему заказу.");
            sendDataWithDelay("\u001BOQ");
            return;
        }

        System.out.println("Проверка позиции курсора перед вводом даты.");

        cursorPosition = getCursorPosition();

        while (cursorPosition.equals("2480") || cursorPosition.equals("2443")) {
            System.out.println("Позиция курсора равна '" + cursorPosition + "'. Нажатие Enter и повторная проверка.");
            sendDataWithDelay("\r");
            cursorPosition = getCursorPosition();
        }

        if (cursorPosition.equals("936")) {
            System.out.println("Курсор на правильной позиции. Ввод даты поставки: " + deliveryDate);
            sendDataWithDelay(deliveryDate);
            sendDataWithDelay("\r");
            sendDataWithDelay("\r");
            Thread.sleep(1500);
        } else {
            System.out.println("Курсор не на правильной позиции для ввода даты.");
            cursorPosition = getCursorPosition();
            sendDataWithDelay(deliveryDate);
            sendDataWithDelay("\r");
            sendDataWithDelay("\r");
            Thread.sleep(1500);
        }

        System.out.println("Ввод номера подтверждения: " + confirmationNumber);
        sendDataWithDelay(confirmationNumber);


        while (!cursorPosition.equals("2375") && !cursorPosition.equals("2376") && !cursorPosition.equals("2377")) {
            sendDataWithDelay("\r");
            cursorPosition = getCursorPosition();

            switch (cursorPosition) {
                case "311", "411" -> {
                    System.out.println("Курсор имеет значение " + cursorPosition + ". Прерывание цикла и переход к следующему заказу.");
                    return;
                }
                case "221" -> {
                    System.out.println("Курсор имеет значение '221'. ");
                    sendDataWithDelay("\u001BOQ");
                }
            }
        }

        sendDataWithDelay("\r");
        cursorPosition = getCursorPosition();

        if (cursorPosition.equals("2440")) {
            System.out.println("Курсор имеет значение '2440'. Нажатие стрелки влево.");
            sendDataWithDelay("\r");
        }

        if (cursorPosition.equals("2362")) {
            System.out.println("Курсор имеет значение '2362'. Нажатие стрелки влево.");
            sendDataWithDelay("\u001BOQ");
        }

        System.out.println("Задержка перед обработкой следующего заказа.");
        if (terminalApp.isStopped()) {
            System.out.println("Processing stopped.");
            return;
        }
        terminalApp.checkForPause();
    }

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

    // Вспомогательный метод для отправки данных с задержкой
    private void sendDataWithDelay(String data) throws IOException, InterruptedException {
        sshConnector.sendData(data);
        int sleepTime = 300; // Задержка в 300 мс
        int interval = 50; // Проверяем каждые 50 мс
        int elapsed = 0;
        while (elapsed < sleepTime) {
            if (terminalApp.isStopped() || Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Processing stopped");
            }
            Thread.sleep(interval);
            elapsed += interval;
        }
    }

}
