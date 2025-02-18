package org.msv.vt100.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.msv.vt100.ssh.SSHConfig;
import org.msv.vt100.ssh.SSHProfileManager;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class ProfileManagerDialog {

    private final Stage dialog;
    private final TabPane tabPane;
    private final Tab profilesTab;
    private final Tab settingsTab;
    private final VBox profilesBox;
    private final ToggleGroup autoConnectToggleGroup;

    // Поля для вкладки "Настройки профиля"
    private TextField userField;
    private TextField hostField;
    private TextField portField;
    private TextField keyPathField;

    // Если редактируем существующий профиль, то здесь хранится он, иначе null – создание нового
    private SSHConfig editingProfile = null;

    // Выбранный профиль (при нажатии "Подключиться")
    private SSHConfig selectedProfile = null;

    public ProfileManagerDialog(Stage owner) {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Профили подключения SSH");

        tabPane = new TabPane();
        profilesTab = new Tab("Профили");
        settingsTab = new Tab("Настройки профиля");
        settingsTab.setClosable(false);

        autoConnectToggleGroup = new ToggleGroup();

        profilesBox = new VBox(10);
        profilesBox.setPadding(new Insets(10));
        updateProfilesList();

        ScrollPane profilesScroll = new ScrollPane(profilesBox);
        profilesTab.setContent(profilesScroll);

        buildSettingsTabContent();

        tabPane.getTabs().addAll(profilesTab, settingsTab);
        tabPane.getSelectionModel().select(profilesTab);

        Scene scene = new Scene(tabPane, 850, 400);
        dialog.setScene(scene);
    }

    private void updateProfilesList() {
        profilesBox.getChildren().clear();
        List<SSHConfig> profiles = SSHProfileManager.getProfiles();
        for (SSHConfig profile : profiles) {
            final SSHConfig[] currentProfile = {profile};
            HBox profileRow = new HBox(10);
            profileRow.setAlignment(Pos.CENTER_LEFT);

            // Создаём метку с информацией и включаем перенос текста
            Label infoLabel = new Label(String.format("User: %s | Host: %s | Port: %d %s",
                    profile.user(), profile.host(), profile.port(),
                    profile.autoConnect() ? "(Автоподключение)" : ""));
            infoLabel.setWrapText(true);
            infoLabel.setMaxWidth(850); // Можно задать ширину, чтобы текст переносился

            Button connectButton = new Button("Подключиться");
            connectButton.setOnAction(e -> {
                // Если выбран переключатель автоподключения, обновляем профиль
                RadioButton selectedRadio = (RadioButton) autoConnectToggleGroup.getSelectedToggle();
                if (selectedRadio != null && selectedRadio.getUserData() instanceof SSHConfig) {
                    SSHConfig radioProfile = (SSHConfig) selectedRadio.getUserData();
                    if (radioProfile.equals(currentProfile[0]) && !currentProfile[0].autoConnect()) {
                        SSHConfig updated = new SSHConfig(
                                currentProfile[0].user(),
                                currentProfile[0].host(),
                                currentProfile[0].port(),
                                currentProfile[0].privateKeyPath(),
                                true
                        );
                        SSHProfileManager.updateProfile(updated);
                        currentProfile[0] = updated;
                        updateProfilesList();
                    }
                }
                // Перед подключением завершаем все текущие соединения (это происходит в TerminalApp.connectWithConfig)
                selectedProfile = currentProfile[0];
                dialog.close();
            });
            Button editButton = new Button("Редактировать");
            editButton.setOnAction(e -> {
                editingProfile = profile;
                populateSettingsFields(profile);
                tabPane.getSelectionModel().select(settingsTab);
            });
            Button deleteButton = new Button("Удалить");
            deleteButton.setOnAction(e -> {
                SSHProfileManager.deleteProfile(profile);
                updateProfilesList();
            });
            RadioButton autoRadio = new RadioButton("Автоподключение");
            autoRadio.setToggleGroup(autoConnectToggleGroup);
            autoRadio.setUserData(profile);
            if (profile.autoConnect()) {
                autoRadio.setSelected(true);
            }
            // Обработчик для отмены авто-подключения при повторном нажатии
            autoRadio.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (autoRadio.isSelected()) {
                    autoConnectToggleGroup.selectToggle(null); // Сброс выбора
                    SSHConfig updated = new SSHConfig(
                            profile.user(),
                            profile.host(),
                            profile.port(),
                            profile.privateKeyPath(),
                            false
                    );
                    SSHProfileManager.updateProfile(updated);
                    event.consume();
                }
            });
            profileRow.getChildren().addAll(infoLabel, connectButton, editButton, deleteButton, autoRadio);
            profilesBox.getChildren().add(profileRow);
        }

        Button createProfileButton = new Button("Создать профиль");
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

        Label userLabel = new Label("User:");
        userField = new TextField();
        userField.setPrefWidth(300);

        Label hostLabel = new Label("Host:");
        hostField = new TextField();
        hostField.setPrefWidth(300);

        Label portLabel = new Label("Port:");
        portField = new TextField("22");
        portField.setPrefWidth(300);

        Label keyPathLabel = new Label("KeyPath:");
        keyPathField = new TextField();
        keyPathField.setPrefWidth(300);
        Button browseButton = new Button("Выбрать");
        browseButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите SSH ключ");
            File selectedFile = fileChooser.showOpenDialog(dialog);
            if (selectedFile != null) {
                keyPathField.setText(selectedFile.getAbsolutePath());
            }
        });
        HBox keyPathBox = new HBox(5, keyPathField, browseButton);

        grid.add(userLabel, 0, 0);
        grid.add(userField, 1, 0);
        grid.add(hostLabel, 0, 1);
        grid.add(hostField, 1, 1);
        grid.add(portLabel, 0, 2);
        grid.add(portField, 1, 2);
        grid.add(keyPathLabel, 0, 3);
        grid.add(keyPathBox, 1, 3);

        Button saveButton = new Button("Сохранить");
        saveButton.setOnAction(e -> {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                SSHConfig newProfile = new SSHConfig(
                        userField.getText().trim(),
                        hostField.getText().trim(),
                        port,
                        keyPathField.getText().trim(),
                        false // автоподключение задается через переключатель в списке
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
                Alert alert = new Alert(AlertType.ERROR, "Порт должен быть числом.");
                alert.showAndWait();
            }
        });
        Button cancelButton = new Button("Отмена");
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
     * Показывает диалог и возвращает выбранный профиль (если пользователь нажал "Подключиться").
     */
    public Optional<SSHConfig> showAndWait() {
        dialog.showAndWait();
        return Optional.ofNullable(selectedProfile);
    }

    // Статический метод для удобного вызова окна
    public static Optional<SSHConfig> showDialog(Stage owner) {
        ProfileManagerDialog dialog = new ProfileManagerDialog(owner);
        return dialog.showAndWait();
    }
}
