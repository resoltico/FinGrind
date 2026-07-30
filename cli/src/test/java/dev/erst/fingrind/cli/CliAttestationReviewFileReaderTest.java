package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exhaustively exercises strict compromise-review artifact admission and refusal paths. */
class CliAttestationReviewFileReaderTest {
  private static final String KEY_ID = "a".repeat(64);

  @TempDir Path tempDirectory;

  @Test
  void readsCanonicalBoundedReviewsWithAnExplicitLastAffectedOrder() throws Exception {
    Path reviewFile = tempDirectory.resolve("review.json");
    Files.writeString(
        reviewFile,
        """
        {"compromiseReviews":[{"credentialKeyId":"%s","firstAffectedOrder":"1","lastAffectedOrder":"3"}]}
        """
            .formatted(KEY_ID));

    assertEquals(
        List.of(new AttestationCompromiseReview(KEY_ID, BigInteger.ONE, BigInteger.valueOf(3))),
        new CliAttestationReviewFileReader().read(reviewFile));
  }

  @Test
  void readsOpenEndedReviewsWhenLastAffectedOrderIsOmittedOrNull() throws Exception {
    Path omittedEnd = tempDirectory.resolve("omitted-end.json");
    Files.writeString(
        omittedEnd,
        """
        {"compromiseReviews":[{"credentialKeyId":"%s","firstAffectedOrder":"1"}]}
        """
            .formatted(KEY_ID));
    Path nullEnd = tempDirectory.resolve("null-end.json");
    Files.writeString(
        nullEnd,
        """
        {"compromiseReviews":[{"credentialKeyId":"%s","firstAffectedOrder":"1","lastAffectedOrder":null}]}
        """
            .formatted(KEY_ID));

    List<AttestationCompromiseReview> expected =
        List.of(new AttestationCompromiseReview(KEY_ID, BigInteger.ONE, null));
    CliAttestationReviewFileReader reader = new CliAttestationReviewFileReader();

    assertEquals(expected, reader.read(omittedEnd));
    assertEquals(expected, reader.read(nullEnd));
  }

  @Test
  void refusesMissingDuplicateOversizedAndMalformedReviewArtifacts() throws Exception {
    assertReviewFileArgument(
        () -> new CliAttestationReviewFileReader().read(tempDirectory.resolve("missing.json")));

    Path duplicateKeys = tempDirectory.resolve("duplicate-keys.json");
    Files.writeString(duplicateKeys, "{\"compromiseReviews\":[],\"compromiseReviews\":[]}");
    assertReviewFileArgument(() -> new CliAttestationReviewFileReader().read(duplicateKeys));

    Path oversized = tempDirectory.resolve("oversized.json");
    Files.write(oversized, new byte[ProtocolInteractionLimits.REQUEST_PAYLOAD_MAX_BYTES + 1]);
    assertReviewFileArgument(() -> new CliAttestationReviewFileReader().read(oversized));

    Path malformed = tempDirectory.resolve("malformed.json");
    Files.writeString(malformed, "{not-json", StandardCharsets.UTF_8);
    CliArgumentsException exception =
        assertThrows(
            CliArgumentsException.class,
            () -> new CliAttestationReviewFileReader().read(malformed));
    assertEquals("--attestation-review-file", exception.argument());
    assertNotNull(exception.getCause());
  }

  @Test
  void refusesAFinalReviewFileAlias() throws Exception {
    Path target = tempDirectory.resolve("review-target.json");
    Files.writeString(target, "{\"compromiseReviews\":[]}", StandardCharsets.UTF_8);
    Path alias = tempDirectory.resolve("review-alias.json");
    createSymbolicLinkOrSkip(alias, target.getFileName());

    assertReviewFileArgument(() -> new CliAttestationReviewFileReader().read(alias));
  }

  private static void assertReviewFileArgument(org.junit.jupiter.api.function.Executable action) {
    CliArgumentsException exception = assertThrows(CliArgumentsException.class, action);
    assertEquals("--attestation-review-file", exception.argument());
  }

  private static void createSymbolicLinkOrSkip(Path alias, Path target) throws java.io.IOException {
    try {
      Files.createSymbolicLink(alias, target);
    } catch (UnsupportedOperationException | SecurityException | FileSystemException unavailable) {
      assumeTrue(
          false, "The filesystem does not permit symbolic-link test fixtures: " + unavailable);
    }
  }
}
