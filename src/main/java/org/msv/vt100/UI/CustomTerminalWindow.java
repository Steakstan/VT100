package org.msv.vt100.UI;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.fxmisc.richtext.InlineCssTextArea;
import org.msv.vt100.TerminalApp;

import java.net.URL;

public class CustomTerminalWindow {

    private final InlineCssTextArea terminalArea;
    private final BorderPane root;
    private final Scene scene;
    private final Stage primaryStage;
    private final TerminalApp terminalApp;

    private double xOffset = 0;
    private double yOffset = 0;

    public CustomTerminalWindow(Stage primaryStage, TerminalApp terminalApp) {
        this.primaryStage = primaryStage;
        this.terminalApp = terminalApp;

        // Створення текстової області з виправленими стилями для щільного розташування символів
        terminalArea = new InlineCssTextArea();
        terminalArea.getStyleClass().add("terminal-area");
        terminalArea.setStyle(
                "-fx-font-family: 'Monospaced'; " +
                        "-fx-font-size: 15px; " +
                        "-fx-background-color: transparent; " +
                        "-rtfx-background-color: transparent; " +
                        "-fx-line-spacing: 0; " +
                        "-fx-padding: 0; " +
                        "-fx-background-insets: 0; " +
                        "-fx-letter-spacing: -1px;"  // Можна експериментувати з від'ємними значеннями
        );

        terminalArea.setEditable(false);
        terminalArea.setWrapText(false);
        terminalArea.setFocusTraversable(true);
        // Якщо API RichTextFX дозволяє, можна встановити lineSpacing програмно:
        // terminalArea.setLineSpacing(0);

        // Створення верхньої панелі (top bar)
        HBox topBar = createTopBar();

        // Створення нижньої панелі (bottom bar)
        HBox bottomBar = createBottomBar();

        // Створення лівого та правого бордюрів
        Region leftBorder = new Region();
        leftBorder.setPrefWidth(3);
        Region rightBorder = new Region();
        rightBorder.setPrefWidth(3);

        // Задаємо колір рамки для бордюрів і панелей
        Color frameColor = Color.rgb(0, 0, 0, 0.3);
        leftBorder.setBackground(new Background(new BackgroundFill(frameColor, CornerRadii.EMPTY, Insets.EMPTY)));
        rightBorder.setBackground(new Background(new BackgroundFill(frameColor, CornerRadii.EMPTY, Insets.EMPTY)));

        // Задаємо фон для текстової області (наприклад, Solarized Dark)
        Color backgroundColor = Color.rgb(0, 43, 54, 0.95);
        Background terminalBackground = new Background(new BackgroundFill(backgroundColor, CornerRadii.EMPTY, Insets.EMPTY));

        // Налаштування кореневого контейнера
        root = new BorderPane();
        root.setBackground(terminalBackground);
        root.setTop(topBar);
        root.setBottom(bottomBar);
        root.setCenter(terminalArea);
        root.setLeft(leftBorder);
        root.setRight(rightBorder);

        // Створення сцени
        scene = new Scene(root, 726, 480 + 30 + 200); // 720+6, 480+topBar+bottomBar
        scene.setFill(Color.TRANSPARENT);

        // Підключення CSS-файлу (якщо він є)
        URL cssResource = getClass().getResource("/org/msv/vt100/UI/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        } else {
            System.err.println("styles.css not found at /org/msv/vt100/UI/styles.css");
        }

        // Додавання можливості перетягування вікна
        enableWindowDragging();
    }

    private HBox createTopBar() {
        HBox topBar = new HBox();
        topBar.setPrefHeight(30);
        topBar.setPadding(new Insets(5, 5, 5, 5));
        topBar.setSpacing(5);

        // Задаємо фон верхньої панелі (світло-сірий)
        Color frameColor = Color.rgb(0, 0, 0, 0.3);
        topBar.setBackground(new Background(new BackgroundFill(frameColor, CornerRadii.EMPTY, Insets.EMPTY)));

        // Створюємо простір для вирівнювання кнопок праворуч
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Кнопка мінімізування
        Button minimizeButton = new Button("_");
        minimizeButton.setOnAction(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.setIconified(true);
        });

        // Кнопка закриття вікна
        Button closeButton = new Button("X");
        closeButton.setOnAction(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.fireEvent(
                    new javafx.stage.WindowEvent(
                            stage,
                            javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST
                    )
            );
        });

        topBar.getChildren().addAll(spacer, minimizeButton, closeButton);
        return topBar;
    }

    private HBox createBottomBar() {
        HBox bottomBar = new HBox();
        bottomBar.setPrefHeight(200);
        // Задаємо фон нижньої панелі (світло-сірий)
        Color frameColor = Color.rgb(0, 0, 0, 0.3);
        bottomBar.setBackground(new Background(new BackgroundFill(frameColor, CornerRadii.EMPTY, Insets.EMPTY)));

        ContentPanel contentPanel = new ContentPanel(primaryStage, terminalApp);
        bottomBar.getChildren().add(contentPanel);
        return bottomBar;
    }

    private void enableWindowDragging() {
        root.setOnMousePressed(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            xOffset = stage.getX() - event.getScreenX();
            yOffset = stage.getY() - event.getScreenY();
        });
        root.setOnMouseDragged(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.setX(event.getScreenX() + xOffset);
            stage.setY(event.getScreenY() + yOffset);
        });
    }

    public void configureStage(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setResizable(false);
    }

    public InlineCssTextArea getTerminalArea() {
        return terminalArea;
    }
}
