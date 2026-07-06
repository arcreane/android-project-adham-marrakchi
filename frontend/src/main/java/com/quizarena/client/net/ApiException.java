package com.quizarena.client.net;

/**
 * Erreur applicative renvoyée par l'API (error.code / error.message)
 * ou erreur réseau (code NETWORK_ERROR, status 0).
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public boolean isAuthFailure() {
        return status == 401;
    }

    public boolean isNetwork() {
        return "NETWORK_ERROR".equals(code);
    }
}
