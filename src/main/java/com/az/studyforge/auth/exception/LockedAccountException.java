package com.az.studyforge.auth.exception;

public class LockedAccountException extends RuntimeException {
    public LockedAccountException() {
        super("Account is locked");
    }
}
