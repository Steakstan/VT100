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

        terminalArea = new InlineCssTextArea();
        terminalArea.setStyle("-fx-font-family: 'Monospaced'; " +
                "-fx-font-size: 15px; " +
                "-fx-background-color: transparent; " + // Make the background transparent
                "-rtfx-background-color: transparent;");
        terminalArea.setEditable(false);
        terminalArea.setWrapText(false);
        terminalArea.setFocusTraversable(true);

        // Create the top bar with close and minimize buttons
        HBox topBar = createTopBar();

        // Create the bottom bar with height 200 pixels
        HBox bottomBar = createBottomBar();

        // Left and right borders
        Region leftBorder = new Region();
        leftBorder.setPrefWidth(3);
        Region rightBorder = new Region();
        rightBorder.setPrefWidth(3);

        // Устанавливаем цвет фона для рамки и баров
        Color frameColor = Color.rgb(0, 0, 0, 0.3);

        // Применяем цвет к левому и правому бордюрам
        leftBorder.setBackground(new Background(new BackgroundFill(frameColor, CornerRadii.EMPTY, Insets.EMPTY)));
        rightBorder.setBackground(new Background(new BackgroundFill(frameColor, CornerRadii.EMPTY, Insets.EMPTY)));

        // Set background color with transparency for the terminal area (Solarized Dark)
        Color backgroundColor = Color.rgb(0, 43, 54, 0.95); // Solarized Dark
        Background terminalBackground = new Background(new BackgroundFill(backgroundColor, CornerRadii.EMPTY, Insets.EMPTY));

        // Root BorderPane
        root = new BorderPane();
        root.setBackground(terminalBackground);
        root.setTop(topBar);
        root.setBottom(bottomBar);
        root.setCenter(terminalArea);
        root.setLeft(leftBorder);
        root.setRight(rightBorder);

        // Scene
        scene = new Scene(root, 720 + 6, 480 + /*topBar height*/30 + /*bottomBar height*/200);
        scene.setFill(Color.TRANSPARENT);

        // Подключаем CSS-файл
        URL cssResource = getClass().getResource("/org/msv/vt100/UI/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        } else {
            System.err.println("Файл styles.css не найден по пути /org/msv/vt100/UI/styles.css");
        }

        // Implement window dragging
        enableWindowDragging();
    }

    private HBox createTopBar() {
        HBox topBar = new HBox();
        topBar.setPrefHeight(30); // Adjust as needed
        topBar.setPadding(new Insets(5, 5, 5, 5));
        topBar.setSpacing(5);


        // Устанавливаем фон верхней панели в мягкий серый цвет
        Color frameColor = Color.rgb(0, 0, 0, 0.3); // Светло-серый цвет
        topBar.setBackground(new Background(new BackgroundFill(frameColor, CornerRadii.EMPTY, Insets.EMPTY)));

        // Spacer to push buttons to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Minimize button
        Button minimizeButton = new Button("_");
        minimizeButton.setOnAction(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.setIconified(true);
        });

        // Close button
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
        bottomBar.setPrefHeight(200); // Adjust height as needed

        // Set background color
        Color frameColor = Color.rgb(0, 0, 0, 0.3); // Light gray color
        bottomBar.setBackground(new Background(new BackgroundFill(frameColor, CornerRadii.EMPTY, Insets.EMPTY)));

        ContentPanel contentPanel = new ContentPanel(primaryStage, terminalApp); // Pass terminalApp

        // Добавляем только contentPanel в нижний бар
        bottomBar.getChildren().add(contentPanel);

        return bottomBar;
    }

    private void enableWindowDragging() {
        // Mouse pressed event handler
        root.setOnMousePressed(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            xOffset = stage.getX() - event.getScreenX();
            yOffset = stage.getY() - event.getScreenY();
        });

        // Mouse dragged event handler
        root.setOnMouseDragged(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.setX(event.getScreenX() + xOffset);
            stage.setY(event.getScreenY() + yOffset);
        });
    }

    public void configureStage(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        // Disable window resizing
        stage.setResizable(false);
    }

    public InlineCssTextArea getTerminalArea() {
        return terminalArea;
    }
}
