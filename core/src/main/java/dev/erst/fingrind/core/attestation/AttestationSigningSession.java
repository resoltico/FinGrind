package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Closeable file-custody session that grants the single ability to authorize exact operations.
 *
 * <p>The session owns the credential handles and clears their passphrase copies when closed. It
 * intentionally exposes no private key, passphrase, or signing primitive to callers.
 */
public final class AttestationSigningSession
    implements AttestationOperationAuthorizer, AutoCloseable {
  private final List<AttestationSigningCredential> credentials;
  private boolean closed;

  private AttestationSigningSession(List<AttestationSigningCredential> credentials) {
    this.credentials = List.copyOf(credentials);
  }

  /**
   * Opens one through five pre-existing encrypted credentials for a single authorization attempt.
   *
   * <p>Mutation credentials are never created implicitly: enrollment is a separate attested
   * operation. A missing file is therefore a typed I/O refusal at the caller's boundary.
   */
  public static AttestationSigningSession open(List<AttestationCredentialSource> sources)
      throws IOException {
    List<AttestationCredentialSource> checkedSources =
        List.copyOf(Objects.requireNonNull(sources, "sources"));
    if (checkedSources.isEmpty() || checkedSources.size() > 5) {
      throw new IllegalArgumentException(
          "Attestation authorization requires one through five credentials.");
    }
    requireDistinctSources(checkedSources);
    List<AttestationSigningCredential> opened = new ArrayList<>(checkedSources.size());
    try {
      for (AttestationCredentialSource source : checkedSources) {
        opened.add(
            AttestationKeyFiles.openExistingCredential(
                source.principalId(), source.encryptedKeyFilePath(), source.passphraseFilePath()));
      }
      return new AttestationSigningSession(opened);
    } catch (IOException | RuntimeException exception) {
      opened.forEach(AttestationSigningCredential::close);
      throw exception;
    }
  }

  @Override
  public AttestationEvidence authorize(AttestationOperationRequest request) {
    if (closed) {
      throw new IllegalStateException("Attestation signing session is closed.");
    }
    AttestationOperationRequest checkedRequest = Objects.requireNonNull(request, "request");
    return AttestationOperationSigner.sign(
        checkedRequest.bookId(),
        checkedRequest.operationOrder(),
        checkedRequest.operationKind(),
        checkedRequest.previousHead(),
        checkedRequest.recordedAt(),
        checkedRequest.requestPreimage(),
        checkedRequest.effectPreimage(),
        credentials);
  }

  @Override
  public void close() {
    if (!closed) {
      credentials.forEach(AttestationSigningCredential::close);
      closed = true;
    }
  }

  private static void requireDistinctSources(List<AttestationCredentialSource> sources) {
    Set<UUID> principalIds = new HashSet<>();
    Set<java.nio.file.Path> keyPaths = new HashSet<>();
    for (AttestationCredentialSource source : sources) {
      AttestationCredentialSource checkedSource = Objects.requireNonNull(source, "sources");
      if (!principalIds.add(checkedSource.principalId())) {
        throw new IllegalArgumentException(
            "Attestation authorization principals must be distinct.");
      }
      if (!keyPaths.add(checkedSource.encryptedKeyFilePath())) {
        throw new IllegalArgumentException("Attestation authorization key files must be distinct.");
      }
    }
  }
}
