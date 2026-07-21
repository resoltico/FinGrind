package dev.erst.fingrind.core.attestation;

import dev.erst.fingrind.core.BookIdentity;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Builds the self-authorizing, unanimously signed genesis operation for one new protected book. */
public final class AttestationGenesis {
  private static final byte[] ZERO_HEAD = new byte[AttestationHash.BYTE_LENGTH];

  private AttestationGenesis() {}

  /**
   * Creates the exact genesis evidence for one new book.
   *
   * <p>The supplied list must contain one through five distinct principal-bound credentials. The
   * credentials establish every initial capability grant and the mandatory default policy.
   */
  public static AttestationEvidence create(
      UUID bookId,
      BookIdentity bookIdentity,
      Instant recordedAt,
      List<AttestationSigningCredential> founders) {
    UUID checkedBookId = Objects.requireNonNull(bookId, "bookId");
    BookIdentity checkedBookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    Instant checkedRecordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
    List<AttestationSigningCredential> checkedFounders =
        List.copyOf(Objects.requireNonNull(founders, "founders"));
    if (checkedFounders.isEmpty() || checkedFounders.size() > 5) {
      throw new IllegalArgumentException("Genesis requires between one and five founders.");
    }
    List<AttestationFounder> founderFacts =
        checkedFounders.stream().map(AttestationGenesisFounders::founder).toList();
    AttestationGenesisFounders.requireDistinctCredentials(founderFacts);
    AttestationOperationPreimages preimages =
        AttestationGenesisPreimageProjection.project(
            checkedBookId, checkedBookIdentity, founderFacts);
    return AttestationOperationSigner.sign(
        checkedBookId,
        BigInteger.ZERO,
        AttestationOperationKind.BOOK_GENESIS.wireToken(),
        ZERO_HEAD,
        checkedRecordedAt,
        preimages.request(),
        preimages.effect(),
        checkedFounders);
  }

  /**
   * Verifies that one standalone genesis is valid and binds the supplied persisted book identity.
   *
   * <p>SQLite uses this before writing its ordinary book rows, so a valid genesis for a different
   * entity cannot be paired with those rows in one protected-book transaction.
   */
  public static UUID requireMatchingBookIdentity(
      AttestationEvidence evidence, BookIdentity bookIdentity) {
    AttestationEvidence checkedEvidence = Objects.requireNonNull(evidence, "evidence");
    BookIdentity checkedBookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    AttestationVerification verification = AttestationVerifier.verifyBook(List.of(checkedEvidence));
    if (verification.headOrder().signum() != 0) {
      throw new IllegalArgumentException(
          "Attestation evidence must contain genesis at order zero.");
    }
    AttestationPreimage effect =
        AttestationPreimage.decode(
            checkedEvidence.effectPreimage(), AttestationAuthorizationFailure.GENESIS_INVALID);
    AttestationPreimage.Fact expected =
        AttestationGenesisPreimageProjection.bookIdentityEffect(
            verification.bookId(), checkedBookIdentity);
    boolean matches =
        effect.records().stream()
            .filter(record -> record.recordTypeTag() == expected.recordTypeTag())
            .anyMatch(record -> hasSameCanonicalEncoding(record, expected));
    if (!matches) {
      throw new IllegalArgumentException(
          "Attestation genesis does not bind the supplied book identity.");
    }
    return verification.bookId();
  }

  private static boolean hasSameCanonicalEncoding(
      AttestationPreimage.Fact left, AttestationPreimage.Fact right) {
    return Arrays.equals(
        AttestationPreimage.of(List.of(left)).encoded(),
        AttestationPreimage.of(List.of(right)).encoded());
  }
}
