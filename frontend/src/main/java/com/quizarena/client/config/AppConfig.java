package com.quizarena.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration du client. L'URL de base de l'API est externalisée (§5.2) :
 * un fichier config.properties placé à côté de l'application (répertoire
 * courant) surcharge les valeurs par défaut embarquées dans le jar.
 */
public final class AppConfig {

    private static final Properties PROPS = load();

    private AppConfig() {
    }

    public static String apiBaseUrl() {
        return PROPS.getProperty("api.baseUrl", "http://127.0.0.1:8085/api/v1").replaceAll("/+$", "");
    }

    public static long pollIntervalMs() {
        try {
            return Long.parseLong(PROPS.getProperty("poll.intervalMs", "1200"));
        } catch (NumberFormatException e) {
            return 1200L;
        }
    }

    public static long requestTimeoutMs() {
        try {
            return Long.parseLong(PROPS.getProperty("api.timeoutMs", "5000"));
        } catch (NumberFormatException e) {
            return 5000L;
        }
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // valeurs par défaut
        }
        Path external = Path.of("config.properties");
        if (Files.isRegularFile(external)) {
            try (InputStream in = Files.newInputStream(external)) {
                props.load(in);
            } catch (IOException ignored) {
                // le fichier externe est optionnel
            }
        }
        return props;
    }
}
