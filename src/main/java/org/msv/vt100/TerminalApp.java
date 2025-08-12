package org.msv.vt100;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.msv.vt100.OrderAutomation.LoginAutomationProcessor;
import org.msv.vt100.OrderAutomation.ScreenTextDetector;
import org.msv.vt100.ansiisequences.*;
import org.msv.vt100.core.*;
import org.msv.vt100.ssh.SSHConfig;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.ssh.SSHProfileManager;
import org.msv.vt100.ui.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class TerminalApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(TerminalApp.class);

    private static final int COLUMNS = 80;
    private static final int ROWS = 25;


    private Cursor cursor;
    private CursorVisibilityManager cursorVisibilityManager;
    private ScreenBuffer screenBuffer;
    private TextFormater textFormater;
    private ScreenTextDetector screenTextDetector;
    private InputProcessor inputProcessor;

    private UIController uiController;

    private SSHConfig currentProfile;
    private SSHManager sshManager;

    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final Object pauseCondition = new Object();
    private FileProcessingService fileProcessingService;

    private static final Thread DUMMY_THREAD = new Thread(() -> {}) {
        @Override public void interrupt() {/* no-op */}
    };
    private volatile Thread processingThread = null;

    private String commentText = "DEM HST NACH WIRD DIE WARE IN KW ** ZUGESTELLT";
    private boolean shouldWriteComment = true;

    private boolean isLoggingEnabled = false;

    private volatile boolean repaintRequested = true;
    private boolean lastCursorVisible = false;
    private Timeline screenUpdateTimeline;


    @Override
    public void start(Stage primaryStage) {
        try {
            initializeComponents();
            initializeUI(primaryStage);
            initializeSSHManager();
            initializeFileProcessingService();

            cursorVisibilityManager.showCursor();


            primaryStage.setOnCloseRequest(event -> {
                try {
                    shutdownApp();
                } catch (Throwable t) {
                    logger.warn("Fehler beim Shutdown: {}", t.getMessage(), t);
                } finally {

                    Platform.exit();
                    System.exit(0);
                }
            });

            Platform.runLater(() -> uiController.getTerminalCanvas().requestFocus());
            startScreenUpdater();
        } catch (Throwable t) {
            logger.error("Fataler Fehler beim Start:", t);
            Platform.runLater(() ->
                    TerminalDialog.showError("Anwendungsstart fehlgeschlagen:\n" + t.getMessage(),
                            primaryStage)
            );
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

        ErasingSequences erasingSequences = new ErasingSequences(
                screenBuffer, cursor, scrollingHandler, leftRightMarginModeHandler
        );
        CursorMovementHandler cursorMovementHandler = new CursorMovementHandler(cursorController);
        CopyRectangularAreaHandler copyRectangularAreaHandler = new CopyRectangularAreaHandler(screenBuffer);
        EraseCharacterHandler eraseCharacterHandler = new EraseCharacterHandler(screenBuffer, cursor, leftRightMarginModeHandler);

        EscapeSequenceHandler escapeSequenceHandler = new EscapeSequenceHandler(
                erasingSequences, cursorMovementHandler, decomHandler, scrollingHandler,
                charsetSwitchHandler, cursorVisibilityManager, textFormater, nrcsHandler,
                cursorController, leftRightMarginModeHandler, copyRectangularAreaHandler,
                eraseCharacterHandler, fillRectangularAreaHandler, cursor,
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

    public void handleBackspace() {
        if (cursor.getColumn() > 0) {
            cursor.moveLeft();
            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new Cell(" ", textFormater.getCurrentStyle()));
        } else if (cursor.getRow() > 0) {
            cursor.setPosition(cursor.getRow() - 1, screenBuffer.getColumns() - 1);
            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new Cell(" ", textFormater.getCurrentStyle()));
        }
        requestRepaint();
    }

    private void initializeUI(Stage primaryStage) {
        uiController = new UIController(primaryStage, this, screenBuffer, cursor);
        uiController.show();
    }

    private void initializeSSHManager() {
        SSHConfig autoProfile = SSHProfileManager.getAutoConnectProfile();
        if (autoProfile != null) {
            connectWithConfig(autoProfile);
            return;
        }

        Optional<SSHConfig> chosen = ProfileManagerDialog.showDialog(uiController.getPrimaryStage(), this);
        chosen.ifPresent(this::connectWithConfig);
    }

    private void initializeFileProcessingService() {
        if (sshManager == null) return;
        this.fileProcessingService = new FileProcessingService(sshManager, cursor, this, screenTextDetector, isPaused, isStopped);
    }


    private void connectWithConfig(SSHConfig config) {
        if (sshManager != null && sshManager.isConnected()) {
            try {
                sshManager.disconnect();
            } catch (Exception e) {
                logger.warn("Fehler beim Trennen der vorherigen Verbindung: {}", e.getMessage());
            }
        }

        currentProfile = config;
        sshManager = new SSHManager(config)
                .withKeepAlive(15_000, 3);

        sshManager.addDataListener(data ->
                Platform.runLater(() -> {
                    processInput(data.toCharArray());
                })
        );

        sshManager.connectAsync()
                .thenRun(() -> Platform.runLater(() -> {
                    logger.info("SSH verbunden, starte Auto-Login…");
                    new LoginAutomationProcessor(this).startAutoLogin();
                }))
                .exceptionally(ex -> {
                    logger.error("SSH-Verbindungsfehler", ex);
                    Platform.runLater(() ->
                            TerminalDialog.showError(
                                    "SSH-Verbindung fehlgeschlagen:\n" + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()),
                                    uiController.getPrimaryStage()
                            )
                    );
                    return null;
                });

        initializeFileProcessingService();
    }

    public void restartConnection() {
        if (currentProfile != null) {
            connectWithConfig(currentProfile);
        } else {
            openProfileDialog();
        }
    }

    public void disconnectConnection() {
        if (sshManager != null && sshManager.isConnected()) {
            sshManager.disconnect();
            processInput("Die Verbindung wurde abgebrochen\r".toCharArray());
        }
    }

    public void processInput(char[] inputChars) {
        inputProcessor.processInput(inputChars);
        requestRepaint();
    }

    private void requestRepaint() {
        repaintRequested = true;
    }

    private void startScreenUpdater() {
        screenUpdateTimeline = new Timeline(
                new KeyFrame(Duration.millis(33), event -> updateScreenIfNeeded())
        );
        screenUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
        screenUpdateTimeline.play();
    }

    @Override
    public void stop() {
        shutdownApp();
    }

    private void shutdownApp() {

        try { stopProcessing(); } catch (Exception ignore) {}

        try {
            if (fileProcessingService != null) {

                fileProcessingService.shutdown();
            }
        } catch (Exception e) {
            logger.debug("Problem beim Herunterfahren des FileProcessingService: {}", e.getMessage());
        }

        try {
            if (screenUpdateTimeline != null) {
                screenUpdateTimeline.stop();
                screenUpdateTimeline = null;
            }
        } catch (Exception ignore) {}

        try {
            if (cursorVisibilityManager != null) {
                cursorVisibilityManager.shutdown();
            }
        } catch (Exception e) {
            logger.debug("Problem beim Herunterfahren des CursorVisibilityManager: {}", e.getMessage());
        }

        try {
            if (sshManager != null && sshManager.isConnected()) {
                sshManager.disconnect();
            }
        } catch (Exception ignore) {}
    }

    private void updateScreenIfNeeded() {
        boolean currentCursorVisible = cursorVisibilityManager.isCursorVisible();
        if (currentCursorVisible != lastCursorVisible) {
            repaintRequested = true;
            lastCursorVisible = currentCursorVisible;
        }
        if (!repaintRequested) {
            return;
        }
        updateScreen();
        repaintRequested = false;
    }

    void updateScreen() {
        screenBuffer.commit();
        TerminalCanvas canvas = uiController.getTerminalCanvas();
        canvas.setCursorPosition(cursor.getRow(), cursor.getColumn());
        canvas.setCursorVisible(cursorVisibilityManager.isCursorVisible());
        canvas.updateScreen();
    }


    public void showProcessingButtons() {
        uiController.getContentPanel().showProcessingButtons();
        logger.debug("Processing-Schaltflächen angezeigt");
    }

    public void hideProcessingButtons() {
        uiController.getContentPanel().hideProcessingButtons();
        logger.debug("Processing-Schaltflächen ausgeblendet");
    }

    public void openBearbeitungseinstellungenDialog() {
        new BearbeitungseinstellungenDialog(this).show();
    }

    public void openProfileDialog() {
        Optional<SSHConfig> chosen = ProfileManagerDialog.showDialog(uiController.getPrimaryStage(), this);
        chosen.ifPresent(this::connectWithConfig);
    }

    public void openPositionssucheDialog() {
        new PositionssucheDialog(this).show();
    }

    public void openLoginSettingsDialog() {
        new LoginSettingsDialog(this).show();
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
        Thread t = processingThread;
        if (t != null) {
            try { t.interrupt(); } catch (Exception ignore) {}
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

    public void setProcessingThread(Thread worker) {
        this.processingThread = worker;
    }

    public Thread getProcessingThread() {
        return processingThread != null ? processingThread : DUMMY_THREAD;
    }

    public boolean isStopped() {
        return isStopped.get();
    }


    public void enableLogging() {
        isLoggingEnabled = true;
        addFileAppender();
        setLoggingLevel(Level.DEBUG);
        logger.info("Logging aktiviert.");
    }

    public void disableLogging() {
        isLoggingEnabled = false;
        removeFileAppender();
        setLoggingLevel(Level.OFF);
        logger.info("Logging deaktiviert.");
    }

    private void addFileAppender() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        if (rootLogger.getAppender("FILE") != null) {
            return;
        }

        String defaultPath = Path.of(System.getProperty("user.home"), "logs", "app.log").toString();
        String logPath = System.getProperty("LOG_PATH", defaultPath);

        File logFile = new File(logPath);
        if (logFile.isDirectory()) {
            logPath = new File(logFile, "app.log").getAbsolutePath();
        }

        File finalLogFile = new File(logPath);
        File parentDir = finalLogFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setName("FILE");
        fileAppender.setContext(loggerContext);
        fileAppender.setFile(logPath);
        fileAppender.setAppend(true);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n");
        encoder.start();

        fileAppender.setEncoder(encoder);
        fileAppender.start();

        rootLogger.addAppender(fileAppender);
    }

    private void removeFileAppender() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        Appender<ILoggingEvent> fileAppender = rootLogger.getAppender("FILE");
        if (fileAppender != null) {
            rootLogger.detachAppender(fileAppender);
            fileAppender.stop();
        }
    }

    private void setLoggingLevel(Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger =
                loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(level);
    }



    public SSHManager getSSHManager() {
        return sshManager;
    }

    public FileProcessingService getFileProcessingService() {
        return fileProcessingService;
    }

    public UIController getUIController() {
        return uiController;
    }

    public Cursor getCursor() {
        return cursor;
    }

    public ScreenBuffer getScreenBuffer() {
        return screenBuffer;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public boolean isShouldWriteComment() {
        return shouldWriteComment;
    }

    public void setShouldWriteComment(boolean shouldWriteComment) {
        this.shouldWriteComment = shouldWriteComment;
    }

    public String getSelectedText() {
        return uiController.getTerminalCanvas().getSelectedText();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
