package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies receipt verification refuses unsafe artifacts before interpreting receipt bytes. */
class AttestationReceiptVerificationOperationsTest {
  @TempDir Path temporaryDirectory;

  @Test
  void classifiesMissingUnreadableAndNondirectReceiptArtifacts() throws IOException {
    Path book = temporaryDirectory.resolve("book.sqlite");
    Path receipt = temporaryDirectory.resolve("receipt.fgar");
    Files.writeString(receipt, "receipt");
    BasicFileAttributes regularAttributes =
        Files.readAttributes(receipt, BasicFileAttributes.class);

    assertInvalid(
        AttestationReceiptVerificationOperations.verify(
            book,
            receipt,
            List.of(),
            ignored -> {
              throw new AssertionError("Missing artifacts must not be read.");
            },
            pathAccess(
                ignored -> {
                  throw new NoSuchFileException(receipt.toString());
                },
                ignored -> receipt)));
    assertRejected(
        AttestationReceiptVerificationOperations.verify(
            book,
            receipt,
            List.of(),
            ignored -> new byte[0],
            pathAccess(
                ignored -> {
                  throw new IOException("attributes unavailable");
                },
                ignored -> receipt)));
    assertInvalid(
        AttestationReceiptVerificationOperations.verify(
            book,
            receipt,
            List.of(),
            ignored -> new byte[0],
            pathAccess(ignored -> nonRegularAttributes(), ignored -> receipt)));
    assertInvalid(
        AttestationReceiptVerificationOperations.verify(
            book,
            receipt,
            List.of(),
            ignored -> new byte[0],
            pathAccess(
                new AttributeReader() {
                  private int calls;

                  @Override
                  public BasicFileAttributes read(Path ignored) {
                    boolean firstCall = calls == 0;
                    calls += 1;
                    return firstCall ? regularAttributes : nonRegularAttributes();
                  }
                },
                ignored -> receipt)));
  }

  @Test
  void classifiesReadAndEncodingFailuresAfterCanonicalArtifactAdmission() throws IOException {
    Path book = temporaryDirectory.resolve("book.sqlite");
    Path receipt = temporaryDirectory.resolve("receipt.fgar");
    Files.writeString(receipt, "receipt");
    BasicFileAttributes attributes = Files.readAttributes(receipt, BasicFileAttributes.class);

    assertRejected(
        AttestationReceiptVerificationOperations.verify(
            book,
            receipt,
            List.of(),
            ignored -> {
              throw new IOException("read unavailable");
            },
            pathAccess(ignored -> attributes, ignored -> receipt)));
    assertInvalid(
        AttestationReceiptVerificationOperations.verify(
            book,
            receipt,
            List.of(),
            ignored -> new byte[] {0},
            pathAccess(ignored -> attributes, ignored -> receipt)),
        AttestationVerificationFailure.PREIMAGE_INVALID);
    assertInvalid(
        AttestationReceiptVerificationOperations.verify(
            book,
            receipt,
            List.of(),
            ignored ->
                new byte
                    [dev.erst.fingrind.core.attestation.AttestationReceipt.maximumEncodedByteCount()
                        + 1],
            pathAccess(ignored -> attributes, ignored -> receipt)));
  }

  @Test
  void boundedReadAndPublicationRetentionFailClosedToTheBookTrustBoundary() throws IOException {
    Path bookDirectory = Files.createDirectory(temporaryDirectory.resolve("book"));
    Path book = bookDirectory.resolve("book.sqlite");
    Path receipt = temporaryDirectory.resolve("receipt.fgar");
    byte[] encoded = new byte[] {1, 2, 3};
    Files.write(receipt, encoded);

    assertEquals(
        encoded.length,
        AttestationReceiptVerificationOperations.readBoundedReceipt(receipt).length);
    assertEquals(
        dev.erst.fingrind.core.attestation.AttestationReceiptRetention.INDEPENDENT,
        AttestationReceiptVerificationOperations.publicationReceiptRetention(book, receipt));
    assertEquals(
        dev.erst.fingrind.core.attestation.AttestationReceiptRetention.WITHIN_BOOK_TRUST_BOUNDARY,
        AttestationReceiptVerificationOperations.publicationReceiptRetention(
            book, temporaryDirectory.resolve("missing", "receipt.fgar")));
  }

  private static void assertInvalid(ContractDecision<VerifyAttestationReceiptResult> decision) {
    assertInvalid(decision, AttestationVerificationFailure.RECEIPT_ARTIFACT_INVALID);
  }

  private static void assertInvalid(
      ContractDecision<VerifyAttestationReceiptResult> decision,
      AttestationVerificationFailure expectedFailure) {
    VerifyAttestationReceiptResult.Invalid invalid =
        assertInstanceOf(VerifyAttestationReceiptResult.Invalid.class, decision.requireAccepted());
    assertEquals(expectedFailure.wireCode(), invalid.failureCode());
  }

  private static void assertRejected(ContractDecision<VerifyAttestationReceiptResult> decision) {
    assertEquals(
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, decision.requireRejected().descriptor());
  }

  private static ReceiptArtifactPathAccess pathAccess(
      AttributeReader attributes, CanonicalPathReader canonicalPath) {
    return new ReceiptArtifactPathAccess() {
      @Override
      public boolean isDirectoryNoFollow(Path path) {
        throw new AssertionError("Receipt verification must not inspect output directories.");
      }

      @Override
      public BasicFileAttributes readBasicAttributesNoFollow(Path path) throws IOException {
        return attributes.read(path);
      }

      @Override
      public Path toRealPath(Path path) throws IOException {
        return canonicalPath.resolve(path);
      }
    };
  }

  private static BasicFileAttributes nonRegularAttributes() {
    return new BasicFileAttributes() {
      @Override
      public java.nio.file.attribute.FileTime lastModifiedTime() {
        return java.nio.file.attribute.FileTime.fromMillis(0);
      }

      @Override
      public java.nio.file.attribute.FileTime lastAccessTime() {
        return java.nio.file.attribute.FileTime.fromMillis(0);
      }

      @Override
      public java.nio.file.attribute.FileTime creationTime() {
        return java.nio.file.attribute.FileTime.fromMillis(0);
      }

      @Override
      public boolean isRegularFile() {
        return false;
      }

      @Override
      public boolean isDirectory() {
        return true;
      }

      @Override
      public boolean isSymbolicLink() {
        return false;
      }

      @Override
      public boolean isOther() {
        return false;
      }

      @Override
      public long size() {
        return 0;
      }

      @Override
      public Object fileKey() {
        return "nonregular";
      }
    };
  }

  /** Reads no-follow attributes for one test-controlled receipt artifact. */
  @FunctionalInterface
  private interface AttributeReader {
    BasicFileAttributes read(Path path) throws IOException;
  }

  /** Resolves one test-controlled receipt path after no-follow admission. */
  @FunctionalInterface
  private interface CanonicalPathReader {
    Path resolve(Path path) throws IOException;
  }
}
