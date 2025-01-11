package org.msv.vt100;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.msv.vt100.ANSIISequences.*;
import org.msv.vt100.OrderAutomation.DeliveryDateProcessor;
import org.msv.vt100.OrderAutomation.OrderConfirmation;
import org.msv.vt100.OrderAutomation.ScreenTextDetector;
import org.msv.vt100.UI.CustomTerminalWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;

import java.io.FileInputStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class TerminalApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(TerminalApp.class);
    private static final int COLUMNS = 80;
    private static final int ROWS = 24;
    private InlineCssTextArea terminalArea;
    private Cursor cursor;
    private CursorVisibilityManager cursorVisibilityManager;
    private ScreenBuffer screenBuffer;
    private EscapeSequenceHandler escapeSequenceHandler;
    private SSHConnector sshConnector;
    private CharsetSwitchHandler charsetSwitchHandler;
    private TextFormater textFormater;
    private NrcsHandler nrcsHandler;
    private CursorController cursorController;
    private Thread processingThread;
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Object pauseCondition = new Object();
    private ScreenTextDetector screenTextDetector;
    private boolean isLoggingEnabled = false;
    private boolean inEscapeSequence = false;
    private boolean inDCSSequence = false;
    private StringBuilder escapeSequence = new StringBuilder();

    @Override
    public void start(Stage primaryStage) {

        initializeSSH();
        initializeComponents();
        initializeUI(primaryStage);

        primaryStage.setOnCloseRequest(event -> sshConnector.disconnect());
        Platform.runLater(() -> terminalArea.requestFocus());
    }

    // Инициализация компонентов, включая основные контроллеры и менеджеры
    private void initializeComponents() {
        cursor = new Cursor(ROWS, COLUMNS);
        cursorVisibilityManager = new CursorVisibilityManager();
        screenBuffer = new ScreenBuffer(ROWS, COLUMNS);
        nrcsHandler = new NrcsHandler();
        charsetSwitchHandler = new CharsetSwitchHandler();
        LineAttributeHandler lineAttributeHandler = new LineAttributeHandler();
        textFormater = new TextFormater(lineAttributeHandler);


        // Инициализация контроллеров и обработчиков
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

        cursorVisibilityManager.addVisibilityChangeListener(this::updateScreen);
    }

    // Настройка интерфейса приложения, включая TerminalArea и обработчики событий
    private void initializeUI(Stage primaryStage) {
        CustomTerminalWindow customWindow = new CustomTerminalWindow(primaryStage, this); // Pass 'this'
        terminalArea = customWindow.getTerminalArea();

        InputHandler inputHandler = new InputHandler(this, sshConnector, screenBuffer, cursor);
        inputHandler.attachEventHandlers(terminalArea);

        customWindow.configureStage(primaryStage);
        primaryStage.show();
    }


    // Инициализация SSH-соединения
    private void initializeSSH() {
        try {
            sshConnector = new SSHConnector("MMBFAEXT", "clustr.lutz.gmbh", 22, "C:\\Intel\\Ukraine_PrivateKey_OPENSSH", this);
            sshConnector.startSSHConnection();
        } catch (Exception e) {
            logger.error("Ошибка подключения по SSH", e);
        }
    }

    // Обновление экрана в зависимости от текущего состояния буфера экрана и стилей
    void updateScreen() {
        StringBuilder textBuilder = new StringBuilder();
        StyleSpansBuilder<String> styleSpansBuilder = new StyleSpansBuilder<>();

        int spanLength = 0;
        String lastStyle = null;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                Cell cell = screenBuffer.getCell(row, col);
                String currentStyle = cell.getStyle();
                String character = cell.getCharacter();

                if (row == cursor.getRow() && col == cursor.getColumn() && cursorVisibilityManager.isCursorVisible()) {
                    currentStyle = cursor.invertColors(currentStyle);
                }

                textBuilder.append(character);
                int charLength = character.codePointCount(0, character.length());

                if (lastStyle == null || !lastStyle.equals(currentStyle)) {
                    if (spanLength > 0) {
                        styleSpansBuilder.add(lastStyle, spanLength);
                    }
                    lastStyle = currentStyle;
                    spanLength = charLength;
                } else {
                    spanLength += charLength;
                }
            }
            textBuilder.append('\n');
            spanLength++;
        }

        if (lastStyle != null) {
            styleSpansBuilder.add(lastStyle, spanLength);
        }

        Platform.runLater(() -> {
            terminalArea.replaceText(textBuilder.toString());
            terminalArea.setStyleSpans(0, styleSpansBuilder.create());
        });
    }



    // Метод для добавления текста в буфер
    private void addTextToBuffer(String input) {
        //logger.debug("Начало добавления текста в буфер. Входящие символы: {}", input);
        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            String currentChar = new String(Character.toChars(codePoint));
            String currentStyle = textFormater.getCurrentStyle();
            cursorController.handleCharacter(currentChar, currentStyle);
            offset += Character.charCount(codePoint);
        }
    }

    // Обработка входных данных с учетом Escape-последовательностей
    public void processInput(char[] inputChars) {
        for (int i = 0; i < inputChars.length; i++) {
            char currentChar = inputChars[i];
            if (inDCSSequence) {
                if (currentChar == '\u001B') {
                    // Проверяем следующий символ
                    if (i + 1 < inputChars.length) {
                        char nextChar = inputChars[i + 1];
                        if (nextChar == '\\') {
                            // Найден терминатор DCS-последовательности ESC \
                            escapeSequence.append(currentChar);
                            escapeSequence.append(nextChar);
                            inDCSSequence = false;
                            escapeSequence.setLength(0);
                            i++; // Пропускаем следующий символ
                        } else {
                            // DCS-последовательность завершается
                            inDCSSequence = false;
                            escapeSequence.setLength(0);
                            // Начинаем новую escape-последовательность
                            inEscapeSequence = true;
                            escapeSequence.append(currentChar);
                        }
                    } else {
                        // Ждем следующий символ
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
                    // Начало DCS-последовательности
                    inEscapeSequence = false;
                    inDCSSequence = true;
                    // Оставляем текущий контент escapeSequence
                }
            } else if (currentChar == '\u001B') {
                inEscapeSequence = true;
                escapeSequence.setLength(0); // Очищаем предыдущий контент
                escapeSequence.append(currentChar);
            }else if (currentChar == '\r') {
                cursorController.moveCursorToLineStart();
            } else if (currentChar == '\n') {
                cursorController.moveCursorDown();
            }else if (currentChar == '\b') {
                // Обработка символа DEL (Backspace)
                handleBackspace();
                updateScreen();
            }

            else {
                // Обработка обычного текста
                String processedChar = nrcsHandler.processText(charsetSwitchHandler.processText(String.valueOf(currentChar)));
                addTextToBuffer(processedChar);
            }
        }
    }

    public void processFile(int choice, String excelFilePath) throws InterruptedException {
        logger.info("Открытие файла Excel: {}", excelFilePath);

        isPaused.set(false);
        isStopped.set(false);

        try (FileInputStream fileInputStream = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fileInputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            Row firstRow = sheet.getRow(0);
            if (firstRow == null) {
                logger.error("Файл Excel пустой. Завершение работы программы.");
                return;
            }

            int columnCount = firstRow.getLastCellNum();

            // Проверка формата файла в зависимости от выбранной операции
            if (choice == 1 && columnCount < 3) {
                logger.error("Таблица не соответствует формату обработки заказов. Ожидается 3 и более колонки.");
                return;
            } else if ((choice == 2 || choice == 3) && columnCount != 2) {
                logger.error("Таблица не соответствует формату обработки комментариев. Ожидается 2 колонки.");
                return;
            } else if (choice == 4 && columnCount < 3) {
                logger.error("Таблица не соответствует формату обработки дат поставки. Ожидается 3 и более колонки.");
                return;
            }

            // Получаем итератор строк
            Iterator<Row> rows = sheet.iterator();

            if (choice == 1) {
                // Создаём OrderConfirmation и обрабатываем заказы
                OrderConfirmation orderConfirmation = new OrderConfirmation(sshConnector, cursor, this, screenTextDetector);
                orderConfirmation.processOrders(rows);
            } else if (choice == 4) {
                // Создаём DeliveryDateProcessor и обрабатываем даты поставки
                DeliveryDateProcessor deliveryDateProcessor = new DeliveryDateProcessor(sshConnector, cursor, this, screenTextDetector);
                deliveryDateProcessor.processDeliveryDates(rows);
            } else {
                // Добавьте обработку для других опций, если необходимо
            }

        } catch (Exception e) {
            logger.error("Ошибка при обработке файла: " + excelFilePath, e);
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
            resumeProcessing(); // Wake up the processing thread if it's waiting
        }
    }


    // Methods to check pause and stop states
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



    public String getSelectedText() {
        return terminalArea.getSelectedText();
    }

    public void enableLogging() {
        isLoggingEnabled = true;
        logger.info("Логирование включено.");
        setLoggingLevel(Level.DEBUG);
    }

    public void disableLogging() {
        isLoggingEnabled = false;
        logger.info("Логирование отключено.");
        setLoggingLevel(Level.OFF);
    }

    private void setLoggingLevel(Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(level);
    }
    public void checkForStop() throws InterruptedException {
        if (isStopped.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Processing stopped");
        }
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

    public static void main(String[] args) {
        launch(args);
    }
}
