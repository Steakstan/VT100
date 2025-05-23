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

public class BearbeitungseinstellungenDialog {

    private final Stage dialog;
    private final TerminalApp terminalApp;
    private ComboBox<String> operationComboBox;
    private TextField filePathField;
    private CheckBox commentCheckBox;
    private TextField commentField;

    public BearbeitungseinstellungenDialog(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
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

        // Operation-Auswahl
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

        // Weißes Dropdown erzwingen
        operationComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setTextFill(Color.WHITE);
            }
        });
        operationComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
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

        // Kommentar-Einstellungen
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

        commentField.addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (commentField.getText().length() >= 49) e.consume();
        });

        Tooltip commentTooltip = new Tooltip("Maximal 49 Zeichen erlaubt.\n\"**\" steht für das Lieferdatum aus der Tabelle.");
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
            if (active && (commentField.getText().isBlank())) {
                commentField.setText("DEM HST NACH WIRD DIE WARE IN KW ** ZUGESTELLT");
            }
        });

        VBox commentBox = new VBox(5, commentCheckBox, commentLabel, commentInputBox);
        commentBox.getStyleClass().add("comment-box-background");
        commentBox.setAlignment(Pos.CENTER_LEFT);

        // Buttons
        Button startButton = new Button("Verarbeitung starten");
        startButton.setOnAction(e -> startProcessing());

        Button cancelButton = new Button("Abbrechen");
        cancelButton.setOnAction(e -> dialog.close());

        HBox buttonBox = DialogHelper.createDialogFooter(startButton, cancelButton);

        // Komponenten ins Grid setzen
        grid.add(operationLabel, 0, 0);
        grid.add(operationComboBox, 1, 0);
        grid.add(fileLabel, 0, 1);
        grid.add(fileBox, 1, 1);
        grid.add(commentBox, 1, 2);
        grid.add(buttonBox, 1, 3);

        // Root-Setup
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
        scene.getStylesheets().addAll(
                safeStylesheet("/org/msv/vt100/ui/styles/base.css"),
                safeStylesheet("/org/msv/vt100/ui/styles/buttons.css"),
                safeStylesheet("/org/msv/vt100/ui/styles/dialogs.css"),
                safeStylesheet("/org/msv/vt100/ui/styles/combobox.css"),
                safeStylesheet("/org/msv/vt100/ui/styles/tooltip.css")
        );

        dialog.setScene(scene);
        DialogHelper.centerDialogOnOwner(dialog, terminalApp.getUIController().getPrimaryStage());
        DialogHelper.enableDragging(dialog, header);

        Platform.runLater(() -> commentTooltip.setStyle("-fx-show-delay: 100ms;"));
    }


    private String safeStylesheet(String path) {
        var url = getClass().getResource(path);
        if (url == null) {
            System.err.println("⚠️ Stylesheet nicht gefunden: " + path);
            return null;
        }
        return url.toExternalForm();
    }

    private void startProcessing() {
        String filePath = filePathField.getText();
        if (filePath == null || filePath.isEmpty()) {
            TerminalDialog.showError("Bitte wählen Sie eine Datei aus.", terminalApp.getUIController().getPrimaryStage());
            return;
        }

        int choice = operationComboBox.getSelectionModel().getSelectedIndex() == 0 ? 4 : 2;
        String commentText = commentField.getText();
        boolean shouldWriteComment = commentCheckBox.isSelected();

        dialog.close();

        new Thread(() -> {
            try {
                terminalApp.setCommentText(commentText);
                terminalApp.setShouldWriteComment(shouldWriteComment);
                terminalApp.getFileProcessingService().processFile(choice, filePath);
            } catch (InterruptedException ex) {
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Verarbeitung gestoppt.", ButtonType.OK).showAndWait();
                    terminalApp.hideProcessingButtons();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    TerminalDialog.showError("Bitte wählen Sie eine Datei aus.", terminalApp.getUIController().getPrimaryStage());
                    terminalApp.hideProcessingButtons();
                });
            }
        }).start();

        terminalApp.showProcessingButtons();
    }

    public void show() {
        dialog.setOnShowing(event ->
                DialogHelper.centerDialogOnOwner(dialog, terminalApp.getUIController().getPrimaryStage())
        );
        Platform.runLater(dialog::show);
    }



}
