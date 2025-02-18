package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.msv.vt100.TerminalApp;

public class ContentPanel extends BorderPane {

    private HBox processingButtons; // Контейнер для кнопок Pause и Stop
    private final Stage primaryStage;
    private final TerminalApp terminalApp;

    public ContentPanel(Stage primaryStage, TerminalApp terminalApp) {
        this.primaryStage = primaryStage;
        this.terminalApp = terminalApp;

        // Изначально контейнер для обработки пустой
        processingButtons = new HBox(10);
        processingButtons.setAlignment(Pos.TOP_LEFT);
        processingButtons.setPadding(new Insets(5, 10, 5, 10));

        // Верхняя панель содержит только Log кнопку (можно расширить по необходимости)
        HBox topBar = new HBox();
        topBar.setPadding(new Insets(5));
        topBar.setAlignment(Pos.TOP_LEFT);

        // Располагаем верхнюю панель сверху, а нижнюю – для кнопок обработки
        this.setTop(topBar);
    }


    // Методы для создания кнопок Pause и Stop
    private Button createPauseButton() {
        Button button = new Button("Pause");
        button.getStyleClass().add("rounded-button");
        button.setOnAction(e -> pauseOrResumeProcessing(button));
        return button;
    }

    private Button createStopButton() {
        Button button = new Button("Stop");
        button.getStyleClass().add("rounded-button");
        button.setOnAction(e -> stopProcessing());
        return button;
    }

    private void pauseOrResumeProcessing(Button pauseButton) {
        if (pauseButton.getText().equals("Pause")) {
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

    // Эти методы будут вызываться из TerminalApp при старте/окончании обработки
    public void showProcessingButtons() {
        Platform.runLater(() -> {
            processingButtons.getChildren().clear();
            processingButtons.getChildren().addAll(createPauseButton(), createStopButton());
            // Устанавливаем фиксированную высоту для кнопок (40 пикселей)
            processingButtons.setMinHeight(60);
            processingButtons.setPrefHeight(60);
            // Устанавливаем отступы: 10 пикселей сверху, снизу и по бокам
            BorderPane.setMargin(processingButtons, new Insets(-10, 5, 10, 10));
            // Центрируем кнопки
            BorderPane.setAlignment(processingButtons, Pos.CENTER);
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
