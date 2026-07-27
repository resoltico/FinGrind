package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationException;
import dev.erst.fingrind.core.attestation.AttestationEvidence;
import dev.erst.fingrind.core.attestation.AttestationReceipt;
import dev.erst.fingrind.core.attestation.AttestationReceiptArtifactException;
import dev.erst.fingrind.core.attestation.AttestationReceiptRetention;
import dev.erst.fingrind.core.attestation.AttestationReceiptVerificationResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves, reads, and verifies one independently retained receipt artifact. */
final class AttestationReceiptVerificationOperations {
  private AttestationReceiptVerificationOperations() {}

  static ContractDecision<VerifyAttestationReceiptResult> verify(
      Path bookPath, Path receiptPath, List<AttestationEvidence> evidence) {
    return verify(
        bookPath,
        receiptPath,
        evidence,
        AttestationReceiptVerificationOperations::readBoundedReceipt,
        ReceiptArtifactPathAccess.FILE_SYSTEM);
  }

  static ContractDecision<VerifyAttestationReceiptResult> verify(
      Path bookPath,
      Path receiptPath,
      List<AttestationEvidence> evidence,
      ReceiptReader receiptReader,
      ReceiptArtifactPathAccess pathAccess) {
    Optional<ReceiptSource> source;
    try {
      source = resolveReceiptSource(bookPath, receiptPath, pathAccess);
    } catch (NoSuchFileException exception) {
      return invalidReceiptArtifact();
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      return receiptReadFailure(receiptPath);
    }
    if (source.isEmpty()) {
      return invalidReceiptArtifact();
    }
    ReceiptSource resolvedSource = source.orElseThrow();
    byte[] receipt;
    try {
      receipt = Objects.requireNonNull(receiptReader, "receiptReader").read(resolvedSource.path());
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      return receiptReadFailure(resolvedSource.path());
    }
    try {
      AttestationReceiptVerificationResult verification =
          AttestationReceipt.verify(receipt, evidence, resolvedSource.retention());
      return ContractDecision.accepted(
          new VerifyAttestationReceiptResult.Valid(
              resolvedSource.path(),
              verification.bookId(),
              verification.operationOrder(),
              HexFormat.of().formatHex(verification.operationHead()),
              verification.findings()));
    } catch (AttestationReceiptArtifactException exception) {
      return invalidReceiptArtifact();
    } catch (AttestationAuthorizationException exception) {
      return ContractDecision.accepted(
          new VerifyAttestationReceiptResult.Invalid(
              AttestationVerificationFailure.fromWireCode(exception.failure().code()).wireCode()));
    }
  }

  static AttestationReceiptRetention publicationReceiptRetention(Path bookPath, Path receiptPath) {
    Path checkedReceiptPath = Objects.requireNonNull(receiptPath, "receiptPath");
    try {
      Path normalizedReceiptPath = checkedReceiptPath.toAbsolutePath().normalize();
      if (normalizedReceiptPath.getNameCount() == 0) {
        return AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY;
      }
      Path receiptParent = normalizedReceiptPath.getParent();
      Path receiptFileName = normalizedReceiptPath.getFileName();
      return resolvedReceiptRetention(
          bookPath,
          Objects.requireNonNull(receiptParent, "normalized receipt parent")
              .toRealPath()
              .resolve(Objects.requireNonNull(receiptFileName, "normalized receipt file name")));
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      return AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY;
    }
  }

  static byte[] readBoundedReceipt(Path canonicalReceiptPath) throws IOException {
    try (InputStream input =
        Files.newInputStream(
            Objects.requireNonNull(canonicalReceiptPath, "canonicalReceiptPath"),
            LinkOption.NOFOLLOW_LINKS)) {
      return input.readNBytes(Math.addExact(AttestationReceipt.maximumEncodedByteCount(), 1));
    }
  }

  private static Optional<ReceiptSource> resolveReceiptSource(
      Path bookPath, Path receiptPath, ReceiptArtifactPathAccess pathAccess) throws IOException {
    Path checkedReceiptPath = Objects.requireNonNull(receiptPath, "receiptPath");
    ReceiptArtifactPathAccess checkedPathAccess = Objects.requireNonNull(pathAccess, "pathAccess");
    BasicFileAttributes selectedAttributes =
        checkedPathAccess.readBasicAttributesNoFollow(checkedReceiptPath);
    if (!selectedAttributes.isRegularFile()) {
      return Optional.empty();
    }
    Path resolvedReceiptPath = checkedPathAccess.toRealPath(checkedReceiptPath);
    BasicFileAttributes resolvedAttributes =
        checkedPathAccess.readBasicAttributesNoFollow(resolvedReceiptPath);
    if (!resolvedAttributes.isRegularFile()) {
      return Optional.empty();
    }
    return Optional.of(
        new ReceiptSource(
            resolvedReceiptPath, resolvedReceiptRetention(bookPath, resolvedReceiptPath)));
  }

  private static AttestationReceiptRetention resolvedReceiptRetention(
      Path bookPath, Path resolvedReceiptPath) {
    Path checkedBookPath = Objects.requireNonNull(bookPath, "bookPath");
    Path checkedResolvedReceiptPath =
        Objects.requireNonNull(resolvedReceiptPath, "resolvedReceiptPath");
    try {
      Path bookParent = checkedBookPath.toAbsolutePath().normalize().getParent();
      if (bookParent == null) {
        return AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY;
      }
      return checkedResolvedReceiptPath.startsWith(bookParent.toRealPath())
          ? AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY
          : AttestationReceiptRetention.INDEPENDENT;
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      return AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY;
    }
  }

  private static ContractDecision<VerifyAttestationReceiptResult> invalidReceiptArtifact() {
    return ContractDecision.accepted(
        new VerifyAttestationReceiptResult.Invalid(
            AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID.wireCode()));
  }

  private static ContractDecision<VerifyAttestationReceiptResult> receiptReadFailure(
      Path receiptPath) {
    return ContractDecision.rejected(
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failureAt(
            receiptPath,
            "FinGrind could not read the selected receipt artifact.",
            "Confirm that the receipt file is readable and retry.",
            "--receipt-file"));
  }

  /** Reads one bounded receipt byte stream from the already-resolved, no-follow source path. */
  @FunctionalInterface
  interface ReceiptReader {
    /**
     * Reads the encoded receipt from the resolved source without following a caller-selected link.
     */
    byte[] read(Path canonicalReceiptPath) throws IOException;
  }

  private record ReceiptSource(Path path, AttestationReceiptRetention retention) {
    private ReceiptSource {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(retention, "retention");
    }
  }
}
