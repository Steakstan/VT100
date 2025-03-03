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

/**
 * BearbeitungseinstellungenDialog is a dialog for configuring processing settings.
 * It allows the user to select an operation and a file to process.
 * User-visible texts (labels, button captions, etc.) remain in German.
 */

public class BearbeitungseinstellungenDialog {

    private final Stage dialog;
    private final TerminalApp terminalApp;
    private ComboBox<String> operationComboBox;
    private TextField filePathField;

    public BearbeitungseinstellungenDialog(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Bearbeitungseinstellungen");
        initUI();
    }

    /**
     * Initializes the user interface for the dialog.
     */
    private void initUI() {
        // Create header with title and close button
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

        // Create main content using a GridPane
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("dialog-grid");

        // Operation label and ComboBox
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

        // File selection label and field
        Label fileLabel = new Label("Datei auswählen:");
        fileLabel.getStyleClass().add("dialog-label-turquoise");

        filePathField = new TextField();
        filePathField.setPrefWidth(300);
        filePathField.getStyleClass().add("dialog-text-field");

        // Browse button for file selection
        Button browseButton = new Button("Durchsuchen");
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

        HBox fileBox = new HBox(5, filePathField, browseButton);
        fileBox.setAlignment(Pos.CENTER_LEFT);

        // Create Start Processing and Cancel buttons
        Button startProcessingButton = new Button("Verarbeitung starten");
        startProcessingButton.getStyleClass().add("dialog-button");
        startProcessingButton.setOnAction(e -> startProcessing());

        Button cancelButton = new Button("Abbrechen");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10, startProcessingButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // Add elements to the grid
        grid.add(operationLabel, 0, 0);
        grid.add(operationComboBox, 1, 0);
        grid.add(fileLabel, 0, 1);
        grid.add(fileBox, 1, 1);
        grid.add(buttonBox, 1, 2);

        // Assemble the dialog's root container
        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(grid);
        root.getStyleClass().add("root-dialog");
        root.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));

        // Apply a clip with rounded corners for effect
        Rectangle clip = new Rectangle(500, 200);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 500, 200);
        scene.setFill(Color.TRANSPARENT);
        // Connect the CSS stylesheet (ensure the path is correct)
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles.css")).toExternalForm());

        dialog.setScene(scene);
    }

    /**
     * Starts the file processing operation.
     */
    private void startProcessing() {
        String filePath = filePathField.getText();
        if (filePath == null || filePath.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Bitte wählen Sie eine Datei aus.", ButtonType.OK);
            alert.showAndWait();
            return;
        }
        int choice = operationComboBox.getSelectionModel().getSelectedIndex() + 1;

        // Close the dialog before starting processing
        dialog.close();

        // Run processing in a separate thread
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

    /**
     * Displays the dialog and waits for user input.
     */
    public void show() {
        dialog.showAndWait();
    }
}
