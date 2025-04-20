package org.msv.vt100.util;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class DialogHelper {

    /**
     * Центрирует диалог относительно окна-родителя.
     * Если окно выходит за границы экрана, оно сдвигается внутрь.
     */
    public static void centerDialogOnOwner(Stage dialog, Stage owner) {
        Platform.runLater(() -> {
            dialog.sizeToScene(); // Убедимся, что размеры рассчитаны

            double centerX = owner.getX() + owner.getWidth() / 2 - dialog.getWidth() / 2;
            double centerY = owner.getY() + owner.getHeight() / 2 - dialog.getHeight() / 2;

            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();

            if (centerX < screenBounds.getMinX()) centerX = screenBounds.getMinX();
            if (centerY < screenBounds.getMinY()) centerY = screenBounds.getMinY();
            if (centerX + dialog.getWidth() > screenBounds.getMaxX())
                centerX = screenBounds.getMaxX() - dialog.getWidth();
            if (centerY + dialog.getHeight() > screenBounds.getMaxY())
                centerY = screenBounds.getMaxY() - dialog.getHeight();

            dialog.setX(centerX);
            dialog.setY(centerY);
        });
    }

    /**
     * Позволяет перетаскивать диалоговое окно, зажав мышью любой элемент (например, заголовок).
     */
    public static void enableDragging(Stage dialog, Node draggableNode) {
        final Delta dragDelta = new Delta();

        draggableNode.setOnMousePressed(mouseEvent -> {
            dragDelta.x = dialog.getX() - mouseEvent.getScreenX();
            dragDelta.y = dialog.getY() - mouseEvent.getScreenY();
        });

        draggableNode.setOnMouseDragged(mouseEvent -> {
            double newX = mouseEvent.getScreenX() + dragDelta.x;
            double newY = mouseEvent.getScreenY() + dragDelta.y;

            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();

            // Ограничим перемещение рамками экрана
            if (newX >= bounds.getMinX() && newX + dialog.getWidth() <= bounds.getMaxX()) {
                dialog.setX(newX);
            }
            if (newY >= bounds.getMinY() && newY + dialog.getHeight() <= bounds.getMaxY()) {
                dialog.setY(newY);
            }
        });
    }

    public static HBox createDialogHeader(String title, Runnable onClose) {
        HBox header = new HBox();
        header.getStyleClass().add("dialog-header");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button("X");
        closeButton.getStyleClass().add("dialog-header-close-button");
        closeButton.setOnAction(e -> onClose.run());

        header.getChildren().addAll(titleLabel, spacer, closeButton);
        return header;
    }

    public static HBox createDialogFooter(Button... buttons) {
        for (Button button : buttons) {
            button.getStyleClass().add("dialog-button");
        }
        HBox box = new HBox(10, buttons);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }


    private static class Delta {
        double x, y;
    }
}
