package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.core.ScreenBuffer;

import java.net.URL;

public class CustomTerminalWindow {

    private final TerminalCanvas terminalCanvas;
    private final BorderPane root;
    private final Scene scene;
    private final Stage primaryStage;
    private final TerminalApp terminalApp;
    private ContentPanel contentPanel;

    // Параметры для перемещения окна
    private double xOffset = 0;
    private double yOffset = 0;

    // Параметры для изменения размера окна
    private double initX, initY, initWidth, initHeight;
    private static final int RESIZE_MARGIN = 8;
    private ResizeDirection resizeDir = ResizeDirection.NONE;
    private static final int TOP_BAR_HEIGHT = 30;
    private static final int BOTTOM_BAR_HEIGHT = 60;

    // Направления изменения размера
    private enum ResizeDirection {
        NONE, NORTH, SOUTH, EAST, WEST, NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST
    }

    public CustomTerminalWindow(Stage primaryStage, TerminalApp terminalApp, ScreenBuffer screenBuffer) {
        this.primaryStage = primaryStage;
        this.terminalApp = terminalApp;

        // Создаем терминальную канву
        double initialCanvasWidth = screenBuffer.getColumns() * 10;
        double initialCanvasHeight = screenBuffer.getRows() * 20;
        terminalCanvas = new TerminalCanvas(screenBuffer, initialCanvasWidth, initialCanvasHeight);
        terminalCanvas.setFocusTraversable(true);

        // Создаем верхнюю панель: topBar + OptionalMenuBar
        HBox topBar = createTopBar();
        OptionalMenuBar optionalMenuBar = new OptionalMenuBar(primaryStage, terminalApp);
        // Добавляем CSS класс, чтобы в стилевом файле задать для него прямоугольные углы
        optionalMenuBar.getStyleClass().add("optional-menu-topbar");

        VBox topContainer = new VBox();
        topContainer.getChildren().addAll(topBar, optionalMenuBar);

        // Нижняя панель для ContentPanel (например, кнопки Pause/Stop)
        HBox bottomBar = createBottomBar();

        // Боковые рамки
        Region leftBorder = new Region();
        leftBorder.setPrefWidth(3);
        Region rightBorder = new Region();
        rightBorder.setPrefWidth(3);
        Color borderColor = Color.rgb(0, 0, 0, 0.5);
        leftBorder.setBackground(new Background(new BackgroundFill(borderColor, CornerRadii.EMPTY, Insets.EMPTY)));
        rightBorder.setBackground(new Background(new BackgroundFill(borderColor, CornerRadii.EMPTY, Insets.EMPTY)));

        // Основное окно – фон с полным скруглением углов (все углы округлены)
        Color terminalBgColor = Color.rgb(0, 43, 54); // фон без прозрачности
        Background terminalBackground = new Background(new BackgroundFill(terminalBgColor, new CornerRadii(15), Insets.EMPTY));

        root = new BorderPane();
        root.setBackground(terminalBackground);
        root.setTop(topContainer);
        root.setBottom(bottomBar);
        root.setCenter(terminalCanvas);
        root.setLeft(leftBorder);
        root.setRight(rightBorder);
        // Применяем CSS класс для основного окна (при необходимости можно задать здесь дополнительные стили)
        root.getStyleClass().add("root");

        double sceneWidth = initialCanvasWidth + 6; // учет боковых рамок
        double sceneHeight = initialCanvasHeight + TOP_BAR_HEIGHT + BOTTOM_BAR_HEIGHT;
        scene = new Scene(root, sceneWidth, sceneHeight);
        scene.setFill(Color.TRANSPARENT);

        // Подключаем CSS
        URL cssResource = getClass().getResource("/org/msv/vt100/ui/styles.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        } else {
            System.err.println("styles.css not found at /org/msv/vt100/ui/styles.css");
        }

        // Глобальный слушатель фокуса для терминальной канвы
        scene.focusOwnerProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal instanceof TerminalCanvas ||
                    newVal instanceof TextInputControl ||
                    newVal instanceof ComboBoxBase ||
                    (newVal != null && newVal.getStyleClass().contains("combo-box-popup"))) {
                return;
            }
            Platform.runLater(() -> terminalCanvas.requestFocus());
        });

        enableWindowDragging();
        enableWindowResizing();

        // Адаптация размеров канвы при изменении размеров сцены
        scene.widthProperty().addListener((observable, oldValue, newValue) -> {
            double newCanvasWidth = newValue.doubleValue() - 6;
            terminalCanvas.setWidth(newCanvasWidth);
            terminalCanvas.requestFocus();
        });
        scene.heightProperty().addListener((observable, oldValue, newValue) -> {
            double newCanvasHeight = newValue.doubleValue() - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT;
            terminalCanvas.setHeight(newCanvasHeight);
            terminalCanvas.requestFocus();
        });

        // Возврат фокуса терминальной канве при активации окна
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
        // Задаем фон верхней панели с закруглением только верхних углов
        Color topBarColor = Color.rgb(0, 0, 0, 0.5);
        topBar.setBackground(new Background(new BackgroundFill(topBarColor, new CornerRadii(15,15,0,0,false), Insets.EMPTY)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimizeButton = new Button("_");
        minimizeButton.getStyleClass().add("top-bar-button");
        minimizeButton.setOnAction(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.setIconified(true);
        });

        Button maximizeButton = new Button("☐");
        maximizeButton.getStyleClass().add("top-bar-button");
        maximizeButton.setOnAction(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.setMaximized(!stage.isMaximized());
            terminalCanvas.requestFocus();
        });

        Button closeButton = new Button("X");
        closeButton.getStyleClass().addAll("top-bar-button", "close");
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
        // Задаем фон нижней панели с закруглением только нижних углов
        bottomBar.setBackground(new Background(new BackgroundFill(bottomBarColor, new CornerRadii(0,0,15,15,false), Insets.EMPTY)));
        this.contentPanel = new ContentPanel(primaryStage, terminalApp);
        contentPanel.setPrefHeight(BOTTOM_BAR_HEIGHT);
        bottomBar.getChildren().add(contentPanel);
        return bottomBar;
    }

    // Логика перемещения окна
    private void enableWindowDragging() {
        root.setOnMousePressed(event -> {
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

    // Логика изменения размера окна
    private void enableWindowResizing() {
        scene.setOnMouseMoved(event -> {
            ResizeDirection dir = getResizeDirection(event);
            switch (dir) {
                case NORTH:
                case SOUTH:
                    scene.setCursor(javafx.scene.Cursor.N_RESIZE);
                    break;
                case EAST:
                case WEST:
                    scene.setCursor(javafx.scene.Cursor.E_RESIZE);
                    break;
                case NORTH_EAST:
                case SOUTH_WEST:
                    scene.setCursor(javafx.scene.Cursor.NE_RESIZE);
                    break;
                case NORTH_WEST:
                case SOUTH_EAST:
                    scene.setCursor(javafx.scene.Cursor.NW_RESIZE);
                    break;
                default:
                    scene.setCursor(javafx.scene.Cursor.DEFAULT);
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
            scene.setCursor(javafx.scene.Cursor.DEFAULT);
        });
    }

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

    public ContentPanel getContentPanel() {
        return contentPanel;
    }

    public TerminalCanvas getTerminalCanvas() {
        return terminalCanvas;
    }

    public void configureStage(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setResizable(true);
        stage.setOpacity(0.95);
    }
}
