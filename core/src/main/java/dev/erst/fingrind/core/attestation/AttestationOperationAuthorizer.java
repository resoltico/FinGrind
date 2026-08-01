package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/**
 * Authorizes one exact operation projection at an already-observed chain head.
 *
 * <p>Storage receives this narrow capability instead of private keys or passphrases. Its caller
 * must invoke it while owning the protected book's immediate write transaction.
 */
@FunctionalInterface
public interface AttestationOperationAuthorizer {
  /** Signs the supplied immutable operation projection or refuses it. */
  AttestationEvidence authorize(AttestationOperationRequest request);

  /** Rejects accidental null authorizers at a mutation boundary. */
  static AttestationOperationAuthorizer require(AttestationOperationAuthorizer authorizer) {
    return Objects.requireNonNull(authorizer, "authorizer");
  }
}
