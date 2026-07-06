package com.quizarena.client.ui;

import com.quizarena.client.net.ApiException;
import javafx.scene.control.Alert;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Petites aides d'affichage partagées par les écrans. */
public final class Ui {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRANCE).withZone(ZoneId.systemDefault());

    private Ui() {
    }

    /** Alerte d'erreur lisible — une erreur réseau n'arrête pas l'application (A-08). */
    public static void showError(ApiException e) {
        Alert alert = new Alert(Alert.AlertType.WARNING, e.getMessage());
        alert.setHeaderText(e.isNetwork() ? "Problème de connexion" : "Action impossible");
        alert.show();
    }

    public static void showInfo(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(header);
        alert.show();
    }

    /** Libellé français d'une difficulté du contrat d'API. */
    public static String difficultyLabel(String difficulty) {
        return switch (difficulty == null ? "" : difficulty) {
            case "easy" -> "Facile";
            case "medium" -> "Moyen";
            case "hard" -> "Difficile";
            case "mixed" -> "Mixte";
            default -> difficulty;
        };
    }

    /** Valeur API depuis un libellé français. */
    public static String difficultyValue(String label) {
        return switch (label == null ? "" : label) {
            case "Facile" -> "easy";
            case "Moyen" -> "medium";
            case "Difficile" -> "hard";
            default -> "mixed";
        };
    }

    public static String formatDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return "—";
        }
        try {
            return DATE_FORMAT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    public static String formatDuration(Integer seconds) {
        if (seconds == null || seconds < 0) {
            return "—";
        }
        return String.format(Locale.FRANCE, "%d min %02d s", seconds / 60, seconds % 60);
    }

    public static String rankLabel(Integer rank) {
        if (rank == null) {
            return "—";
        }
        return rank == 1 ? "1er" : rank + "e";
    }
}
