package org.msv.vt100.core;

import org.apache.poi.ss.usermodel.*;
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

public class FileProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(FileProcessingService.class);

    private SSHManager sshManager;
    private Cursor cursor;
    private TerminalApp terminalApp;
    private ScreenTextDetector screenTextDetector;
    private AtomicBoolean isPaused;
    private AtomicBoolean isStopped;

    public FileProcessingService(SSHManager sshManager,
                                 Cursor cursor,
                                 TerminalApp terminalApp,
                                 ScreenTextDetector screenTextDetector,
                                 AtomicBoolean isPaused,
                                 AtomicBoolean isStopped) {
        this.sshManager = sshManager;
        this.cursor = cursor;
        this.terminalApp = terminalApp;
        this.screenTextDetector = screenTextDetector;
        this.isPaused = isPaused;
        this.isStopped = isStopped;
    }

    public void processFile(int choice, String excelFilePath) throws InterruptedException {
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
