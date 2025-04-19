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
import org.msv.vt100.util.DialogHelper;

import java.io.File;
import java.util.Objects;

public class PositionssucheDialog {

    private final Stage dialog;
    private final TerminalApp terminalApp;
    private TextField orderNumbersFileField;
    private TextField terminalDataFileField;
    private TextField firmNumbersField;

    public PositionssucheDialog(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Positionssuche");
        initUI();
    }

    private void initUI() {
        // Header mit Titel und Schließen-Button
        HBox header = new HBox();
        header.getStyleClass().add("dialog-header");

        Label titleLabel = new Label("Positionssuche");
        titleLabel.getStyleClass().add("dialog-header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button("X");
        closeButton.getStyleClass().add("dialog-header-close-button");
        closeButton.setOnAction(e -> dialog.close());

        header.getChildren().addAll(titleLabel, spacer, closeButton);

        // Grid für Eingaben
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // Auftrag-Datei
        Label orderFileLabel = new Label("Auftragsnummern-Datei:");
        orderFileLabel.getStyleClass().add("dialog-label-turquoise");

        orderNumbersFileField = new TextField();
        orderNumbersFileField.getStyleClass().add("dialog-text-field");

        Button browseOrderFileButton = new Button("Auswählen");
        browseOrderFileButton.getStyleClass().add("dialog-button");
        browseOrderFileButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Wählen Sie die Auftragsnummern-Datei");
            File selected = chooser.showOpenDialog(dialog);
            if (selected != null) {
                orderNumbersFileField.setText(selected.getAbsolutePath());
            }
        });

        HBox orderBox = new HBox(5, orderNumbersFileField, browseOrderFileButton);
        orderBox.setAlignment(Pos.CENTER_LEFT);

        // Terminaldaten-Datei
        Label terminalDataLabel = new Label("Terminaldaten-Datei:");
        terminalDataLabel.getStyleClass().add("dialog-label-turquoise");

        terminalDataFileField = new TextField();
        terminalDataFileField.getStyleClass().add("dialog-text-field");

        Button browseTerminalDataButton = new Button("Auswählen");
        browseTerminalDataButton.getStyleClass().add("dialog-button");
        browseTerminalDataButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Wählen Sie den Speicherort für Terminaldaten");
            File selected = chooser.showSaveDialog(dialog);
            if (selected != null) {
                terminalDataFileField.setText(selected.getAbsolutePath());
            }
        });

        HBox terminalBox = new HBox(5, terminalDataFileField, browseTerminalDataButton);
        terminalBox.setAlignment(Pos.CENTER_LEFT);

        // Firmennummern
        Label firmNumbersLabel = new Label("Firmennummern (4-stellig, Komma getrennt):");
        firmNumbersLabel.getStyleClass().add("dialog-label-turquoise");

        firmNumbersField = new TextField();
        firmNumbersField.getStyleClass().add("dialog-text-field");

        // Buttons unten
        Button searchButton = new Button("Suchen");
        searchButton.getStyleClass().add("dialog-button");
        searchButton.setOnAction(e -> startSearch());

        Button cancelButton = new Button("Abbrechen");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(10, searchButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        // Komponenten ins Grid einfügen
        grid.add(orderFileLabel, 0, 0);
        grid.add(orderBox, 1, 0);
        grid.add(terminalDataLabel, 0, 1);
        grid.add(terminalBox, 1, 1);
        grid.add(firmNumbersLabel, 0, 2);
        grid.add(firmNumbersField, 1, 2);
        grid.add(buttonBox, 1, 3);

        // Gesamtlayout
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-dialog");
        root.setTop(header);
        root.setCenter(grid);

        Rectangle clip = new Rectangle(600, 250);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 600, 250);
        scene.setFill(Color.TRANSPARENT);

        // Modular CSS laden
        scene.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/base.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/buttons.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/dialogs.css")).toExternalForm()
        );

        dialog.setScene(scene);
        DialogHelper.centerDialogOnOwner(dialog, terminalApp.getUIController().getPrimaryStage());
        DialogHelper.enableDragging(dialog, header);
    }

    private void startSearch() {
        String orderPath = orderNumbersFileField.getText().trim();
        String terminalPath = terminalDataFileField.getText().trim();
        String firmNumbers = firmNumbersField.getText().trim();

        if (orderPath.isEmpty() || terminalPath.isEmpty() || firmNumbers.isEmpty()) {
            TerminalDialog.showError("Bitte füllen Sie alle Felder aus.", terminalApp.getUIController().getPrimaryStage());
            return;
        }

        String[] firms = firmNumbers.split(",");
        for (String firm : firms) {
            if (!firm.trim().matches("\\d{4}")) {
                TerminalDialog.showError("Firmennummern müssen vierstellige Zahlen sein.", terminalApp.getUIController().getPrimaryStage());
                return;
            }
        }

        org.msv.vt100.OrderAutomation.PositionssucheProcessor processor =
                new org.msv.vt100.OrderAutomation.PositionssucheProcessor(
                        orderPath,
                        terminalPath,
                        firmNumbers,
                        terminalApp,
                        terminalApp.getScreenBuffer(),
                        terminalApp.getCursor()
                );

        dialog.close();

        processor.startSearch(() -> TerminalDialog.showInfo("Positionssuche abgeschlossen.", terminalApp.getUIController().getPrimaryStage()));
    }

    public void show() {
        dialog.setOnShowing(event ->
                DialogHelper.centerDialogOnOwner(dialog, terminalApp.getUIController().getPrimaryStage())
        );
        Platform.runLater(dialog::show);
    }


}
