package com.quizarena.client.ui;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Interrogation régulière de l'API (multijoueur synchrone par polling,
 * toutes les 1 à 2 secondes — §1.1). Le travail s'exécute sur un thread
 * dédié ; l'appelant repasse sur le thread JavaFX via Platform.runLater.
 */
public final class Poller {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "quizarena-poll");
                thread.setDaemon(true);
                return thread;
            });

    private ScheduledFuture<?> task;
    private volatile boolean busy;

    public synchronized void start(long periodMs, Runnable tick) {
        stop();
        task = SCHEDULER.scheduleAtFixedRate(() -> {
            if (busy) {
                return; // ne pas empiler les requêtes si le serveur est lent
            }
            busy = true;
            try {
                tick.run();
            } catch (Exception ignored) {
                // une erreur ponctuelle de polling ne doit pas tuer la boucle (A-08)
            } finally {
                busy = false;
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }
}
