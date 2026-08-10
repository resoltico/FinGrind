package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Verifies canonical authenticated publication-transaction journal encoding and admission. */
class PublicationTransactionJournalCodecTest {
  private static final byte[] OWNER_KEY = {
    0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
    0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
    0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
    0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
  };

  @Test
  void roundTripsOnePreparedJournalThroughOneCanonicalAuthenticatedRepresentation()
      throws Exception {
    PublicationTransactionJournal journal = preparedJournal();

    byte[] encoded = PublicationTransactionJournalCodec.encode(journal, OWNER_KEY);
    PublicationTransactionJournal decoded =
        PublicationTransactionJournalCodec.decode(encoded, OWNER_KEY);

    assertEquals(journal, decoded);
    assertArrayEquals(encoded, PublicationTransactionJournalCodec.encode(decoded, OWNER_KEY));
  }

  @Test
  void rejectsOneAuthenticatedJournalWhenItsPayloadChanges() throws Exception {
    String encoded =
        new String(
            PublicationTransactionJournalCodec.encode(preparedJournal(), OWNER_KEY),
            StandardCharsets.UTF_8);
    byte[] modified =
        encoded.replaceFirst("protected-book", "protected-cook").getBytes(StandardCharsets.UTF_8);

    PublicationTransactionJournalViolation violation =
        assertThrows(
            PublicationTransactionJournalViolation.class,
            () -> PublicationTransactionJournalCodec.decode(modified, OWNER_KEY));

    assertEquals(PublicationTransactionJournalViolation.Kind.INTEGRITY, violation.kind());
  }

  @Test
  void rejectsWhitespaceEvenWhenItsParsedPayloadWouldAuthenticate() throws Exception {
    byte[] encoded = PublicationTransactionJournalCodec.encode(preparedJournal(), OWNER_KEY);
    byte[] nonCanonical =
        (" " + new String(encoded, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);

    PublicationTransactionJournalViolation violation =
        assertThrows(
            PublicationTransactionJournalViolation.class,
            () -> PublicationTransactionJournalCodec.decode(nonCanonical, OWNER_KEY));

    assertEquals(PublicationTransactionJournalViolation.Kind.NON_CANONICAL, violation.kind());
  }

  @Test
  void rejectsDuplicatePropertiesBeforeItTreatsTheJournalAsOwned() throws Exception {
    String encoded =
        new String(
            PublicationTransactionJournalCodec.encode(preparedJournal(), OWNER_KEY),
            StandardCharsets.UTF_8);
    byte[] duplicateSchema =
        encoded
            .replaceFirst("\\\"schema\\\":2", "\\\"schema\\\":2,\\\"schema\\\":2")
            .getBytes(StandardCharsets.UTF_8);

    PublicationTransactionJournalViolation violation =
        assertThrows(
            PublicationTransactionJournalViolation.class,
            () -> PublicationTransactionJournalCodec.decode(duplicateSchema, OWNER_KEY));

    assertEquals(PublicationTransactionJournalViolation.Kind.MALFORMED, violation.kind());
  }

  @Test
  void roundTripsEverySecretBearingMemberAndTheFullSuccessTransitionSequence() throws Exception {
    PublicationTransactionJournal journal = completedJournal();

    byte[] encoded = PublicationTransactionJournalCodec.encode(journal, OWNER_KEY);

    assertEquals(journal, PublicationTransactionJournalCodec.decode(encoded, OWNER_KEY));
  }

  @Test
  void roundTripsTheLegacyNoReplaceShapeWithoutGuessingReplacementAuthority() throws Exception {
    PublicationTransactionJournal legacy = legacyNoReplaceJournal();

    byte[] encoded = PublicationTransactionJournalCodec.encode(legacy, OWNER_KEY);

    assertEquals(legacy, PublicationTransactionJournalCodec.decode(encoded, OWNER_KEY));
    assertArrayEquals(encoded, PublicationTransactionJournalCodec.encode(legacy, OWNER_KEY));
  }

  @Test
  void rejectsEveryMalformedJournalShapeBeforeRecoveryCanTreatItAsOwned() throws Exception {
    String prepared =
        new String(
            PublicationTransactionJournalCodec.encode(preparedJournal(), OWNER_KEY),
            StandardCharsets.UTF_8);
    String completed =
        new String(
            PublicationTransactionJournalCodec.encode(completedJournal(), OWNER_KEY),
            StandardCharsets.UTF_8);
    String legacy =
        new String(
            PublicationTransactionJournalCodec.encode(legacyNoReplaceJournal(), OWNER_KEY),
            StandardCharsets.UTF_8);
    List<byte[]> malformed =
        List.of(
            new byte[0],
            "[\"not a journal\"]".getBytes(StandardCharsets.UTF_8),
            "{".getBytes(StandardCharsets.UTF_8),
            prepared.replaceFirst("\\\"schema\\\":2,", "").getBytes(StandardCharsets.UTF_8),
            prepared
                .replaceFirst("\\\"schema\\\":2", "\\\"unsupported\\\":2")
                .getBytes(StandardCharsets.UTF_8),
            prepared
                .replaceFirst("\\\"schema\\\":2", "\\\"schema\\\":\\\"2\\\"")
                .getBytes(StandardCharsets.UTF_8),
            prepared
                .replaceFirst("\\\"members\\\":\\[", "\\\"members\\\":{}")
                .getBytes(StandardCharsets.UTF_8),
            prepared
                .replaceFirst("\\\"members\\\":\\[\\{", "\\\"members\\\":[null")
                .getBytes(StandardCharsets.UTF_8),
            prepared
                .replaceFirst("\\\"stagedArtifact\\\":null", "\\\"stagedArtifact\\\":[]")
                .getBytes(StandardCharsets.UTF_8),
            prepared
                .replaceFirst("\\\"replacementTarget\\\":null", "\\\"replacementTarget\\\":[]")
                .getBytes(StandardCharsets.UTF_8),
            prepared
                .replaceFirst("\\\"replacementTarget\\\":null,", "")
                .getBytes(StandardCharsets.UTF_8),
            legacy
                .replaceFirst("\\\"schema\\\":1", "\\\"schema\\\":2")
                .getBytes(StandardCharsets.UTF_8),
            legacy
                .replaceFirst(
                    "\\\"publicationMode\\\":\\\"no-replace-link\\\",",
                    "\\\"publicationMode\\\":\\\"no-replace-link\\\",\\\"replacementTarget\\\":null,")
                .getBytes(StandardCharsets.UTF_8),
            completed
                .replaceFirst("\\\"stagedArtifact\\\":\\{", "\\\"stagedArtifact\\\":[]")
                .getBytes(StandardCharsets.UTF_8),
            completed
                .replaceFirst("\\\"finalizedArtifact\\\":\\{", "\\\"finalizedArtifact\\\":[]")
                .getBytes(StandardCharsets.UTF_8),
            completed
                .replaceFirst(
                    "\\\"transitions\\\":\\[.*?\\],\\\"integrity\\\"",
                    "\\\"transitions\\\":{},\\\"integrity\\\"")
                .getBytes(StandardCharsets.UTF_8),
            completed
                .replaceFirst("\\\"state\\\":\\\"prepared\\\"", "\\\"state\\\":1")
                .getBytes(StandardCharsets.UTF_8),
            completed
                .replaceFirst(
                    "\\\"recordedAt\\\":\\\"2026-08-10T12:34:56Z\\\"",
                    "\\\"recordedAt\\\":\\\"not-an-instant\\\"")
                .getBytes(StandardCharsets.UTF_8),
            prepared
                .replaceFirst(
                    "\\\"nonceHex\\\":\\\"[0-9a-f]+\\\"", "\\\"nonceHex\\\":\\\"invalid\\\"")
                .getBytes(StandardCharsets.UTF_8),
            prepared
                .replaceFirst(
                    "\\\"nonceHex\\\":\\\"[0-9a-f]+\\\"",
                    "\\\"nonceHex\\\":\\\"" + "A".repeat(32) + "\\\"")
                .getBytes(StandardCharsets.UTF_8));

    for (byte[] bytes : malformed) {
      assertMalformed(bytes);
    }
  }

  @Test
  void rejectsKeysThatCannotServeAsOneOwnerAuthenticationSecret() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PublicationTransactionJournalCodec.encode(preparedJournal(), new byte[31]));
    assertThrows(
        IllegalArgumentException.class,
        () -> PublicationTransactionJournalCodec.decode(new byte[0], new byte[33]));
  }

  @Test
  void reportsAnEncodingFailureInsteadOfPublishingAnUnverifiedPartialJournal() throws Exception {
    try (OutputStream failingOutput =
        new OutputStream() {
          @Override
          public void write(int ignored) throws IOException {
            throw new IOException("write failed");
          }
        }) {

      assertThrows(
          IllegalStateException.class,
          () ->
              PublicationTransactionJournalJsonWriter.writeTo(
                  JsonMapper.builder().build(),
                  failingOutput,
                  preparedJournal(),
                  Optional.empty()));
    }
  }

  @Test
  void rejectsWhitespaceOnlyJournalInputAtTheStrictReaderBoundary() {
    PublicationTransactionJournalViolation violation =
        assertThrows(
            PublicationTransactionJournalViolation.class,
            () ->
                PublicationTransactionJournalJsonReader.read(
                    JsonMapper.builder().build(), " \t".getBytes(StandardCharsets.UTF_8)));

    assertEquals(PublicationTransactionJournalViolation.Kind.MALFORMED, violation.kind());
  }

  private static PublicationTransactionJournal preparedJournal() {
    return PublicationTransactionJournal.prepared(
        new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
        "fedcba9876543210fedcba9876543210",
        CryptographicPrimitives.sha256Hex(OWNER_KEY),
        Instant.parse("2026-08-10T12:34:56Z"),
        List.of(
            new PublicationTransactionMember(
                "protected-book",
                PublicationTransactionMemberRole.PROTECTED_BOOK,
                Path.of("journal-test", "protected-book.fgb"),
                Path.of("journal-test", ".protected-book-stage"),
                "directory-identity",
                PublicationMode.NO_REPLACE_LINK,
                PublicationTransactionMemberProgress.PLANNED,
                Optional.empty(),
                Optional.empty())));
  }

  private static PublicationTransactionJournal completedJournal() {
    PublicationTransactionJournal prepared =
        PublicationTransactionJournal.prepared(
            new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
            "fedcba9876543210fedcba9876543210",
            CryptographicPrimitives.sha256Hex(OWNER_KEY),
            Instant.parse("2026-08-10T12:34:56Z"),
            List.of(
                committedMember(
                    "protected-book",
                    PublicationTransactionMemberRole.PROTECTED_BOOK,
                    PublicationMode.NO_REPLACE_LINK,
                    "a"),
                committedMember(
                    "encrypted-book-key",
                    PublicationTransactionMemberRole.ENCRYPTED_BOOK_KEY,
                    PublicationMode.NO_REPLACE_LINK,
                    "b"),
                committedMember(
                    "attestation-key",
                    PublicationTransactionMemberRole.ATTESTATION_KEY,
                    PublicationMode.NO_REPLACE_LINK,
                    "c"),
                committedMember(
                    "attestation-receipt",
                    PublicationTransactionMemberRole.ATTESTATION_RECEIPT,
                    PublicationMode.REPLACE,
                    "d"),
                committedMember(
                    "pdf-report",
                    PublicationTransactionMemberRole.PDF_REPORT,
                    PublicationMode.REPLACE,
                    "e"),
                committedMember(
                    "passphrase-file",
                    PublicationTransactionMemberRole.PASSPHRASE_FILE,
                    PublicationMode.REPLACE,
                    "f")));
    return prepared
        .transition(
            transition(
                PublicationTransactionState.STAGED,
                PublicationCommitOutcome.NONE_COMMITTED,
                PublicationCleanupOutcome.COMPLETE,
                1L))
        .transition(
            transition(
                PublicationTransactionState.COMMITTING,
                PublicationCommitOutcome.NONE_COMMITTED,
                PublicationCleanupOutcome.COMPLETE,
                2L))
        .transition(
            transition(
                PublicationTransactionState.COMMITTED,
                PublicationCommitOutcome.ALL_COMMITTED,
                PublicationCleanupOutcome.COMPLETE,
                3L))
        .transition(
            transition(
                PublicationTransactionState.CLEANING,
                PublicationCommitOutcome.ALL_COMMITTED,
                PublicationCleanupOutcome.COMPLETE,
                4L))
        .updateMembers(
            prepared.members().stream()
                .map(PublicationTransactionJournalCodecTest::cleanedMember)
                .toList())
        .transition(
            transition(
                PublicationTransactionState.COMPLETE,
                PublicationCommitOutcome.ALL_COMMITTED,
                PublicationCleanupOutcome.COMPLETE,
                5L));
  }

  private static PublicationTransactionJournal legacyNoReplaceJournal() {
    return new PublicationTransactionJournal(
        PublicationTransactionJournal.LEGACY_SCHEMA_VERSION,
        new PublicationTransactionId("0123456789abcdef0123456789abcdef"),
        "fedcba9876543210fedcba9876543210",
        CryptographicPrimitives.sha256Hex(OWNER_KEY),
        Instant.parse("2026-08-10T12:34:56Z"),
        List.of(
            new PublicationTransactionMember(
                "protected-book",
                PublicationTransactionMemberRole.PROTECTED_BOOK,
                Path.of("journal-test", "protected-book.fgb"),
                Path.of("journal-test", ".protected-book-stage"),
                "directory-identity",
                PublicationMode.NO_REPLACE_LINK,
                PublicationTransactionMemberProgress.PLANNED,
                Optional.empty(),
                Optional.empty())),
        List.of(PublicationTransactionTransition.prepared(Instant.parse("2026-08-10T12:34:56Z"))));
  }

  private static PublicationTransactionMember committedMember(
      String memberId,
      PublicationTransactionMemberRole role,
      PublicationMode mode,
      String hexCharacter) {
    String digest = hexCharacter.repeat(64);
    return new PublicationTransactionMember(
        memberId,
        role,
        Path.of("reports", memberId),
        Path.of("reports", "." + memberId + "-stage"),
        "directory-" + memberId,
        mode,
        replacementTarget(memberId, mode),
        PublicationTransactionMemberProgress.COMMITTED,
        Optional.of(
            new PublicationTransactionStagedArtifact(
                Path.of("reports", "." + memberId + "-stage"), "stage-" + memberId, digest)),
        Optional.of(new PublicationTransactionFinalizedArtifact("final-" + memberId, digest)));
  }

  private static PublicationTransactionMember cleanedMember(PublicationTransactionMember member) {
    return new PublicationTransactionMember(
        member.memberId(),
        member.role(),
        member.finalPath(),
        member.stagePath(),
        member.physicalDirectoryIdentity(),
        member.publicationMode(),
        member.replacementTarget(),
        PublicationTransactionMemberProgress.CLEANED,
        member.stagedArtifact(),
        member.finalizedArtifact());
  }

  private static Optional<PublicationTransactionFinalizedArtifact> replacementTarget(
      String memberId, PublicationMode mode) {
    if (mode == PublicationMode.NO_REPLACE_LINK) {
      return Optional.empty();
    }
    return Optional.of(
        new PublicationTransactionFinalizedArtifact("replacement-" + memberId, "f".repeat(64)));
  }

  private static PublicationTransactionTransition transition(
      PublicationTransactionState state,
      PublicationCommitOutcome commitOutcome,
      PublicationCleanupOutcome cleanupOutcome,
      long seconds) {
    return new PublicationTransactionTransition(
        state,
        Instant.parse("2026-08-10T12:34:56Z").plusSeconds(seconds),
        new PublicationTransactionOutcome(commitOutcome, cleanupOutcome));
  }

  private static void assertMalformed(byte[] bytes) {
    PublicationTransactionJournalViolation violation =
        assertThrows(
            PublicationTransactionJournalViolation.class,
            () -> PublicationTransactionJournalCodec.decode(bytes, OWNER_KEY));
    assertEquals(PublicationTransactionJournalViolation.Kind.MALFORMED, violation.kind());
  }
}
