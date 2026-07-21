package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationReviewResult;
import dev.erst.fingrind.contract.bookkeeping.ExportAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationReceipt;
import dev.erst.fingrind.core.attestation.AttestationReceiptRetention;
import dev.erst.fingrind.core.attestation.AttestationReceiptVerificationResult;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.core.attestation.AttestationVerification;
import dev.erst.fingrind.core.attestation.AttestationVerificationException;
import dev.erst.fingrind.core.attestation.AttestationVerifier;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
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

/** Read-only verification, review, and independently retained receipt service for one book. */
public final class AttestationInspectionService {
  private final Clock clock;
  private final AttestedProtectedBookMaintenanceStore store;

  /** Creates one service over the mandatory persisted-attestation evidence boundary. */
  public AttestationInspectionService(Clock clock, ProtectedBookMaintenanceStore store) {
    this.clock = Objects.requireNonNull(clock, "clock");
    this.store = AttestedProtectedBookMaintenanceStore.require(store);
  }

  /** Verifies every immutable attestation structure from genesis to the current head. */
  public ContractDecision<VerifyBookAttestationResult> verifyBook(BookAccess bookAccess) {
    return readEvidence(bookAccess)
        .fold(
            evidence -> {
              try {
                AttestationVerification verification = AttestationVerifier.verifyBook(evidence);
                return ContractDecision.accepted(validBookResult(verification));
              } catch (AttestationVerificationException exception) {
                return ContractDecision.accepted(
                    new VerifyBookAttestationResult.Invalid(exception.code()));
              }
            },
            ContractDecision::rejected);
  }

  /** Returns the non-persisted compromise-review findings for a structurally valid book. */
  public ContractDecision<AttestationReviewResult> review(BookAccess bookAccess) {
    return readEvidence(bookAccess)
        .fold(
            evidence -> {
              try {
                AttestationVerification verification = AttestationVerifier.verifyBook(evidence);
                return ContractDecision.accepted(
                    new AttestationReviewResult(
                        verification.bookId(),
                        verification.headOrder(),
                        verification.reviewFindings()));
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
            },
            ContractDecision::rejected);
  }

  /** Exports one non-mutating, quorum-signed receipt through atomic no-clobber publication. */
  public ContractDecision<ExportAttestationReceiptResult> exportReceipt(
      BookAccess bookAccess, Path receiptFilePath) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    Path checkedReceiptPath =
        Objects.requireNonNull(receiptFilePath, "receiptFilePath").toAbsolutePath().normalize();
    return readEvidence(checkedBookAccess)
        .fold(
            evidence -> exportReceipt(checkedBookAccess, checkedReceiptPath, evidence),
            ContractDecision::rejected);
  }

  /** Verifies an independently retained receipt against the complete supplied book chain. */
  public ContractDecision<VerifyAttestationReceiptResult> verifyReceipt(
      BookAccess bookAccess, Path receiptFilePath) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    Path checkedReceiptPath =
        Objects.requireNonNull(receiptFilePath, "receiptFilePath").toAbsolutePath().normalize();
    return readEvidence(checkedBookAccess)
        .fold(
            evidence ->
                verifyReceipt(checkedBookAccess.bookFilePath(), checkedReceiptPath, evidence),
            ContractDecision::rejected);
  }

  private ContractDecision<ExportAttestationReceiptResult> exportReceipt(
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
      return invalidAttestationCredentials(bookAccess.bookFilePath());
    }
    byte[] receipt;
    try (AttestationSigningSession session = AttestationSigningSessionFactory.open(sources)) {
      receipt =
          session.createReceipt(
              verification.bookId(),
              verification.headOrder(),
              verification.operationHead(),
              clock.instant());
    } catch (AttestationCredentialException
        | IllegalArgumentException
        | NullPointerException exception) {
      return invalidAttestationCredentials(bookAccess.bookFilePath());
    }
    AttestationReceipt.verify(receipt, evidence, AttestationReceiptRetention.INDEPENDENT);
    return publishReceipt(receiptPath, receipt, bookAccess.bookFilePath(), verification);
  }

  private ContractDecision<VerifyAttestationReceiptResult> verifyReceipt(
      Path bookPath, Path receiptPath, List<AttestationEvidence> evidence) {
    byte[] receipt;
    try {
      if (!Files.isRegularFile(receiptPath, LinkOption.NOFOLLOW_LINKS)) {
        return ContractDecision.accepted(
            new VerifyAttestationReceiptResult.Invalid("receipt-artifact-invalid"));
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
    } catch (AttestationVerificationException exception) {
      return ContractDecision.accepted(
          new VerifyAttestationReceiptResult.Invalid(exception.code()));
    } catch (IllegalArgumentException exception) {
      return ContractDecision.accepted(
          new VerifyAttestationReceiptResult.Invalid("receipt-artifact-invalid"));
    }
  }

  private ContractDecision<ExportAttestationReceiptResult> publishReceipt(
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
      List<String> warnings = new ArrayList<>();
      if (receiptRetention(bookPath, receiptPath)
          == AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY) {
        warnings.add("receipt-not-independent");
      }
      String cleanupWarning = deleteStagedReceipt(stagedPath);
      stagedPath = null;
      if (cleanupWarning != null) {
        warnings.add(cleanupWarning);
      }
      return ContractDecision.accepted(
          new ExportAttestationReceiptResult(
              receiptPath,
              verification.bookId(),
              verification.headOrder(),
              HexFormat.of().formatHex(verification.operationHead()),
              warnings));
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
        try {
          Files.deleteIfExists(stagedPath);
        } catch (IOException ignored) {
          // A second best-effort removal cannot change the already returned primary publication
          // outcome.
        }
      }
    }
  }

  private ContractDecision<List<AttestationEvidence>> readEvidence(BookAccess bookAccess) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    return store
        .verifyInitializedBook(
            ProtectedBookAccess.fromPublished(checkedBookAccess),
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK)
        .fold(
            verification -> {
              if (verification instanceof ProtectedBookMaintenanceStore.VerifiedBook verifiedBook) {
                try (verifiedBook) {
                  return ContractDecision.accepted(store.loadAttestationEvidence(verifiedBook));
                }
              }
              return ContractDecision.rejected(
                  ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failureAt(
                      checkedBookAccess.bookFilePath(),
                      "The selected protected book could not be opened and verified.",
                      "Confirm the book path and passphrase source, then retry.",
                      "--book-file"));
            },
            failure -> ContractDecision.rejected(failure.toContractFailure()));
  }

  private static VerifyBookAttestationResult.Valid validBookResult(
      AttestationVerification verification) {
    return new VerifyBookAttestationResult.Valid(
        verification.bookId(),
        verification.headOrder(),
        HexFormat.of().formatHex(verification.operationHead()),
        verification.reviewFindings());
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

  private static @Nullable String deleteStagedReceipt(Path stagedPath) {
    try {
      Files.delete(stagedPath);
      return null;
    } catch (IOException exception) {
      return "receipt-staging-cleanup-required:" + stagedPath;
    }
  }

  private static <T> ContractDecision<T> invalidAttestationCredentials(Path bookPath) {
    return ContractDecision.rejected(
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.failureAt(
            bookPath,
            "FinGrind could not open the selected attestation credentials.",
            "Provide one through five readable existing attestation credential triples authorized for receipt export.",
            "--attestation-principal-id"));
  }
}
