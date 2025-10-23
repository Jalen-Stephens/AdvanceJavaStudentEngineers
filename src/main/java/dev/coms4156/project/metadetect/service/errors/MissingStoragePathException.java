package dev.coms4156.project.metadetect.service.errors;

/**
 * Thrown when there is a storage path exception.
 */
public class MissingStoragePathException extends RuntimeException {
  public MissingStoragePathException(String message) {
    super(message);
  }
}
