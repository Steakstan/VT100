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
import java.util.Objects;

public class BearbeitungseinstellungenDialog {

    private final Stage dialog;
    private final TerminalApp terminalApp;
    private ComboBox<String> operationComboBox;
    private TextField filePathField;

    public BearbeitungseinstellungenDialog(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
        this.dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Bearbeitungseinstellungen");
        initUI();
    }

    private void initUI() {
        // Header
        HBox header = new HBox();
        header.getStyleClass().add("dialog-header");

        Label titleLabel = new Label("Bearbeitungseinstellungen");
        titleLabel.getStyleClass().add("dialog-header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button("X");
        closeButton.getStyleClass().add("dialog-header-close-button");
        closeButton.setOnAction(e -> dialog.close());

        header.getChildren().addAll(titleLabel, spacer, closeButton);

        // Inhalt
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

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
        filePathField.getStyleClass().add("dialog-text-field");

        Button browseButton = new Button("Durchsuchen");
        browseButton.getStyleClass().add("dialog-button");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Datei auswählen");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));
            File selected = fileChooser.showOpenDialog(dialog);
            if (selected != null) {
                filePathField.setText(selected.getAbsolutePath());
            }
        });

        HBox fileBox = new HBox(5, filePathField, browseButton);
        fileBox.setAlignment(Pos.CENTER_LEFT);

        Button startButton = new Button("Verarbeitung starten");
        startButton.getStyleClass().add("dialog-button");
        startButton.setOnAction(e -> startProcessing());

        Button cancelButton = new Button("Abbrechen");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10, startButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // Вставка элементов в сетку
        grid.add(operationLabel, 0, 0);
        grid.add(operationComboBox, 1, 0);
        grid.add(fileLabel, 0, 1);
        grid.add(fileBox, 1, 1);
        grid.add(buttonBox, 1, 2);

        // Корневой контейнер
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dialog");
        root.setTop(header);
        root.setCenter(grid);

        Rectangle clip = new Rectangle(500, 200);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 500, 200);
        scene.setFill(Color.TRANSPARENT);

        scene.getStylesheets().addAll(
                getClass().getResource("/org/msv/vt100/ui/styles/base.css").toExternalForm(),
                getClass().getResource("/org/msv/vt100/ui/styles/buttons.css").toExternalForm(),
                getClass().getResource("/org/msv/vt100/ui/styles/dialogs.css").toExternalForm()
        );

        dialog.setScene(scene);
    }

    private void startProcessing() {
        String filePath = filePathField.getText();
        if (filePath == null || filePath.isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "Bitte wählen Sie eine Datei aus.", ButtonType.OK).showAndWait();
            return;
        }

        int choice = operationComboBox.getSelectionModel().getSelectedIndex() + 1;
        dialog.close();

        new Thread(() -> {
            try {
                terminalApp.getFileProcessingService().processFile(choice, filePath);
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Verarbeitung abgeschlossen.", ButtonType.OK).showAndWait();
                    terminalApp.hideProcessingButtons();
                });
            } catch (InterruptedException ex) {
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Verarbeitung gestoppt.", ButtonType.OK).showAndWait();
                    terminalApp.hideProcessingButtons();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.ERROR, "Fehler bei der Verarbeitung: " + ex.getMessage(), ButtonType.OK).showAndWait();
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
