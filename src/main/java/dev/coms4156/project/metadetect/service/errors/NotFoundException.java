package dev.coms4156.project.metadetect.service.errors;

/**
 * Thrown when a requested resource cannot be found or does not exist.
 */
public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }
}
