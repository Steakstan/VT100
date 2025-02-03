package org.msv.vt100;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.msv.vt100.ANSIISequences.*;
import org.msv.vt100.OrderAutomation.DeliveryDateProcessor;
import org.msv.vt100.OrderAutomation.OrderConfirmation;
import org.msv.vt100.OrderAutomation.ScreenTextDetector;
import org.msv.vt100.UI.CustomTerminalWindow;
import org.msv.vt100.UI.TerminalCanvas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class TerminalApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(TerminalApp.class);
    private static final int COLUMNS = 80;
    private static final int ROWS = 25;

    // Замість InlineCssTextArea використовується CustomTerminalWindow, який містить TerminalCanvas
    private CustomTerminalWindow customTerminalWindow;

    // Основні компоненти логіки термінала
    private Cursor cursor;
    private CursorVisibilityManager cursorVisibilityManager;
    private ScreenBuffer screenBuffer;
    private EscapeSequenceHandler escapeSequenceHandler;
    private SSHConnector sshConnector;
    private CharsetSwitchHandler charsetSwitchHandler;
    private TextFormater textFormater;
    private NrcsHandler nrcsHandler;
    private CursorController cursorController;
    private ScreenTextDetector screenTextDetector;

    // Обробка процесу (наприклад, для операцій із Excel)
    private Thread processingThread;
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Object pauseCondition = new Object();

    // Escape-послідовності
    private boolean inEscapeSequence = false;
    private boolean inDCSSequence = false;
    private StringBuilder escapeSequence = new StringBuilder();

    // Логування
    private boolean isLoggingEnabled = false;

    @Override
    public void start(Stage primaryStage) {
        initializeSSH();
        initializeComponents();
        initializeUI(primaryStage);
        // Вмикаємо курсор (це встановлює cursorEnabled у true)
        cursorVisibilityManager.showCursor();

        primaryStage.setOnCloseRequest(event -> sshConnector.disconnect());

        // Встановлюємо фокус на Canvas
        Platform.runLater(() -> customTerminalWindow.getTerminalCanvas().requestFocus());
    }

    private void initializeComponents() {
        cursor = new Cursor(ROWS, COLUMNS);
        cursorVisibilityManager = new CursorVisibilityManager();
        screenBuffer = new ScreenBuffer(ROWS, COLUMNS);
        nrcsHandler = new NrcsHandler();
        charsetSwitchHandler = new CharsetSwitchHandler();
        LineAttributeHandler lineAttributeHandler = new LineAttributeHandler();
        textFormater = new TextFormater(lineAttributeHandler);

        LeftRightMarginModeHandler leftRightMarginModeHandler = new LeftRightMarginModeHandler();
        FillRectangularAreaHandler fillRectangularAreaHandler = new FillRectangularAreaHandler(screenBuffer);
        DECOMHandler decomHandler = new DECOMHandler();
        cursorVisibilityManager.initializeCursorBlinking();

        cursorController = new CursorController(
                cursor, screenBuffer, leftRightMarginModeHandler, decomHandler, lineAttributeHandler
        );

        LeftRightMarginSequenceHandler leftRightMarginSequenceHandler = new LeftRightMarginSequenceHandler(
                leftRightMarginModeHandler, cursorController, screenBuffer
        );

        ScrollingRegionHandler scrollingHandler = new ScrollingRegionHandler(
                screenBuffer, leftRightMarginModeHandler, leftRightMarginSequenceHandler
        );

        InsertLineHandler insertLineHandler = new InsertLineHandler(
                screenBuffer, cursor, scrollingHandler, leftRightMarginModeHandler
        );

        cursorController.setScrollingRegionHandler(scrollingHandler);

        ErasingSequences erasingSequences = new ErasingSequences(screenBuffer, cursor, scrollingHandler, leftRightMarginModeHandler);
        CursorMovementHandler cursorMovementHandler = new CursorMovementHandler(cursorController);
        CopyRectangularAreaHandler copyRectangularAreaHandler = new CopyRectangularAreaHandler(screenBuffer);
        EraseCharacterHandler eraseCharacterHandler = new EraseCharacterHandler(screenBuffer, cursor, leftRightMarginModeHandler);

        escapeSequenceHandler = new EscapeSequenceHandler(
                erasingSequences, cursorMovementHandler, decomHandler, scrollingHandler,
                charsetSwitchHandler, cursorVisibilityManager, textFormater, nrcsHandler,
                cursorController, leftRightMarginModeHandler, copyRectangularAreaHandler,
                this, eraseCharacterHandler, fillRectangularAreaHandler, cursor,
                lineAttributeHandler, screenBuffer, leftRightMarginSequenceHandler, insertLineHandler
        );
        screenTextDetector = new ScreenTextDetector(screenBuffer);

        // Додаємо оновлення екрана при зміні видимості курсора
        cursorVisibilityManager.addVisibilityChangeListener(this::updateScreen);
    }

    private void initializeUI(Stage primaryStage) {
        // Створюємо CustomTerminalWindow з використанням TerminalCanvas
        customTerminalWindow = new CustomTerminalWindow(primaryStage, this, screenBuffer);

        // Створюємо InputHandler і прикріплюємо обробку клавіш до TerminalCanvas
        InputHandler inputHandler = new InputHandler(this, sshConnector, screenBuffer, cursor);
        customTerminalWindow.getTerminalCanvas().setOnKeyPressed(inputHandler::handleKeyPressed);
        customTerminalWindow.getTerminalCanvas().setOnKeyTyped(inputHandler::handleKeyTyped);

        customTerminalWindow.configureStage(primaryStage);
        primaryStage.show();
    }

    private void initializeSSH() {
        try {
            sshConnector = new SSHConnector("MMBFAEXT", "clustr.lutz.gmbh", 22,
                    "D:\\XXXLutz\\Wichtiges\\Ukraine_PrivateKey_OPENSSH", this);
            sshConnector.startSSHConnection();
        } catch (Exception e) {
            logger.error("Error establishing SSH connection", e);
        }
    }

    // Оновлення екрану через Canvas: оновлюємо позицію курсора і стан видимості
    public void updateScreen() {
        TerminalCanvas canvas = customTerminalWindow.getTerminalCanvas();
        // Оновлюємо позицію курсора із значень, що зберігаються в об'єкті cursor
        canvas.setCursorPosition(cursor.getRow(), cursor.getColumn());
        // Встановлюємо прапорець видимості курсора згідно з логікою CursorVisibilityManager
        canvas.cursorVisible = cursorVisibilityManager.isCursorVisible();
        // Оновлюємо екран (забезпечуємо виконання на FX-потоці)
        Platform.runLater(() -> canvas.updateScreen());
    }

    // Метод для додавання тексту до екранного буфера
    private void addTextToBuffer(String input) {
        // Попередня обробка через CharsetSwitchHandler
        String processedInput = charsetSwitchHandler.processText(input);
        for (int offset = 0; offset < processedInput.length(); ) {
            int codePoint = processedInput.codePointAt(offset);
            String currentChar = new String(Character.toChars(codePoint));
            String currentStyle = textFormater.getCurrentStyle();
            cursorController.handleCharacter(currentChar, currentStyle);
            offset += Character.charCount(codePoint);
        }
    }


    // Обробка вхідних даних (escape-послідовності та звичайний текст)
    public void processInput(char[] inputChars) {
        for (int i = 0; i < inputChars.length; i++) {
            char currentChar = inputChars[i];
            if (inDCSSequence) {
                if (currentChar == '\u001B') {
                    if (i + 1 < inputChars.length) {
                        char nextChar = inputChars[i + 1];
                        if (nextChar == '\\') {
                            escapeSequence.append(currentChar);
                            escapeSequence.append(nextChar);
                            inDCSSequence = false;
                            escapeSequence.setLength(0);
                            i++;
                        } else {
                            inDCSSequence = false;
                            escapeSequence.setLength(0);
                            inEscapeSequence = true;
                            escapeSequence.append(currentChar);
                        }
                    } else {
                        escapeSequence.append(currentChar);
                    }
                } else {
                    escapeSequence.append(currentChar);
                }
            } else if (inEscapeSequence) {
                escapeSequence.append(currentChar);
                if (escapeSequenceHandler.isEndOfSequence(escapeSequence)) {
                    escapeSequenceHandler.processEscapeSequence(escapeSequence.toString());
                    inEscapeSequence = false;
                    escapeSequence.setLength(0);
                } else if (escapeSequence.toString().startsWith("\u001BP")) {
                    inEscapeSequence = false;
                    inDCSSequence = true;
                }
            } else if (currentChar == '\u001B') {
                inEscapeSequence = true;
                escapeSequence.setLength(0);
                escapeSequence.append(currentChar);
            } else if (currentChar == '\r') {
                cursorController.moveCursorToLineStart();
            } else if (currentChar == '\n') {
                cursorController.moveCursorDown();
            } else if (currentChar == '\b') {
                handleBackspace();
                updateScreen();
            } else {
                String processedChar = nrcsHandler.processText(
                        charsetSwitchHandler.processText(String.valueOf(currentChar))
                );
                addTextToBuffer(processedChar);
            }
        }
    }

    public void processFile(int choice, String excelFilePath) throws InterruptedException {
        logger.info("Opening Excel file: {}", excelFilePath);
        isPaused.set(false);
        isStopped.set(false);
        try (FileInputStream fileInputStream = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fileInputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row firstRow = sheet.getRow(0);
            if (firstRow == null) {
                logger.error("Excel file is empty. Exiting.");
                return;
            }

            int columnCount = firstRow.getLastCellNum();
            if (choice == 1 && columnCount < 3) {
                logger.error("Table format incorrect for order processing. Expected 3+ columns.");
                return;
            } else if ((choice == 2 || choice == 3) && columnCount != 2) {
                logger.error("Table format incorrect for comment processing. Expected 2 columns.");
                return;
            } else if (choice == 4 && columnCount < 3) {
                logger.error("Table format incorrect for delivery date processing. Expected 3+ columns.");
                return;
            }

            Iterator<Row> rows = sheet.iterator();
            if (choice == 1) {
                OrderConfirmation orderConfirmation = new OrderConfirmation(sshConnector, cursor, this, screenTextDetector);
                orderConfirmation.processOrders(rows);
            } else if (choice == 4) {
                DeliveryDateProcessor deliveryDateProcessor = new DeliveryDateProcessor(sshConnector, cursor, this, screenTextDetector);
                deliveryDateProcessor.processDeliveryDates(rows);
            } else {
                // Additional processing if needed
            }
        } catch (Exception e) {
            logger.error("Error processing file: {}", excelFilePath, e);
        }
    }

    public void pauseProcessing() {
        isPaused.set(true);
    }

    public void resumeProcessing() {
        isPaused.set(false);
        synchronized (pauseCondition) {
            pauseCondition.notifyAll();
        }
    }

    public void stopProcessing() {
        isStopped.set(true);
        if (isPaused.get()) {
            resumeProcessing();
        }
    }

    public void checkForPause() {
        synchronized (pauseCondition) {
            while (isPaused.get()) {
                try {
                    pauseCondition.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    public boolean isStopped() {
        return isStopped.get();
    }

    // Метод для отримання вибраного тексту – для Canvas не реалізовано
    public String getSelectedText() {
        return "";
    }

    public void enableLogging() {
        isLoggingEnabled = true;
        logger.info("Logging enabled.");
        setLoggingLevel(Level.DEBUG);
    }

    public void disableLogging() {
        isLoggingEnabled = false;
        logger.info("Logging disabled.");
        setLoggingLevel(Level.OFF);
    }

    private void setLoggingLevel(Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(level);
    }

    public boolean isLoggingEnabled() {
        return isLoggingEnabled;
    }

    public void handleBackspace() {
        if (cursor.getColumn() > 0) {
            cursor.moveLeft();
            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new Cell(" ", textFormater.getCurrentStyle()));
        } else if (cursor.getRow() > 0) {
            cursor.setPosition(cursor.getRow() - 1, screenBuffer.getColumns() - 1);
            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new Cell(" ", textFormater.getCurrentStyle()));
        }
    }
    public void checkForStop() throws InterruptedException {
        if (isStopped.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Processing stopped");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
