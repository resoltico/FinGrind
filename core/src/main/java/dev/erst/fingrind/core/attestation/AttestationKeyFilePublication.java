package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.PublicationMode;
import dev.erst.fingrind.core.PublicationTransactionArtifact;
import dev.erst.fingrind.core.PublicationTransactionMemberRequest;
import dev.erst.fingrind.core.PublicationTransactionMemberRole;
import dev.erst.fingrind.core.PublicationTransactionPublisher;
import dev.erst.fingrind.core.PublicationTransactionRequest;
import dev.erst.fingrind.core.PublicationTransactionResult;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Publishes encrypted key bytes only through the canonical transaction journal owner. */
final class AttestationKeyFilePublication {
  private static final String MEMBER_ID = "attestation-key";

  private AttestationKeyFilePublication() {}

  /**
   * Publishes one fresh encrypted key without replacement and returns only complete transaction
   * evidence.
   */
  static PublicationTransactionArtifact writeNewKeyFile(Path path, byte[] encryptedPrivateKey)
      throws IOException {
    AttestationKeyFileDestination destination =
        AttestationKeyFileLocation.publicationDestination(
            Objects.requireNonNull(path, "path").toAbsolutePath().normalize());
    PublicationTransactionResult transaction =
        PublicationTransactionPublisher.openCanonical()
            .publish(
                new PublicationTransactionRequest(
                    List.of(
                        new PublicationTransactionMemberRequest(
                            MEMBER_ID,
                            PublicationTransactionMemberRole.ATTESTATION_KEY,
                            destination.finalPath(),
                            PublicationMode.NO_REPLACE_LINK,
                            Objects.requireNonNull(encryptedPrivateKey, "encryptedPrivateKey")))));
    return new PublicationTransactionArtifact(destination.finalPath(), transaction);
  }
}
