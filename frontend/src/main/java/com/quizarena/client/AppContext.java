package com.quizarena.client;

import com.quizarena.client.net.ApiClient;
import com.quizarena.client.net.Dto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Session locale de l'application : client API, joueur connecté,
 * partie courante. Le jeton est persisté dans ~/.quizarena pour
 * restaurer la session au prochain lancement (F-02).
 */
public final class AppContext {

    private static final Path SESSION_FILE =
            Path.of(System.getProperty("user.home"), ".quizarena", "session.properties");

    private static final ApiClient API = new ApiClient();
    private static Dto.Player me;
    private static Integer currentGameId;
    private static Dto.GameState lastState;
    private static Dto.Question lastQuestion;

    private AppContext() {
    }

    public static ApiClient api() {
        return API;
    }

    public static Dto.Player me() {
        return me;
    }

    public static void setMe(Dto.Player player) {
        me = player;
    }

    public static Integer currentGameId() {
        return currentGameId;
    }

    public static void setCurrentGameId(Integer gameId) {
        currentGameId = gameId;
    }

    public static Dto.GameState lastState() {
        return lastState;
    }

    public static void setLastState(Dto.GameState state) {
        lastState = state;
    }

    public static Dto.Question lastQuestion() {
        return lastQuestion;
    }

    public static void setLastQuestion(Dto.Question question) {
        lastQuestion = question;
    }

    public static void clearGame() {
        currentGameId = null;
        lastState = null;
        lastQuestion = null;
    }

    // ------------------------------------------------------------------
    // Persistance de session
    // ------------------------------------------------------------------

    public static void saveSession(String token) {
        try {
            Files.createDirectories(SESSION_FILE.getParent());
            Properties props = new Properties();
            props.setProperty("token", token);
            try (OutputStream out = Files.newOutputStream(SESSION_FILE)) {
                props.store(out, "QuizArena — session locale");
            }
        } catch (IOException ignored) {
            // session non persistée : l'utilisateur devra se reconnecter
        }
    }

    public static String loadSessionToken() {
        if (!Files.isRegularFile(SESSION_FILE)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(SESSION_FILE)) {
            Properties props = new Properties();
            props.load(in);
            String token = props.getProperty("token");
            return (token == null || token.isBlank()) ? null : token;
        } catch (IOException e) {
            return null;
        }
    }

    public static void clearSession() {
        me = null;
        API.setToken(null);
        clearGame();
        try {
            Files.deleteIfExists(SESSION_FILE);
        } catch (IOException ignored) {
            // au pire le jeton expirera côté serveur
        }
    }
}
