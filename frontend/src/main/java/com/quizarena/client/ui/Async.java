package com.quizarena.client.ui;

import com.quizarena.client.AppContext;
import com.quizarena.client.net.ApiException;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Exécute les appels API hors du thread JavaFX et ramène le résultat
 * sur le thread UI. Gère globalement la session expirée (§3.3) :
 * toute erreur 401 renvoie à l'écran de connexion.
 */
public final class Async {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "quizarena-api");
        thread.setDaemon(true);
        return thread;
    });

    private Async() {
    }

    public static <T> void run(Supplier<T> call, Consumer<T> onSuccess, Consumer<ApiException> onError) {
        EXECUTOR.submit(() -> {
            try {
                T result = call.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (ApiException e) {
                Platform.runLater(() -> {
                    if (e.isAuthFailure()) {
                        handleSessionExpired(e);
                    } else {
                        onError.accept(e);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> onError.accept(
                        new ApiException(0, "CLIENT_ERROR", "Erreur inattendue : " + e.getMessage())));
            }
        });
    }

    /** Variante sans gestion d'erreur personnalisée : alerte standard. */
    public static <T> void run(Supplier<T> call, Consumer<T> onSuccess) {
        run(call, onSuccess, Ui::showError);
    }

    private static void handleSessionExpired(ApiException e) {
        AppContext.clearSession();
        Navigator.go("login");
        Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "Votre session a expiré, veuillez vous reconnecter.");
        alert.setHeaderText("Session expirée");
        alert.show();
    }
}
