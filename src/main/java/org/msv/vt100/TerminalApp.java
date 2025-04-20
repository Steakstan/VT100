package org.msv.vt100;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.msv.vt100.OrderAutomation.LoginAutomationProcessor;
import org.msv.vt100.ansiisequences.*;
import org.msv.vt100.OrderAutomation.ScreenTextDetector;
import org.msv.vt100.ssh.SSHProfileManager;
import org.msv.vt100.ui.*;
import org.msv.vt100.core.*;
import org.msv.vt100.ssh.SSHConfig;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.core.FileProcessingService;
import org.msv.vt100.ui.LoginSettingsDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TerminalApp is a JavaFX application that emulates a VT520-type terminal with SSH connectivity.
 * It is adapted for integration with SHD’s order management software.
 * <p>
 * All user-visible messages (logs, button labels, etc.) have been localized to German.
 * </p>
 */
public class TerminalApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(TerminalApp.class);
    private static final int COLUMNS = 80;
    private static final int ROWS = 25;

    // Terminal logic components
    private Cursor cursor;
    private CursorVisibilityManager cursorVisibilityManager;
    private ScreenBuffer screenBuffer;
    private TextFormater textFormater;
    private ScreenTextDetector screenTextDetector;
    private InputProcessor inputProcessor;
    private UIController uiController;
    private SSHConfig currentProfile;
    private String commentText = "DEM HST NACH WIRD DIE WARE IN KW ** ZUGESTELLT";
    private boolean shouldWriteComment = true;

    // SSH service
    private SSHManager sshManager;

    // Process control flags
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isStopped = new AtomicBoolean(false);
    private final Object pauseCondition = new Object();
    private FileProcessingService fileProcessingService;
    Thread processingThread;

    // Logging flag
     boolean isLoggingEnabled = false;

    /**
     * The start method initializes the SSH connection, terminal components, UI, and file processing service.
     * It also sets up the periodic screen update and the shutdown hook.
     *
     * @param primaryStage the main stage of the JavaFX application
     */
    @Override
    public void start(Stage primaryStage) {
        initializeSSHManager();
        initializeComponents();
        initializeUI(primaryStage);
        initializeFileProcessingService();
        cursorVisibilityManager.showCursor();

        // On application close, disconnect SSH
        primaryStage.setOnCloseRequest(event -> {
            if (sshManager != null) {
                sshManager.disconnect();
            }
        });
        Platform.runLater(() -> uiController.getTerminalCanvas().requestFocus());
        startScreenUpdater();
    }

    /**
     * Initializes the SSHManager by checking for an auto-connect profile.
     * If none exists, it shows the profile selection dialog.
     */
    private void initializeSSHManager() {
        SSHConfig autoProfile = SSHProfileManager.getAutoConnectProfile();
        if (autoProfile != null) {
            connectWithConfig(autoProfile);
        } else {
            // Show the profile dialog with the main window as owner
            Optional<SSHConfig> chosen = ProfileManagerDialog.showDialog(uiController.getPrimaryStage(), this);

            chosen.ifPresent(this::connectWithConfig);
        }
    }

    /**
     * Connects to an SSH server using the provided configuration.
     * If already connected, the existing connection is disconnected.
     *
     * @param config the SSH configuration to use for connection
     */
    private void connectWithConfig(SSHConfig config) {
        if (sshManager != null && sshManager.isConnected()) {
            sshManager.disconnect();
        }
        try {
            currentProfile = config; // Сохраняем профиль
            sshManager = new SSHManager(config);
            sshManager.addDataListener(data -> Platform.runLater(() -> processInput(data.toCharArray())));
            sshManager.connectAsync()
                    .thenRun(() -> {
                        // После успешного подключения запускаем автоматическую авторизацию
                        new LoginAutomationProcessor(this).startAutoLogin();
                    })
                    .exceptionally(ex -> {
                        System.err.println("SSH-Verbindungsfehler: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            System.err.println("Fehler bei der Initialisierung von SSHManager: " + e.getMessage());
        }
    }


    /**
     * Initializes terminal components such as the cursor, screen buffer, text formatter, and various handlers.
     */
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
                cursorController, leftRightMarginModeHandler, copyRectangularAreaHandler, eraseCharacterHandler, fillRectangularAreaHandler, cursor,
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

    /**
     * Initializes the user interface using JavaFX.
     *
     * @param primaryStage the primary stage for the UI
     */
    private void initializeUI(Stage primaryStage) {
        uiController = new UIController(primaryStage, this, screenBuffer, cursor);
        uiController.show();
    }

    /**
     * Opens the "Bearbeitungseinstellungen" dialog (i.e., editing settings dialog).
     */
    public void openBearbeitungseinstellungenDialog() {
        BearbeitungseinstellungenDialog dialog = new BearbeitungseinstellungenDialog(this);
        dialog.show();
    }

    /**
     * Initializes the file processing service for Excel file handling.
     */
    private void initializeFileProcessingService() {
        // Excel file processing service
        this.fileProcessingService = new FileProcessingService(sshManager, cursor, this, screenTextDetector, isPaused, isStopped);
    }

    /**
     * Shows the processing buttons and prints a message in German.
     */
    public void showProcessingButtons() {
        uiController.getContentPanel().showProcessingButtons();
        System.out.println("Schaltfläche in der unteren Leiste aktiviert");
    }

    /**
     * Hides the processing buttons and prints a message in German.
     */
    public void hideProcessingButtons() {
        uiController.getContentPanel().hideProcessingButtons();
        System.out.println("Schaltfläche in der unteren Leiste deaktiviert");
    }

    /**
     * Updates the terminal screen by setting the cursor position, updating the cursor visibility,
     * and rendering the canvas.
     */
    void updateScreen() {
        TerminalCanvas canvas = uiController.getTerminalCanvas();
        canvas.setCursorPosition(cursor.getRow(), cursor.getColumn());
        canvas.cursorVisible = cursorVisibilityManager.isCursorVisible();
        canvas.updateScreen();  // Render the canvas
    }

    /**
     * Starts the periodic screen updater (approximately every 33ms).
     */
    private void startScreenUpdater() {
        Timeline screenUpdateTimeline = new Timeline(
                new KeyFrame(Duration.millis(4), event -> updateScreen())
        );
        screenUpdateTimeline.setCycleCount(Timeline.INDEFINITE);
        screenUpdateTimeline.play();
    }

    /**
     * Processes input characters from the SSH data stream.
     *
     * @param inputChars an array of characters received from SSH
     */
    public void processInput(char[] inputChars) {
        inputProcessor.processInput(inputChars);
    }

    /**
     * Pauses the processing of incoming data.
     */
    public void pauseProcessing() {
        isPaused.set(true);
    }

    /**
     * Resumes the processing of incoming data.
     */
    public void resumeProcessing() {
        isPaused.set(false);
        synchronized (pauseCondition) {
            pauseCondition.notifyAll();
        }
    }

    /**
     * Stops the processing of incoming data.
     */
    public void stopProcessing() {
        isStopped.set(true);
        if (isPaused.get()) {
            resumeProcessing();
        }
    }

    /**
     * Checks for a pause condition and waits if processing is paused.
     */
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


    /**
     * Returns the current SSHManager.
     *
     * @return the SSHManager instance
     */
    public SSHManager getSSHManager() {
        return sshManager;
    }

    /**
     * Returns the file processing service instance.
     *
     * @return the FileProcessingService instance
     */
    public FileProcessingService getFileProcessingService() {
        return fileProcessingService;
    }

    /**
     * Opens the profile selection dialog.
     */
    public void openProfileDialog() {
        Optional<SSHConfig> chosen = ProfileManagerDialog.showDialog(uiController.getPrimaryStage(), this);
        chosen.ifPresent(config -> {
            if (sshManager != null && sshManager.isConnected()) {
                sshManager.disconnect();
            }
            connectWithConfig(config);
        });
    }

    /**
     * Restarts the current SSH connection using the saved profile.
     * If no profile is found, it opens the profile selection dialog.
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

    /**
     * Disconnects the SSH connection and displays a message to the terminal.
     */
    public void disconnectConnection() {
        if (sshManager != null && sshManager.isConnected()) {
            sshManager.disconnect();
            processInput("Die Verbindung wurde abgebrochen\r".toCharArray());
        }
    }

    /**
     * Returns the thread handling the processing of data.
     *
     * @return the processing thread
     */
    public Thread getProcessingThread() {
        return processingThread;
    }

    /**
     * Enables logging by setting the log level to DEBUG and printing a German log message.
     */
    public void enableLogging() {
        isLoggingEnabled = true;
        addFileAppender();
        setLoggingLevel(Level.DEBUG);
        logger.info("Logging aktiviert.");
    }

    /**
     * Disables logging by setting the log level to OFF and printing a German log message.
     */
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

        FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
        fileAppender.setName("FILE");
        fileAppender.setContext(loggerContext);

        String logPath = System.getProperty("LOG_PATH", "logs/app.log");


        File logFile = new File(logPath);
        if (logFile.isDirectory()) {
            logPath = new File(logFile, "app.log").getAbsolutePath();
        }

        File finalLogFile = new File(logPath);
        File parentDir = finalLogFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

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



    /**
     * Sets the logging level for the root logger.
     *
     * @param level the desired logging level
     */
    private void setLoggingLevel(Level level) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(level);
    }

    /**
     * Handles the Backspace key by moving the cursor left and clearing the previous cell.
     */
    public void handleBackspace() {
        if (cursor.getColumn() > 0) {
            cursor.moveLeft();
            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new org.msv.vt100.core.Cell(" ", textFormater.getCurrentStyle()));
        } else if (cursor.getRow() > 0) {
            cursor.setPosition(cursor.getRow() - 1, screenBuffer.getColumns() - 1);
            screenBuffer.setCell(cursor.getRow(), cursor.getColumn(), new org.msv.vt100.core.Cell(" ", textFormater.getCurrentStyle()));
        }
    }

    public String getSelectedText() {
        return uiController.getTerminalCanvas().getSelectedText();
    }

    public boolean isStopped() {
        return isStopped.get();
    }

    public void openPositionssucheDialog() {
        PositionssucheDialog dialog = new PositionssucheDialog(this);
        dialog.show();
    }

    public ScreenBuffer getScreenBuffer() {
        return screenBuffer;
    }

    public void openLoginSettingsDialog() {
        LoginSettingsDialog dialog = new LoginSettingsDialog(this);
        dialog.show();
    }
    public Cursor getCursor() {
        return cursor;
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


    public UIController getUIController() {
        return uiController;
    }

    /**
     * The main entry point for the application.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        launch(args);
    }

}
