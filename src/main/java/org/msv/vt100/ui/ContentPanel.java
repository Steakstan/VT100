package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.msv.vt100.TerminalApp;

public class ContentPanel extends BorderPane {

    private final HBox processingButtons;
    private final TerminalApp terminalApp;

    private final Button pauseButton;
    private final Button stopButton;
    private volatile boolean paused = false;
    private volatile boolean visible = false;

    public ContentPanel(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;

        processingButtons = new HBox(8);
        processingButtons.getStyleClass().add("processing-button-bar");
        processingButtons.setAlignment(Pos.CENTER_LEFT);

        pauseButton = new Button("Pause");
        pauseButton.getStyleClass().addAll("dialog-button", "processing-button");
        pauseButton.setOnAction(e -> togglePause());

        stopButton = new Button("Stop");
        stopButton.getStyleClass().addAll("dialog-button", "processing-button");
        stopButton.setOnAction(e -> stopProcessing());

        processingButtons.getChildren().addAll(pauseButton, stopButton);
    }

    private void togglePause() {
        if (!visible) return;
        if (!paused) {
            terminalApp.pauseProcessing();
            paused = true;
            pauseButton.setText("Fortsetzen");
        } else {
            terminalApp.resumeProcessing();
            paused = false;
            pauseButton.setText("Pause");
        }
    }

    private void stopProcessing() {
        if (!visible) return;
        terminalApp.stopProcessing();
        hideProcessingButtons();
    }

    public void showProcessingButtons() {
        Platform.runLater(() -> {
            if (visible) return;
            paused = false;
            pauseButton.setText("Pause");
            pauseButton.setDisable(false);
            stopButton.setDisable(false);

            setBottom(processingButtons);
            visible = true;
        });
    }

    public void hideProcessingButtons() {
        Platform.runLater(() -> {
            if (!visible) return;
            setBottom(null);
            visible = false;

            paused = false;
            pauseButton.setText("Pause");
        });
    }
}
