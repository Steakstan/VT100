package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.core.FileProcessingService;

import java.io.File;

public class ContentPanel extends BorderPane {

    private final ComboBox<String> operationComboBox;
    private final TextField filePathField;
    private final Button browseButton;
    private final Button startProcessingButton;
    private final Button pauseButton;
    private final Button stopButton;
    private final Button logToggleButton; // Добавлено
    private final Stage primaryStage;
    private final TerminalApp terminalApp;
    private Thread processingThread;
    private HBox processingButtons;

    public ContentPanel(Stage primaryStage, TerminalApp terminalApp) {
        this.primaryStage = primaryStage;
        this.terminalApp = terminalApp;

        // Инициализация компонентов
        operationComboBox = createOperationComboBox();
        filePathField = createFilePathField();
        browseButton = createBrowseButton();
        startProcessingButton = createStartProcessingButton();
        pauseButton = createPauseButton();
        stopButton = createStopButton();
        logToggleButton = createLogToggleButton(); // Добавлено

        // Создаём основной контент
        VBox mainContent = new VBox();
        mainContent.setSpacing(10);
        mainContent.setPadding(new Insets(10));
        mainContent.setAlignment(Pos.CENTER_LEFT);
        mainContent.getStyleClass().add("content-panel");

        // Добавляем элементы в основной контент
        mainContent.getChildren().addAll(
                createOperationSelection(),
                createFileSelection(),
                createProcessingButtons()
        );

        // Устанавливаем основной контент в центр BorderPane
        this.setCenter(mainContent);
    }

    private ComboBox<String> createOperationComboBox() {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(
                "AB-Verarbeitung",
                "Kommentare verarbeiten",
                "Kommentare für Lagerbestellung verarbeiten",
                "Liefertermine verarbeiten"
        );
        comboBox.getStyleClass().add("combo-box");
        comboBox.setPrefWidth(400); // Adjust as needed
        return comboBox;
    }

    private TextField createFilePathField() {
        TextField textField = new TextField();
        textField.getStyleClass().add("text-field");
        textField.setPrefWidth(300);
        return textField;
    }

    private Button createBrowseButton() {
        Button button = new Button("Durchsuchen");
        button.getStyleClass().add("rounded-button");
        button.setOnAction(e -> showFileChooser());
        return button;
    }

    private Button createStartProcessingButton() {
        Button button = new Button("Verarbeitung starten");
        button.getStyleClass().add("rounded-button");
        button.setOnAction(e -> startProcessing());
        return button;
    }



    private Button createPauseButton() {
        Button button = new Button("Pause");
        button.getStyleClass().add("rounded-button");
        button.setOnAction(e -> pauseOrResumeProcessing());
        button.setVisible(false); // Initially hidden
        return button;
    }

    private Button createStopButton() {
        Button button = new Button("Stop");
        button.getStyleClass().add("rounded-button");
        button.setOnAction(e -> stopProcessing());
        button.setVisible(false); // Initially hidden
        return button;
    }

    private Button createLogToggleButton() {
        Button button = new Button("Log on");
        button.getStyleClass().add("rounded-button");
        button.setOnAction(e -> toggleLogging());
        return button;
    }

    private void toggleLogging() {
        if (logToggleButton.getText().equals("Log on")) {
            terminalApp.enableLogging();
            logToggleButton.setText("Log off");
        } else {
            terminalApp.disableLogging();
            logToggleButton.setText("Log on");
        }
    }

    private HBox createOperationSelection() {
        HBox hbox = new HBox(10);
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setPadding(new Insets(5));
        hbox.setPrefWidth(712); // Allow HBox to fill width
        //hbox.setBackground(Background.); // Make background transparent

        Label label = createLabel("Operation:");
        hbox.getChildren().addAll(label, operationComboBox);

        // Spacer to push the button to the right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        hbox.getChildren().addAll(spacer, logToggleButton);

        return hbox;
    }


    private HBox createFileSelection() {
        HBox hbox = new HBox(10);
        hbox.setAlignment(Pos.CENTER_LEFT);

        Label label = createLabel("Datei auswählen:");
        hbox.getChildren().addAll(label, filePathField, browseButton);

        return hbox;
    }

    private HBox createProcessingButtons() {
        processingButtons = new HBox(10); // Initialize the instance variable
        processingButtons.setAlignment(Pos.CENTER_LEFT);

        processingButtons.getChildren().add(startProcessingButton); // Initially add only the start button

        return processingButtons;
    }


    private Label createLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("label-white");
        return label;
    }

    private void showFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));
        fileChooser.setTitle("Datei auswählen");
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            filePathField.setText(selectedFile.getAbsolutePath());
        }
    }

    private void startProcessing() {
        int choice = operationComboBox.getSelectionModel().getSelectedIndex() + 1;
        String filePath = filePathField.getText();

        if (filePath.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Fehler", "Bitte wählen Sie eine Datei aus.");
            return;
        }

        // Replace start button with pause and stop buttons
        Platform.runLater(() -> {
            processingButtons.getChildren().clear();
            processingButtons.getChildren().addAll(pauseButton, stopButton);
            pauseButton.setVisible(true);
            stopButton.setVisible(true);
        });

        // Run processing in a new thread to avoid freezing the UI
        processingThread = new Thread(() -> {
            try {
                FileProcessingService.processFile(choice, filePath);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Erfolg", "Verarbeitung abgeschlossen.");
                    resetButtons();
                });
            } catch (InterruptedException e) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Info", "Verarbeitung gestoppt.");
                    resetButtons();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Fehler", "Fehler bei der Verarbeitung der Datei: " + e.getMessage());
                    e.printStackTrace();
                    resetButtons();
                });
            }
        });
        processingThread.start();
    }



    private void pauseOrResumeProcessing() {
        if (pauseButton.getText().equals("Pause")) {
            terminalApp.pauseProcessing();
            pauseButton.setText("Fortsetzen");
        } else {
            terminalApp.resumeProcessing();
            pauseButton.setText("Pause");
        }
    }

    private void stopProcessing() {
        terminalApp.stopProcessing();
        if (processingThread != null) {
            processingThread.interrupt();
        }
        resetButtons(); // This call is now safe because it uses Platform.runLater()
    }



    private void resetButtons() {
        Platform.runLater(() -> {
            processingButtons.getChildren().clear();
            processingButtons.getChildren().add(startProcessingButton);
            pauseButton.setText("Pause");
            pauseButton.setVisible(false);
            stopButton.setVisible(false);
        });
    }



    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.initOwner(primaryStage);
        alert.showAndWait();
    }
}
