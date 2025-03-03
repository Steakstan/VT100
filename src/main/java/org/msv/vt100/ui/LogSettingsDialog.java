package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.msv.vt100.TerminalApp;

import java.io.File;
import java.util.prefs.Preferences;

public class LogSettingsDialog {

    private final Stage dialog;
    private final TerminalApp terminalApp;
    private final Preferences prefs;
    private final TextField logPathField;
    private final CheckBox enableLoggingCheckBox;
    private final Button browseButton;
    private final Button saveButton;
    private final Button cancelButton;

    private static final String PREF_LOG_PATH = "logPath";
    private static final String PREF_LOG_ENABLED = "logEnabled";
    private static final String DEFAULT_LOG_PATH = "C:\\Users\\Documents";

    /**
     * Constructs a LogSettingsDialog for configuring logging settings.
     *
     * @param terminalApp the main TerminalApp instance.
     */
    public LogSettingsDialog(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
        prefs = Preferences.userNodeForPackage(LogSettingsDialog.class);

        // Create a dialog with a transparent style
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Log Einstellungen");

        // Dialog header with title and close button – similar to LogSettingsDialog style
        HBox header = new HBox();
        header.getStyleClass().add("dialog-header");

        Label titleLabel = new Label("Log Einstellungen");
        titleLabel.getStyleClass().add("dialog-header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button headerCloseButton = new Button("X");
        headerCloseButton.getStyleClass().add("dialog-header-close-button");
        headerCloseButton.setOnAction(e -> dialog.close());

        header.getChildren().addAll(titleLabel, spacer, headerCloseButton);

        // Main content – GridPane layout
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");

        // Label "Protokollpfad:" in turquoise
        Label pathLabel = new Label("Protokollpfad:");
        pathLabel.getStyleClass().add("dialog-label-turquoise");

        // Text field for log path
        logPathField = new TextField();
        logPathField.getStyleClass().add("dialog-text-field");
        String storedPath = prefs.get(PREF_LOG_PATH, DEFAULT_LOG_PATH);
        logPathField.setText(storedPath);

        // "Durchsuchen" button for browsing directories
        browseButton = new Button("Durchsuchen");
        browseButton.getStyleClass().add("dialog-button");
        browseButton.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Wählen Sie ein Protokollverzeichnis aus");
            File selectedDirectory = directoryChooser.showDialog(dialog);
            if (selectedDirectory != null) {
                logPathField.setText(selectedDirectory.getAbsolutePath());
            }
        });

        HBox pathBox = new HBox(5, logPathField, browseButton);
        pathBox.setAlignment(Pos.CENTER_LEFT);

        // Label "Protokollierung aktivieren:" in turquoise
        Label enableLabel = new Label("Protokollierung aktivieren:");
        enableLabel.getStyleClass().add("dialog-label-turquoise");

        enableLoggingCheckBox = new CheckBox();
        // Optionally style the checkbox; here text color is set to white.
        enableLoggingCheckBox.setStyle("-fx-text-fill: white;");
        boolean isEnabled = prefs.getBoolean(PREF_LOG_ENABLED, false);
        enableLoggingCheckBox.setSelected(isEnabled);

        HBox enableBox = new HBox(5, enableLabel, enableLoggingCheckBox);
        enableBox.setAlignment(Pos.CENTER_LEFT);

        // Save and Cancel buttons
        saveButton = new Button("Speichern");
        saveButton.getStyleClass().add("dialog-button");
        saveButton.setOnAction(e -> saveSettings());

        cancelButton = new Button("Abbrechen");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // Layout of elements in the GridPane
        grid.add(pathLabel, 0, 0);
        grid.add(pathBox, 1, 0);
        grid.add(enableLabel, 0, 1);
        grid.add(enableLoggingCheckBox, 1, 1);
        grid.add(buttonBox, 1, 2);

        // Root container of the dialog
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dialog");
        root.setTop(header);
        root.setCenter(grid);
        root.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));

        // Apply a clip with rounded corners for effect
        Rectangle clip = new Rectangle(540, 150);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 540, 150);
        scene.setFill(Color.TRANSPARENT);

        // Load CSS styles (path may vary depending on your project structure)
        scene.getStylesheets().add(getClass().getResource("/org/msv/vt100/ui/styles.css").toExternalForm());

        dialog.setScene(scene);
    }

    /**
     * Saves the logging settings and applies them to the TerminalApp.
     */
    private void saveSettings() {
        String logPath = logPathField.getText().trim();
        boolean logEnabled = enableLoggingCheckBox.isSelected();

        prefs.put(PREF_LOG_PATH, logPath);
        prefs.putBoolean(PREF_LOG_ENABLED, logEnabled);

        if (logEnabled) {
            terminalApp.enableLogging();
        } else {
            terminalApp.disableLogging();
        }
        dialog.close();
    }

    /**
     * Displays the dialog and waits for user input.
     */
    public void show() {
        Platform.runLater(() -> dialog.showAndWait());
    }
}
