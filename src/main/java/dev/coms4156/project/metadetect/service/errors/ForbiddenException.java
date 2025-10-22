package dev.coms4156.project.metadetect.service.errors;

/**
 * Thrown when a user attempts to access or modify a resource they do not own.
 */
public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }
}
