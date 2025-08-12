package org.msv.vt100.ui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.msv.vt100.TerminalApp;
import org.msv.vt100.login.LoginProfile;
import org.msv.vt100.login.LoginProfileManager;
import org.msv.vt100.util.DialogHelper;

import java.util.List;
import java.util.Objects;

public class LoginSettingsDialog {

    private final Stage dialog;
    private final TableView<LoginProfile> profileTable;
    private final ToggleGroup autoConnectGroup = new ToggleGroup();
    private final TextField profileNameField;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final TerminalApp terminalApp;

    public LoginSettingsDialog(TerminalApp terminalApp) {
        this.terminalApp = terminalApp;
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.setTitle("Login‑Einstellungen");

        HBox header = new HBox();
        header.getStyleClass().add("dialog-header");
        Label titleLabel = new Label("Login‑Einstellungen");
        titleLabel.getStyleClass().add("dialog-header-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeButton = new Button("X");
        closeButton.getStyleClass().add("dialog-header-close-button");
        closeButton.setOnAction(e -> dialog.close());
        header.getChildren().addAll(titleLabel, spacer, closeButton);

        GridPane inputGrid = new GridPane();
        inputGrid.setHgap(10);
        inputGrid.setVgap(10);
        inputGrid.setPadding(new Insets(10));
        Label profileNameLabel = new Label("Profilname:");
        profileNameLabel.getStyleClass().add("dialog-label-turquoise");
        profileNameField = new TextField();
        profileNameField.getStyleClass().add("dialog-text-field");

        Label usernameLabel = new Label("Benutzername:");
        usernameLabel.getStyleClass().add("dialog-label-turquoise");
        usernameField = new TextField();
        usernameField.getStyleClass().add("dialog-text-field");

        Label passwordLabel = new Label("Passwort:");
        passwordLabel.getStyleClass().add("dialog-label-turquoise");
        passwordField = new PasswordField();
        passwordField.getStyleClass().add("dialog-text-field");

        Button addButton = new Button("Profil erstellen");
        addButton.getStyleClass().add("dialog-button");
        addButton.setOnAction(e -> addProfile());

        Button deleteButton = new Button("Profil löschen");
        deleteButton.getStyleClass().add("dialog-button");
        deleteButton.setOnAction(e -> deleteSelectedProfile());

        inputGrid.add(profileNameLabel, 0, 0);
        inputGrid.add(profileNameField, 1, 0);
        inputGrid.add(usernameLabel, 0, 1);
        inputGrid.add(usernameField, 1, 1);
        inputGrid.add(passwordLabel, 0, 2);
        inputGrid.add(passwordField, 1, 2);
        HBox buttonBox = new HBox(10, addButton, deleteButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        inputGrid.add(buttonBox, 1, 3);

        profileTable = new TableView<>();
        profileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        profileTable.setPlaceholder(new Label("Keine Profile verfügbar"));
        profileTable.getStyleClass().add("custom-table");
        profileTable.setPrefWidth(400);

        TableColumn<LoginProfile, String> nameCol = new TableColumn<>("Profilname");
        nameCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().profileName()));

        TableColumn<LoginProfile, String> userCol = new TableColumn<>("Benutzername");
        userCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().username()));

        TableColumn<LoginProfile, Boolean> autoCol = new TableColumn<>("Automatisch verbinden");
        autoCol.setCellFactory(col -> new TableCell<>() {
            private final RadioButton radio = new RadioButton();
            {
                radio.setToggleGroup(autoConnectGroup);
                radio.setOnAction(e -> {
                    LoginProfile item = getTableView().getItems().get(getIndex());
                    LoginProfileManager.updateProfile(new LoginProfile(
                            item.profileName(), item.username(), item.password(), true));
                    updateProfileList();
                });
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    LoginProfile itemData = getTableView().getItems().get(getIndex());
                    radio.setSelected(itemData.autoConnect());
                    setGraphic(radio);
                }
            }
        });

        profileTable.getColumns().addAll(nameCol, userCol, autoCol);
        updateProfileList();

        VBox tableBox = new VBox(profileTable);
        tableBox.setPadding(new Insets(10));

        HBox contentRow = new HBox(10, inputGrid, tableBox);
        contentRow.setPadding(new Insets(10));

        StackPane contentPanel = new StackPane();
        contentPanel.getStyleClass().add("dialog-grid");
        contentPanel.getChildren().add(contentRow);

        VBox root = new VBox();
        root.setSpacing(0);
        root.getChildren().addAll(header, contentPanel);
        root.setBackground(new Background(new BackgroundFill(Color.rgb(0, 43, 54, 0), null, null)));

        Rectangle clip = new Rectangle(900, 480);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        root.setClip(clip);

        Scene scene = new Scene(root, 900, 480);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().addAll(
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/base.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/buttons.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/dialogs.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/listview.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/org/msv/vt100/ui/styles/table.css")).toExternalForm()
        );

        dialog.setScene(scene);
        DialogHelper.centerDialogOnOwner(dialog, terminalApp.getUIController().getPrimaryStage());
        DialogHelper.enableDragging(dialog, header);
    }

    private void updateProfileList() {
        List<LoginProfile> profiles = LoginProfileManager.loadProfiles();
        profileTable.getItems().setAll(profiles != null ? profiles : List.of());
    }

    private void addProfile() {
        String profileName = profileNameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (profileName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "Bitte füllen Sie alle Felder aus.", ButtonType.OK).showAndWait();
            return;
        }

        LoginProfile newProfile = new LoginProfile(profileName, username, password, false);
        LoginProfileManager.addProfile(newProfile);
        updateProfileList();

        profileNameField.clear();
        usernameField.clear();
        passwordField.clear();
    }

    private void deleteSelectedProfile() {
        LoginProfile selected = profileTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            LoginProfileManager.deleteProfile(selected);
            updateProfileList();
        }
    }

    public void show() {
        dialog.setOnShowing(event ->
                DialogHelper.centerDialogOnOwner(dialog, terminalApp.getUIController().getPrimaryStage())
        );
        Platform.runLater(dialog::show);
    }


}
