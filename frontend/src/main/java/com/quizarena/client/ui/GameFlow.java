package com.quizarena.client.ui;

import com.quizarena.client.AppContext;
import com.quizarena.client.net.Dto;

/**
 * Aiguillage unique du déroulement de partie : chaque écran de jeu
 * interroge l'état serveur (source de vérité) et laisse cette classe
 * décider de l'écran à afficher. À appeler sur le thread JavaFX.
 */
public final class GameFlow {

    private GameFlow() {
    }

    public static void route(Dto.GameState state) {
        AppContext.setLastState(state);
        switch (state.phase()) {
            case "waiting" -> Navigator.goIfNot("room");
            case "question" -> Navigator.goIfNot("question");
            case "results" -> Navigator.goIfNot("results");
            case "finished" -> Navigator.goIfNot("podium");
            case "cancelled" -> {
                AppContext.clearGame();
                Navigator.goIfNot("lobby");
                Ui.showInfo("Salle fermée", "L'hôte a fermé la salle.");
            }
            default -> {
                // phase inconnue : on reste sur l'écran courant
            }
        }
    }
}
