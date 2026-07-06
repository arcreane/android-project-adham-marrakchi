package com.quizarena.client.ui;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.HashMap;
import java.util.Map;

/**
 * Avatars générés localement (pastille colorée + initiale) pour rester
 * fonctionnel hors ligne. Si avatar_url est renseigné côté serveur,
 * l'image distante est utilisée à la place.
 */
public final class Avatars {

    private static final Color[] PALETTE = {
            Color.web("#2E86DE"), Color.web("#E67E22"), Color.web("#27AE60"), Color.web("#8E44AD"),
            Color.web("#E74C3C"), Color.web("#16A085"), Color.web("#D35400"), Color.web("#2C3E50"),
    };

    private static final Map<String, Image> CACHE = new HashMap<>();

    private Avatars() {
    }

    /** Image d'avatar pour un pseudo (à appeler sur le thread JavaFX). */
    public static Image imageFor(String pseudo, String avatarUrl, double size) {
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            return new Image(avatarUrl, size, size, true, true, true);
        }
        String key = pseudo + "@" + (int) size;
        return CACHE.computeIfAbsent(key, k -> generate(pseudo, size));
    }

    private static Image generate(String pseudo, double size) {
        String initial = (pseudo == null || pseudo.isBlank())
                ? "?" : pseudo.substring(0, 1).toUpperCase();
        Color color = PALETTE[Math.abs((pseudo == null ? "?" : pseudo).hashCode()) % PALETTE.length];

        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(color);
        g.fillOval(0, 0, size, size);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("System", FontWeight.BOLD, size * 0.48));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(initial, size / 2, size * 0.66);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return canvas.snapshot(params, null);
    }
}
