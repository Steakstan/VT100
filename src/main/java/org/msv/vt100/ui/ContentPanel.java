package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import org.msv.vt100.TerminalApp;

public class ContentPanel extends BorderPane {

    private final HBox processingButtons;
    private final TerminalApp terminalApp;

    public ContentPanel( TerminalApp terminalApp) {
        this.terminalApp = terminalApp;

        processingButtons = new HBox();
        processingButtons.getStyleClass().add("processing-button-bar");

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.TOP_LEFT);
        this.setTop(topBar);
    }

    private Button createPauseButton() {
        Button button = new Button("Pause");
        button.getStyleClass().addAll("dialog-button", "processing-button");
        button.setOnAction(e -> togglePause(button));
        return button;
    }

    private Button createStopButton() {
        Button button = new Button("Stop");
        button.getStyleClass().addAll("dialog-button", "processing-button");
        button.setOnAction(e -> stopProcessing());
        return button;
    }

    private void togglePause(Button pauseButton) {
        if ("Pause".equals(pauseButton.getText())) {
            terminalApp.pauseProcessing();
            pauseButton.setText("Fortsetzen");
        } else {
            terminalApp.resumeProcessing();
            pauseButton.setText("Pause");
        }
    }

    private void stopProcessing() {
        terminalApp.stopProcessing();
        terminalApp.getProcessingThread().interrupt();
        hideProcessingButtons();
    }

    public void showProcessingButtons() {
        Platform.runLater(() -> {
            processingButtons.getChildren().clear();
            processingButtons.getChildren().addAll(createPauseButton(), createStopButton());
            this.setBottom(processingButtons);
        });
    }

    public void hideProcessingButtons() {
        Platform.runLater(() -> {
            processingButtons.getChildren().clear();
            this.setBottom(null);
        });
    }
}
