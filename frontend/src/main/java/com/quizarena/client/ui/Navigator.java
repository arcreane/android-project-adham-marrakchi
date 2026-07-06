package com.quizarena.client.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * Navigation entre les écrans : charge le FXML, ferme proprement le
 * contrôleur précédent (arrêt du polling) et remplace la racine de scène.
 */
public final class Navigator {

    /** Contrôleur d'écran capable de libérer ses ressources (pollers...). */
    public interface Screen {
        default void onClose() {
        }
    }

    private static Stage stage;
    private static Object currentController;
    private static String currentView = "";

    private Navigator() {
    }

    public static void init(Stage primaryStage) {
        stage = primaryStage;
        stage.setMinWidth(1024);
        stage.setMinHeight(660);
        stage.setWidth(1280);
        stage.setHeight(720);
        stage.setOnCloseRequest(e -> {
            if (currentController instanceof Screen screen) {
                screen.onClose();
            }
        });
    }

    /** Charge /fxml/{view}.fxml et l'affiche. À appeler sur le thread JavaFX. */
    public static void go(String view) {
        if (currentController instanceof Screen screen) {
            screen.onClose();
        }
        try {
            FXMLLoader loader = new FXMLLoader(Navigator.class.getResource("/fxml/" + view + ".fxml"));
            Parent root = loader.load();
            currentController = loader.getController();
            currentView = view;
            if (stage.getScene() == null) {
                Scene scene = new Scene(root);
                scene.getStylesheets().add(Objects.requireNonNull(
                        Navigator.class.getResource("/css/app.css")).toExternalForm());
                stage.setScene(scene);
            } else {
                stage.getScene().setRoot(root);
            }
            stage.setTitle("QuizArena — " + view);
            stage.show();
        } catch (IOException e) {
            throw new IllegalStateException("Écran introuvable : " + view, e);
        }
    }

    public static String current() {
        return currentView;
    }

    /** Navigation seulement si l'écran demandé n'est pas déjà affiché. */
    public static void goIfNot(String view) {
        if (!currentView.equals(view)) {
            go(view);
        }
    }
}
