package com.quizarena.client.controller;

import com.quizarena.client.AppContext;
import com.quizarena.client.config.AppConfig;
import com.quizarena.client.net.ApiException;
import com.quizarena.client.net.Dto;
import com.quizarena.client.ui.Async;
import com.quizarena.client.ui.GameFlow;
import com.quizarena.client.ui.Navigator;
import com.quizarena.client.ui.Poller;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Duration;

/**
 * Résultat de manche (F-09) : bonne réponse, points gagnés,
 * classement et joueurs éliminés.
 */
public class ResultsController implements Navigator.Screen {

    @FXML private Label roundResultTitle;
    @FXML private Label correctAnswerLabel;
    @FXML private Label myResultLabel;
    @FXML private ListView<Dto.ScoreRow> rankingList;

    private final Poller poller = new Poller();

    @FXML
    public void initialize() {
        rankingList.setPlaceholder(new Label("Calcul du classement…"));
        rankingList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Dto.ScoreRow row, boolean empty) {
                super.updateItem(row, empty);
                getStyleClass().remove("row-eliminated");
                if (empty || row == null) {
                    setText(null);
                    return;
                }
                String marker = row.lastCorrect() == null ? "—" : (row.lastCorrect() ? "✔" : "✘");
                String points = row.lastPoints() == null ? "aucune réponse"
                        : "+" + row.lastPoints() + " pts";
                String suffix = switch (row.status()) {
                    case "eliminated" -> row.eliminatedThisRound() ? "   ÉLIMINÉ !" : "   (éliminé)";
                    case "left" -> "   (a quitté)";
                    default -> "";
                };
                setText("%d. %s   %s %s   —   total %d pts%s".formatted(
                        row.rank(), row.pseudo(), marker, points, row.score(), suffix));
                if (row.eliminatedThisRound()) {
                    getStyleClass().add("row-eliminated");
                }
            }
        });

        loadScores();
        poller.start(AppConfig.pollIntervalMs(), this::poll);

        FadeTransition fade = new FadeTransition(Duration.millis(450), rankingList);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void loadScores() {
        Integer gameId = AppContext.currentGameId();
        if (gameId == null) {
            return;
        }
        Async.run(() -> AppContext.api().scores(gameId), scores -> {
            roundResultTitle.setText("Résultat de la manche " + scores.roundNumber());
            rankingList.getItems().setAll(scores.ranking());

            Dto.Question lastQuestion = AppContext.lastQuestion();
            if (scores.correctIndex() != null && lastQuestion != null
                    && lastQuestion.roundNumber() == scores.roundNumber()
                    && scores.correctIndex() < lastQuestion.choices().size()) {
                correctAnswerLabel.setText("Bonne réponse : "
                        + lastQuestion.choices().get(scores.correctIndex()));
            } else {
                correctAnswerLabel.setText("");
            }

            Dto.Player me = AppContext.me();
            if (me != null) {
                scores.ranking().stream()
                        .filter(r -> r.playerId() == me.id())
                        .findFirst()
                        .ifPresent(r -> {
                            if (r.eliminatedThisRound()) {
                                myResultLabel.setText("Vous êtes éliminé ! Vous restez spectateur.");
                            } else if (r.lastPoints() == null) {
                                myResultLabel.setText("Aucune réponse enregistrée : 0 point.");
                            } else if (Boolean.TRUE.equals(r.lastCorrect())) {
                                myResultLabel.setText("Bonne réponse : +" + r.lastPoints() + " points !");
                            } else {
                                myResultLabel.setText("Mauvaise réponse : 0 point.");
                            }
                        });
            }
        }, e -> roundResultTitle.setText(e.getMessage()));
    }

    private void poll() {
        Integer gameId = AppContext.currentGameId();
        if (gameId == null) {
            return;
        }
        try {
            Dto.GameState state = AppContext.api().state(gameId);
            Platform.runLater(() -> {
                if (!"results".equals(state.phase())) {
                    GameFlow.route(state);
                }
            });
        } catch (ApiException ignored) {
            // nouvelle tentative au tick suivant (A-08)
        }
    }

    @Override
    public void onClose() {
        poller.stop();
    }
}
