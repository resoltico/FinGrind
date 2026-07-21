package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationFounderInput;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationGenesis;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import dev.erst.fingrind.core.attestation.AttestationSigningCredential;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Creates signed book genesis evidence while keeping encrypted-key access below the CLI layer. */
public final class AttestationGenesisFactory {
  private AttestationGenesisFactory() {}

  /** Builds one self-authorizing genesis operation from explicit encrypted-key sources. */
  public static AttestationEvidence create(
      BookIdentity bookIdentity, Instant recordedAt, List<AttestationFounderInput> founders) {
    BookIdentity checkedBookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    Instant checkedRecordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    List<AttestationFounderInput> checkedFounders =
        List.copyOf(Objects.requireNonNull(founders, "founders"));
    List<AttestationSigningCredential> credentials = new ArrayList<>();
    try {
      for (AttestationFounderInput founder : checkedFounders) {
        credentials.add(openFounderCredential(founder));
      }
      return AttestationGenesis.create(
          UUID.randomUUID(), checkedBookIdentity, checkedRecordedAt, credentials);
    } finally {
      credentials.forEach(AttestationSigningCredential::close);
    }
  }

  private static AttestationSigningCredential openFounderCredential(AttestationFounderInput founder) {
    try {
      return AttestationKeyFiles.openOrCreateCredential(
          founder.principalId(), founder.encryptedKeyFilePath(), founder.passphraseFilePath());
    } catch (IOException | IllegalArgumentException exception) {
      throw new AttestationCredentialException(founder.encryptedKeyFilePath(), exception);
    }
  }
}
