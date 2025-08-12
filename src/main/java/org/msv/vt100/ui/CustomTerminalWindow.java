package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.core.ScreenBuffer;

import java.util.Objects;

public class CustomTerminalWindow {

    private final TerminalCanvas terminalCanvas;
    private final BorderPane root;
    private final Scene scene;
    private final Stage primaryStage;
    private final TerminalApp terminalApp;
    private ContentPanel contentPanel;

    private double xOffset = 0;
    private double yOffset = 0;

    private double initX, initY, initWidth, initHeight;
    private static final int RESIZE_MARGIN = 8;
    private ResizeDirection resizeDir = ResizeDirection.NONE;
    private static final int TOP_BAR_HEIGHT = 30;
    private static final int BOTTOM_BAR_HEIGHT = 30;

    private boolean maximizedToWorkArea = false;
    private double restoreX, restoreY, restoreW, restoreH;

    private enum ResizeDirection {
        NONE, NORTH, SOUTH, EAST, WEST, NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST
    }

    public CustomTerminalWindow(Stage primaryStage, TerminalApp terminalApp, ScreenBuffer screenBuffer) {
        this.primaryStage = primaryStage;
        this.terminalApp = terminalApp;

        double initialCanvasWidth = screenBuffer.getColumns() * 10;
        double initialCanvasHeight = screenBuffer.getRows() * 20;
        terminalCanvas = new TerminalCanvas(screenBuffer, initialCanvasWidth, initialCanvasHeight);
        terminalCanvas.setFocusTraversable(true);

        HBox topBar = createTopBar();
        OptionalMenuBar optionalMenuBar = new OptionalMenuBar(terminalApp);
        optionalMenuBar.getStyleClass().add("optional-menu-topbar");

        VBox topContainer = new VBox(topBar, optionalMenuBar);
        VBox.setMargin(optionalMenuBar, new Insets(0, 3, 0, 3));

        HBox bottomBar = createBottomBar();

        root = new BorderPane();
        root.getStyleClass().add("terminal-root");
        root.setTop(topContainer);
        root.setBottom(bottomBar);
        root.setCenter(terminalCanvas);

        double sceneWidth = initialCanvasWidth + 6;
        double sceneHeight = initialCanvasHeight + TOP_BAR_HEIGHT + BOTTOM_BAR_HEIGHT + 55;
        scene = new Scene(root, sceneWidth, sceneHeight);
        scene.setFill(Color.TRANSPARENT);

        scene.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/base.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/buttons.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/menu.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/tabs.css")).toExternalForm()
        );

        scene.focusOwnerProperty().addListener((obs, oldVal, newVal) -> {
            if (!(newVal instanceof TerminalCanvas ||
                    newVal instanceof TextInputControl ||
                    newVal instanceof ComboBoxBase ||
                    (newVal != null && newVal.getStyleClass().contains("combo-box-popup")))) {
                Platform.runLater(terminalCanvas::requestFocus);
            }
        });

        enableWindowDragging();
        enableWindowResizing();

        scene.widthProperty().addListener((observable, oldValue, newValue) -> {
            terminalCanvas.setWidth(newValue.doubleValue() - 6);
            terminalCanvas.requestFocus();
        });
        scene.heightProperty().addListener((observable, oldValue, newValue) -> {
            terminalCanvas.setHeight(newValue.doubleValue() - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT - 55);
            terminalCanvas.requestFocus();
        });

        primaryStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                terminalCanvas.requestFocus();
            }
        });
    }

    private HBox createTopBar() {
        HBox topBar = new HBox();
        topBar.getStyleClass().add("top-bar");
        topBar.setPrefHeight(TOP_BAR_HEIGHT);
        topBar.setPadding(new Insets(5));
        topBar.setSpacing(5);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button minimizeButton = new Button("_");
        minimizeButton.getStyleClass().add("top-bar-button");
        minimizeButton.setOnAction(event -> primaryStage.setIconified(true));

        Button maximizeButton = new Button("☐");
        maximizeButton.getStyleClass().add("top-bar-button");
        maximizeButton.setOnAction(event -> toggleWorkAreaMaximize());

        Button closeButton = new Button("X");
        closeButton.getStyleClass().addAll("top-bar-button", "close");
        closeButton.setOnAction(event -> {
            Stage stage = (Stage) root.getScene().getWindow();
            stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
        });

        topBar.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) toggleWorkAreaMaximize();
        });

        topBar.getChildren().addAll(spacer, minimizeButton, maximizeButton, closeButton);
        return topBar;
    }

    private HBox createBottomBar() {
        HBox bottomBar = new HBox();
        bottomBar.getStyleClass().add("bottom-bar");
        bottomBar.setPrefHeight(BOTTOM_BAR_HEIGHT);
        contentPanel = new ContentPanel(terminalApp);
        contentPanel.setPrefHeight(BOTTOM_BAR_HEIGHT);
        bottomBar.getChildren().add(contentPanel);
        return bottomBar;
    }

    private void enableWindowDragging() {
        root.setOnMousePressed(event -> {
            if (maximizedToWorkArea) return;
            if (resizeDir == ResizeDirection.NONE) {
                xOffset = primaryStage.getX() - event.getScreenX();
                yOffset = primaryStage.getY() - event.getScreenY();
            }
        });
        root.setOnMouseDragged(event -> {
            if (maximizedToWorkArea) return;
            if (resizeDir == ResizeDirection.NONE) {
                primaryStage.setX(event.getScreenX() + xOffset);
                primaryStage.setY(event.getScreenY() + yOffset);
            }
        });
    }

    private void enableWindowResizing() {
        scene.setOnMouseMoved(event -> scene.setCursor(switch (maximizedToWorkArea ? ResizeDirection.NONE : getResizeDirection(event)) {
            case NORTH, SOUTH -> javafx.scene.Cursor.N_RESIZE;
            case EAST, WEST -> javafx.scene.Cursor.E_RESIZE;
            case NORTH_EAST, SOUTH_WEST -> javafx.scene.Cursor.NE_RESIZE;
            case NORTH_WEST, SOUTH_EAST -> javafx.scene.Cursor.NW_RESIZE;
            default -> javafx.scene.Cursor.DEFAULT;
        }));

        scene.setOnMousePressed(event -> {
            if (maximizedToWorkArea) return;
            resizeDir = getResizeDirection(event);
            if (resizeDir != ResizeDirection.NONE) {
                initX = event.getScreenX();
                initY = event.getScreenY();
                initWidth = primaryStage.getWidth();
                initHeight = primaryStage.getHeight();
            }
        });

        scene.setOnMouseDragged(event -> {
            if (maximizedToWorkArea) return;
            if (resizeDir != ResizeDirection.NONE) {
                double dx = event.getScreenX() - initX;
                double dy = event.getScreenY() - initY;

                if (resizeDir == ResizeDirection.EAST || resizeDir == ResizeDirection.NORTH_EAST || resizeDir == ResizeDirection.SOUTH_EAST) {
                    primaryStage.setWidth(Math.max(300, initWidth + dx));
                }
                if (resizeDir == ResizeDirection.SOUTH || resizeDir == ResizeDirection.SOUTH_EAST || resizeDir == ResizeDirection.SOUTH_WEST) {
                    primaryStage.setHeight(Math.max(200, initHeight + dy));
                }
                if (resizeDir == ResizeDirection.WEST || resizeDir == ResizeDirection.NORTH_WEST || resizeDir == ResizeDirection.SOUTH_WEST) {
                    double newWidth = initWidth - dx;
                    if (newWidth >= 300) {
                        primaryStage.setWidth(newWidth);
                        primaryStage.setX(event.getScreenX());
                    }
                }
                if (resizeDir == ResizeDirection.NORTH || resizeDir == ResizeDirection.NORTH_EAST || resizeDir == ResizeDirection.NORTH_WEST) {
                    double newHeight = initHeight - dy;
                    if (newHeight >= 200) {
                        primaryStage.setHeight(newHeight);
                        primaryStage.setY(event.getScreenY());
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

    private void toggleWorkAreaMaximize() {
        if (!maximizedToWorkArea) {
            restoreX = primaryStage.getX();
            restoreY = primaryStage.getY();
            restoreW = primaryStage.getWidth();
            restoreH = primaryStage.getHeight();
            Screen screen = getCurrentScreen();
            Rectangle2D vb = screen.getVisualBounds();
            primaryStage.setX(vb.getMinX());
            primaryStage.setY(vb.getMinY());
            primaryStage.setWidth(vb.getWidth());
            primaryStage.setHeight(vb.getHeight());

            maximizedToWorkArea = true;
        } else {
            primaryStage.setX(restoreX);
            primaryStage.setY(restoreY);
            primaryStage.setWidth(restoreW);
            primaryStage.setHeight(restoreH);
            maximizedToWorkArea = false;
        }
        terminalCanvas.requestFocus();
    }

    private Screen getCurrentScreen() {
        Rectangle2D wnd = new Rectangle2D(
                primaryStage.getX(), primaryStage.getY(),
                Math.max(1, primaryStage.getWidth()),
                Math.max(1, primaryStage.getHeight())
        );
        return Screen.getScreensForRectangle(wnd).stream().findFirst().orElse(Screen.getPrimary());
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
