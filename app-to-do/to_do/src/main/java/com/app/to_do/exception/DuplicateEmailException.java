package com.app.to_do.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super("El email ya está registrado: " + email);
    }
}
