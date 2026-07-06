package com.quizarena.client.controller;

import com.quizarena.client.AppContext;
import com.quizarena.client.config.AppConfig;
import com.quizarena.client.net.ApiException;
import com.quizarena.client.net.Dto;
import com.quizarena.client.ui.Async;
import com.quizarena.client.ui.Avatars;
import com.quizarena.client.ui.GameFlow;
import com.quizarena.client.ui.Navigator;
import com.quizarena.client.ui.Poller;
import com.quizarena.client.ui.Ui;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;

/**
 * Salle d'attente : joueurs et hôte dans un TreeView avec badges
 * prêt / non prêt ; le lancement est réservé à l'hôte (F-05, F-06).
 */
public class RoomController implements Navigator.Screen {

    @FXML private Label roomTitle;
    @FXML private Label roomInfo;
    @FXML private Label statusLabel;
    @FXML private TreeView<String> playersTree;
    @FXML private Button readyButton;
    @FXML private Button startButton;

    private final Poller poller = new Poller();
    private boolean myReady;

    @FXML
    public void initialize() {
        playersTree.setShowRoot(true);
        Dto.GameState cached = AppContext.lastState();
        if (cached != null) {
            render(cached);
        }
        poller.start(AppConfig.pollIntervalMs(), this::poll);
    }

    private void poll() {
        Integer gameId = AppContext.currentGameId();
        if (gameId == null) {
            return;
        }
        try {
            Dto.GameState state = AppContext.api().state(gameId);
            Platform.runLater(() -> {
                if (!"waiting".equals(state.phase())) {
                    GameFlow.route(state);
                } else {
                    render(state);
                }
            });
        } catch (ApiException e) {
            Platform.runLater(() -> statusLabel.setText(e.getMessage()));
        }
    }

    private void render(Dto.GameState state) {
        AppContext.setLastState(state);
        roomTitle.setText("Salle n°" + state.id() + " — hôte : " + state.hostPseudo());
        roomInfo.setText("%s • %s • %d manches • %d/%d joueurs".formatted(
                state.categoryName(), Ui.difficultyLabel(state.difficulty()),
                state.roundsTotal(), state.players().size(), state.maxPlayers()));

        TreeItem<String> root = new TreeItem<>("Joueurs (" + state.players().size() + ")");
        root.setExpanded(true);
        for (Dto.GamePlayer player : state.players()) {
            String badge = player.isHost() ? "Hôte"
                    : (player.ready() ? "Prêt" : "En attente");
            TreeItem<String> item = new TreeItem<>(player.pseudo() + "  —  " + badge);
            ImageView avatar = new ImageView(Avatars.imageFor(player.pseudo(), player.avatarUrl(), 28));
            item.setGraphic(avatar);
            root.getChildren().add(item);
        }
        playersTree.setRoot(root);

        Dto.GamePlayer you = state.you();
        boolean isHost = you != null && you.isHost();
        myReady = you != null && you.ready();

        readyButton.setVisible(!isHost);
        readyButton.setManaged(!isHost);
        readyButton.setText(myReady ? "Annuler « prêt »" : "Je suis prêt !");

        startButton.setVisible(isHost);
        startButton.setManaged(isHost);
        if (isHost) {
            boolean everyoneReady = state.players().stream()
                    .filter(p -> !p.isHost())
                    .allMatch(Dto.GamePlayer::ready);
            boolean enoughPlayers = state.players().size() >= 2;
            startButton.setDisable(!(everyoneReady && enoughPlayers));
            statusLabel.setText(!enoughPlayers
                    ? "En attente d'au moins un autre joueur…"
                    : (everyoneReady ? "Tout le monde est prêt, vous pouvez lancer !"
                    : "En attente que tous les joueurs soient prêts…"));
        } else {
            statusLabel.setText(myReady
                    ? "En attente du lancement par l'hôte…"
                    : "Cliquez sur « Je suis prêt » quand vous êtes disponible.");
        }
    }

    @FXML
    private void onReady() {
        Integer gameId = AppContext.currentGameId();
        if (gameId == null) {
            return;
        }
        boolean target = !myReady;
        readyButton.setDisable(true);
        Async.run(() -> AppContext.api().ready(gameId, target), state -> {
            readyButton.setDisable(false);
            render(state);
        }, e -> {
            readyButton.setDisable(false);
            statusLabel.setText(e.getMessage());
        });
    }

    @FXML
    private void onStart() {
        Integer gameId = AppContext.currentGameId();
        if (gameId == null) {
            return;
        }
        startButton.setDisable(true);
        Async.run(() -> AppContext.api().start(gameId), GameFlow::route, e -> {
            startButton.setDisable(false);
            statusLabel.setText(e.getMessage());
            Ui.showError(e);
        });
    }

    @FXML
    private void onLeave() {
        Integer gameId = AppContext.currentGameId();
        if (gameId == null) {
            Navigator.go("lobby");
            return;
        }
        Async.run(() -> {
            AppContext.api().leave(gameId);
            return true;
        }, ok -> {
            AppContext.clearGame();
            Navigator.go("lobby");
        }, e -> {
            AppContext.clearGame();
            Navigator.go("lobby");
        });
    }

    @Override
    public void onClose() {
        poller.stop();
    }
}
