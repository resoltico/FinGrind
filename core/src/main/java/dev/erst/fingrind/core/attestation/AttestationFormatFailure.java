package dev.erst.fingrind.core.attestation;

/** Classifies raw-format implementation failures without discarding causal diagnostics. */
final class AttestationFormatFailure {
  private AttestationFormatFailure() {}

  /** Runs one raw-format decode while preserving an already classified authorization failure. */
  static <T> T decoding(
      AttestationAuthorizationFailure failure, DecodingOperation<T> decodingOperation) {
    try {
      return decodingOperation.decode();
    } catch (RuntimeException exception) {
      throw classify(exception, failure);
    }
  }

  static AttestationAuthorizationException classify(
      RuntimeException exception, AttestationAuthorizationFailure failure) {
    if (exception instanceof AttestationAuthorizationException authorizationException) {
      return authorizationException;
    }
    return new AttestationAuthorizationException(failure, exception);
  }

  /** One decoding computation whose input failures must retain their classified cause. */
  @FunctionalInterface
  interface DecodingOperation<T> {
    /** Decodes one raw format value. */
    T decode();
  }
}
