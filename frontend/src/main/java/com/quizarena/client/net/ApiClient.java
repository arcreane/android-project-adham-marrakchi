package com.quizarena.client.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.quizarena.client.config.AppConfig;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Client HTTP de l'API REST QuizArena.
 * Échanges exclusivement en JSON, jeton Bearer sur les routes privées,
 * timeout 5 s et une relance automatique maximum sur les GET (§5.2).
 */
public final class ApiClient {

    private static final Gson GSON = new Gson();

    private final HttpClient http;
    private final String baseUrl;
    private volatile String token;

    public ApiClient() {
        this.baseUrl = AppConfig.apiBaseUrl();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(AppConfig.requestTimeoutMs()))
                .build();
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    // ------------------------------------------------------------------
    // Authentification
    // ------------------------------------------------------------------

    public Dto.AuthResponse register(String pseudo, String email, String password) {
        Dto.AuthResponse auth = post("/auth/register",
                Map.of("pseudo", pseudo, "email", email, "password", password),
                Dto.AuthResponse.class);
        this.token = auth.token();
        return auth;
    }

    public Dto.AuthResponse login(String login, String password) {
        Dto.AuthResponse auth = post("/auth/login",
                Map.of("login", login, "password", password),
                Dto.AuthResponse.class);
        this.token = auth.token();
        return auth;
    }

    // ------------------------------------------------------------------
    // Joueur
    // ------------------------------------------------------------------

    public Dto.Me me() {
        return get("/players/me", Dto.Me.class);
    }

    public List<Dto.HistoryEntry> history() {
        return get("/players/me/history", new TypeToken<List<Dto.HistoryEntry>>() { }.getType());
    }

    public List<Dto.LeaderboardEntry> leaderboard() {
        return get("/leaderboard", new TypeToken<List<Dto.LeaderboardEntry>>() { }.getType());
    }

    public List<Dto.Category> categories() {
        return get("/categories", new TypeToken<List<Dto.Category>>() { }.getType());
    }

    // ------------------------------------------------------------------
    // Salles et partie
    // ------------------------------------------------------------------

    public List<Dto.RoomSummary> games() {
        return get("/games", new TypeToken<List<Dto.RoomSummary>>() { }.getType());
    }

    public Dto.GameState createGame(int categoryId, String difficulty, int maxPlayers, int rounds) {
        return post("/games", Map.of(
                "categoryId", categoryId,
                "difficulty", difficulty,
                "maxPlayers", maxPlayers,
                "rounds", rounds), Dto.GameState.class);
    }

    public Dto.GameState join(int gameId) {
        return post("/games/" + gameId + "/join", Map.of(), Dto.GameState.class);
    }

    public Dto.GameState ready(int gameId, boolean ready) {
        return post("/games/" + gameId + "/ready", Map.of("ready", ready), Dto.GameState.class);
    }

    public Dto.GameState start(int gameId) {
        return post("/games/" + gameId + "/start", Map.of(), Dto.GameState.class);
    }

    public void leave(int gameId) {
        post("/games/" + gameId + "/leave", Map.of(), JsonObject.class);
    }

    public Dto.GameState state(int gameId) {
        return get("/games/" + gameId + "/state", Dto.GameState.class);
    }

    public Dto.Question question(int gameId) {
        return get("/games/" + gameId + "/question", Dto.Question.class);
    }

    public Dto.AnswerAck answer(int gameId, int questionId, int choiceIndex) {
        return post("/games/" + gameId + "/answers",
                Map.of("questionId", questionId, "choiceIndex", choiceIndex),
                Dto.AnswerAck.class);
    }

    public Dto.Scores scores(int gameId) {
        return get("/games/" + gameId + "/scores", Dto.Scores.class);
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    private <T> T get(String path, Type type) {
        try {
            return exchange("GET", path, null, type);
        } catch (ApiException e) {
            if (e.isNetwork()) {
                return exchange("GET", path, null, type); // une seule relance sur les GET
            }
            throw e;
        }
    }

    private <T> T post(String path, Object body, Type type) {
        return exchange("POST", path, body, type);
    }

    private <T> T exchange(String method, String path, Object body, Type type) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(AppConfig.requestTimeoutMs()))
                .header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json; charset=utf-8");
            builder.POST(HttpRequest.BodyPublishers.ofString(
                    body == null ? "{}" : GSON.toJson(body), StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }

        HttpResponse<String> response;
        try {
            response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException(0, "NETWORK_ERROR",
                    "Serveur injoignable (" + baseUrl + "). Vérifiez le réseau et réessayez.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "NETWORK_ERROR", "Requête interrompue.");
        }
        return parseEnvelope(response.statusCode(), response.body(), type);
    }

    /**
     * Décode l'enveloppe standard : {"data": ...} en cas de succès,
     * {"error": {"code", "message"}} en cas d'échec.
     */
    @SuppressWarnings("unchecked")
    static <T> T parseEnvelope(int status, String rawBody, Type type) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(rawBody == null || rawBody.isBlank() ? "{}" : rawBody);
            root = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (JsonSyntaxException e) {
            throw new ApiException(status, "BAD_RESPONSE", "Réponse illisible du serveur (HTTP " + status + ").");
        }
        if (root.has("error")) {
            JsonObject error = root.getAsJsonObject("error");
            throw new ApiException(status,
                    error.has("code") ? error.get("code").getAsString() : "UNKNOWN",
                    error.has("message") ? error.get("message").getAsString() : "Erreur inconnue.");
        }
        if (status >= 400) {
            throw new ApiException(status, "HTTP_" + status, "Erreur HTTP " + status + ".");
        }
        return (T) GSON.fromJson(root.get("data"), type);
    }

    /** Date ISO-8601 de l'API -> époque en millisecondes (0 si absente). */
    public static long isoToMs(String iso) {
        if (iso == null || iso.isBlank()) {
            return 0L;
        }
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }
}
