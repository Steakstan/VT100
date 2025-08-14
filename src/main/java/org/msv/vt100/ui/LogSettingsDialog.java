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
    private final TextField deliveryLogPathField;
    private final CheckBox enableDeliveryLoggingCheckBox;

    private static final String PREF_LOG_PATH = "logPath";
    private static final String PREF_LOG_ENABLED = "logEnabled";
    private static final String PREF_DELIVERY_LOG_PATH = "deliveryLogPath";
    private static final String PREF_DELIVERY_LOG_ENABLED = "deliveryLogEnabled";
    private static final String DEFAULT_LOG_PATH = "C:\\Users\\Documents";
    private static final String DEFAULT_DELIVERY_LOG_PATH = "C:\\Users\\Documents";

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

        Label deliveryPathLabel = new Label("Delivery Log Path:");
        deliveryPathLabel.getStyleClass().add("dialog-label-turquoise");

        deliveryLogPathField = new TextField();
        deliveryLogPathField.getStyleClass().add("dialog-text-field");
        deliveryLogPathField.setText(prefs.get(PREF_DELIVERY_LOG_PATH, DEFAULT_DELIVERY_LOG_PATH));

        Button deliveryBrowseButton = new Button("Browse");
        deliveryBrowseButton.getStyleClass().add("dialog-button");
        deliveryBrowseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Delivery Log Directory");
            File dir = chooser.showDialog(dialog);
            if (dir != null) {
                deliveryLogPathField.setText(dir.getAbsolutePath());
            }
        });

        HBox deliveryPathBox = new HBox(5, deliveryLogPathField, deliveryBrowseButton);
        deliveryPathBox.setAlignment(Pos.CENTER_LEFT);

        Label enableDeliveryLabel = new Label("Enable Delivery Logging:");
        enableDeliveryLabel.getStyleClass().add("dialog-label-turquoise");

        enableDeliveryLoggingCheckBox = new CheckBox();
        enableDeliveryLoggingCheckBox.setSelected(prefs.getBoolean(PREF_DELIVERY_LOG_ENABLED, false));

        HBox enableDeliveryBox = new HBox(5, enableDeliveryLabel, enableDeliveryLoggingCheckBox);
        enableDeliveryBox.setAlignment(Pos.CENTER_LEFT);


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
        grid.add(deliveryPathLabel, 0, 2);
        grid.add(deliveryPathBox, 1, 2);
        grid.add(enableDeliveryLabel, 0, 3);
        grid.add(enableDeliveryLoggingCheckBox, 1, 3);
        grid.add(buttonBox, 1, 4);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dialog");
        root.setTop(header);
        root.setCenter(grid);

        Rectangle clip = new Rectangle(580, 260);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 580, 260);
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
        String deliveryLogPath = deliveryLogPathField.getText().trim();
        boolean deliveryLoggingEnabled = enableDeliveryLoggingCheckBox.isSelected();

        prefs.put(PREF_LOG_PATH, logPath);
        prefs.putBoolean(PREF_LOG_ENABLED, loggingEnabled);
        prefs.put(PREF_DELIVERY_LOG_PATH, deliveryLogPath);
        prefs.putBoolean(PREF_DELIVERY_LOG_ENABLED, deliveryLoggingEnabled);

        System.setProperty("LOG_PATH", logPath);
        System.setProperty("DELIVERY_LOG_PATH", deliveryLogPath);

        if (loggingEnabled) {
            terminalApp.enableLogging();
        } else {
            terminalApp.disableLogging();
        }

        if (deliveryLoggingEnabled) {
            terminalApp.enableDeliveryLogging();
        } else {
            terminalApp.disableDeliveryLogging();
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
