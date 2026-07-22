package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Exercises a public credential enrollment through the protected SQLite lifecycle workflow. */
class FinGrindCliAttestationRegistryMutationWorkflowTest extends FinGrindCliTestSupport {
  private static final String CREDENTIAL_SPKI =
      "MCowBQYDK2VwAyEAJYpWgBK4pHaKkIRKs9p8_6B01sG0SuOXLjI69Q5mGlI";
  private static final String REPLACEMENT_CREDENTIAL_SPKI =
      "MCowBQYDK2VwAyEAJYpWgBK4pHaKkIRKs9p8_6B01sG0SuOXLjI69Q5mGlM";

  @Test
  void run_enrollKeyAppendsOneVerifiableAuthorityMutation() throws IOException {
    Path bookFilePath = tempDirectory.resolve("attestation-registry").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    Path requestFile =
        writeNamedRequest(
            "enroll-key.json",
            """
            {
              "principalId": "01234567-89ab-4cde-8fab-0123456789ab",
              "credentialSpki": "%s",
              "credentialPurpose": "operator"
            }
            """
                .formatted(CREDENTIAL_SPKI));

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock())
            .run(
                jsonArguments(
                    "enroll-key",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    requestFile.toString()));

    assertEquals(0, exitCode, output.toString(StandardCharsets.UTF_8));
    JsonNode payload = new ObjectMapper().readTree(output.toByteArray()).path("payload");
    assertEquals("enroll-key", payload.path("operationKind").stringValue());
    assertEquals("1", payload.path("headOrder").stringValue());
    assertTrue(payload.path("bookFile").stringValue().endsWith("entity.sqlite"));
  }

  @Test
  void run_rolloverRevocationAndPolicyChangesAppendThroughTheirOwnCommands() throws IOException {
    Path bookFilePath = tempDirectory.resolve("attestation-registry-all").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    Path enrollmentRequest =
        writeNamedRequest(
            "enroll.json",
            """
            {
              "principalId": "01234567-89ab-4cde-8fab-0123456789ab",
              "credentialSpki": "%s",
              "credentialPurpose": "operator"
            }
            """
                .formatted(CREDENTIAL_SPKI));
    Path rolloverRequest =
        writeNamedRequest(
            "rollover.json",
            """
            {
              "principalId": "01234567-89ab-4cde-8fab-0123456789ab",
              "credentialSpki": "%s",
              "credentialPurpose": "system",
              "predecessorCredentialSpki": "%s"
            }
            """
                .formatted(REPLACEMENT_CREDENTIAL_SPKI, CREDENTIAL_SPKI));
    Path revocationRequest =
        writeNamedRequest(
            "revoke.json",
            """
            {
              "principalId": "01234567-89ab-4cde-8fab-0123456789ab",
              "credentialSpki": "%s",
              "reason": "superseded"
            }
            """
                .formatted(CREDENTIAL_SPKI));
    Path policyRequest =
        writeNamedRequest(
            "policy.json",
            """
            {
              "policyRules": [{ "capability": "post", "quorum": 1 }]
            }
            """);

    assertJsonMutationSucceeds(bookFilePath, bookKeyFilePath, "enroll-key", enrollmentRequest);
    assertJsonMutationSucceeds(bookFilePath, bookKeyFilePath, "rollover-key", rolloverRequest);
    assertJsonMutationSucceeds(bookFilePath, bookKeyFilePath, "revoke-key", revocationRequest);

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock())
            .run(
                jsonArguments(
                    "alter-policy",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    policyRequest.toString(),
                    "--output",
                    "text"));

    assertEquals(0, exitCode, output.toString(StandardCharsets.UTF_8));
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("alter-policy appended"));
  }

  private void assertJsonMutationSucceeds(
      Path bookFilePath, Path bookKeyFilePath, String command, Path requestFile) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock())
            .run(
                jsonArguments(
                    command,
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    requestFile.toString()));
    assertEquals(0, exitCode, output.toString(StandardCharsets.UTF_8));
  }
}
