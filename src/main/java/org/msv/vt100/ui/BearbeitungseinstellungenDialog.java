package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.util.DialogHelper;

import java.io.File;
import java.util.Objects;
import java.util.prefs.Preferences;

public class BearbeitungseinstellungenDialog {

    private static final String PREF_LAST_DIR = "be_last_dir";
    private static final int MAX_COMMENT_LEN = 49;
    private static final String DEFAULT_COMMENT =
            "DEM HST NACH WIRD DIE WARE IN KW ** ZUGESTELLT";

    // Значения операций — замените на ваши реальные, если они другие
    private static final int OP_DELIVERY_AND_AB = 4;
    private static final int OP_COMMENTS_ONLY = 2;

    private final Stage dialog;
    private final TerminalApp terminalApp;
    private final Preferences prefs = Preferences.userNodeForPackage(BearbeitungseinstellungenDialog.class);

    private ComboBox<String> operationComboBox;
    private TextField filePathField;
    private CheckBox commentCheckBox;
    private TextField commentField;

    public BearbeitungseinstellungenDialog(TerminalApp terminalApp) {
        this.terminalApp = Objects.requireNonNull(terminalApp, "terminalApp");
        this.dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Bearbeitungseinstellungen");
        initUI();
    }

    private void initUI() {
        HBox header = DialogHelper.createDialogHeader("Bearbeitungseinstellungen", dialog::close);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // Operation
        Label operationLabel = new Label("Operation:");
        operationLabel.getStyleClass().add("dialog-label-turquoise");

        operationComboBox = new ComboBox<>();
        operationComboBox.getItems().addAll(
                "Liefertermine und AB-Nummern",
                "Kommentare verarbeiten"
        );
        operationComboBox.getSelectionModel().select(0);
        operationComboBox.setPrefWidth(300);
        operationComboBox.setPrefHeight(30);
        operationComboBox.getStyleClass().add("custom-combobox");

        // Выпадающее — белый текст
        operationComboBox.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setTextFill(Color.WHITE);
            }
        });
        operationComboBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setTextFill(Color.WHITE);
            }
        });

        // Datei-Auswahl
        Label fileLabel = new Label("Datei auswählen:");
        fileLabel.getStyleClass().add("dialog-label-turquoise");

        filePathField = new TextField();
        filePathField.getStyleClass().add("dialog-text-field");

        Button browseButton = new Button("Durchsuchen");
        browseButton.getStyleClass().add("dialog-button");
        browseButton.setOnAction(e -> chooseFile());

        HBox fileBox = new HBox(5, filePathField, browseButton);
        fileBox.setAlignment(Pos.CENTER_LEFT);

        // Kommentar
        commentCheckBox = new CheckBox("Kommentar schreiben");
        commentCheckBox.setSelected(false);
        commentCheckBox.getStyleClass().add("dialog-label-turquoise");

        Label commentLabel = new Label("Kommentartext:");
        commentLabel.getStyleClass().add("dialog-label-turquoise");

        commentField = new TextField();
        commentField.setPromptText("Kommentar eingeben...");
        commentField.setDisable(true);
        commentField.setPrefWidth(300);
        commentField.setPrefHeight(30);
        commentField.getStyleClass().add("dialog-text-field");

        // Ограничение длины коммента — через TextFormatter (максимально стабильно)
        commentField.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            return newText.length() <= MAX_COMMENT_LEN ? change : null;
        }));

        Tooltip commentTooltip = new Tooltip(
                "Maximal " + MAX_COMMENT_LEN + " Zeichen erlaubt.\n" +
                        "\"**\" steht für das Lieferdatum aus der Tabelle."
        );
        commentTooltip.getStyleClass().add("custom-tooltip");

        Label questionIcon = new Label("?");
        questionIcon.getStyleClass().add("tooltip-icon");
        questionIcon.setAlignment(Pos.CENTER);
        Tooltip.install(questionIcon, commentTooltip);

        HBox commentInputBox = new HBox(5, commentField, questionIcon);
        commentInputBox.setAlignment(Pos.CENTER_LEFT);

        commentCheckBox.setOnAction(e -> {
            boolean active = commentCheckBox.isSelected();
            commentField.setDisable(!active);
            questionIcon.setDisable(!active);
            if (active && commentField.getText().isBlank()) {
                commentField.setText(DEFAULT_COMMENT);
            }
        });

        VBox commentBox = new VBox(5, commentCheckBox, commentLabel, commentInputBox);
        commentBox.getStyleClass().add("comment-box-background");
        commentBox.setAlignment(Pos.CENTER_LEFT);

        // Buttons
        Button startButton = new Button("Verarbeitung starten");
        startButton.getStyleClass().add("dialog-button");
        startButton.setOnAction(e -> startProcessing());

        Button cancelButton = new Button("Abbrechen");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setOnAction(e -> dialog.close());

        HBox buttonBox = DialogHelper.createDialogFooter(startButton, cancelButton);

        // Layout
        grid.add(operationLabel, 0, 0);
        grid.add(operationComboBox, 1, 0);
        grid.add(fileLabel, 0, 1);
        grid.add(fileBox, 1, 1);
        grid.add(commentBox, 1, 2);
        grid.add(buttonBox, 1, 3);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dialog");
        root.setTop(header);
        root.setCenter(grid);

        Rectangle clip = new Rectangle(530, 300);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 530, 300);
        scene.setFill(Color.TRANSPARENT);
        // Стили подключаем «мягко»
        addStylesheetIfExists(scene, "/org/msv/vt100/ui/styles/base.css");
        addStylesheetIfExists(scene, "/org/msv/vt100/ui/styles/buttons.css");
        addStylesheetIfExists(scene, "/org/msv/vt100/ui/styles/dialogs.css");
        addStylesheetIfExists(scene, "/org/msv/vt100/ui/styles/combobox.css");
        addStylesheetIfExists(scene, "/org/msv/vt100/ui/styles/tooltip.css");

        dialog.setScene(scene);
        DialogHelper.centerDialogOnOwner(dialog, terminalApp.getUIController().getPrimaryStage());
        DialogHelper.enableDragging(dialog, header);

        Platform.runLater(() -> commentTooltip.setStyle("-fx-show-delay: 100ms;"));
    }

    private void chooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Datei auswählen");

        // Начальная директория — из prefs
        String lastDir = prefs.get(PREF_LAST_DIR, null);
        if (lastDir != null) {
            File dir = new File(lastDir);
            if (dir.isDirectory()) {
                fileChooser.setInitialDirectory(dir);
            }
        }

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Alle Dateien", "*.*")
        );

        File selected = fileChooser.showOpenDialog(dialog);
        if (selected != null) {
            filePathField.setText(selected.getAbsolutePath());
            // Запомним директорию
            File parent = selected.getParentFile();
            if (parent != null && parent.isDirectory()) {
                prefs.put(PREF_LAST_DIR, parent.getAbsolutePath());
            }
        }
    }

    private void startProcessing() {
        String filePath = filePathField.getText();
        if (filePath == null || filePath.isBlank()) {
            showError("Bitte wählen Sie eine Datei aus.");
            return;
        }
        File f = new File(filePath);
        if (!f.isFile()) {
            showError("Die ausgewählte Datei existiert nicht oder ist nicht lesbar.");
            return;
        }

        final int choice = operationComboBox.getSelectionModel().getSelectedIndex() == 0
                ? OP_DELIVERY_AND_AB
                : OP_COMMENTS_ONLY;

        final boolean shouldWriteComment = commentCheckBox.isSelected();
        final String commentText = shouldWriteComment ? commentField.getText().trim() : "";

        // Применяем настройки в приложении (в FX-потоке)
        terminalApp.setShouldWriteComment(shouldWriteComment);
        terminalApp.setCommentText(commentText);

        // Запускаем фоновую обработку
        dialog.close();
        terminalApp.showProcessingButtons();

        Thread worker = new Thread(() -> {
            try {
                terminalApp.getFileProcessingService().processFile(choice, filePath);
                Platform.runLater(() -> {
                    TerminalDialog.showInfo("Verarbeitung abgeschlossen.", terminalApp.getUIController().getPrimaryStage());
                    terminalApp.hideProcessingButtons();
                });
            } catch (InterruptedException ie) {
                // штатная остановка
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Verarbeitung gestoppt.", ButtonType.OK).showAndWait();
                    terminalApp.hideProcessingButtons();
                });
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    TerminalDialog.showError("Fehler bei der Verarbeitung:\n" + ex.getMessage(),
                            terminalApp.getUIController().getPrimaryStage());
                    terminalApp.hideProcessingButtons();
                });
            }
        }, "file-processing");
        worker.setDaemon(true);
        terminalApp.setProcessingThread(worker);
        worker.start();
    }

    private void addStylesheetIfExists(Scene scene, String path) {
        var url = getClass().getResource(path);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        } else {
            System.err.println("⚠️ Stylesheet nicht gefunden: " + path);
        }
    }

    private void showError(String msg) {
        TerminalDialog.showError(msg, terminalApp.getUIController().getPrimaryStage());
    }

    public void show() {
        dialog.setOnShowing(event ->
                DialogHelper.centerDialogOnOwner(dialog, terminalApp.getUIController().getPrimaryStage())
        );
        Platform.runLater(dialog::show);
    }
}
