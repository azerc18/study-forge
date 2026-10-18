package com.az.studyforge.auth.exception;

public class LockedAccountException extends RuntimeException {
  public LockedAccountException(String message) {
    super(message);
  }
}
