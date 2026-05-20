package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.SqliteRuntimeArtifactEvidence;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SqliteRuntimeArtifactEvidence}. */
class SqliteRuntimeArtifactEvidenceTest {
  @Test
  void constructorNormalizesEveryRequiredField() {
    SqliteRuntimeArtifactEvidence evidence =
        new SqliteRuntimeArtifactEvidence(
            " toolchain-fingerprint.json ",
            " toolchain-digest ",
            " build-contract.json ",
            " build-digest ");

    assertEquals("toolchain-fingerprint.json", evidence.toolchainFingerprintPath());
    assertEquals("toolchain-digest", evidence.toolchainFingerprintSha256());
    assertEquals("build-contract.json", evidence.buildContractPath());
    assertEquals("build-digest", evidence.buildContractSha256());
  }

  @Test
  void constructorRejectsMissingOrBlankFields() {
    assertThrows(
        NullPointerException.class,
        () ->
            new SqliteRuntimeArtifactEvidence(
                NullTestSupport.nullOf(),
                "toolchain-digest",
                "build-contract.json",
                "build-digest"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntimeArtifactEvidence(
                "toolchain-fingerprint.json", " ", "build-contract.json", "build-digest"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntimeArtifactEvidence(
                "toolchain-fingerprint.json", "toolchain-digest", " ", "build-digest"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new SqliteRuntimeArtifactEvidence(
                "toolchain-fingerprint.json", "toolchain-digest", "build-contract.json", " "));
  }
}
