package org.msv.vt100.ui;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.msv.vt100.TerminalApp;

import java.util.ArrayList;
import java.util.List;

public class OptionalMenuBar extends HBox {

    private Popup currentPopup = null;
    private Button currentPopupButton = null;
    private final TerminalApp terminalApp;

    public OptionalMenuBar(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;

        setSpacing(1);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("custom-tab-header");

        Button btnDatei = createMenuButton("Datei");
        Button btnBearbeiten = createMenuButton("Bearbeiten");
        Button btnLog = createMenuButton("Log");

        btnDatei.setOnAction(e -> togglePopup(btnDatei));
        btnBearbeiten.setOnAction(e -> togglePopup(btnBearbeiten));
        btnLog.setOnAction(e -> togglePopup(btnLog));

        setupHoverToggle(btnDatei);
        setupHoverToggle(btnBearbeiten);
        setupHoverToggle(btnLog);

        getChildren().addAll(btnDatei, btnBearbeiten, btnLog);
    }

    private Button createMenuButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().setAll("menu-button");
        HBox.setHgrow(button, Priority.NEVER);
        return button;
    }

    private void setupHoverToggle(Button button) {
        button.setOnMouseEntered(e -> {
            if (currentPopup != null && currentPopupButton != button) {
                togglePopup(button);
            }
        });
    }

    private void togglePopup(Button button) {
        if (currentPopup != null && currentPopupButton == button) {
            closePopup();
        } else {
            closePopup();

            switch (button.getText()) {
                case "Datei" -> currentPopup = createPopupMenu(
                        new MenuEntry[]{
                                new MenuEntry("⚙ Verbindung Einstellungen", terminalApp::openProfileDialog),
                                new MenuEntry("🔄 Verbindung neu starten", terminalApp::restartConnection),
                                new MenuEntry("🔐 Login‑Einstellungen", terminalApp::openLoginSettingsDialog),
                                MenuEntry.separator(),
                                new MenuEntry("⛔ Verbindung abfallen", terminalApp::disconnectConnection)
                        });

                case "Bearbeiten" -> currentPopup = createPopupMenu(
                        new MenuEntry[]{
                                new MenuEntry("📝 Bearbeitungseinstellungen", terminalApp::openBearbeitungseinstellungenDialog),
                                new MenuEntry("🔍 Positionssuche", terminalApp::openPositionssucheDialog)
                        });

                case "Log" -> currentPopup = createPopupMenu(
                        new MenuEntry[]{
                                new MenuEntry("📄 Log Einstellungen", () -> new LogSettingsDialog(terminalApp).show())
                        });

                default -> currentPopup = createPopupMenu(
                        new MenuEntry[]{new MenuEntry("❓ Nicht realisiert", () -> {})}
                );
            }

            currentPopup.setAutoHide(true);

            double x = button.localToScreen(button.getBoundsInLocal()).getMinX();
            double y = button.localToScreen(button.getBoundsInLocal()).getMaxY();
            currentPopup.show(button, x, y);
            currentPopupButton = button;
        }
    }

    private void closePopup() {
        if (currentPopup != null) {
            currentPopup.hide();
            currentPopup = null;
            currentPopupButton = null;
        }
    }

    private Popup createPopupMenu(MenuEntry[] entries) {
        Popup popup = new Popup();
        VBox content = new VBox(5);
        content.getStyleClass().add("popup-container");

        List<Button> buttons = new ArrayList<>();

        for (MenuEntry entry : entries) {
            if (entry.isSeparator()) {
                Separator separator = new Separator();
                separator.setOpacity(0.3);
                content.getChildren().add(separator);
                continue;
            }

            Button btn = new Button(entry.label());
            btn.getStyleClass().add("menu-item");
            btn.setOnAction(e -> {
                entry.action().run();
                popup.hide();
            });
            buttons.add(btn);
            content.getChildren().add(btn);
        }

        for (Button b : buttons) {
            b.applyCss();
            b.layout();
        }

        double maxWidth = buttons.stream()
                .mapToDouble(this::computeTextWidth)
                .max()
                .orElse(0) + 50;

        for (Button b : buttons) {
            b.setPrefWidth(maxWidth);
        }

        // 👇 Анимация появления popup
        content.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(120), content);
        ft.setToValue(1.0);
        ft.play();

        popup.getContent().add(content);
        return popup;
    }

    private double computeTextWidth(Button button) {
        Text text = new Text(button.getText());
        text.setFont(button.getFont());

        double dpiScale = button.getScene() != null
                ? button.getScene().getWindow().getRenderScaleX()
                : 1.0;

        Insets padding = button.getPadding();
        double paddingSum = (padding != null) ? padding.getLeft() + padding.getRight() : 0;

        return text.getLayoutBounds().getWidth() * dpiScale + paddingSum;
    }
}
