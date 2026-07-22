package dev.erst.fingrind.core.attestation;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
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
   * Opens one through the current policy maximum of pre-existing encrypted credentials for one
   * authorization attempt.
   *
   * <p>Mutation credentials are never created implicitly: enrollment is a separate attested
   * operation. A missing file is therefore a typed credential-use refusal at the caller's boundary.
   */
  public static AttestationSigningSession open(List<AttestationCredentialSource> sources) {
    List<AttestationCredentialSource> checkedSources =
        List.copyOf(Objects.requireNonNull(sources, "sources"));
    if (checkedSources.isEmpty()
        || checkedSources.size() > AttestationAuthorizationLimits.MAXIMUM_QUORUM) {
      throw new IllegalArgumentException(
          "Attestation authorization requires "
              + AttestationAuthorizationLimits.MINIMUM_QUORUM
              + " through "
              + AttestationAuthorizationLimits.MAXIMUM_QUORUM
              + " credentials.");
    }
    requireDistinctSources(checkedSources);
    List<AttestationSigningCredential> opened = new ArrayList<>(checkedSources.size());
    try {
      for (AttestationCredentialSource source : checkedSources) {
        opened.add(openCredential(source));
      }
      return new AttestationSigningSession(opened);
    } catch (RuntimeException exception) {
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

  /**
   * Signs one independently restorable backup artifact with the session's BACKUP candidates.
   *
   * <p>Authorization remains enforced by artifact verification against the source snapshot's
   * historical registry and policy; this boundary only performs custody-confined signing.
   */
  public byte[] createBackupArtifact(
      byte[] snapshot,
      UUID bookId,
      UUID backupId,
      BigInteger sourceOrder,
      byte[] sourceOperationHead) {
    requireOpen();
    return AttestationBackupArtifact.create(
        snapshot, bookId, backupId, sourceOrder, sourceOperationHead, credentials);
  }

  /** Creates one non-mutating receipt signed by the session's ANCHOR candidates. */
  public byte[] createReceipt(
      UUID bookId, BigInteger operationOrder, byte[] operationHead, Instant receiptTimestamp) {
    requireOpen();
    return AttestationReceipt.create(
        bookId, operationOrder, operationHead, receiptTimestamp, credentials);
  }

  @Override
  public void close() {
    if (!closed) {
      credentials.forEach(AttestationSigningCredential::close);
      closed = true;
    }
  }

  private void requireOpen() {
    if (closed) {
      throw new IllegalStateException("Attestation signing session is closed.");
    }
  }

  private static void requireDistinctSources(List<AttestationCredentialSource> sources) {
    Set<UUID> principalIds = new HashSet<>();
    Set<java.nio.file.Path> keyPaths = new HashSet<>();
    for (AttestationCredentialSource source : sources) {
      AttestationCredentialSource checkedSource = Objects.requireNonNull(source, "sources");
      if (!principalIds.add(checkedSource.principalId())) {
        throw AttestationAdmissionRejectedException.from(
            AttestationAuthorizationFailure.DUPLICATE_PRINCIPAL);
      }
      if (!keyPaths.add(checkedSource.encryptedKeyFilePath())) {
        throw AttestationAdmissionRejectedException.from(
            AttestationAuthorizationFailure.DUPLICATE_KEY);
      }
    }
  }

  private static AttestationSigningCredential openCredential(AttestationCredentialSource source) {
    AttestationCredentialSource checkedSource = Objects.requireNonNull(source, "sources");
    try {
      return switch (checkedSource.custodian()) {
        case FILE_PKCS8 ->
            AttestationKeyFiles.openExistingCredential(
                checkedSource.principalId(),
                checkedSource.encryptedKeyFilePath(),
                checkedSource.passphraseFilePath());
      };
    } catch (IOException | IllegalArgumentException exception) {
      throw new AttestationCredentialUseException(
          checkedSource.encryptedKeyFilePath(),
          "Attestation credential source could not be opened.",
          exception);
    }
  }
}
