package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationKeyFiles;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Exercises the public off-book credential custody lifecycle through the real CLI boundary. */
class FinGrindCliAttestationKeyFileWorkflowTest extends FinGrindCliTestSupport {
  @Test
  void generateAndInspect_publishOnlyTheExactEnrollmentIdentity() throws Exception {
    Path keyFilePath = tempDirectory.resolve("secrets").resolve("operator.fgatk");
    Path passphraseFilePath = tempDirectory.resolve("secrets").resolve("operator.passphrase");
    writeSecureKey(passphraseFilePath, "operator credential passphrase");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock());

    assertEquals(
        0,
        cli.run(
            new String[] {
              "generate-attestation-key-file",
              "--new-attestation-key-file",
              keyFilePath.toString(),
              "--attestation-passphrase-file",
              passphraseFilePath.toString(),
              "--output",
              "json"
            }));

    JsonNode generated = new ObjectMapper().readTree(output.toByteArray());
    String credentialSpki = generated.path("payload").path("credentialSpki").stringValue();
    String keyId = generated.path("payload").path("keyId").stringValue();
    assertTrue(Files.isRegularFile(keyFilePath));
    assertEquals(
        ProtocolArtifactOutput.attestationKeyFileFormat(),
        generated.path("artifacts").get(0).path("format").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(keyFilePath),
        generated.path("artifacts").get(0).path("path").stringValue());
    assertEquals(
        credentialSpki,
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(AttestationKeyFiles.loadPublicCredential(keyFilePath).spki()));
    assertFalse(generated.toString().contains("operator credential passphrase"));

    output.reset();
    assertEquals(
        0,
        cli.run(
            new String[] {
              "inspect-attestation-key-file",
              "--attestation-key-file",
              keyFilePath.toString(),
              "--output",
              "json"
            }));
    JsonNode inspected = new ObjectMapper().readTree(output.toByteArray());
    assertEquals(credentialSpki, inspected.path("payload").path("credentialSpki").stringValue());
    assertEquals(keyId, inspected.path("payload").path("keyId").stringValue());
    assertFalse(inspected.has("artifacts"));
  }

  @Test
  void generate_refusesAnExistingTargetAndInvalidCustodyInput() throws Exception {
    Path keyFilePath = tempDirectory.resolve("secrets").resolve("operator.fgatk");
    Path passphraseFilePath = tempDirectory.resolve("secrets").resolve("operator.passphrase");
    writeSecureKey(passphraseFilePath, "operator credential passphrase");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock());
    String[] command =
        new String[] {
          "generate-attestation-key-file",
          "--new-attestation-key-file",
          keyFilePath.toString(),
          "--attestation-passphrase-file",
          passphraseFilePath.toString(),
          "--output",
          "json"
        };
    assertEquals(0, cli.run(command));

    output.reset();
    assertEquals(7, cli.run(command));
    assertEquals(
        ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.code(),
        new ObjectMapper().readTree(output.toByteArray()).path("code").stringValue());

    output.reset();
    assertEquals(
        6,
        cli.run(
            new String[] {
              "generate-attestation-key-file",
              "--new-attestation-key-file",
              tempDirectory.resolve("missing").resolve("operator.fgatk").toString(),
              "--attestation-passphrase-file",
              passphraseFilePath.toString(),
              "--output",
              "json"
            }));
    assertEquals(
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.code(),
        new ObjectMapper().readTree(output.toByteArray()).path("code").stringValue());

    output.reset();
    assertEquals(
        6,
        cli.run(
            new String[] {
              "inspect-attestation-key-file",
              "--attestation-key-file",
              tempDirectory.resolve("missing.fgatk").toString(),
              "--output",
              "json"
            }));
    assertEquals(
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.code(),
        new ObjectMapper().readTree(output.toByteArray()).path("code").stringValue());
  }
}
