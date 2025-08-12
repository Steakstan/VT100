package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.util.DialogHelper;

import java.io.File;
import java.util.Objects;
import java.util.prefs.Preferences;

public class LogSettingsDialog {

    private final Stage dialog;
    private final TerminalApp terminalApp;
    private final Preferences prefs;
    private final TextField logPathField;
    private final CheckBox enableLoggingCheckBox;

    private static final String PREF_LOG_PATH = "logPath";
    private static final String PREF_LOG_ENABLED = "logEnabled";
    private static final String DEFAULT_LOG_PATH = "C:\\Users\\Documents";

    public LogSettingsDialog(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
        this.prefs = Preferences.userNodeForPackage(LogSettingsDialog.class);
        this.dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Log Einstellungen");

        HBox header = new HBox();
        header.getStyleClass().add("dialog-header");

        Label titleLabel = new Label("Log Einstellungen");
        titleLabel.getStyleClass().add("dialog-header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button("X");
        closeButton.getStyleClass().add("dialog-header-close-button");
        closeButton.setOnAction(e -> dialog.close());

        header.getChildren().addAll(titleLabel, spacer, closeButton);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        Label pathLabel = new Label("Log Path:");
        pathLabel.getStyleClass().add("dialog-label-turquoise");

        logPathField = new TextField();
        logPathField.getStyleClass().add("dialog-text-field");
        logPathField.setText(prefs.get(PREF_LOG_PATH, DEFAULT_LOG_PATH));

        Button browseButton = new Button("Browse");
        browseButton.getStyleClass().add("dialog-button");
        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Log Directory");
            File dir = chooser.showDialog(dialog);
            if (dir != null) {
                logPathField.setText(dir.getAbsolutePath());
            }
        });

        HBox pathBox = new HBox(5, logPathField, browseButton);
        pathBox.setAlignment(Pos.CENTER_LEFT);

        Label enableLabel = new Label("Enable Logging:");
        enableLabel.getStyleClass().add("dialog-label-turquoise");

        enableLoggingCheckBox = new CheckBox();
        enableLoggingCheckBox.setSelected(prefs.getBoolean(PREF_LOG_ENABLED, false));

        HBox enableBox = new HBox(5, enableLabel, enableLoggingCheckBox);
        enableBox.setAlignment(Pos.CENTER_LEFT);

        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("dialog-button");
        saveButton.setOnAction(e -> saveSettings());

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        grid.add(pathLabel, 0, 0);
        grid.add(pathBox, 1, 0);
        grid.add(enableLabel, 0, 1);
        grid.add(enableLoggingCheckBox, 1, 1);
        grid.add(buttonBox, 1, 2);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dialog");
        root.setTop(header);
        root.setCenter(grid);

        Rectangle clip = new Rectangle(540, 180);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 540, 180);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/base.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/buttons.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/dialogs.css")).toExternalForm()
        );

        dialog.setScene(scene);
        DialogHelper.centerDialogOnOwner(dialog, terminalApp.getUIController().getPrimaryStage());
        DialogHelper.enableDragging(dialog, header);
    }

    private void saveSettings() {
        String logPath = logPathField.getText().trim();
        boolean loggingEnabled = enableLoggingCheckBox.isSelected();

        prefs.put(PREF_LOG_PATH, logPath);
        prefs.putBoolean(PREF_LOG_ENABLED, loggingEnabled);

        System.setProperty("LOG_PATH", logPath);

        if (loggingEnabled) {
            terminalApp.enableLogging();
        } else {
            terminalApp.disableLogging();
        }

        dialog.close();
    }

    public void show() {
        dialog.setOnShowing(event ->
                DialogHelper.centerDialogOnOwner(dialog, terminalApp.getUIController().getPrimaryStage())
        );
        Platform.runLater(dialog::show);
    }


}
