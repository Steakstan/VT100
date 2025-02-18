package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.InputHandler;
import org.msv.vt100.ssh.SSHManager;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.core.ScreenBuffer;

/**
 * UIController отвечает за инициализацию и обновление всех элементов JavaFX‑интерфейса.
 * Он создаёт главное окно терминала (CustomTerminalWindow), прикрепляет обработчики ввода
 * и предоставляет методы для обновления UI (например, обновление канвы, установка фокуса).
 */
public class UIController {

    private final Stage primaryStage;
    private final TerminalCanvas terminalCanvas;
    private final CustomTerminalWindow customTerminalWindow;

    /**
     * Конструктор UIController.
     *
     * @param primaryStage основной Stage JavaFX
     * @param terminalApp  ссылка на координатора (TerminalApp)
     * @param screenBuffer экранный буфер для терминала
     * @param sshManager   экземпляр SSHManager (для передачи в InputHandler)
     * @param cursor       объект курсора, используемый в терминале
     */
    public UIController(Stage primaryStage, TerminalApp terminalApp,
                        ScreenBuffer screenBuffer, SSHManager sshManager, Cursor cursor) {
        this.primaryStage = primaryStage;
        this.customTerminalWindow = new CustomTerminalWindow(primaryStage, terminalApp, screenBuffer);
        // Создаём главное окно терминала, которое инкапсулирует все UI-элементы
        this.terminalCanvas = this.customTerminalWindow.getTerminalCanvas();

        // Инициализируем обработчик ввода и прикрепляем его к канве
        InputHandler inputHandler = new InputHandler(terminalApp, sshManager, screenBuffer, cursor);
        terminalCanvas.setOnKeyPressed(inputHandler::handleKeyPressed);
        terminalCanvas.setOnKeyTyped(inputHandler::handleKeyTyped);

        // Настраиваем сцену и окно через CustomTerminalWindow
        customTerminalWindow.configureStage(primaryStage);
    }

    /**
     * Показывает основное окно приложения и устанавливает фокус на канве.
     */
    public void show() {
        primaryStage.show();
        // Гарантируем, что после отображения окна фокус переходит на канву терминала
        Platform.runLater(terminalCanvas::requestFocus);
    }

    /**
     * Возвращает ссылку на TerminalCanvas.
     *
     * @return объект TerminalCanvas
     */
    public TerminalCanvas getTerminalCanvas() {
        return terminalCanvas;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }
    public ContentPanel getContentPanel() {

        return customTerminalWindow.getContentPanel();
    }
}
