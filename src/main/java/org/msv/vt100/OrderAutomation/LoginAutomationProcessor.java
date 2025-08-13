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
                logger.error("Login-Automatisierung unterbrochen", e);
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                logger.error("Fehler während der Login-Automatisierung", ex);
            }
        }, "LoginAutomationProcessorThread").start();
    }

    private void processAutoLogin() throws InterruptedException, IOException {
        if (autoLoginPerformed) {
            logger.info("Auto-Login bereits durchgeführt. Überspringe.");
            return;
        }

        logger.info("Warte auf Eingabeaufforderung 'Ihr Kurzzeichen:' im Bildschirmpuffer...");
        boolean kurzzeichenOk = waitUntil("'Ihr Kurzzeichen:' im Bildschirmtext", () ->
                screenBuffer.toString().contains("Ihr Kurzzeichen:")
        );
        if (!kurzzeichenOk || terminalApp.isStopped()) {
            logger.info("Terminal gestoppt oder Timeout. Abbruch.");
            return;
        }

        logger.info("Warte, bis der Cursor Position 15,36 erreicht...");
        boolean cursorLogin = waitUntil("Cursor bei 15,36", () ->
                cursor.getCursorPosition().equals("15,36")
        );
        if (!cursorLogin || terminalApp.isStopped()) return;

        LoginProfile autoLoginProfile = LoginProfileManager.getAutoConnectProfile();
        if (autoLoginProfile != null) {
            logger.info("Auto-Login-Profil gefunden. Sende Benutzername: {}", autoLoginProfile.username());
            sshManager.send(autoLoginProfile.username());
            sshManager.send("\r");
        } else {
            logger.info("Kein Auto-Login-Profil markiert. Überspringe Eingabe des Benutzernamens.");
        }

        logger.info("Warte auf Eingabeaufforderung 'Ihr  Schutzcode:' im Bildschirmpuffer...");
        boolean schutzcodeOk = waitUntil("'Ihr  Schutzcode:' im Bildschirmtext", () ->
                screenBuffer.toString().contains("Ihr  Schutzcode:")
        );
        if (!schutzcodeOk || terminalApp.isStopped()) return;

        logger.info("Warte, bis der Cursor Position 17,36 erreicht...");
        boolean cursorPwd = waitUntil("Cursor bei 17,36", () ->
                cursor.getCursorPosition().equals("17,36")
        );
        if (!cursorPwd || terminalApp.isStopped()) return;

        if (autoLoginProfile != null) {
            logger.info("Sende Passwort für Auto-Login-Profil.");
            sshManager.send(autoLoginProfile.password());
            sshManager.send("\r");
        }

        logger.info("Warte auf Post-Login-Aufforderung 'Bitte Eingabe-Taste druecken' und Cursor bei 23,55...");
        boolean postLoginPrompt = waitUntil("Bitte Eingabe-Taste druecken + Cursor 23,55", () ->
                screenBuffer.toString().contains("Bitte Eingabe-Taste druecken")
                        && cursor.getCursorPosition().equals("23,55")
        );
        if (postLoginPrompt && !terminalApp.isStopped()) {
            logger.info("Aufforderung erkannt. Sende Enter.");
            sshManager.send("\r");
        }

        autoLoginPerformed = true;
        logger.info("Auto-Login-Prozess abgeschlossen.");
    }


}
