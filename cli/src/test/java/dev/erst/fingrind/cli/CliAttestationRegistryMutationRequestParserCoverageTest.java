package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.attestation.AttestationCapability;
import dev.erst.fingrind.core.attestation.AttestationGrantState;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Boundary coverage for the closed public credential-registry request language. */
class CliAttestationRegistryMutationRequestParserCoverageTest {
  private static final String CREDENTIAL_SPKI =
      "MCowBQYDK2VwAyEAJYpWgBK4pHaKkIRKs9p8_6B01sG0SuOXLjI69Q5mGlI";
  private static final String REPLACEMENT_CREDENTIAL_SPKI =
      "MCowBQYDK2VwAyEAJYpWgBK4pHaKkIRKs9p8_6B01sG0SuOXLjI69Q5mGlM";
  private static final String PRINCIPAL_ID = "01234567-89ab-4cde-8fab-0123456789ab";

  @Test
  void readsRolloverAndRevocationWithPresentAndAbsentReasons() {
    AttestationRegistryMutation.RolloverKey rollover =
        assertInstanceOf(
            AttestationRegistryMutation.RolloverKey.class,
            read(
                """
                {
                  "principalId": "%s",
                  "credentialSpki": "%s",
                  "credentialPurpose": "system",
                  "predecessorCredentialSpki": "%s"
                }
                """
                    .formatted(PRINCIPAL_ID, REPLACEMENT_CREDENTIAL_SPKI, CREDENTIAL_SPKI),
                OperationId.ROLLOVER_KEY));
    assertEquals(REPLACEMENT_CREDENTIAL_SPKI, encode(rollover.credential()));

    AttestationRegistryMutation.RevokeKey revocation =
        assertInstanceOf(
            AttestationRegistryMutation.RevokeKey.class,
            read(
                """
                {
                  "principalId": "%s",
                  "credentialSpki": "%s",
                  "reason": "hardware retired"
                }
                """
                    .formatted(PRINCIPAL_ID, CREDENTIAL_SPKI),
                OperationId.REVOKE_KEY));
    assertEquals("hardware retired", revocation.reason().orElseThrow());

    AttestationRegistryMutation.RevokeKey noReason =
        assertInstanceOf(
            AttestationRegistryMutation.RevokeKey.class,
            read(
                """
                {
                  "principalId": "%s",
                  "credentialSpki": "%s"
                }
                """
                    .formatted(PRINCIPAL_ID, CREDENTIAL_SPKI),
                OperationId.REVOKE_KEY));
    assertTrue(noReason.reason().isEmpty());
  }

  @Test
  void readsEveryClosedPolicyToken() {
    String policyRules =
        Arrays.stream(AttestationCapability.values())
            .map(capability -> "{\"capability\":\"%s\",\"quorum\":1}".formatted(capability.token()))
            .collect(Collectors.joining(","));

    AttestationRegistryMutation.AlterPolicy policy =
        assertInstanceOf(
            AttestationRegistryMutation.AlterPolicy.class,
            read(
                """
                {
                  "policyRules": [%s],
                  "capabilityGrants": [
                    {
                      "principalId": "%s",
                      "capability": "post",
                      "state": "grant"
                    },
                    {
                      "principalId": "11111111-2222-4333-8444-555555555555",
                      "capability": "approve",
                      "state": "revoke"
                    }
                  ],
                  "systemWorkflowPolicies": [
                    {
                      "workflowId": "22222222-3333-4444-8555-666666666666",
                      "workflowKind": "interim-result-sweep",
                      "resultHoldingAccountCode": "3000",
                      "active": true
                    },
                    {
                      "workflowId": "33333333-4444-4555-8666-777777777777",
                      "workflowKind": "fiscal-year-close",
                      "resultHoldingAccountCode": "3000",
                      "capitalAccountCode": "3100",
                      "retainedResultAccountCode": "3200",
                      "active": false
                    }
                  ]
                }
                """
                    .formatted(policyRules, PRINCIPAL_ID),
                OperationId.ALTER_POLICY));

    assertEquals(AttestationCapability.values().length, policy.policyRules().size());
    assertEquals(AttestationGrantState.REVOKE, policy.capabilityGrants().get(1).state());
    assertEquals(2, policy.systemWorkflowPolicies().size());
  }

  @Test
  void rejectsEveryClosedVocabularyOutsideItsPublishedSet() {
    assertInvalid(
        """
        {
          "principalId": "%s",
          "credentialSpki": "%s",
          "credentialPurpose": "human"
        }
        """
            .formatted(PRINCIPAL_ID, CREDENTIAL_SPKI),
        OperationId.ENROLL_KEY,
        "credentialPurpose");
    assertInvalid(
        """
        {
          "policyRules": [{ "capability": "anything", "quorum": 1 }]
        }
        """,
        OperationId.ALTER_POLICY,
        "capability");
    assertInvalid(
        """
        {
          "capabilityGrants": [{
            "principalId": "%s",
            "capability": "post",
            "state": "maybe"
          }]
        }
        """
            .formatted(PRINCIPAL_ID),
        OperationId.ALTER_POLICY,
        "state");
    assertInvalid(
        """
        {
          "systemWorkflowPolicies": [{
            "workflowId": "22222222-3333-4444-8555-666666666666",
            "workflowKind": "unknown",
            "resultHoldingAccountCode": "3000",
            "active": true
          }]
        }
        """,
        OperationId.ALTER_POLICY,
        "workflowKind");
  }

  @Test
  void rejectsMalformedPublicScalarsBeforeTheyCanBecomeAttestations() {
    assertInvalid(
        """
        {
          "principalId": "not-a-uuid",
          "credentialSpki": "%s",
          "credentialPurpose": "operator"
        }
        """
            .formatted(CREDENTIAL_SPKI),
        OperationId.ENROLL_KEY,
        "principalId");
    assertInvalid(
        """
        {
          "principalId": "%s",
          "credentialSpki": "%s",
          "credentialPurpose": "operator"
        }
        """
            .formatted(
                PRINCIPAL_ID, CREDENTIAL_SPKI.substring(0, CREDENTIAL_SPKI.length() - 1) + "J"),
        OperationId.ENROLL_KEY,
        "credentialSpki");
    assertInvalid(
        """
        {
          "principalId": "%s",
          "credentialSpki": "%s",
          "credentialPurpose": "operator"
        }
        """
            .formatted(PRINCIPAL_ID, "A".repeat(CREDENTIAL_SPKI.length())),
        OperationId.ENROLL_KEY,
        "credentialSpki");
  }

  @Test
  void rejectsNonArrayPolicyFieldsAndAcceptsExplicitNullAsAbsent() {
    assertInvalid(
        """
        { "policyRules": "not-an-array" }
        """,
        OperationId.ALTER_POLICY,
        "policyRules");
    assertInvalid(
        """
        {
          "policyRules": null,
          "capabilityGrants": null,
          "systemWorkflowPolicies": null
        }
        """,
        OperationId.ALTER_POLICY,
        "at least one change");
  }

  @Test
  void refusesAnOperationOutsideTheRegistryParserOwnership() throws Exception {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliAttestationRegistryMutationRequestParser.read(
                    new ObjectMapper().createObjectNode(), OperationId.OPEN_BOOK));
    assertEquals(
        "Attestation registry request parser does not own open-book.", exception.getMessage());
  }

  private static AttestationRegistryMutation read(String payload, OperationId operationId) {
    return new CliRequestReader(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)))
        .readAttestationRegistryMutation(Path.of("-"), operationId);
  }

  private static String encode(
      dev.erst.fingrind.core.attestation.AttestationPublicCredential credential) {
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(credential.spki());
  }

  private static void assertInvalid(
      String payload, OperationId operationId, String expectedMessage) {
    CliRequestException exception =
        assertThrows(CliRequestException.class, () -> read(payload, operationId));
    String message = java.util.Objects.requireNonNull(exception.getMessage(), "message");
    assertTrue(message.contains(expectedMessage), message);
  }
}
