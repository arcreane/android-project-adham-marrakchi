package com.quizarena.client.net;

import java.util.List;

/**
 * Objets du contrat JSON /api/v1 (camelCase, voir docs/openapi.yaml).
 * Des records immuables mappés directement par Gson.
 */
public final class Dto {

    private Dto() {
    }

    public record Player(int id, String pseudo, String avatarUrl, int scoreTotal,
                         int gamesPlayed, int wins, String createdAt) {
    }

    public record AuthResponse(String token, Player player) {
    }

    public record Me(Player player, List<HistoryEntry> lastGames) {
    }

    public record HistoryEntry(int gameId, String endedAt, String categoryName, Integer finalRank,
                               int score, int playersCount, Integer durationSeconds) {
    }

    public record LeaderboardEntry(int rank, int playerId, String pseudo, String avatarUrl,
                                   int scoreTotal, int wins) {
    }

    public record Category(int id, String name, String iconUrl, String colorHex) {
    }

    public record RoomSummary(int id, String hostPseudo, int categoryId, String categoryName,
                              String difficulty, int playersCount, int maxPlayers,
                              int roundsTotal, String createdAt) {
    }

    public record GamePlayer(int id, String pseudo, String avatarUrl, int score, String status,
                             boolean ready, boolean isHost, Integer finalRank, Integer lastChoice,
                             Boolean lastCorrect, Integer lastPoints, boolean eliminatedThisRound) {
    }

    public record LastRound(int number, int correctIndex) {
    }

    public record GameState(int id, String status, String phase, int categoryId, String categoryName,
                            String difficulty, int maxPlayers, int roundsTotal, int roundNo,
                            int hostId, String hostPseudo, int youId, String serverNow,
                            String deadlineAt, String resultsUntil, Integer winnerId,
                            String winnerPseudo, LastRound lastRound, List<GamePlayer> players) {

        public GamePlayer you() {
            return players.stream().filter(p -> p.id() == youId).findFirst().orElse(null);
        }
    }

    public record Question(int roundNumber, int questionId, String text, List<String> choices,
                           String imageUrl, String openedAt, String deadlineAt, String serverNow,
                           boolean answered, Integer yourChoice) {
    }

    public record AnswerAck(boolean accepted, String receivedAt) {
    }

    public record ScoreRow(int playerId, String pseudo, String avatarUrl, int rank, int score,
                           String status, Integer lastChoice, Boolean lastCorrect, Integer lastPoints,
                           Integer lastResponseMs, boolean eliminatedThisRound) {
    }

    public record Scores(int roundNumber, Integer correctIndex, List<ScoreRow> ranking) {
    }
}
