package com.quizarena.client;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vérification une par une du chargement des 8 vues FXML (câblage fx:id / contrôleurs). */
class FxmlSmokeTest {

    @Test
    void allScreensLoad() throws Exception {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyStarted) {
            // toolkit déjà initialisé
        }
        List<String> views = List.of("login", "home", "lobby", "room",
                "question", "results", "podium", "history");
        for (String view : views) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(
                            FxmlSmokeTest.class.getResource("/fxml/" + view + ".fxml"));
                    loader.load();
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    latch.countDown();
                }
            });
            assertTrue(latch.await(15, TimeUnit.SECONDS), "délai dépassé pour " + view);
            if (error.get() != null) {
                error.get().printStackTrace();
            }
            assertNull(error.get(), "échec de chargement de " + view + " : " + error.get());
        }
    }
}
