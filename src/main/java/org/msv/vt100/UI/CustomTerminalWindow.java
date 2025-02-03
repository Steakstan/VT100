package org.msv.vt100.UI;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.msv.vt100.ScreenBuffer;
import org.msv.vt100.TerminalApp;

import java.net.URL;

public class CustomTerminalWindow {

    private final TerminalCanvas terminalCanvas;
    private final BorderPane root;
    private final Scene scene;
    private final Stage primaryStage;
    private final TerminalApp terminalApp;

    // Параметри для переміщення вікна
    private double xOffset = 0;
    private double yOffset = 0;

    // Параметри для зміни розміру вікна
    private double initX, initY, initWidth, initHeight;
    private static final int RESIZE_MARGIN = 8;
    private ResizeDirection resizeDir = ResizeDirection.NONE;

    // Фіксовані розміри панелей та рамок
    private final double TOP_BAR_HEIGHT = 30;
    private final double BOTTOM_BAR_HEIGHT = 200;
    private final double SIDE_BORDER_WIDTH = 3 * 2; // 3 пікселі з кожного боку

    // Напрямки зміни розміру
    private enum ResizeDirection {
        NONE, NORTH, SOUTH, EAST, WEST, NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST
    }

    public CustomTerminalWindow(Stage primaryStage, TerminalApp terminalApp, ScreenBuffer screenBuffer) {
        this.primaryStage = primaryStage;
        this.terminalApp = terminalApp;

        // Початкові розміри канви (вони будуть адаптовані при зміні розміру)
        double initialCanvasWidth = screenBuffer.getColumns() * 10;
        double initialCanvasHeight = screenBuffer.getRows() * 20;
        terminalCanvas = new TerminalCanvas(screenBuffer, initialCanvasWidth, initialCanvasHeight);

        // Створення верхньої панелі з прозорим фоном
        HBox topBar = createTopBar();

        // Створення нижньої панелі з прозорим фоном
        HBox bottomBar = createBottomBar();

        // Лівий і правий бордюри з 50% прозорістю
        Region leftBorder = new Region();
        leftBorder.setPrefWidth(3);
        Region rightBorder = new Region();
        rightBorder.setPrefWidth(3);
        Color borderColor = Color.rgb(0, 0, 0, 0.5);
        leftBorder.setBackground(new Background(new BackgroundFill(borderColor, CornerRadii.EMPTY, Insets.EMPTY)));
        rightBorder.setBackground(new Background(new BackgroundFill(borderColor, CornerRadii.EMPTY, Insets.EMPTY)));

        // Фон термінальної області з 50% прозорістю
        Color terminalBgColor = Color.rgb(0, 43, 54, 0.5);
        Background terminalBackground = new Background(new BackgroundFill(terminalBgColor, CornerRadii.EMPTY, Insets.EMPTY));

        // Налаштовуємо кореневий контейнер
        root = new BorderPane();
        root.setBackground(terminalBackground);
        root.setTop(topBar);
        root.setBottom(bottomBar);
        root.setCenter(terminalCanvas);
        root.setLeft(leftBorder);
        root.setRight(rightBorder);

        // Розрахунок початкових розмірів сцени
        double sceneWidth = initialCanvasWidth + SIDE_BORDER_WIDTH;
        double sceneHeight = initialCanvasHeight + TOP_BAR_HEIGHT + BOTTOM_BAR_HEIGHT;
        scene = new Scene(root, sceneWidth, sceneHeight);
        scene.setFill(Color.TRANSPARENT);

        // Підключення CSS (якщо необхідно)
        URL cssResource = getClass().getResource("/org/msv/vt100/UI/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        } else {
            System.err.println("styles.css not found at /org/msv/vt100/UI/styles.css");
        }

        // Додаємо можливість переміщення вікна
        enableWindowDragging();
        // Додаємо можливість зміни розміру вікна
        enableWindowResizing();

        terminalCanvas.setFocusTraversable(true);

        // Слухачі для адаптації розмірів канви при зміні розміру сцени
        scene.widthProperty().addListener((observable, oldValue, newValue) -> {
            double newCanvasWidth = newValue.doubleValue() - SIDE_BORDER_WIDTH;
            terminalCanvas.setWidth(newCanvasWidth);
            terminalCanvas.updateScreen();
            terminalCanvas.requestFocus();
        });
        scene.heightProperty().addListener((observable, oldValue, newValue) -> {
            double newCanvasHeight = newValue.doubleValue() - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT;
            terminalCanvas.setHeight(newCanvasHeight);
            terminalCanvas.updateScreen();
            terminalCanvas.requestFocus();
        });

        // Слухач для відновлення фокусу при поверненні вікна
        primaryStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                terminalCanvas.requestFocus();
            }
        });
    }

    private HBox createTopBar() {
        HBox topBar = new HBox();
        topBar.setPrefHeight(TOP_BAR_HEIGHT);
        topBar.setPadding(new Insets(5));
        topBar.setSpacing(5);
        Color topBarColor = Color.rgb(0, 0, 0, 0.5);
        topBar.setBackground(new Background(new BackgroundFill(topBarColor, CornerRadii.EMPTY, Insets.EMPTY)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Кнопка для мінімізації
        Button minimizeButton = new Button("_");
        minimizeButton.setOnAction(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.setIconified(true);
        });
        // Кнопка для максимізації/відновлення
        Button maximizeButton = new Button("☐");
        maximizeButton.setOnAction(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.setMaximized(!stage.isMaximized());
            // Після максимізації чи відновлення повертаємо фокус канві
            terminalCanvas.requestFocus();
        });
        // Кнопка для закриття
        Button closeButton = new Button("X");
        closeButton.setOnAction(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
        });

        topBar.getChildren().addAll(spacer, minimizeButton, maximizeButton, closeButton);
        return topBar;
    }

    private HBox createBottomBar() {
        HBox bottomBar = new HBox();
        bottomBar.setPrefHeight(BOTTOM_BAR_HEIGHT);
        Color bottomBarColor = Color.rgb(0, 0, 0, 0.5);
        bottomBar.setBackground(new Background(new BackgroundFill(bottomBarColor, CornerRadii.EMPTY, Insets.EMPTY)));
        ContentPanel contentPanel = new ContentPanel(primaryStage, terminalApp);
        bottomBar.getChildren().add(contentPanel);
        return bottomBar;
    }

    // Логіка переміщення вікна
    private void enableWindowDragging() {
        root.setOnMousePressed(event -> {
            // Якщо не змінюємо розмір, рухаємо вікно
            if (resizeDir == ResizeDirection.NONE) {
                Stage stage = (Stage) root.getScene().getWindow();
                xOffset = stage.getX() - event.getScreenX();
                yOffset = stage.getY() - event.getScreenY();
            }
        });
        root.setOnMouseDragged(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            if (resizeDir == ResizeDirection.NONE) {
                stage.setX(event.getScreenX() + xOffset);
                stage.setY(event.getScreenY() + yOffset);
            }
        });
    }

    // Логіка зміни розміру вікна
    private void enableWindowResizing() {
        scene.setOnMouseMoved(event -> {
            ResizeDirection dir = getResizeDirection(event);
            switch (dir) {
                case NORTH:
                case SOUTH:
                    scene.setCursor(Cursor.N_RESIZE);
                    break;
                case EAST:
                case WEST:
                    scene.setCursor(Cursor.E_RESIZE);
                    break;
                case NORTH_EAST:
                case SOUTH_WEST:
                    scene.setCursor(Cursor.NE_RESIZE);
                    break;
                case NORTH_WEST:
                case SOUTH_EAST:
                    scene.setCursor(Cursor.NW_RESIZE);
                    break;
                default:
                    scene.setCursor(Cursor.DEFAULT);
            }
        });

        scene.setOnMousePressed(event -> {
            resizeDir = getResizeDirection(event);
            if (resizeDir != ResizeDirection.NONE) {
                Stage stage = (Stage) root.getScene().getWindow();
                initX = event.getScreenX();
                initY = event.getScreenY();
                initWidth = stage.getWidth();
                initHeight = stage.getHeight();
            }
        });

        scene.setOnMouseDragged(event -> {
            if (resizeDir != ResizeDirection.NONE) {
                Stage stage = (Stage) root.getScene().getWindow();
                double dx = event.getScreenX() - initX;
                double dy = event.getScreenY() - initY;

                if (resizeDir == ResizeDirection.EAST || resizeDir == ResizeDirection.NORTH_EAST || resizeDir == ResizeDirection.SOUTH_EAST) {
                    stage.setWidth(Math.max(300, initWidth + dx));
                }
                if (resizeDir == ResizeDirection.SOUTH || resizeDir == ResizeDirection.SOUTH_EAST || resizeDir == ResizeDirection.SOUTH_WEST) {
                    stage.setHeight(Math.max(200, initHeight + dy));
                }
                if (resizeDir == ResizeDirection.WEST || resizeDir == ResizeDirection.NORTH_WEST || resizeDir == ResizeDirection.SOUTH_WEST) {
                    double newWidth = initWidth - dx;
                    if (newWidth >= 300) {
                        stage.setWidth(newWidth);
                        stage.setX(event.getScreenX());
                    }
                }
                if (resizeDir == ResizeDirection.NORTH || resizeDir == ResizeDirection.NORTH_EAST || resizeDir == ResizeDirection.NORTH_WEST) {
                    double newHeight = initHeight - dy;
                    if (newHeight >= 200) {
                        stage.setHeight(newHeight);
                        stage.setY(event.getScreenY());
                    }
                }
            }
        });

        scene.setOnMouseReleased(event -> {
            resizeDir = ResizeDirection.NONE;
            scene.setCursor(Cursor.DEFAULT);
        });
    }

    // Визначення напрямку зміни розміру за координатами миші
    private ResizeDirection getResizeDirection(MouseEvent event) {
        double mouseX = event.getSceneX();
        double mouseY = event.getSceneY();
        double sceneWidth = scene.getWidth();
        double sceneHeight = scene.getHeight();

        boolean left = mouseX < RESIZE_MARGIN;
        boolean right = mouseX > sceneWidth - RESIZE_MARGIN;
        boolean top = mouseY < RESIZE_MARGIN;
        boolean bottom = mouseY > sceneHeight - RESIZE_MARGIN;

        if (top && left) return ResizeDirection.NORTH_WEST;
        if (top && right) return ResizeDirection.NORTH_EAST;
        if (bottom && left) return ResizeDirection.SOUTH_WEST;
        if (bottom && right) return ResizeDirection.SOUTH_EAST;
        if (top) return ResizeDirection.NORTH;
        if (bottom) return ResizeDirection.SOUTH;
        if (left) return ResizeDirection.WEST;
        if (right) return ResizeDirection.EAST;
        return ResizeDirection.NONE;
    }

    public void configureStage(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setResizable(true);
        // Встановлюємо прозорість вікна на 50%
        stage.setOpacity(0.95);
    }

    public TerminalCanvas getTerminalCanvas() {
        return terminalCanvas;
    }
}
