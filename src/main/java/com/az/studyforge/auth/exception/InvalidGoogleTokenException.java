package com.az.studyforge.auth.exception;

public class InvalidGoogleTokenException extends RuntimeException {
  public InvalidGoogleTokenException(String message) {
    super(message);
  }
}
