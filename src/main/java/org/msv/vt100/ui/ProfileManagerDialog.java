package org.msv.vt100.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.msv.vt100.ssh.SSHConfig;
import org.msv.vt100.ssh.SSHProfileManager;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class ProfileManagerDialog {

    private final Stage dialog;
    private final BorderPane root;
    private final TabPane tabPane;
    private final Tab profilesTab;
    private final Tab settingsTab;
    private final VBox profilesBox;
    private final ToggleGroup autoConnectToggleGroup;

    // Fields for the settings tab
    private TextField userField;
    private TextField hostField;
    private TextField portField;
    private TextField keyPathField;

    // If editing an existing profile, it is stored here; otherwise null for new profile creation
    private SSHConfig editingProfile = null;

    // The selected profile (when the user clicks "Verbinden")
    private SSHConfig selectedProfile = null;

    public ProfileManagerDialog(Stage owner) {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        // Transparent window style to simulate LogSettingsDialog
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("SSH-Verbindungsprofile");

        // Create a header with a title and close button – similar to LogSettingsDialog
        HBox header = new HBox();
        header.getStyleClass().add("dialog-header");
        Label titleLabel = new Label("SSH-Verbindungsprofile");
        titleLabel.getStyleClass().add("dialog-header-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button headerCloseButton = new Button("X");
        headerCloseButton.getStyleClass().add("dialog-header-close-button");
        headerCloseButton.setOnAction(e -> dialog.close());
        header.getChildren().addAll(titleLabel, spacer, headerCloseButton);

        // Create a TabPane with two tabs: profile list and profile settings
        tabPane = new TabPane();
        tabPane.getStyleClass().add("custom-tab-pane");
        profilesTab = new Tab("Profile");
        profilesTab.getStyleClass().add("tab-header-area-button");
        profilesTab.setClosable(false);

        settingsTab = new Tab("Profileinstellungen");
        settingsTab.getStyleClass().add("tab-header-area-button");
        settingsTab.setClosable(false);

        autoConnectToggleGroup = new ToggleGroup();
        profilesBox = new VBox(10);
        profilesBox.setPadding(new Insets(10));
        // Set the same background for the "Profile" tab
        profilesBox.setStyle("-fx-background-color: rgba(0,43,54); -fx-background-radius: 0 0 15 15;");
        updateProfilesList();

        ScrollPane profilesScroll = new ScrollPane(profilesBox);
        profilesScroll.setFitToWidth(true);
        profilesScroll.setFitToHeight(true);
        profilesTab.setContent(profilesScroll);

        buildSettingsTabContent();

        tabPane.getTabs().addAll(profilesTab, settingsTab);
        tabPane.getSelectionModel().select(profilesTab);

        // Assemble the root container – header on top and TabPane in the center
        root = new BorderPane();
        root.setTop(header);
        root.setCenter(tabPane);
        // Add an external style for the border, similar to Log Einstellungen
        root.getStyleClass().add("root-dialog");

        // Apply a clip with rounded corners
        Rectangle clip = new Rectangle(850, 400);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 850, 400);
        scene.setFill(Color.TRANSPARENT);
        // Load CSS styles
        scene.getStylesheets().add(getClass().getResource("/org/msv/vt100/ui/styles.css").toExternalForm());
        dialog.setScene(scene);
    }

    private void updateProfilesList() {
        profilesBox.getChildren().clear();
        List<SSHConfig> profiles = SSHProfileManager.getProfiles();

        for (SSHConfig profile : profiles) {
            // Create a final copy to use in lambdas
            final SSHConfig currentProfile = profile;

            HBox profileRow = new HBox(10);
            profileRow.setAlignment(Pos.CENTER_LEFT);
            // Optionally add style for each row
            profileRow.setStyle("-fx-background-color: transparent;");

            Label infoLabel = new Label(String.format("Benutzer: %s | Host: %s | Port: %d %s",
                    currentProfile.user(), currentProfile.host(), currentProfile.port(),
                    currentProfile.autoConnect() ? "(Automatische Verbindung)" : ""));
            // Apply style for inactive text (turquoise)
            infoLabel.getStyleClass().add("dialog-label-turquoise");
            infoLabel.setWrapText(true);
            infoLabel.setMaxWidth(600);

            Button connectButton = new Button("Verbinden");
            connectButton.getStyleClass().add("dialog-button");
            connectButton.setOnAction(e -> {
                RadioButton selectedRadio = (RadioButton) autoConnectToggleGroup.getSelectedToggle();
                if (selectedRadio != null && selectedRadio.getUserData() instanceof SSHConfig) {
                    SSHConfig radioProfile = (SSHConfig) selectedRadio.getUserData();
                    if (radioProfile.equals(currentProfile) && !currentProfile.autoConnect()) {
                        SSHConfig updated = new SSHConfig(
                                currentProfile.user(),
                                currentProfile.host(),
                                currentProfile.port(),
                                currentProfile.privateKeyPath(),
                                true
                        );
                        SSHProfileManager.updateProfile(updated);
                        updateProfilesList();
                        return; // End processing since the list has been updated
                    }
                }
                selectedProfile = currentProfile;
                dialog.close();
            });

            Button editButton = new Button("Bearbeiten");
            editButton.getStyleClass().add("dialog-button");
            editButton.setOnAction(e -> {
                editingProfile = currentProfile;
                populateSettingsFields(currentProfile);
                tabPane.getSelectionModel().select(settingsTab);
            });

            Button deleteButton = new Button("Löschen");
            deleteButton.getStyleClass().add("dialog-button");
            deleteButton.setOnAction(e -> {
                SSHProfileManager.deleteProfile(currentProfile);
                updateProfilesList();
            });

            RadioButton autoRadio = new RadioButton("Automatische Verbindung");
            autoRadio.getStyleClass().add("dialog-label-turquoise");
            autoRadio.setToggleGroup(autoConnectToggleGroup);
            autoRadio.setUserData(currentProfile);
            if (currentProfile.autoConnect()) {
                autoRadio.setSelected(true);
            }
            // If the selected toggle is pressed again, disable auto-connect
            autoRadio.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (autoRadio.isSelected()) {
                    autoConnectToggleGroup.selectToggle(null);
                    SSHConfig updated = new SSHConfig(
                            currentProfile.user(),
                            currentProfile.host(),
                            currentProfile.port(),
                            currentProfile.privateKeyPath(),
                            false
                    );
                    SSHProfileManager.updateProfile(updated);
                    event.consume();
                }
            });

            profileRow.getChildren().addAll(infoLabel, connectButton, editButton, deleteButton, autoRadio);
            profilesBox.getChildren().add(profileRow);
        }

        Button createProfileButton = new Button("Profil erstellen");
        createProfileButton.getStyleClass().add("dialog-button");
        createProfileButton.setOnAction(e -> {
            editingProfile = null;
            clearSettingsFields();
            tabPane.getSelectionModel().select(settingsTab);
        });
        profilesBox.getChildren().add(createProfileButton);
    }

    private void buildSettingsTabContent() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        // Set the same background as in the "Profile" tab
        grid.setStyle("-fx-background-color: rgba(0,43,54,0.95);");

        Label userLabel = new Label("Benutzer:");
        userLabel.getStyleClass().add("dialog-label-turquoise");
        userField = new TextField();
        userField.setPrefWidth(300);
        userField.getStyleClass().add("dialog-text-field");

        Label hostLabel = new Label("Host:");
        hostLabel.getStyleClass().add("dialog-label-turquoise");
        hostField = new TextField();
        hostField.setPrefWidth(300);
        hostField.getStyleClass().add("dialog-text-field");

        Label portLabel = new Label("Port:");
        portLabel.getStyleClass().add("dialog-label-turquoise");
        portField = new TextField("22");
        portField.setPrefWidth(300);
        portField.getStyleClass().add("dialog-text-field");

        Label keyPathLabel = new Label("Schlüsselpfad:");
        keyPathLabel.getStyleClass().add("dialog-label-turquoise");
        keyPathField = new TextField();
        keyPathField.setPrefWidth(300);
        keyPathField.getStyleClass().add("dialog-text-field");
        Button browseButton = new Button("Auswählen");
        browseButton.getStyleClass().add("dialog-button");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Wählen Sie einen SSH-Schlüssel aus");
            File selectedFile = fileChooser.showOpenDialog(dialog);
            if (selectedFile != null) {
                keyPathField.setText(selectedFile.getAbsolutePath());
            }
        });
        HBox keyPathBox = new HBox(5, keyPathField, browseButton);
        keyPathBox.setAlignment(Pos.CENTER_LEFT);

        grid.add(userLabel, 0, 0);
        grid.add(userField, 1, 0);
        grid.add(hostLabel, 0, 1);
        grid.add(hostField, 1, 1);
        grid.add(portLabel, 0, 2);
        grid.add(portField, 1, 2);
        grid.add(keyPathLabel, 0, 3);
        grid.add(keyPathBox, 1, 3);

        Button saveButton = new Button("Speichern");
        saveButton.getStyleClass().add("dialog-button");
        saveButton.setOnAction(e -> {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                SSHConfig newProfile = new SSHConfig(
                        userField.getText().trim(),
                        hostField.getText().trim(),
                        port,
                        keyPathField.getText().trim(),
                        false // Auto-connect is set via the toggle in the list
                );
                if (editingProfile == null) {
                    SSHProfileManager.addProfile(newProfile);
                } else {
                    SSHProfileManager.updateProfile(newProfile);
                }
                clearSettingsFields();
                editingProfile = null;
                updateProfilesList();
                tabPane.getSelectionModel().select(profilesTab);
            } catch (NumberFormatException ex) {
                Alert alert = new Alert(AlertType.ERROR, "Port muss eine Zahl sein.");
                alert.showAndWait();
            }
        });
        Button cancelButton = new Button("Abbrechen");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setOnAction(e -> {
            clearSettingsFields();
            editingProfile = null;
            tabPane.getSelectionModel().select(profilesTab);
        });
        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        grid.add(buttonBox, 1, 4);

        settingsTab.setContent(grid);
    }

    private void populateSettingsFields(SSHConfig profile) {
        userField.setText(profile.user());
        hostField.setText(profile.host());
        portField.setText(String.valueOf(profile.port()));
        keyPathField.setText(profile.privateKeyPath());
    }

    private void clearSettingsFields() {
        userField.clear();
        hostField.clear();
        portField.setText("22");
        keyPathField.clear();
    }

    /**
     * Shows the dialog and returns the selected profile (if the user clicked "Verbinden").
     *
     * @return an Optional containing the selected SSHConfig, if any.
     */
    public Optional<SSHConfig> showAndWait() {
        dialog.showAndWait();
        return Optional.ofNullable(selectedProfile);
    }

    /**
     * Static method for convenient dialog invocation.
     *
     * @param owner the owner Stage.
     * @return an Optional containing the selected SSHConfig, if any.
     */
    public static Optional<SSHConfig> showDialog(Stage owner) {
        ProfileManagerDialog dialog = new ProfileManagerDialog(owner);
        return dialog.showAndWait();
    }
}
