package org.msv.vt100.ui;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class ProfileManagerDialog {

    private final Stage dialog;
    private final BorderPane root;
    private final TabPane tabPane;
    private final Tab profilesTab;
    private final Tab settingsTab;
    private final TableView<SSHConfig> profileTable;
    private final ToggleGroup autoConnectToggleGroup;

    private TextField userField;
    private TextField hostField;
    private TextField portField;
    private TextField keyPathField;

    private SSHConfig editingProfile = null;
    private SSHConfig selectedProfile = null;

    public ProfileManagerDialog(Stage owner) {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("SSH-Verbindungsprofile");

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

        tabPane = new TabPane();
        tabPane.getStyleClass().add("custom-tab-pane");

        profilesTab = new Tab("Profile");
        profilesTab.setClosable(false);
        settingsTab = new Tab("Profileinstellungen");
        settingsTab.setClosable(false);

        autoConnectToggleGroup = new ToggleGroup();
        profileTable = new TableView<>();
        profileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        profileTable.setPlaceholder(new Label("Keine Profile verfügbar"));
        profileTable.getStyleClass().add("custom-table");

        TableColumn<SSHConfig, String> nameCol = new TableColumn<>("Name des Profils");
        nameCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().user()));

        TableColumn<SSHConfig, String> hostCol = new TableColumn<>("Verbindung");
        hostCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().host()));

        TableColumn<SSHConfig, Boolean> autoCol = new TableColumn<>("Automatische Verbindung");
        autoCol.setCellFactory(col -> new TableCell<>() {
            private final RadioButton radio = new RadioButton();
            {
                radio.setToggleGroup(autoConnectToggleGroup);
                radio.setOnAction(e -> SSHProfileManager.updateProfile(
                        new SSHConfig(
                                getTableView().getItems().get(getIndex()).user(),
                                getTableView().getItems().get(getIndex()).host(),
                                getTableView().getItems().get(getIndex()).port(),
                                getTableView().getItems().get(getIndex()).privateKeyPath(),
                                true
                        )));
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    radio.setSelected(getTableView().getItems().get(getIndex()).autoConnect());
                    setGraphic(radio);
                }
            }
        });

        TableColumn<SSHConfig, String> dateCol = new TableColumn<>("Letzte Verbindung");
        dateCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))));

        profileTable.getColumns().addAll(nameCol, hostCol, autoCol, dateCol);
        updateProfileList();

        profileTable.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                SSHConfig selected = profileTable.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectedProfile = selected;
                    dialog.close();
                }
            }
        });

        VBox tableBox = new VBox(10);
        tableBox.setPadding(new Insets(10));
        tableBox.getStyleClass().add("dialog-grid");
        HBox tableWithButtons = new HBox(10);
        tableWithButtons.getChildren().add(profileTable);
        HBox.setHgrow(profileTable, Priority.ALWAYS);

        VBox buttonBar = new VBox(10);
        buttonBar.setAlignment(Pos.TOP_RIGHT);
        buttonBar.setPadding(new Insets(5, 0, 0, 0));

        Button connectBtn = new Button("Verbinden");
        Button editBtn = new Button("Bearbeiten");
        Button deleteBtn = new Button("Löschen");

        for (Button b : List.of(connectBtn, editBtn, deleteBtn)) {
            b.getStyleClass().add("dialog-button");
            b.setDisable(true);
            b.setPrefWidth(100);
            b.setMinWidth(100);
        }

        connectBtn.setOnAction(e -> {
            SSHConfig selected = profileTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                selectedProfile = selected;
                dialog.close();
            }
        });

        editBtn.setOnAction(e -> {
            SSHConfig selected = profileTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                editingProfile = selected;
                populateSettingsFields(selected);
                tabPane.getSelectionModel().select(settingsTab);
            }
        });

        deleteBtn.setOnAction(e -> {
            SSHConfig selected = profileTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                SSHProfileManager.deleteProfile(selected);
                updateProfileList();
            }
        });

        profileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean enabled = newVal != null;
            connectBtn.setDisable(!enabled);
            editBtn.setDisable(!enabled);
            deleteBtn.setDisable(!enabled);
        });

        buttonBar.getChildren().addAll(connectBtn, editBtn, deleteBtn);
        tableWithButtons.getChildren().add(buttonBar);
        tableBox.getChildren().add(tableWithButtons);

        profilesTab.setContent(tableBox);
        buildSettingsTabContent();
        tabPane.getTabs().addAll(profilesTab, settingsTab);
        tabPane.getSelectionModel().select(profilesTab);

        root = new BorderPane();
        root.setTop(header);
        root.setCenter(tabPane);
        root.getStyleClass().add("root-dialog");

        Rectangle clip = new Rectangle(850, 400);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 850, 400);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().addAll(
                getClass().getResource("/org/msv/vt100/ui/styles/base.css").toExternalForm(),
                getClass().getResource("/org/msv/vt100/ui/styles/buttons.css").toExternalForm(),
                getClass().getResource("/org/msv/vt100/ui/styles/contextmenu.css").toExternalForm(),
                getClass().getResource("/org/msv/vt100/ui/styles/dialogs.css").toExternalForm(),
                getClass().getResource("/org/msv/vt100/ui/styles/listview.css").toExternalForm(),
                getClass().getResource("/org/msv/vt100/ui/styles/menu.css").toExternalForm(),
                getClass().getResource("/org/msv/vt100/ui/styles/tabs.css").toExternalForm(),
                getClass().getResource("/org/msv/vt100/ui/styles/table.css").toExternalForm()
        );
        dialog.setScene(scene);
    }

    private void updateProfileList() {
        profileTable.getItems().setAll(SSHProfileManager.getProfiles());
    }

    private void populateSettingsFields(SSHConfig profile) {
        userField.setText(profile.user());
        hostField.setText(profile.host());
        portField.setText(String.valueOf(profile.port()));
        keyPathField.setText(profile.privateKeyPath());
    }

    private void buildSettingsTabContent() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("dialog-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        userField = createLabeledTextField(grid, "Benutzer:", 0);
        hostField = createLabeledTextField(grid, "Host:", 1);
        portField = createLabeledTextField(grid, "Port:", 2);
        portField.setText("22");

        Label keyPathLabel = new Label("Schlüsselpfad:");
        keyPathLabel.getStyleClass().add("dialog-label-turquoise");
        keyPathField = new TextField();
        keyPathField.getStyleClass().add("dialog-text-field");
        keyPathField.setPrefWidth(300);

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
                        false
                );
                if (editingProfile == null) {
                    SSHProfileManager.addProfile(newProfile);
                } else {
                    SSHProfileManager.updateProfile(newProfile);
                }
                clearFields();
                updateProfileList();
                tabPane.getSelectionModel().select(profilesTab);
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "Port muss eine Zahl sein").showAndWait();
            }
        });

        Button cancelButton = new Button("Abbrechen");
        cancelButton.getStyleClass().add("dialog-button");
        cancelButton.setOnAction(e -> {
            clearFields();
            tabPane.getSelectionModel().select(profilesTab);
        });

        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        grid.add(buttonBox, 1, 4);

        settingsTab.setContent(grid);
    }

    private TextField createLabeledTextField(GridPane grid, String labelText, int row) {
        Label label = new Label(labelText);
        label.getStyleClass().add("dialog-label-turquoise");
        TextField textField = new TextField();
        textField.getStyleClass().add("dialog-text-field");
        textField.setPrefWidth(300);
        grid.add(label, 0, row);
        grid.add(textField, 1, row);
        return textField;
    }

    private void clearFields() {
        userField.clear();
        hostField.clear();
        portField.setText("22");
        keyPathField.clear();
    }

    public Optional<SSHConfig> showAndWait() {
        dialog.showAndWait();
        return Optional.ofNullable(selectedProfile);
    }

    public static Optional<SSHConfig> showDialog(Stage owner) {
        return new ProfileManagerDialog(owner).showAndWait();
    }
}