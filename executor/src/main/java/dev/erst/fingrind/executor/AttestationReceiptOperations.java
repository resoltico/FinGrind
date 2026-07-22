package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationException;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationCredentialUseException;
import dev.erst.fingrind.core.attestation.AttestationDirectoryDurability;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationReceipt;
import dev.erst.fingrind.core.attestation.AttestationReceiptArtifactException;
import dev.erst.fingrind.core.attestation.AttestationReceiptRetention;
import dev.erst.fingrind.core.attestation.AttestationReceiptVerificationResult;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerificationException;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns receipt verification and atomic independently retained receipt publication. */
final class AttestationReceiptOperations {
  private final Clock clock;

  AttestationReceiptOperations(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  ContractDecision<ExportAttestationReceiptResult> export(
      BookAccess bookAccess, Path receiptPath, List<AttestationEvidence> evidence) {
    AttestationVerification verification;
    try {
      verification = AttestationVerifier.verifyBook(evidence);
    } catch (AttestationVerificationException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failureAt(
              bookAccess.bookFilePath(),
              "The selected book's attestation chain is structurally invalid: "
                  + exception.code()
                  + ".",
              "Run "
                  + OperationId.VERIFY_BOOK.wireName()
                  + " and repair from a valid independently retained backup or receipt.",
              "--book-file"));
    }
    List<AttestationCredentialSource> sources;
    try {
      sources = bookAccess.requireAttestationCredentialSources();
    } catch (IllegalStateException exception) {
      return AttestationCredentialRefusals.forReceiptExport(bookAccess.bookFilePath());
    }
    byte[] receipt;
    try (AttestationSigningSession session = AttestationSigningSessionFactory.open(sources)) {
      receipt =
          session.createReceipt(
              verification.bookId(),
              verification.headOrder(),
              verification.operationHead(),
              clock.instant());
    } catch (AttestationCredentialException | AttestationCredentialUseException exception) {
      return AttestationCredentialRefusals.forReceiptExport(bookAccess.bookFilePath());
    }
    try {
      AttestationReceipt.verify(receipt, evidence, AttestationReceiptRetention.INDEPENDENT);
    } catch (AttestationAuthorizationException exception) {
      return ContractDecision.accepted(
          new ExportAttestationReceiptResult.AuthorizationRejected(
              AttestationVerificationFailure.fromWireCode(exception.failure().code())));
    }
    return publish(receiptPath, receipt, bookAccess.bookFilePath(), verification);
  }

  ContractDecision<VerifyAttestationReceiptResult> verify(
      Path bookPath, Path receiptPath, List<AttestationEvidence> evidence) {
    byte[] receipt;
    try {
      if (!Files.isRegularFile(receiptPath, LinkOption.NOFOLLOW_LINKS)) {
        return ContractDecision.accepted(
            new VerifyAttestationReceiptResult.Invalid(
                AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID.wireCode()));
      }
      receipt = Files.readAllBytes(receiptPath);
    } catch (IOException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failureAt(
              receiptPath,
              "FinGrind could not read the selected receipt artifact.",
              "Confirm that the receipt file is readable and retry.",
              "--receipt-file"));
    }
    try {
      AttestationReceiptVerificationResult verification =
          AttestationReceipt.verify(receipt, evidence, receiptRetention(bookPath, receiptPath));
      return ContractDecision.accepted(
          new VerifyAttestationReceiptResult.Valid(
              verification.bookId(), verification.operationOrder(), verification.findings()));
    } catch (AttestationReceiptArtifactException exception) {
      return ContractDecision.accepted(
          new VerifyAttestationReceiptResult.Invalid(
              AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID.wireCode()));
    } catch (AttestationAuthorizationException exception) {
      return ContractDecision.accepted(
          new VerifyAttestationReceiptResult.Invalid(
              AttestationVerificationFailure.fromWireCode(exception.failure().code()).wireCode()));
    }
  }

  private static ContractDecision<ExportAttestationReceiptResult> publish(
      Path receiptPath, byte[] receipt, Path bookPath, AttestationVerification verification) {
    Path parent = receiptPath.getParent();
    if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failureAt(
              receiptPath,
              "The receipt output parent directory does not exist.",
              "Choose an existing receipt output directory and rerun the command.",
              "--receipt-file"));
    }
    Path stagedPath = null;
    try {
      stagedPath = Files.createTempFile(parent, ".fingrind-receipt-", ".fgar");
      writeAndForce(stagedPath, receipt);
      Files.createLink(receiptPath, stagedPath);
      AttestationDirectoryDurability.force(parent);
      String cleanupWarning = deleteStagedReceipt(stagedPath);
      stagedPath = null;
      return ContractDecision.accepted(
          new ExportAttestationReceiptResult.Exported(
              receiptPath,
              verification.bookId(),
              verification.headOrder(),
              HexFormat.of().formatHex(verification.operationHead()),
              publicationWarnings(bookPath, receiptPath, cleanupWarning)));
    } catch (FileAlreadyExistsException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.ARTIFACT_OUTPUT_ALREADY_EXISTS.failureAt(
              receiptPath,
              "The selected receipt output already exists and FinGrind will not overwrite it.",
              "Choose an absent --receipt-file path and rerun the command.",
              "--receipt-file"));
    } catch (IOException exception) {
      return ContractDecision.rejected(
          ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failureAt(
              receiptPath,
              "FinGrind could not publish the receipt artifact atomically.",
              "Choose a writable output directory on a filesystem supporting atomic no-clobber publication.",
              "--receipt-file"));
    } finally {
      if (stagedPath != null) {
        deleteStagedQuietly(stagedPath);
      }
    }
  }

  private static AttestationReceiptRetention receiptRetention(Path bookPath, Path receiptPath) {
    Path normalizedBookParent =
        Objects.requireNonNull(bookPath, "bookPath").toAbsolutePath().normalize().getParent();
    if (normalizedBookParent == null) {
      return AttestationReceiptRetention.INDEPENDENT;
    }
    return receiptPath.startsWith(normalizedBookParent)
        ? AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY
        : AttestationReceiptRetention.INDEPENDENT;
  }

  private static void writeAndForce(Path stagedPath, byte[] receipt) throws IOException {
    try (FileChannel channel =
        FileChannel.open(
            stagedPath, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
      ByteBuffer bytes = ByteBuffer.wrap(Objects.requireNonNull(receipt, "receipt"));
      while (bytes.hasRemaining()) {
        channel.write(bytes);
      }
      channel.force(true);
    }
  }

  /** Returns a truthful warning when the published receipt's staging file could not be removed. */
  static @Nullable String deleteStagedReceipt(Path stagedPath) {
    try {
      Files.delete(stagedPath);
      return null;
    } catch (IOException exception) {
      return "receipt-staging-cleanup-required:" + stagedPath;
    }
  }

  /** Performs best-effort cleanup after a primary receipt-publication failure is decided. */
  static void deleteStagedQuietly(Path stagedPath) {
    try {
      Files.deleteIfExists(stagedPath);
    } catch (IOException ignored) {
      // A second best-effort removal cannot change the already returned primary publication
      // outcome.
    }
  }

  /** Derives caller-visible publication warnings from the canonical receipt-retention outcome. */
  static List<String> publicationWarnings(
      Path bookPath, Path receiptPath, @Nullable String cleanupWarning) {
    List<String> warnings = new ArrayList<>();
    if (receiptRetention(bookPath, receiptPath)
        == AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY) {
      warnings.add("receipt-not-independent");
    }
    if (cleanupWarning != null) {
      warnings.add(cleanupWarning);
    }
    return List.copyOf(warnings);
  }
}
