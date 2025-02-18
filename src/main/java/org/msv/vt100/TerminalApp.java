package org.msv.vt100;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.msv.vt100.ansiisequences.*;
import org.msv.vt100.OrderAutomation.ScreenTextDetector;
import org.msv.vt100.ssh.SSHProfileManager;
import org.msv.vt100.ui.*;
import org.msv.vt100.core.*;
import org.msv.vt100.ssh.SSHConfig;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.core.FileProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class TerminalApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(TerminalApp.class);
    private static final int COLUMNS = 80;
    private static final int ROWS = 25;

    // Компоненты логики терминала
    private Cursor cursor;
    private CursorVisibilityManager cursorVisibilityManager;
    private ScreenBuffer screenBuffer;
    private TextFormater textFormater;
    private ScreenTextDetector screenTextDetector;
    private InputProcessor inputProcessor;
    private UIController uiController;
    private CustomTerminalWindow customTerminalWindow;
    private SSHConfig currentProfile;

    // Новый SSH-сервис
    private SSHManager sshManager;

    // Флаги управления процессами
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Object pauseCondition = new Object();
    private FileProcessingService fileProcessingService;
    private Thread processingThread;


    // Логирование
    private boolean isLoggingEnabled = false;

    @Override
    public void start(Stage primaryStage) {
        initializeSSHManager();
        initializeComponents();
        initializeUI(primaryStage);
        initializeFileProcessingService();
        cursorVisibilityManager.showCursor();

        primaryStage.setOnCloseRequest(event -> {
            if (sshManager != null) {
                sshManager.disconnect();
            }
        });
        Platform.runLater(() -> uiController.getTerminalCanvas().requestFocus());
        startScreenUpdater();
    }

    /**
     * Инициализирует SSHManager.
     */
    private void initializeSSHManager() {
        List<SSHConfig> profiles = SSHProfileManager.getProfiles();
        SSHConfig autoProfile = SSHProfileManager.getAutoConnectProfile();
        if (autoProfile != null) {
            connectWithConfig(autoProfile);
        } else {
            // Показываем диалог профилей с основным окном как owner
            Optional<SSHConfig> chosen = ProfileManagerDialog.showDialog(uiController.getPrimaryStage());
            chosen.ifPresent(this::connectWithConfig);
        }
    }



    private void connectWithConfig(SSHConfig config) {
        if (sshManager != null && sshManager.isConnected()) {
            sshManager.disconnect();
        }
        try {
            currentProfile = config; // сохраняем профиль
            sshManager = new SSHManager(config);
            sshManager.addDataListener(data -> Platform.runLater(() -> processInput(data.toCharArray())));
            sshManager.connectAsync().exceptionally(ex -> {
                System.err.println("Ошибка подключения по SSH: " + ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            System.err.println("Ошибка при инициализации SSHManager: " + e.getMessage());
        }
    }





    private void initializeComponents() {
        cursor = new Cursor(ROWS, COLUMNS);
        cursorVisibilityManager = new CursorVisibilityManager();
        screenBuffer = new ScreenBuffer(ROWS, COLUMNS);
        NrcsHandler nrcsHandler = new NrcsHandler();
        CharsetSwitchHandler charsetSwitchHandler = new CharsetSwitchHandler();
        LineAttributeHandler lineAttributeHandler = new LineAttributeHandler();
        textFormater = new TextFormater(lineAttributeHandler);

        LeftRightMarginModeHandler leftRightMarginModeHandler = new LeftRightMarginModeHandler();
        FillRectangularAreaHandler fillRectangularAreaHandler = new FillRectangularAreaHandler(screenBuffer);
        DECOMHandler decomHandler = new DECOMHandler();
        cursorVisibilityManager.initializeCursorBlinking();

        CursorController cursorController = new CursorController(
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

        EscapeSequenceHandler escapeSequenceHandler = new EscapeSequenceHandler(
                erasingSequences, cursorMovementHandler, decomHandler, scrollingHandler,
                charsetSwitchHandler, cursorVisibilityManager, textFormater, nrcsHandler,
                cursorController, leftRightMarginModeHandler, copyRectangularAreaHandler,
                this, eraseCharacterHandler, fillRectangularAreaHandler, cursor,
                lineAttributeHandler, screenBuffer, leftRightMarginSequenceHandler, insertLineHandler
        );
        screenTextDetector = new ScreenTextDetector(screenBuffer);

        inputProcessor = new InputProcessor(
                escapeSequenceHandler,
                cursorController,
                nrcsHandler,
                charsetSwitchHandler,
                textFormater,
                this::handleBackspace
        );
    }


    private void initializeUI(Stage primaryStage) {
        uiController = new UIController(primaryStage, this, screenBuffer, sshManager, cursor);
        uiController.show();
    }
    public void openBearbeitungseinstellungenDialog() {
        BearbeitungseinstellungenDialog dialog = new BearbeitungseinstellungenDialog(this);
        dialog.show();
    }


    private void initializeFileProcessingService() {
        // Сервис обработки Excel файлов
        this.fileProcessingService = new FileProcessingService(sshManager, cursor, this, screenTextDetector, isPaused, isStopped);
    }
    public void showProcessingButtons() {
        uiController.getContentPanel().showProcessingButtons();
        System.out.println("The button in the donwbar on");
    }

    public void hideProcessingButtons() {
        uiController.getContentPanel().hideProcessingButtons();
        System.out.println("The button in the donwbar off");
    }


    void updateScreen() {
        TerminalCanvas canvas = uiController.getTerminalCanvas();
        canvas.setCursorPosition(cursor.getRow(), cursor.getColumn());
        canvas.cursorVisible = cursorVisibilityManager.isCursorVisible();
        canvas.updateScreen();  // Отрисовка канвы
    }

    // Метод для старта таймера обновления экрана
    private void startScreenUpdater() {
        Timeline screenUpdateTimeline = new Timeline(
                new KeyFrame(Duration.millis(33), event -> updateScreen())
        );
        screenUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
        screenUpdateTimeline.play();
    }

    public void processInput(char[] inputChars) {
        inputProcessor.processInput(inputChars);
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

    public String getSelectedText() {
        return "";
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

    public void handleBackspace() {
        if (cursor.getColumn() > 0) {
            cursor.moveLeft();
            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new org.msv.vt100.core.Cell(" ", textFormater.getCurrentStyle()));
        } else if (cursor.getRow() > 0) {
            cursor.setPosition(cursor.getRow() - 1, screenBuffer.getColumns() - 1);
            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new org.msv.vt100.core.Cell(" ", textFormater.getCurrentStyle()));
        }
    }

    public void checkForStop() throws InterruptedException {
        if (isStopped.get() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Обработка остановлена");
        }
    }
    public SSHManager getSSHManager() {
        return sshManager;
    }
    public FileProcessingService getFileProcessingService() {
        return fileProcessingService;
    }
    public void openProfileDialog() {
        Optional<SSHConfig> chosen = ProfileManagerDialog.showDialog(uiController.getPrimaryStage());
        chosen.ifPresent(config -> {
            if (sshManager != null && sshManager.isConnected()) {
                sshManager.disconnect();
            }
            connectWithConfig(config);
        });
    }

    /**
     * Перезапускает текущее соединение, используя сохранённый профиль.
     * Если профиль не найден, открывает диалог выбора.
     */
    public void restartConnection() {
        if (sshManager != null && sshManager.isConnected()) {
            sshManager.disconnect();
            if (currentProfile != null) {
                connectWithConfig(currentProfile);
            } else {
                openProfileDialog();
            }
        } else {
            openProfileDialog();
        }
    }

    public void disconnectConnection() {
        if (sshManager != null && sshManager.isConnected()) {
            sshManager.disconnect();
            processInput("Подключение было отключено\r".toCharArray());
        }
    }

    public Thread getProcessingThread() {
        return processingThread;
    }


    public static void main(String[] args) {
        launch(args);
    }
}
