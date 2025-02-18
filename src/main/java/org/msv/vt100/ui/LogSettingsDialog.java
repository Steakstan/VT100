package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
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

    public LogSettingsDialog(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
        prefs = Preferences.userNodeForPackage(LogSettingsDialog.class);

        // Создаем диалог с прозрачным стилем
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Log Einstellungen");

        // Заголовок диалога
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

        // Основное содержимое – GridPane
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");

        // Метка "Log Path:" – бирюзовая
        Label pathLabel = new Label("Log Path:");
        pathLabel.getStyleClass().add("dialog-label-turquoise");

        // Поле ввода лог-пути
        logPathField = new TextField();
        logPathField.getStyleClass().add("dialog-text-field");
        String storedPath = prefs.get(PREF_LOG_PATH, DEFAULT_LOG_PATH);
        logPathField.setText(storedPath);

        // Кнопка Browse
        browseButton = new Button("Browse");
        browseButton.getStyleClass().add("dialog-button");
        browseButton.setOnAction(e -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle("Select Log Directory");
            File selectedDirectory = directoryChooser.showDialog(dialog);
            if (selectedDirectory != null) {
                logPathField.setText(selectedDirectory.getAbsolutePath());
            }
        });

        HBox pathBox = new HBox(5, logPathField, browseButton);
        pathBox.setAlignment(Pos.CENTER_LEFT);

        // Метка "Enable Logging:" – бирюзовая
        Label enableLabel = new Label("Enable Logging:");
        enableLabel.getStyleClass().add("dialog-label-turquoise");

        enableLoggingCheckBox = new CheckBox();
        // Для чекбокса можно оставить стиль по умолчанию или задать при необходимости
        enableLoggingCheckBox.setStyle("-fx-text-fill: white;");
        boolean isEnabled = prefs.getBoolean(PREF_LOG_ENABLED, false);
        enableLoggingCheckBox.setSelected(isEnabled);

        HBox enableBox = new HBox(5, enableLabel, enableLoggingCheckBox);
        enableBox.setAlignment(Pos.CENTER_LEFT);

        // Кнопки Save и Cancel
        saveButton = new Button("Save");
        saveButton.getStyleClass().add("dialog-button");
        saveButton.setOnAction(e -> saveSettings());

        cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // Расположение элементов в GridPane
        grid.add(pathLabel, 0, 0);
        grid.add(pathBox, 1, 0);
        grid.add(enableLabel, 0, 1);
        grid.add(enableLoggingCheckBox, 1, 1);
        grid.add(buttonBox, 1, 2);

        // Корневой контейнер диалога
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dialog");
        root.setTop(header);
        root.setCenter(grid);
        root.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));

        // Клип с закругленными углами для эффекта
        Rectangle clip = new Rectangle(540, 150);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 540, 150);
        scene.setFill(Color.TRANSPARENT);

        // Подключаем CSS-файл (путь зависит от структуры проекта)
        scene.getStylesheets().add(getClass().getResource("/org/msv/vt100/ui/styles.css").toExternalForm());

        dialog.setScene(scene);
    }

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

    public void show() {
        Platform.runLater(() -> dialog.showAndWait());
    }
}
