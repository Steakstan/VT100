package org.msv.vt100.OrderAutomation;

import javafx.application.Platform;
import org.apache.poi.ss.usermodel.Row;
import org.msv.vt100.Cursor;
import org.msv.vt100.SSHConnector;
import org.msv.vt100.TerminalApp;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

public class DeliveryDateProcessor {
    private final SSHConnector sshConnector;
    private final Cursor cursor;
    private final TerminalApp terminalApp;
    private final ScreenTextDetector screenTextDetector;


    public DeliveryDateProcessor(SSHConnector sshConnector, Cursor cursor, TerminalApp terminalApp, ScreenTextDetector screenTextDetector) {
        this.sshConnector = sshConnector;
        this.cursor = cursor;
        this.terminalApp = terminalApp;
        this.screenTextDetector = screenTextDetector;
    }

    public void processDeliveryDate(Row row) throws InterruptedException, IOException {
        String orderNumber = FileExtractor.extractCellValueAsString(row.getCell(0));
        String positionNumber = FileExtractor.extractCellValueAsString(row.getCell(1));
        String deliveryDate = FileExtractor.extractCellValueAsString(row.getCell(2));
        if (screenTextDetector.isAufNrDisplayed()) {
            if (orderNumber.length() == 5) {
                System.out.println("На экране 'Auf-Nr.:' и номер заказа состоит из 5 цифр. Отправляем 'L' на сервер.");
                terminalApp.checkForStop();
                sendDataWithDelay("L");
                sendDataWithDelay("\r");
            } else if (orderNumber.length() != 6) {
                System.out.println("Ошибка: Номер заказа должен содержать 5 или 6 символов. Остановлена обработка.");
                return;
            }
        } else if (screenTextDetector.isLbNrDisplayed()) {
            if (orderNumber.length() == 6) {
                System.out.println("На экране 'LB-Nr.:' и номер заказа состоит из 6 цифр. Отправляем 'K' на сервер.");
                terminalApp.checkForStop();
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

        // Метка для возврата к началу обработки заказа
        startProcessing:

        while (true) {
            System.out.println("Обработка даты поставки для заказа: " + orderNumber);

            String cursorPosition = getCursorPosition();

            while (!cursorPosition.equals("311")) {
                System.out.println("Значение позиции курсора не равно '311'. Перемещение курсора влево и повторная проверка.");
                terminalApp.checkForStop();
                sendDataWithDelay("\u001BOQ");

                // Повторная проверка позиции курсора
                cursorPosition = getCursorPosition();

                if (cursorPosition.equals("2362")) {
                    System.out.println("Курсор в положении '2362'. Ввод буквы 'L' и значений '5.0321'.");
                    terminalApp.checkForStop();
                    sendDataWithDelay("L");
                    sendDataWithDelay("\r");
                    terminalApp.checkForStop();
                    sendDataWithDelay("5.0321");
                    sendDataWithDelay("\r");

                    System.out.println("Повторная обработка текущего заказа: " + orderNumber);
                    processDeliveryDate(row); // Повторный вызов метода для обработки текущего заказа
                    return; // Завершение текущей обработки
                }

                // Перемещаем курсор влево до позиции 311
                while (!cursorPosition.equals("311")) {
                    terminalApp.checkForStop();
                    sendDataWithDelay("\u001BOQ");
                    cursorPosition = getCursorPosition();
                }

                System.out.println("Курсор установлен на позицию 311. Повторная попытка обработки заказа.");

                // Повторяем ввод заказа
                processDeliveryDate(row);
                return;
            }

            System.out.println("Курсор установлен на позицию 311.");

            // Ввод номера заказа и позиции
            System.out.println("Ввод номера заказа: " + orderNumber);
            terminalApp.checkForStop();
            sendDataWithDelay(orderNumber);
            sendDataWithDelay("\r");

            System.out.println("Ввод номера позиции: " + positionNumber);
            terminalApp.checkForStop();
            sendDataWithDelay(positionNumber);
            sendDataWithDelay("\r");

            System.out.println("Проверка, не доставлен ли заказ.");
            if (screenTextDetector.isWareneingangDisplayed()) {
                return;
            }

            cursorPosition = getCursorPosition();
            if (!cursorPosition.equals("1374")) {
                System.out.println("Курсор не на позиции 1374. Переход к следующему заказу. Текущая позиция " + cursorPosition);
                return; // Прерываем обработку текущего заказа
            }

            // Если курсор на позиции 1374
            System.out.println("Курсор установлен на позицию 1374. Ввод буквы 'N' и нажатие Enter.");
            terminalApp.checkForStop();
            sendDataWithDelay("N");
            sendDataWithDelay("\r");
            cursorPosition = getCursorPosition();

            // Продолжаем нажатие Enter до позиции 936
            System.out.println("Продолжаем нажатие Enter до достижения позиции 936.");
            // Для хранения предыдущей позиции курсора
            int consecutive411Count = 0; // Счетчик для отслеживания повторных появлений позиции 411

            while (!cursorPosition.equals("936")) {
                terminalApp.checkForStop();
                sendDataWithDelay("\r");
                cursorPosition = getCursorPosition();
                System.out.println("Нажимаем энтер. Номер позиции " + cursorPosition);

                // Проверка на повторную позицию 411
                if (cursorPosition.equals("411")) {
                    terminalApp.checkForStop();
                    consecutive411Count++;
                    System.out.println("Курсор на позиции 411. Счетчик повторных появлений: " + consecutive411Count);
                    if (consecutive411Count == 2) {
                        System.out.println("Курсор дважды подряд на позиции 411. Сброс обработки заказа.");
                        return; // Возвращаемся к началу обработки того же заказа
                    }
                } else {
                    consecutive411Count = 0; // Сбрасываем счетчик если позиция отличается от 411
                }
            }
            System.out.println("Курсор установлен на позицию 936.");

            // Ввод даты поставки
            System.out.println("Ввод даты поставки: " + deliveryDate);
            terminalApp.checkForStop();
            sendDataWithDelay(deliveryDate);
            sendDataWithDelay("\r");

            // Нажимаем "T" и Enter до позиции 2375
            System.out.println("Ввод буквы 'T' и нажатие Enter до достижения позиции 2375.");
            terminalApp.checkForStop();
            sendDataWithDelay("T");
            sendDataWithDelay("\r");
                Thread.sleep(2500);
            label:
            while (true) {
                terminalApp.checkForStop();
                sendDataWithDelay("\r");
                cursorPosition = getCursorPosition();
                System.out.println("нажатие Enter " + cursorPosition);

                switch (cursorPosition) {
                    case "2375":
                        terminalApp.checkForStop();
                        System.out.println("Курсор установлен на позицию 2375.");
                        break label;
                    case "2376":
                        terminalApp.checkForStop();
                        System.out.println("Курсор установлен на позицию 2376.");
                        break label;
                    case "2377":
                        terminalApp.checkForStop();
                        System.out.println("Курсор установлен на позицию 2377.");
                        break label;
                    case "2378":
                        terminalApp.checkForStop();
                        System.out.println("Курсор установлен на позицию 2378.");
                        break label;
                    case "2362":
                        terminalApp.checkForStop();
                        System.out.println("Курсор в положении '2362'. Ввод буквы 'L' и значений '5.0321'.");
                        sendDataWithDelay("L");
                        sendDataWithDelay("\r");
                        sendDataWithDelay("5.0321");
                        sendDataWithDelay("\r");

                        System.out.println("Повторная обработка текущего заказа: " + orderNumber);
                        processDeliveryDate(row); // Повторный вызов метода для обработки текущего заказа
                        return;
                    case "411":
                        terminalApp.checkForStop();
                        System.out.println("Курсор установлен на позицию 411. Прерывание цикла и повторная обработка заказа.");
                        continue startProcessing;
                }
            }

            // Вводим "Z" и нажимаем Enter до позиции 2212
            System.out.println("Ввод буквы 'Z' и нажатие Enter до достижения позиции 2212.");
            terminalApp.checkForStop();
            sendDataWithDelay("Z");
            sendDataWithDelay("\r");
            cursorPosition = getCursorPosition();
            while (!cursorPosition.equals("2212") && !cursorPosition.equals("2211")) {
                terminalApp.checkForStop();
                sendDataWithDelay("\r");
                cursorPosition = getCursorPosition();
            }
            System.out.println("Курсор установлен на позицию " + cursorPosition + ".");

            // Извлекаем первые два числа из даты поставки для комментария
            String kwWeek = extractWeekFromDeliveryDate(deliveryDate);

            // Вставляем комментарий с использованием даты поставки
            String comment = "DEM HST NACH WIRD DIE WARE IN KW " + kwWeek + " ZUGESTELLT";
            System.out.println("Ввод комментария: " + comment);
            terminalApp.checkForStop();
            sendDataWithDelay(comment);
            sendDataWithDelay("\r");

            // Проверка позиции 2274
            System.out.println("Проверка позиции курсора через F1.");
            cursorPosition = getCursorPosition();
            if (cursorPosition.equals("2274") || cursorPosition.equals("2273") || cursorPosition.equals("2278")) {
                terminalApp.checkForStop();
                System.out.println("Курсор установлен на позицию " + cursorPosition + ". Нажатие Enter.");
                sendDataWithDelay("\r");
            }

            // Проверка позиции 222
            System.out.println("Проверка позиции 222.");
            cursorPosition = getCursorPosition();
            if (cursorPosition.equals("222")) {
                System.out.println("Курсор установлен на позицию 222.");
                // Возвращаемся к позиции 2375
                System.out.println("Возвращение к позиции 2375 путем нажатия стрелки влево.");
                while (!cursorPosition.equals("2375") && !cursorPosition.equals("2376") && !cursorPosition.equals("2377") && !cursorPosition.equals("2378")) {
                    terminalApp.checkForStop();
                    sendDataWithDelay("\u001BOQ");
                    cursorPosition = getCursorPosition();
                }
                sendDataWithDelay("\r");
            }

            cursorPosition = getCursorPosition();

            if(screenTextDetector.isPosNrDisplayed()&&cursorPosition.equals("2362")){
                terminalApp.checkForStop();
                sendDataWithDelay("\r");
            }

            // Проверка на позицию 2362 для завершения
            System.out.println("Проверка позиции 2362.");
            cursorPosition = getCursorPosition();
            if (cursorPosition.equals("2362")) {
                terminalApp.checkForStop();
                System.out.println("Курсор установлен на позицию 2362. Нажатие стрелки влево.");
                sendDataWithDelay("\u001BOQ");
            }

            System.out.println("Задержка перед обработкой следующего заказа.");

            break; // Выходим из цикла после успешной обработки заказа
        }
    }

    private String extractWeekFromDeliveryDate(String deliveryDate) {
        // Предполагается, что deliveryDate имеет формат "dd.MM.yyyy" или подобный
        if (deliveryDate != null && deliveryDate.length() >= 2) {
            return deliveryDate.substring(0, 2);  // Извлекаем первые два символа
        }
        return "??";  // Возвращаем значение по умолчанию, если дата некорректна
    }

    public void processDeliveryDates(Iterator<Row> rows) throws IOException, InterruptedException {
        while (rows.hasNext()) {
            if (terminalApp.isStopped()) {
                System.out.println("Processing stopped.");
                break;
            }

            terminalApp.checkForPause();

            Row currentRow = rows.next();
            processDeliveryDate(currentRow);
        }
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
        int sleepTime = 500; // Задержка в 500 мс
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
