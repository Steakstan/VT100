package org.msv.vt100.ui;

import javafx.geometry.Insets; import javafx.geometry.Pos; import javafx.scene.Scene; import javafx.scene.control.Button; import javafx.scene.control.Label; import javafx.scene.layout.*; import javafx.scene.paint.Color; import javafx.scene.shape.Rectangle; import javafx.stage.Modality; import javafx.stage.Stage; import javafx.stage.StageStyle;
import org.msv.vt100.util.DialogHelper;

import java.util.Objects;

public class TerminalDialog {
    public enum DialogType {
        ERROR(Color.rgb(153, 0, 0), "Fehler"),
        INFO(Color.rgb(0, 70, 140), "Information"),
        WARNING(Color.rgb(204, 102, 0), "Warnung"),
        CONFIRM(Color.rgb(0, 100, 0), "Bestätigung");

        private final Color headerColor;
        private final String title;

        DialogType(Color headerColor, String title) {
            this.headerColor = headerColor;
            this.title = title;
        }

        public Color getHeaderColor() {
            return headerColor;
        }

        public String getTitle() {
            return title;
        }
    }

    public static void show(DialogType type, String message, Stage ownerStage) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle(type.getTitle());

        // Header
        HBox header = new HBox();
        header.setPadding(new Insets(10));
        header.setBackground(new Background(new BackgroundFill(type.getHeaderColor(), CornerRadii.EMPTY, Insets.EMPTY)));
        Label titleLabel = new Label(type.getTitle());
        titleLabel.getStyleClass().add("dialog-header-title");
        titleLabel.setTextFill(Color.WHITE);
        header.getChildren().add(titleLabel);

        // Message
        Label label = new Label(message);
        label.getStyleClass().add("dialog-label-turquoise");
        label.setWrapText(true);
        label.setMaxWidth(450);

        // OK Button
        Button okButton = new Button("OK");
        okButton.getStyleClass().add("dialog-button");
        okButton.setPrefWidth(100);
        okButton.setMinHeight(32);
        okButton.setOnAction(e -> dialog.close());

        HBox buttonBox = new HBox(okButton);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        VBox content = new VBox(10, label, buttonBox);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_LEFT);
        content.getStyleClass().add("dialog-grid");

        VBox root = new VBox(header, content);
        root.getStyleClass().add("root-dialog");

        Rectangle clip = new Rectangle(500, 200);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 500, 200);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().addAll(
                Objects.requireNonNull(TerminalDialog.class.getResource("/org/msv/vt100/ui/styles/dialogs.css")).toExternalForm(),
                Objects.requireNonNull(TerminalDialog.class.getResource("/org/msv/vt100/ui/styles/buttons.css")).toExternalForm()
        );

        dialog.setScene(scene);

        // Центрируем по родителю, если он передан
        if (ownerStage != null) {
            DialogHelper.centerDialogOnOwner(dialog, ownerStage);
        }

        dialog.showAndWait();
    }



    // Упрощённые методы
    public static void showError(String msg, Stage primaryStage) {
        show(DialogType.ERROR, msg, primaryStage);
    }

    public static void showInfo(String msg, Stage primaryStage) {
        show(DialogType.INFO, msg, primaryStage);
    }

    /*public static void showWarning(String msg, Stage primaryStage) {
        show(DialogType.WARNING, msg, primaryStage);
    }

    public static void showConfirm(String msg, Stage primaryStage) {
        show(DialogType.CONFIRM, msg, primaryStage);
    }*/
}
