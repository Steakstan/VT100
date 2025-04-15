package org.msv.vt100.OrderAutomation;

import javafx.application.Platform;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.login.LoginProfile;
import org.msv.vt100.login.LoginProfileManager;
import org.msv.vt100.ssh.SSHManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class LoginAutomationProcessor {
    private static final Logger logger = LoggerFactory.getLogger(LoginAutomationProcessor.class);
    private final TerminalApp terminalApp;
    private final SSHManager sshManager;
    private final ScreenBuffer screenBuffer;
    private final Cursor cursor;

    // Флаг, что автоматический логин уже выполнен (гарантирует однократное срабатывание)
    private static volatile boolean autoLoginPerformed = false;

    public LoginAutomationProcessor(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
        this.sshManager = terminalApp.getSSHManager();
        this.screenBuffer = terminalApp.getScreenBuffer();
        this.cursor = terminalApp.getCursor();
    }

    public void startAutoLogin() {
        new Thread(() -> {
            try {
                processAutoLogin();
            } catch (InterruptedException e) {
                logger.error("Login automation interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                logger.error("Error during login automation", ex);
            }
        }, "LoginAutomationProcessorThread").start();
    }

    private void processAutoLogin() throws InterruptedException, IOException {
        if (autoLoginPerformed) {
            logger.info("Auto login already performed. Skipping.");
            return;
        }

        // Ожидаем появления строки "Ihr Kurzzeichen:" в буфере экрана
        logger.info("Waiting for prompt 'Ihr Kurzzeichen:' in screen buffer...");
        while (!screenBuffer.toString().contains("Ihr Kurzzeichen:")) {
            if (terminalApp.isStopped()) {
                logger.info("Terminal processing stopped. Aborting auto login.");
                return;
            }
            Thread.sleep(500);
        }
        logger.info("Prompt 'Ihr Kurzzeichen:' detected.");

        // Ожидаем, пока курсор не окажется на позиции 15,45
        logger.info("Waiting for cursor to reach position 15,45...");
        while (!getCursorPosition().equals("15,45")) {
            if (terminalApp.isStopped()) {
                logger.info("Terminal processing stopped. Aborting auto login.");
                return;
            }
            Thread.sleep(500);
        }
        logger.info("Cursor is at position 15,45.");

        // Получаем профиль логина, отмеченный как автоподключаемый
        LoginProfile autoLoginProfile = LoginProfileManager.getAutoConnectProfile();
        if (autoLoginProfile != null) {
            logger.info("Auto-login profile found. Sending username: {}", autoLoginProfile.username());
            sshManager.send(autoLoginProfile.username());
            sshManager.send("\r");
        } else {
            logger.info("No auto-login profile marked. Skipping username entry.");
        }

        // Ожидаем появления строки "Ihr  Schutzcode:" в буфере экрана
        logger.info("Waiting for prompt 'Ihr  Schutzcode:' in screen buffer...");
        while (!screenBuffer.toString().contains("Ihr  Schutzcode:")) {
            if (terminalApp.isStopped()) {
                logger.info("Terminal processing stopped. Aborting auto login.");
                return;
            }
            Thread.sleep(500);
        }
        logger.info("Prompt 'Ihr  Schutzcode:' detected.");

        // Ожидаем, пока курсор не окажется на позиции 17,45
        logger.info("Waiting for cursor to reach position 17,45...");
        while (!getCursorPosition().equals("17,45")) {
            if (terminalApp.isStopped()) {
                logger.info("Terminal processing stopped. Aborting auto login.");
                return;
            }
            Thread.sleep(500);
        }
        logger.info("Cursor is at position 17,45.");

        // Если профиль найден, отправляем пароль
        if (autoLoginProfile != null) {
            logger.info("Sending password for auto-login profile.");
            sshManager.send(autoLoginProfile.password());
            sshManager.send("\r");
        } else {
            logger.info("No auto-login profile available. Skipping password entry.");
        }

        // Дополнительная проверка: если в буфере отображается "Bitte Eingabe-Taste druecken"
        // и курсор находится на позиции 23,55, то отправляем Enter
        logger.info("Waiting for post-login prompt 'Bitte Eingabe-Taste druecken' and cursor at 23,55...");
        while (!terminalApp.isStopped() &&
                (!screenBuffer.toString().contains("Bitte Eingabe-Taste druecken") ||
                        !getCursorPosition().equals("23,55"))) {
            Thread.sleep(500);
        }
        if (!terminalApp.isStopped()) {
            logger.info("Post-login prompt detected with cursor at 23,55. Sending Enter.");
            sshManager.send("\r");
        }

        autoLoginPerformed = true;
        logger.info("Auto login process completed.");
    }

    private String getCursorPosition() throws InterruptedException {
        final String[] position = new String[1];
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            position[0] = (cursor.getRow() + 1) + "," + (cursor.getColumn() + 1);
            latch.countDown();
        });
        latch.await();
        return position[0];
    }
}
