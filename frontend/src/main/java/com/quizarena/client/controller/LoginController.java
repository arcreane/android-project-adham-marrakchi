package com.quizarena.client.controller;

import com.quizarena.client.AppContext;
import com.quizarena.client.net.Dto;
import com.quizarena.client.ui.Async;
import com.quizarena.client.ui.Navigator;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Écran connexion / inscription (F-01, F-02).
 * Validation locale avant appel API, erreurs serveur affichées telles quelles.
 */
public class LoginController implements Navigator.Screen {

    @FXML private TextField loginField;
    @FXML private PasswordField loginPassword;
    @FXML private Button loginButton;

    @FXML private TextField regPseudo;
    @FXML private TextField regEmail;
    @FXML private PasswordField regPassword;
    @FXML private PasswordField regConfirm;
    @FXML private Button registerButton;

    @FXML private Label statusLabel;

    @FXML
    private void onLogin() {
        String login = loginField.getText().trim();
        String password = loginPassword.getText();
        if (login.isEmpty() || password.isEmpty()) {
            status("Saisissez votre pseudo (ou email) et votre mot de passe.");
            return;
        }
        loginButton.setDisable(true);
        status("Connexion…");
        Async.run(() -> AppContext.api().login(login, password),
                this::onAuthenticated,
                e -> {
                    loginButton.setDisable(false);
                    status(e.getMessage());
                });
    }

    @FXML
    private void onRegister() {
        String pseudo = regPseudo.getText().trim();
        String email = regEmail.getText().trim();
        String password = regPassword.getText();

        if (!pseudo.matches("[A-Za-z0-9_-]{3,20}")) {
            status("Le pseudo doit contenir 3 à 20 caractères (lettres, chiffres, tirets).");
            return;
        }
        if (!email.matches(".+@.+\\..+")) {
            status("Adresse email invalide.");
            return;
        }
        if (password.length() < 8) {
            status("Le mot de passe doit contenir au moins 8 caractères.");
            return;
        }
        if (!password.equals(regConfirm.getText())) {
            status("Les deux mots de passe ne correspondent pas.");
            return;
        }
        registerButton.setDisable(true);
        status("Création du compte…");
        Async.run(() -> AppContext.api().register(pseudo, email, password),
                this::onAuthenticated,
                e -> {
                    registerButton.setDisable(false);
                    status(e.getMessage());
                });
    }

    private void onAuthenticated(Dto.AuthResponse auth) {
        AppContext.setMe(auth.player());
        AppContext.saveSession(auth.token());
        Navigator.go("home");
    }

    private void status(String message) {
        statusLabel.setText(message);
    }
}
