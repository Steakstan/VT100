package org.msv.vt100.core;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.msv.vt100.OrderAutomation.DeliveryDateProcessor;
import org.msv.vt100.OrderAutomation.OrderConfirmation;
import org.msv.vt100.OrderAutomation.ScreenTextDetector;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.TerminalApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FileProcessingService инкапсулирует логику обработки Excel-файлов.
 * В зависимости от переданного параметра (choice) выбирается соответствующая
 * обработка (например, OrderConfirmation для заказов или DeliveryDateProcessor для дат поставки).
 * Зависимости (SSHManager, Cursor, TerminalApp и ScreenTextDetector) передаются через конструктор,
 * что позволяет отделить логику обработки файлов от остальной логики терминала.
 */
public class FileProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(FileProcessingService.class);

    private static SSHManager sshManager = null;
    private static Cursor cursor = null;
    private static TerminalApp terminalApp = null;
    private static ScreenTextDetector screenTextDetector = null;
    private static AtomicBoolean isPaused = null;
    private static AtomicBoolean isStopped = null;

    /**
     * Конструктор FileProcessingService.
     *
     * @param sshManager         объект SSHManager для передачи в обработчики заказов
     * @param cursor             объект Cursor, управляющий положением курсора
     * @param terminalApp        координатор (TerminalApp) для вызова методов вроде checkForStop()
     * @param screenTextDetector объект для определения отображаемых на экране текстовых маркеров
     * @param isPaused           флаг паузы обработки
     * @param isStopped          флаг остановки обработки
     */
    public FileProcessingService(SSHManager sshManager,
                                 Cursor cursor,
                                 TerminalApp terminalApp,
                                 ScreenTextDetector screenTextDetector,
                                 AtomicBoolean isPaused,
                                 AtomicBoolean isStopped) {
        FileProcessingService.sshManager = sshManager;
        FileProcessingService.cursor = cursor;
        FileProcessingService.terminalApp = terminalApp;
        FileProcessingService.screenTextDetector = screenTextDetector;
        FileProcessingService.isPaused = isPaused;
        FileProcessingService.isStopped = isStopped;
    }

    /**
     * Обрабатывает Excel-файл по заданной операции.
     * Для choice == 1 используется OrderConfirmation,
     * для choice == 4 – DeliveryDateProcessor.
     * Если формат файла неверен, выводится сообщение об ошибке.
     *
     * @param choice         выбранная операция
     * @param excelFilePath  путь к Excel-файлу
     * @throws InterruptedException если поток прерван (например, при остановке обработки)
     */
    public static void processFile(int choice, String excelFilePath) throws InterruptedException {
        logger.info("Открытие Excel файла: {}", excelFilePath);
        isPaused.set(false);
        isStopped.set(false);
        try (FileInputStream fileInputStream = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fileInputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row firstRow = sheet.getRow(0);
            if (firstRow == null) {
                logger.error("Excel файл пуст. Выход.");
                return;
            }

            int columnCount = firstRow.getLastCellNum();
            if (choice == 1 && columnCount < 3) {
                logger.error("Неверный формат таблицы для обработки заказов. Ожидается минимум 3 столбца.");
                return;
            } else if ((choice == 2 || choice == 3) && columnCount != 2) {
                logger.error("Неверный формат таблицы для обработки комментариев. Ожидается 2 столбца.");
                return;
            } else if (choice == 4 && columnCount < 3) {
                logger.error("Неверный формат таблицы для обработки дат поставки. Ожидается минимум 3 столбца.");
                return;
            }

            Iterator<Row> rows = sheet.iterator();
            if (choice == 1) {
                OrderConfirmation orderConfirmation = new OrderConfirmation(sshManager, cursor, terminalApp, screenTextDetector);
                orderConfirmation.processOrders(rows);
            } else if (choice == 4) {
                DeliveryDateProcessor deliveryDateProcessor = new DeliveryDateProcessor(sshManager, cursor, terminalApp, screenTextDetector);
                deliveryDateProcessor.processDeliveryDates(rows);
            } else {
                logger.warn("Обработка для выбранного типа (choice = {}) не реализована.", choice);
            }
        } catch (Exception e) {
            logger.error("Ошибка при обработке файла: {}", excelFilePath, e);
        }
    }
}
