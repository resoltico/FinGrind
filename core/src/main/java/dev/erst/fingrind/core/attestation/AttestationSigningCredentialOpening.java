package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.ArtifactPublicationResult;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One opened signing credential and, only when this invocation created it, its durable key-file
 * publication fact.
 */
public record AttestationSigningCredentialOpening(
    AttestationSigningCredential credential,
    @Nullable ArtifactPublicationResult createdKeyFilePublication) {
  /** Validates one credential opening without mistaking an existing key for a new artifact. */
  public AttestationSigningCredentialOpening {
    Objects.requireNonNull(credential, "credential");
  }
}
