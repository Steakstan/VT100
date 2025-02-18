package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.msv.vt100.TerminalApp;

import java.io.File;

public class BearbeitungseinstellungenDialog {

    private final Stage dialog;
    private final TerminalApp terminalApp;
    private ComboBox<String> operationComboBox;
    private TextField filePathField;
    private Button browseButton;
    private Button startProcessingButton;
    private Button cancelButton;

    public BearbeitungseinstellungenDialog(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
        dialog = new Stage();
        // Используем прозрачный стиль, как в LogSettingsDialog
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Bearbeitungseinstellungen");
        initUI();
    }

    private void initUI() {
        // Создание заголовка (header) с названием и кнопкой закрытия
        HBox header = new HBox();
        header.getStyleClass().add("dialog-header");

        Label titleLabel = new Label("Bearbeitungseinstellungen");
        titleLabel.getStyleClass().add("dialog-header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button headerCloseButton = new Button("X");
        headerCloseButton.getStyleClass().add("dialog-header-close-button");
        headerCloseButton.setOnAction(e -> dialog.close());

        header.getChildren().addAll(titleLabel, spacer, headerCloseButton);

        // Основное содержимое – сетка с полями и кнопками
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("dialog-grid");

        Label operationLabel = new Label("Operation:");
        operationLabel.getStyleClass().add("dialog-label-turquoise");

        operationComboBox = new ComboBox<>();
        operationComboBox.getItems().addAll(
                "AB-Verarbeitung",
                "Kommentare verarbeiten",
                "Kommentare für Lagerbestellung verarbeiten",
                "Liefertermine verarbeiten"
        );
        operationComboBox.getSelectionModel().selectFirst();

        Label fileLabel = new Label("Datei auswählen:");
        fileLabel.getStyleClass().add("dialog-label-turquoise");

        filePathField = new TextField();
        filePathField.setPrefWidth(300);
        filePathField.getStyleClass().add("dialog-text-field");

        browseButton = new Button("Durchsuchen");
        browseButton.getStyleClass().add("dialog-button");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));
            fileChooser.setTitle("Datei auswählen");
            File selectedFile = fileChooser.showOpenDialog(dialog);
            if (selectedFile != null) {
                filePathField.setText(selectedFile.getAbsolutePath());
            }
        });

        startProcessingButton = new Button("Verarbeitung starten");
        startProcessingButton.getStyleClass().add("dialog-button");
        startProcessingButton.setOnAction(e -> startProcessing());

        cancelButton = new Button("Abbrechen");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setOnAction(e -> dialog.close());

        grid.add(operationLabel, 0, 0);
        grid.add(operationComboBox, 1, 0);
        grid.add(fileLabel, 0, 1);
        HBox fileBox = new HBox(5, filePathField, browseButton);
        fileBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(fileBox, 1, 1);
        HBox buttonBox = new HBox(10, startProcessingButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        grid.add(buttonBox, 1, 2);

        // Объединяем header и основное содержимое в корневой контейнер
        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(grid);
        root.getStyleClass().add("root-dialog");
        // Задаём прозрачный фон
        root.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));

        // Применяем клип с закруглёнными углами
        Rectangle clip = new Rectangle(500, 200);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 500, 200);
        scene.setFill(Color.TRANSPARENT);
        // Подключаем CSS-стили (убедитесь, что путь корректный)
        scene.getStylesheets().add(getClass().getResource("/org/msv/vt100/ui/styles.css").toExternalForm());
        dialog.setScene(scene);
    }

    private void startProcessing() {
        String filePath = filePathField.getText();
        if (filePath == null || filePath.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Bitte wählen Sie eine Datei aus.", ButtonType.OK);
            alert.showAndWait();
            return;
        }
        int choice = operationComboBox.getSelectionModel().getSelectedIndex() + 1;

        // Закрываем диалог перед запуском обработки
        dialog.close();

        // Запускаем обработку в отдельном потоке
        new Thread(() -> {
            try {
                terminalApp.getFileProcessingService().processFile(choice, filePath);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Verarbeitung abgeschlossen.", ButtonType.OK);
                    alert.showAndWait();
                    terminalApp.hideProcessingButtons();
                });
            } catch (InterruptedException ex) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION, "Verarbeitung gestoppt.", ButtonType.OK);
                    alert.showAndWait();
                    terminalApp.hideProcessingButtons();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler bei der Verarbeitung: " + ex.getMessage(), ButtonType.OK);
                    alert.showAndWait();
                    terminalApp.hideProcessingButtons();
                });
            }
        }).start();

        terminalApp.showProcessingButtons();
    }

    public void show() {
        dialog.showAndWait();
    }
}
