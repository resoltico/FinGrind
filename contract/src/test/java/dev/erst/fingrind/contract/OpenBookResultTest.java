package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Contract tests for the founder-key publication facts retained by successful book opening. */
class OpenBookResultTest extends ContractTestSupport {
  private static final String OPERATION_HEAD =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @TempDir Path temporaryDirectory;

  @Test
  void opened_preservesDistinctFounderKeyPublicationFactsWithoutAliasingCallerCollections() {
    ArtifactPublicationResult firstFounderKey =
        new ArtifactPublicationResult(
            temporaryDirectory.resolve("founder-1.fgatk"),
            new ArtifactPublicationRetention(temporaryDirectory.resolve(".founder-1-stage")));
    ArtifactPublicationResult secondFounderKey =
        new ArtifactPublicationResult(
            temporaryDirectory.resolve("founder-2.fgatk"),
            new ArtifactPublicationRetention(temporaryDirectory.resolve(".founder-2-stage")));
    List<ArtifactPublicationResult> mutableFounderKeys =
        new ArrayList<>(List.of(firstFounderKey, secondFounderKey));
    AttestationRegistryInspection trustRoot = attestationTrustRoot();

    OpenBookResult.Opened opened =
        new OpenBookResult.Opened(
            Instant.parse("2026-07-26T12:00:00Z"),
            bookIdentity(),
            trustRoot,
            new AttestationCommit(trustRoot.headOrder(), trustRoot.operationHeadHex()),
            mutableFounderKeys);
    mutableFounderKeys.clear();

    assertEquals(List.of(firstFounderKey, secondFounderKey), opened.retainedFounderKeyArtifacts());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new OpenBookResult.Opened(
                Instant.parse("2026-07-26T12:00:00Z"),
                bookIdentity(),
                trustRoot,
                new AttestationCommit(trustRoot.headOrder(), trustRoot.operationHeadHex()),
                List.of(firstFounderKey, firstFounderKey)));
  }

  private static AttestationRegistryInspection attestationTrustRoot() {
    return new AttestationRegistryInspection(
        UUID.fromString("10213243-5465-7687-98a9-babcbddceeff"),
        BigInteger.ZERO,
        OPERATION_HEAD,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
