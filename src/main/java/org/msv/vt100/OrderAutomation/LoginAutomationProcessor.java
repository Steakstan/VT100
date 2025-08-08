package org.msv.vt100.OrderAutomation;

import org.msv.vt100.TerminalApp;
import org.msv.vt100.core.Cursor;
import org.msv.vt100.core.ScreenBuffer;
import org.msv.vt100.login.LoginProfile;
import org.msv.vt100.login.LoginProfileManager;
import org.msv.vt100.ssh.SSHManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.msv.vt100.util.Waiter.waitUntil;

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

        logger.info("Waiting for prompt 'Ihr Kurzzeichen:' in screen buffer...");
        boolean kurzzeichenOk = waitUntil("'Ihr Kurzzeichen:' im Bildschirmtext", () ->
                screenBuffer.toString().contains("Ihr Kurzzeichen:")
        );
        if (!kurzzeichenOk || terminalApp.isStopped()) {
            logger.info("Terminal stopped or timeout. Abort.");
            return;
        }

        logger.info("Waiting for cursor to reach position 15,45...");
        boolean cursorLogin = waitUntil("Cursor bei 15,45", () ->
                cursor.getCursorPosition().equals("15,36")
        );
        if (!cursorLogin || terminalApp.isStopped()) return;

        LoginProfile autoLoginProfile = LoginProfileManager.getAutoConnectProfile();
        if (autoLoginProfile != null) {
            logger.info("Auto-login profile found. Sending username: {}", autoLoginProfile.username());
            sshManager.send(autoLoginProfile.username());
            sshManager.send("\r");
        } else {
            logger.info("No auto-login profile marked. Skipping username entry.");
        }

        logger.info("Waiting for prompt 'Ihr  Schutzcode:' in screen buffer...");
        boolean schutzcodeOk = waitUntil("'Ihr  Schutzcode:' im Bildschirmtext", () ->
                screenBuffer.toString().contains("Ihr  Schutzcode:")
        );
        if (!schutzcodeOk || terminalApp.isStopped()) return;

        logger.info("Waiting for cursor to reach position 17,45...");
        boolean cursorPwd = waitUntil("Cursor bei 17,45", () ->
                cursor.getCursorPosition().equals("17,36")
        );
        if (!cursorPwd || terminalApp.isStopped()) return;

        if (autoLoginProfile != null) {
            logger.info("Sending password for auto-login profile.");
            sshManager.send(autoLoginProfile.password());
            sshManager.send("\r");
        }

        logger.info("Waiting for post-login prompt 'Bitte Eingabe-Taste druecken' and cursor at 23,55...");
        boolean postLoginPrompt = waitUntil("Bitte Eingabe-Taste druecken + Cursor 23,55", () ->
                screenBuffer.toString().contains("Bitte Eingabe-Taste druecken")
                        && cursor.getCursorPosition().equals("23,55")
        );
        if (postLoginPrompt && !terminalApp.isStopped()) {
            logger.info("Prompt detected. Sending Enter.");
            sshManager.send("\r");
        }

        autoLoginPerformed = true;
        logger.info("Auto login process completed.");
    }


}
