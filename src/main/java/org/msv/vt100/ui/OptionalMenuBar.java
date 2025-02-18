package org.msv.vt100.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.msv.vt100.TerminalApp;

public class OptionalMenuBar extends HBox {

    private Popup currentPopup = null;
    private Button currentPopupButton = null;
    private final TerminalApp terminalApp;

    public OptionalMenuBar(Stage primaryStage, TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
        setSpacing(10);
        setPadding(new Insets(5));
        setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        setAlignment(Pos.CENTER_LEFT);

        // Создаем кнопки меню
        Button btnDatei = createMenuButton("Datei");
        Button btnBearbeiten = createMenuButton("Bearbeiten");
        Button btnLog = createMenuButton("Log");

        // Устанавливаем обработчики кликов
        btnDatei.setOnAction(e -> togglePopup(btnDatei, primaryStage));
        btnBearbeiten.setOnAction(e -> togglePopup(btnBearbeiten, primaryStage));
        btnLog.setOnAction(e -> togglePopup(btnLog, primaryStage));

        // При наведении переключаем popup, если курсор переходит на другую кнопку
        btnDatei.setOnMouseEntered(e -> {
            if (currentPopup != null && currentPopupButton != btnDatei) {
                togglePopup(btnDatei, primaryStage);
            }
        });
        btnBearbeiten.setOnMouseEntered(e -> {
            if (currentPopup != null && currentPopupButton != btnBearbeiten) {
                togglePopup(btnBearbeiten, primaryStage);
            }
        });
        btnLog.setOnMouseEntered(e -> {
            if (currentPopup != null && currentPopupButton != btnLog) {
                togglePopup(btnLog, primaryStage);
            }
        });

        getChildren().addAll(btnDatei, btnBearbeiten, btnLog);
    }

    /**
     * Создает кнопку меню с базовой стилизацией.
     */
    private Button createMenuButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("menu-button");
        // Базовая стилизация задается через CSS, но можно также указать inline
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        button.setOnMouseEntered(e -> button.setStyle("-fx-background-color: white; -fx-text-fill: black;"));
        button.setOnMouseExited(e -> button.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"));
        return button;
    }

    /**
     * Переключает popup-окно для заданной кнопки.
     */
    private void togglePopup(Button button, Stage primaryStage) {
        if (currentPopup != null && currentPopupButton == button) {
            currentPopup.hide();
            currentPopup = null;
            currentPopupButton = null;
        } else {
            if (currentPopup != null) {
                currentPopup.hide();
                currentPopup = null;
                currentPopupButton = null;
            }
            Popup newPopup = createPopupForButton(button, primaryStage);
            newPopup.setAutoHide(true);
            // Вычисляем позицию popup ниже кнопки
            double x = button.localToScreen(button.getBoundsInLocal()).getMinX();
            double y = button.localToScreen(button.getBoundsInLocal()).getMaxY();
            newPopup.show(button, x, y);
            currentPopup = newPopup;
            currentPopupButton = button;
        }
    }

    /**
     * Создает popup-окно для кнопки по её тексту.
     */
    private Popup createPopupForButton(Button button, Stage primaryStage) {
        String text = button.getText();
        Popup popup = new Popup();
        VBox content = createPopupContent();

        switch (text) {
            case "Datei":
                content.getChildren().addAll(
                        createActionButton("Verbindung Einstellungen", () -> {
                            terminalApp.openProfileDialog();
                            popup.hide();
                        }),
                        createActionButton("Verbindung neu starten", () -> {
                            terminalApp.restartConnection();
                            popup.hide();
                        }),
                        createActionButton("Verbindung abfallen", () -> {
                            terminalApp.disconnectConnection();
                            popup.hide();
                        })
                );
                break;
            case "Bearbeiten":
                content.getChildren().add(
                        createActionButton("Bearbeitungseinstellungen", () -> {
                            terminalApp.openBearbeitungseinstellungenDialog();
                            popup.hide();
                        })
                );
                break;
            case "Log":
                content.getChildren().add(
                        createActionButton("Log Einstellungen", () -> {
                            new LogSettingsDialog(terminalApp).show();
                            popup.hide();
                        })
                );
                break;
            default:
                content.getChildren().add(
                        createActionButton("Пока не реализовано", popup::hide)
                );
        }
        popup.getContent().add(content);
        return popup;
    }

    /**
     * Создает VBox-контейнер для содержимого popup с единым стилем,
     * используя стиль из CSS (класс "popup-container").
     */
    private VBox createPopupContent() {
        VBox content = new VBox(5);
        content.getStyleClass().add("popup-container");
        return content;
    }

    /**
     * Создает кнопку для popup-а с заданным текстом и действием.
     */
    private Button createActionButton(String text, Runnable action) {
        Button button = new Button(text);
        styleMenuButton(button);
        button.setOnAction(e -> action.run());
        return button;
    }

    /**
     * Применяет стили к кнопке popup-а.
     */
    private void styleMenuButton(Button button) {
        button.getStyleClass().add("menu-item");
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        button.addEventHandler(MouseEvent.MOUSE_ENTERED, e ->
                button.setStyle("-fx-background-color: white; -fx-text-fill: black;")
        );
        button.addEventHandler(MouseEvent.MOUSE_EXITED, e ->
                button.setStyle("-fx-background-color: transparent; -fx-text-fill: white;")
        );
    }
}
