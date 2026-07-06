package com.quizarena.client.net;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests du décodage de l'enveloppe standard {"data"} / {"error"} (NF-10). */
class ApiClientTest {

    @Test
    void decodeSuccessEnvelope() {
        String body = """
                {"data": {"token": "abc123", "player": {
                    "id": 7, "pseudo": "alice", "scoreTotal": 1200,
                    "gamesPlayed": 3, "wins": 1}}}
                """;
        Dto.AuthResponse auth = ApiClient.parseEnvelope(200, body, Dto.AuthResponse.class);
        assertEquals("abc123", auth.token());
        assertEquals("alice", auth.player().pseudo());
        assertEquals(1200, auth.player().scoreTotal());
        assertNull(auth.player().avatarUrl());
    }

    @Test
    void decodeListEnvelope() {
        String body = """
                {"data": [
                    {"id": 1, "hostPseudo": "bob", "categoryId": 2, "categoryName": "Sport",
                     "difficulty": "mixed", "playersCount": 3, "maxPlayers": 8, "roundsTotal": 10}
                ]}
                """;
        List<Dto.RoomSummary> rooms = ApiClient.parseEnvelope(200, body,
                new TypeToken<List<Dto.RoomSummary>>() { }.getType());
        assertEquals(1, rooms.size());
        assertEquals("bob", rooms.get(0).hostPseudo());
        assertEquals("mixed", rooms.get(0).difficulty());
    }

    @Test
    void decodeErrorEnvelope() {
        String body = """
                {"error": {"code": "GAME_FULL", "message": "La salle est pleine."}, "requestId": "x"}
                """;
        ApiException e = assertThrows(ApiException.class,
                () -> ApiClient.parseEnvelope(409, body, Dto.GameState.class));
        assertEquals(409, e.status());
        assertEquals("GAME_FULL", e.code());
        assertEquals("La salle est pleine.", e.getMessage());
    }

    @Test
    void decodeGameStatePhases() {
        String body = """
                {"data": {"id": 5, "status": "running", "phase": "question", "categoryId": 1,
                  "categoryName": "Sciences", "difficulty": "easy", "maxPlayers": 4,
                  "roundsTotal": 10, "roundNo": 2, "hostId": 7, "hostPseudo": "alice", "youId": 8,
                  "serverNow": "2026-07-05T14:00:00.000Z", "deadlineAt": "2026-07-05T14:00:15.000Z",
                  "players": [
                    {"id": 7, "pseudo": "alice", "score": 1540, "status": "active",
                     "ready": true, "isHost": true, "eliminatedThisRound": false},
                    {"id": 8, "pseudo": "bob", "score": 850, "status": "active",
                     "ready": true, "isHost": false, "eliminatedThisRound": false}
                  ]}}
                """;
        Dto.GameState state = ApiClient.parseEnvelope(200, body, Dto.GameState.class);
        assertEquals("question", state.phase());
        assertEquals(2, state.players().size());
        assertEquals("bob", state.you().pseudo());
        assertTrue(state.players().get(0).isHost());
    }

    @Test
    void isoDatesAreParsedToEpochMillis() {
        assertEquals(0L, ApiClient.isoToMs(null));
        assertEquals(0L, ApiClient.isoToMs("pas une date"));
        long ms = ApiClient.isoToMs("2026-07-05T14:00:15.123Z");
        assertEquals(1783346415123L % 1000, ms % 1000); // millisecondes conservées
        assertTrue(ms > 1_700_000_000_000L);
    }
}
