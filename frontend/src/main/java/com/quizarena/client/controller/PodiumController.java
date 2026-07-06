package com.quizarena.client.controller;

import com.quizarena.client.AppContext;
import com.quizarena.client.net.Dto;
import com.quizarena.client.ui.Avatars;
import com.quizarena.client.ui.Navigator;
import com.quizarena.client.ui.Ui;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Comparator;
import java.util.List;

/** Podium (F-10) : top 3 animé, score final, rejouer ou retour accueil. */
public class PodiumController implements Navigator.Screen {

    @FXML private Label winnerLabel;
    @FXML private Label myRankLabel;
    @FXML private VBox podiumFirst;
    @FXML private VBox podiumSecond;
    @FXML private VBox podiumThird;
    @FXML private ListView<Dto.GamePlayer> finalRankingList;

    @FXML
    public void initialize() {
        finalRankingList.setPlaceholder(new Label(""));
        finalRankingList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Dto.GamePlayer player, boolean empty) {
                super.updateItem(player, empty);
                setText(empty || player == null ? null : "%s  %s — %d points".formatted(
                        Ui.rankLabel(player.finalRank()), player.pseudo(), player.score()));
            }
        });

        Dto.GameState state = AppContext.lastState();
        if (state == null) {
            winnerLabel.setText("Partie terminée.");
            return;
        }

        List<Dto.GamePlayer> ranked = state.players().stream()
                .sorted(Comparator.comparing(p -> p.finalRank() == null ? Integer.MAX_VALUE : p.finalRank()))
                .toList();

        winnerLabel.setText(state.winnerPseudo() != null
                ? "🏆 " + state.winnerPseudo() + " remporte la partie !"
                : "Partie terminée.");

        Dto.GamePlayer you = state.you();
        if (you != null) {
            myRankLabel.setText("Votre résultat : %s avec %d points.".formatted(
                    Ui.rankLabel(you.finalRank()), you.score()));
        }

        fillPodium(podiumFirst, ranked, 0);
        fillPodium(podiumSecond, ranked, 1);
        fillPodium(podiumThird, ranked, 2);
        finalRankingList.getItems().setAll(
                ranked.size() > 3 ? ranked.subList(3, ranked.size()) : List.of());

        animate(podiumSecond, 250);
        animate(podiumFirst, 500);
        animate(podiumThird, 750);
    }

    private void fillPodium(VBox box, List<Dto.GamePlayer> ranked, int index) {
        if (index >= ranked.size()) {
            box.setVisible(false);
            return;
        }
        Dto.GamePlayer player = ranked.get(index);
        for (var node : box.getChildren()) {
            if (node instanceof ImageView imageView) {
                imageView.setImage(Avatars.imageFor(player.pseudo(), player.avatarUrl(), 64));
            } else if (node instanceof Label label && label.getStyleClass().contains("podium-name")) {
                label.setText(player.pseudo());
            } else if (node instanceof Label label && label.getStyleClass().contains("podium-score")) {
                label.setText(player.score() + " pts");
            }
        }
    }

    private void animate(VBox box, int delayMs) {
        if (!box.isVisible()) {
            return;
        }
        TranslateTransition rise = new TranslateTransition(Duration.millis(600), box);
        rise.setFromY(60);
        rise.setToY(0);
        FadeTransition fade = new FadeTransition(Duration.millis(600), box);
        fade.setFromValue(0);
        fade.setToValue(1);
        ParallelTransition animation = new ParallelTransition(rise, fade);
        animation.setDelay(Duration.millis(delayMs));
        box.setOpacity(0);
        animation.play();
    }

    @FXML
    private void onReplay() {
        AppContext.clearGame();
        Navigator.go("lobby");
    }

    @FXML
    private void onHome() {
        AppContext.clearGame();
        Navigator.go("home");
    }
}
