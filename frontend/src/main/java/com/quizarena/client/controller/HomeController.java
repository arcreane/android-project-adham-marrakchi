package com.quizarena.client.controller;

import com.quizarena.client.AppContext;
import com.quizarena.client.net.Dto;
import com.quizarena.client.ui.Async;
import com.quizarena.client.ui.Avatars;
import com.quizarena.client.ui.Navigator;
import com.quizarena.client.ui.Ui;
import javafx.fxml.FXML;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;

import java.util.List;

/** Accueil / profil (F-11) : avatar, statistiques, dernières parties. */
public class HomeController implements Navigator.Screen {

    @FXML private ImageView avatarView;
    @FXML private Label pseudoLabel;
    @FXML private Label gamesLabel;
    @FXML private Label winsLabel;
    @FXML private Label totalLabel;
    @FXML private ListView<Dto.HistoryEntry> lastGamesList;

    @FXML
    public void initialize() {
        lastGamesList.setPlaceholder(new Label("Aucune partie jouée pour le moment."));
        lastGamesList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Dto.HistoryEntry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "%s — %s — %s, %d points".formatted(
                        Ui.formatDate(item.endedAt()), item.categoryName(),
                        Ui.rankLabel(item.finalRank()), item.score()));
            }
        });

        Dto.Player cached = AppContext.me();
        if (cached != null) {
            showPlayer(cached, List.of());
        }
        Async.run(() -> AppContext.api().me(), me -> {
            AppContext.setMe(me.player());
            showPlayer(me.player(), me.lastGames());
        });
    }

    private void showPlayer(Dto.Player player, List<Dto.HistoryEntry> lastGames) {
        pseudoLabel.setText(player.pseudo());
        avatarView.setImage(Avatars.imageFor(player.pseudo(), player.avatarUrl(), 96));
        gamesLabel.setText(String.valueOf(player.gamesPlayed()));
        winsLabel.setText(String.valueOf(player.wins()));
        totalLabel.setText(String.valueOf(player.scoreTotal()));
        lastGamesList.getItems().setAll(lastGames);
    }

    @FXML
    private void onPlay() {
        Navigator.go("lobby");
    }

    @FXML
    private void onHistory() {
        Navigator.go("history");
    }

    @FXML
    private void onLeaderboard() {
        Async.run(() -> AppContext.api().leaderboard(), entries -> {
            ListView<Dto.LeaderboardEntry> list = new ListView<>();
            list.setPrefSize(430, 380);
            list.setPlaceholder(new Label("Aucun joueur classé pour le moment."));
            list.setCellFactory(v -> new ListCell<>() {
                @Override
                protected void updateItem(Dto.LeaderboardEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : "%d. %s — %d points (%d victoires)"
                            .formatted(item.rank(), item.pseudo(), item.scoreTotal(), item.wins()));
                }
            });
            list.getItems().setAll(entries);

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Classement global");
            dialog.setHeaderText("Les 20 meilleurs joueurs");
            dialog.getDialogPane().setContent(list);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.show();
        });
    }

    @FXML
    private void onLogout() {
        AppContext.clearSession();
        Navigator.go("login");
    }
}
