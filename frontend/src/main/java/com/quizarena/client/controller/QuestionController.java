package com.quizarena.client.controller;

import com.quizarena.client.AppContext;
import com.quizarena.client.config.AppConfig;
import com.quizarena.client.net.ApiClient;
import com.quizarena.client.net.ApiException;
import com.quizarena.client.net.Dto;
import com.quizarena.client.ui.Async;
import com.quizarena.client.ui.GameFlow;
import com.quizarena.client.ui.Navigator;
import com.quizarena.client.ui.Poller;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.List;
import java.util.Locale;

/**
 * Écran question (F-07, F-08) : 4 choix, compte à rebours animé,
 * verrouillage après validation. L'échéance vient du serveur ; l'horloge
 * locale est recalée avec serverNow pour éviter tout écart d'horloge.
 */
public class QuestionController implements Navigator.Screen {

    @FXML private Label roundLabel;
    @FXML private Label categoryLabel;
    @FXML private Label scoreLabel;
    @FXML private Label questionText;
    @FXML private Label timerLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressBar timeBar;
    @FXML private ImageView questionImage;
    @FXML private Button choice0;
    @FXML private Button choice1;
    @FXML private Button choice2;
    @FXML private Button choice3;

    private final Poller poller = new Poller();
    private AnimationTimer countdown;
    private List<Button> choiceButtons;
    private Dto.Question question;
    private boolean locked;
    private long deadlineLocalMs;
    private long totalMs = 15000;

    @FXML
    public void initialize() {
        choiceButtons = List.of(choice0, choice1, choice2, choice3);
        for (int i = 0; i < choiceButtons.size(); i++) {
            final int index = i;
            choiceButtons.get(i).setOnAction(event -> submit(index));
            choiceButtons.get(i).setDisable(true);
        }
        questionImage.setVisible(false);
        questionImage.setManaged(false);

        Dto.GameState state = AppContext.lastState();
        if (state != null) {
            renderHeader(state);
            Dto.GamePlayer you = state.you();
            if (you != null && !"active".equals(you.status())) {
                locked = true;
                statusLabel.setText("Vous êtes éliminé — mode spectateur.");
            }
        }

        loadQuestion();
        poller.start(AppConfig.pollIntervalMs(), this::poll);
    }

    private void renderHeader(Dto.GameState state) {
        roundLabel.setText("Manche " + state.roundNo() + " / " + state.roundsTotal());
        categoryLabel.setText(state.categoryName());
        Dto.GamePlayer you = state.you();
        scoreLabel.setText((you != null ? you.score() : 0) + " pts");
    }

    private void loadQuestion() {
        Integer gameId = AppContext.currentGameId();
        if (gameId == null) {
            return;
        }
        Async.run(() -> AppContext.api().question(gameId), q -> {
            question = q;
            AppContext.setLastQuestion(q);
            questionText.setText(q.text());
            for (int i = 0; i < choiceButtons.size() && i < q.choices().size(); i++) {
                choiceButtons.get(i).setText(q.choices().get(i));
                choiceButtons.get(i).setDisable(locked);
            }
            if (q.imageUrl() != null && !q.imageUrl().isBlank()) {
                questionImage.setImage(new Image(q.imageUrl(), 420, 200, true, true, true));
                questionImage.setVisible(true);
                questionImage.setManaged(true);
            }
            if (q.answered()) {
                lockChoices(q.yourChoice());
                statusLabel.setText("Réponse enregistrée, en attente des autres joueurs…");
            }
            startCountdown(q);
        }, e -> {
            // la manche vient sans doute de se clôturer : l'état routera vers les résultats
            statusLabel.setText(e.getMessage());
        });
    }

    private void startCountdown(Dto.Question q) {
        long serverNow = ApiClient.isoToMs(q.serverNow());
        long deadline = ApiClient.isoToMs(q.deadlineAt());
        long opened = ApiClient.isoToMs(q.openedAt());
        long offset = serverNow - System.currentTimeMillis(); // recalage horloge serveur
        deadlineLocalMs = deadline - offset;
        totalMs = Math.max(1, deadline - opened);

        if (countdown != null) {
            countdown.stop();
        }
        countdown = new AnimationTimer() {
            @Override
            public void handle(long ignored) {
                long remaining = deadlineLocalMs - System.currentTimeMillis();
                if (remaining <= 0) {
                    timeBar.setProgress(0);
                    timerLabel.setText("0,0 s");
                    if (!locked) {
                        lockChoices(null);
                        statusLabel.setText("Temps écoulé !");
                    }
                    stop();
                    return;
                }
                timeBar.setProgress((double) remaining / totalMs);
                timerLabel.setText(String.format(Locale.FRANCE, "%.1f s", remaining / 1000.0));
            }
        };
        countdown.start();
    }

    private void submit(int choiceIndex) {
        if (locked || question == null) {
            return;
        }
        lockChoices(choiceIndex);
        statusLabel.setText("Envoi de la réponse…");
        Integer gameId = AppContext.currentGameId();
        int questionId = question.questionId();
        Async.run(() -> AppContext.api().answer(gameId, questionId, choiceIndex),
                ack -> statusLabel.setText("Réponse enregistrée, en attente des autres joueurs…"),
                e -> statusLabel.setText(switch (e.code()) {
                    case "DEADLINE_PASSED" -> "Trop tard, la manche est terminée.";
                    case "ALREADY_ANSWERED" -> "Une réponse a déjà été enregistrée.";
                    default -> e.getMessage();
                }));
    }

    /** Verrouille les 4 boutons ; met en évidence le choix retenu (§3.2). */
    private void lockChoices(Integer selectedIndex) {
        locked = true;
        for (int i = 0; i < choiceButtons.size(); i++) {
            Button button = choiceButtons.get(i);
            button.setDisable(true);
            if (selectedIndex != null && i == selectedIndex) {
                button.getStyleClass().add("choice-selected");
            }
        }
    }

    private void poll() {
        Integer gameId = AppContext.currentGameId();
        if (gameId == null) {
            return;
        }
        try {
            Dto.GameState state = AppContext.api().state(gameId);
            Platform.runLater(() -> {
                renderHeader(state);
                if (!"question".equals(state.phase())) {
                    GameFlow.route(state);
                } else if (question != null && state.roundNo() != question.roundNumber()) {
                    Navigator.go("question"); // nouvelle manche : écran rechargé
                }
            });
        } catch (ApiException ignored) {
            // erreur ponctuelle de polling : nouvelle tentative au tick suivant (A-08)
        }
    }

    @FXML
    private void onLeave() {
        Integer gameId = AppContext.currentGameId();
        if (gameId != null) {
            Async.run(() -> {
                AppContext.api().leave(gameId);
                return true;
            }, ok -> {
            }, e -> {
            });
        }
        AppContext.clearGame();
        Navigator.go("lobby");
    }

    @Override
    public void onClose() {
        poller.stop();
        if (countdown != null) {
            countdown.stop();
        }
    }
}
