package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
class FinGrindCliAttestationKeyFileWorkflowTest extends CliWorkflowFixtureSupport {
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
              "--attestation-custodian",
              "file-pkcs8",
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
        CliPublicPaths.absoluteValue(keyFilePath.toRealPath()),
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
              "--attestation-custodian",
              "file-pkcs8",
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
          "--attestation-custodian",
          "file-pkcs8",
          "--new-attestation-key-file",
          keyFilePath.toString(),
          "--attestation-passphrase-file",
          passphraseFilePath.toString(),
          "--output",
          "json"
        };
    assertEquals(0, cli.run(command));
    byte[] keyFileBefore = Files.readAllBytes(keyFilePath);

    output.reset();
    assertEquals(7, cli.run(command));
    JsonNode occupiedTarget = new ObjectMapper().readTree(output.toByteArray());
    assertEquals("error", occupiedTarget.path("status").stringValue());
    assertEquals(
        ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.code(),
        occupiedTarget.path("code").stringValue());
    assertArrayEquals(keyFileBefore, Files.readAllBytes(keyFilePath));

    output.reset();
    assertEquals(
        6,
        cli.run(
            new String[] {
              "generate-attestation-key-file",
              "--attestation-custodian",
              "file-pkcs8",
              "--new-attestation-key-file",
              tempDirectory.resolve("missing").resolve("operator.fgatk").toString(),
              "--attestation-passphrase-file",
              passphraseFilePath.toString(),
              "--output",
              "json"
            }));
    assertEquals(
        ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY.code(),
        new ObjectMapper().readTree(output.toByteArray()).path("code").stringValue());

    output.reset();
    assertEquals(
        6,
        cli.run(
            new String[] {
              "generate-attestation-key-file",
              "--attestation-custodian",
              "file-pkcs8",
              "--new-attestation-key-file",
              tempDirectory.resolve("secrets").resolve("invalid-passphrase.fgatk").toString(),
              "--attestation-passphrase-file",
              tempDirectory.resolve("missing.passphrase").toString(),
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
              "--attestation-custodian",
              "file-pkcs8",
              "--attestation-key-file",
              tempDirectory.resolve("missing.fgatk").toString(),
              "--output",
              "json"
            }));
    assertEquals(
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.code(),
        new ObjectMapper().readTree(output.toByteArray()).path("code").stringValue());

    output.reset();
    assertEquals(
        2,
        cli.run(
            new String[] {
              "generate-attestation-key-file",
              "--attestation-custodian",
              "pkcs11",
              "--new-attestation-key-file",
              tempDirectory.resolve("unsupported.fgatk").toString(),
              "--attestation-passphrase-file",
              passphraseFilePath.toString(),
              "--output",
              "json"
            }));
    assertEquals(
        ContractErrors.Descriptor.CUSTODIAN_NOT_SUPPORTED.code(),
        new ObjectMapper().readTree(output.toByteArray()).path("code").stringValue());
  }

  @Test
  void generate_refusesParentlessAndSymlinkedKeyTargetsBeforeCreatingACredential()
      throws Exception {
    Path passphraseFilePath = tempDirectory.resolve("secrets").resolve("operator.passphrase");
    writeSecureKey(passphraseFilePath, "operator credential passphrase");
    Path symlinkParent = tempDirectory.resolve("linked-secrets");
    Files.createSymbolicLink(symlinkParent, tempDirectory.resolve("secrets"));
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock());

    assertEquals(
        6,
        cli.run(
            new String[] {
              "generate-attestation-key-file",
              "--attestation-custodian",
              "file-pkcs8",
              "--new-attestation-key-file",
              Path.of("/").toString(),
              "--attestation-passphrase-file",
              passphraseFilePath.toString(),
              "--output",
              "json"
            }));
    assertEquals(
        ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY.code(),
        new ObjectMapper().readTree(output.toByteArray()).path("code").stringValue());

    output.reset();
    assertEquals(
        6,
        cli.run(
            new String[] {
              "generate-attestation-key-file",
              "--attestation-custodian",
              "file-pkcs8",
              "--new-attestation-key-file",
              symlinkParent.resolve("operator.fgatk").toString(),
              "--attestation-passphrase-file",
              passphraseFilePath.toString(),
              "--output",
              "json"
            }));
    assertEquals(
        ContractErrors.Descriptor.INVALID_ARTIFACT_OUTPUT_DIRECTORY.code(),
        new ObjectMapper().readTree(output.toByteArray()).path("code").stringValue());
  }

  @Test
  void generate_reportsTheCanonicalKeyPathForFailuresAfterIntermediateAliasAdmission()
      throws Exception {
    Path physicalKeyDirectory = tempDirectory.resolve("physical-key-output");
    createExistingOwnerOnlyParentDirectory(physicalKeyDirectory.resolve("operator.fgatk"));
    Path outputRootAlias = tempDirectory.resolve("key-output-root-alias");
    Files.createSymbolicLink(outputRootAlias, tempDirectory);
    Path requestedOccupiedPath = outputRootAlias.resolve("physical-key-output/operator.fgatk");
    Path canonicalOccupiedPath =
        physicalKeyDirectory.toRealPath().resolve("operator.fgatk").toAbsolutePath().normalize();
    Files.writeString(canonicalOccupiedPath, "occupied");
    Path passphraseFilePath = tempDirectory.resolve("secrets").resolve("operator.passphrase");
    writeSecureKey(passphraseFilePath, "operator credential passphrase");
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock());

    assertEquals(7, cli.run(generateArguments(requestedOccupiedPath, passphraseFilePath)));
    JsonNode occupied = new ObjectMapper().readTree(output.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.SECRET_TARGET_OCCUPIED.code(),
        occupied.path("code").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(canonicalOccupiedPath), occupied.path("path").stringValue());
    assertNotEquals(
        CliPublicPaths.absoluteValue(requestedOccupiedPath), occupied.path("path").stringValue());

    output.reset();
    Path requestedCredentialPath =
        outputRootAlias.resolve("physical-key-output/missing-passphrase.fgatk");
    Path canonicalCredentialPath =
        physicalKeyDirectory
            .toRealPath()
            .resolve("missing-passphrase.fgatk")
            .toAbsolutePath()
            .normalize();
    assertEquals(
        6,
        cli.run(
            generateArguments(
                requestedCredentialPath, tempDirectory.resolve("missing.passphrase"))));
    JsonNode invalidCredential = new ObjectMapper().readTree(output.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.code(),
        invalidCredential.path("code").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(canonicalCredentialPath),
        invalidCredential.path("path").stringValue());
    assertNotEquals(
        CliPublicPaths.absoluteValue(requestedCredentialPath),
        invalidCredential.path("path").stringValue());
  }

  private static String[] generateArguments(Path keyFilePath, Path passphraseFilePath) {
    return new String[] {
      "generate-attestation-key-file",
      "--attestation-custodian",
      "file-pkcs8",
      "--new-attestation-key-file",
      keyFilePath.toString(),
      "--attestation-passphrase-file",
      passphraseFilePath.toString(),
      "--output",
      "json"
    };
  }
}
