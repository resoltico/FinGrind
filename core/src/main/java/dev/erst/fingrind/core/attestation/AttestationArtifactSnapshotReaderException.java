package dev.erst.fingrind.core.attestation;

/**
 * Signals a snapshot-reader failure that its storage adapter intentionally classifies separately.
 *
 * <p>Artifact verification normalizes ordinary reader failures as malformed manifest evidence. An
 * adapter throws this type only when it has already established a distinct external-source failure,
 * such as an unreadable selected backup-key source.
 */
public class AttestationArtifactSnapshotReaderException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /** Creates one classified snapshot-reader failure with its operator-safe message. */
  public AttestationArtifactSnapshotReaderException(String message) {
    super(message);
  }

  /** Creates one classified snapshot-reader failure while retaining its underlying cause. */
  public AttestationArtifactSnapshotReaderException(String message, Throwable cause) {
    super(message, cause);
  }
}
