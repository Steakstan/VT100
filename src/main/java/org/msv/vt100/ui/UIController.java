package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.InputHandler;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.core.ScreenBuffer;

/**
 * UIController is responsible for initializing and updating all JavaFX UI elements.
 * It creates the main terminal window (CustomTerminalWindow), attaches input handlers,
 * and provides methods to update the UI (such as updating the canvas and setting focus).
 */
public class UIController {

    private final Stage primaryStage;
    private final TerminalCanvas terminalCanvas;
    private final CustomTerminalWindow customTerminalWindow;

    /**
     * Constructs a UIController.
     *
     * @param primaryStage the primary JavaFX Stage.
     * @param terminalApp  the TerminalApp instance (the coordinator).
     * @param screenBuffer the screen buffer for the terminal.

     * @param cursor       the Cursor object used in the terminal.
     */
    public UIController(Stage primaryStage, TerminalApp terminalApp,
                        ScreenBuffer screenBuffer, Cursor cursor) {
        this.primaryStage = primaryStage;
        this.customTerminalWindow = new CustomTerminalWindow(primaryStage, terminalApp, screenBuffer);
        // Create the main terminal window that encapsulates all UI elements
        this.terminalCanvas = this.customTerminalWindow.getTerminalCanvas();

        // Initialize the input handler and attach it to the canvas
        InputHandler inputHandler = new InputHandler(terminalApp, screenBuffer, cursor);
        terminalCanvas.setOnKeyPressed(inputHandler::handleKeyPressed);
        terminalCanvas.setOnKeyTyped(inputHandler::handleKeyTyped);

        // Configure the stage and window through CustomTerminalWindow
        customTerminalWindow.configureStage(primaryStage);
    }

    /**
     * Shows the main application window and sets focus on the terminal canvas.
     */
    public void show() {
        primaryStage.show();
        // Ensure that the terminal canvas receives focus after the window is displayed
        Platform.runLater(terminalCanvas::requestFocus);
    }

    /**
     * Returns the TerminalCanvas instance.
     *
     * @return the TerminalCanvas.
     */
    public TerminalCanvas getTerminalCanvas() {
        return terminalCanvas;
    }

    /**
     * Returns the primary Stage.
     *
     * @return the primary Stage.
     */
    public Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Returns the ContentPanel from the custom terminal window.
     *
     * @return the ContentPanel.
     */
    public ContentPanel getContentPanel() {
        return customTerminalWindow.getContentPanel();
    }
}
