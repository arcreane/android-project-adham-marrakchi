package com.quizarena.client;

import com.quizarena.client.ui.Navigator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * QuizArena — client JavaFX.
 * Lancement : mvn javafx:run (depuis le dossier frontend/).
 */
public class QuizArenaApp extends Application {

    @Override
    public void start(Stage stage) {
        Navigator.init(stage);
        Navigator.go("login");
        restoreSession();
    }

    /** F-02 : restaure une session valide persistée localement. */
    private void restoreSession() {
        String token = AppContext.loadSessionToken();
        if (token == null) {
            return;
        }
        AppContext.api().setToken(token);
        Thread restore = new Thread(() -> {
            try {
                var me = AppContext.api().me();
                Platform.runLater(() -> {
                    AppContext.setMe(me.player());
                    Navigator.go("home");
                });
            } catch (Exception e) {
                Platform.runLater(AppContext::clearSession);
            }
        }, "session-restore");
        restore.setDaemon(true);
        restore.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
