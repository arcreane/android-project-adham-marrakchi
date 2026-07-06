package com.quizarena.client.controller;

import com.quizarena.client.AppContext;
import com.quizarena.client.config.AppConfig;
import com.quizarena.client.net.ApiException;
import com.quizarena.client.net.Dto;
import com.quizarena.client.ui.Async;
import com.quizarena.client.ui.GameFlow;
import com.quizarena.client.ui.Navigator;
import com.quizarena.client.ui.Poller;
import com.quizarena.client.ui.Ui;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;

import java.util.List;

/** Lobby (F-03, F-04, F-05) : lister, filtrer, créer, rejoindre, rafraîchir. */
public class LobbyController implements Navigator.Screen {

    private static final String ALL_CATEGORIES = "Toutes les catégories";

    @FXML private ComboBox<String> categoryFilter;
    @FXML private ListView<Dto.RoomSummary> roomsList;
    @FXML private Label statusLabel;

    private final Poller poller = new Poller();
    private List<Dto.RoomSummary> allRooms = List.of();

    @FXML
    public void initialize() {
        categoryFilter.getItems().setAll(ALL_CATEGORIES);
        categoryFilter.getSelectionModel().selectFirst();
        categoryFilter.valueProperty().addListener((obs, oldV, newV) -> applyFilter());

        roomsList.setPlaceholder(new Label("Aucune salle ouverte — créez la vôtre !"));
        roomsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Dto.RoomSummary room, boolean empty) {
                super.updateItem(room, empty);
                setText(empty || room == null ? null
                        : "Salle de %s — %s (%s) — %d/%d joueurs — %d manches".formatted(
                        room.hostPseudo(), room.categoryName(), Ui.difficultyLabel(room.difficulty()),
                        room.playersCount(), room.maxPlayers(), room.roundsTotal()));
            }
        });
        roomsList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onJoin();
            }
        });

        statusLabel.setText("Chargement des salles…");
        Async.run(() -> AppContext.api().categories(), categories -> {
            for (Dto.Category category : categories) {
                categoryFilter.getItems().add(category.name());
            }
        });
        poller.start(2000, this::refreshRooms);
    }

    private void refreshRooms() {
        try {
            List<Dto.RoomSummary> rooms = AppContext.api().games();
            Platform.runLater(() -> {
                allRooms = rooms;
                applyFilter();
                statusLabel.setText(rooms.isEmpty() ? "" : rooms.size() + " salle(s) ouverte(s)");
            });
        } catch (ApiException e) {
            Platform.runLater(() -> statusLabel.setText(e.getMessage()));
        }
    }

    private void applyFilter() {
        String selected = categoryFilter.getValue();
        Dto.RoomSummary current = roomsList.getSelectionModel().getSelectedItem();
        List<Dto.RoomSummary> filtered = (selected == null || ALL_CATEGORIES.equals(selected))
                ? allRooms
                : allRooms.stream().filter(r -> selected.equals(r.categoryName())).toList();
        roomsList.getItems().setAll(filtered);
        if (current != null) {
            filtered.stream().filter(r -> r.id() == current.id()).findFirst()
                    .ifPresent(r -> roomsList.getSelectionModel().select(r));
        }
    }

    @FXML
    private void onRefresh() {
        statusLabel.setText("Actualisation…");
        Async.run(() -> AppContext.api().games(), rooms -> {
            allRooms = rooms;
            applyFilter();
            statusLabel.setText(rooms.isEmpty() ? "" : rooms.size() + " salle(s) ouverte(s)");
        }, e -> statusLabel.setText(e.getMessage()));
    }

    @FXML
    private void onJoin() {
        Dto.RoomSummary room = roomsList.getSelectionModel().getSelectedItem();
        if (room == null) {
            statusLabel.setText("Sélectionnez d'abord une salle.");
            return;
        }
        Async.run(() -> AppContext.api().join(room.id()), state -> {
            AppContext.setCurrentGameId(state.id());
            GameFlow.route(state);
        }, e -> {
            // salle pleine / partie commencée / déjà en partie : message lisible (§3.3)
            statusLabel.setText(e.getMessage());
            Ui.showError(e);
        });
    }

    @FXML
    private void onCreate() {
        Async.run(() -> AppContext.api().categories(), this::showCreateDialog);
    }

    private void showCreateDialog(List<Dto.Category> categories) {
        if (categories.isEmpty()) {
            Ui.showInfo("Impossible", "Aucune catégorie disponible côté serveur.");
            return;
        }
        ComboBox<Dto.Category> categoryBox = new ComboBox<>();
        categoryBox.getItems().setAll(categories);
        categoryBox.getSelectionModel().selectFirst();
        categoryBox.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Dto.Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        categoryBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Dto.Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });

        ComboBox<String> difficultyBox = new ComboBox<>();
        difficultyBox.getItems().setAll("Mixte", "Facile", "Moyen", "Difficile");
        difficultyBox.getSelectionModel().selectFirst();

        Spinner<Integer> playersSpinner = new Spinner<>(2, 8, 4);
        Spinner<Integer> roundsSpinner = new Spinner<>(3, 10, 10);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Catégorie"), categoryBox);
        grid.addRow(1, new Label("Difficulté"), difficultyBox);
        grid.addRow(2, new Label("Joueurs max."), playersSpinner);
        grid.addRow(3, new Label("Manches"), roundsSpinner);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Créer une salle");
        dialog.setHeaderText("Paramètres de la partie");
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(button -> {
            if (button != ButtonType.OK) {
                return;
            }
            int categoryId = categoryBox.getValue().id();
            String difficulty = Ui.difficultyValue(difficultyBox.getValue());
            int maxPlayers = playersSpinner.getValue();
            int rounds = roundsSpinner.getValue();
            Async.run(() -> AppContext.api().createGame(categoryId, difficulty, maxPlayers, rounds),
                    state -> {
                        AppContext.setCurrentGameId(state.id());
                        GameFlow.route(state);
                    },
                    e -> {
                        statusLabel.setText(e.getMessage());
                        Ui.showError(e);
                    });
        });
    }

    @FXML
    private void onBack() {
        Navigator.go("home");
    }

    @Override
    public void onClose() {
        poller.stop();
    }
}
