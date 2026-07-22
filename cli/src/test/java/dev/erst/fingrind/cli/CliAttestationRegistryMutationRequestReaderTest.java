package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.attestation.AttestationCapability;
import dev.erst.fingrind.core.attestation.AttestationCredentialPurpose;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationSystemWorkflowKind;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Covers strict public-SPKI credential and policy request decoding. */
class CliAttestationRegistryMutationRequestReaderTest extends CliRequestReaderTestSupport {
  private static final String CREDENTIAL_SPKI =
      "MCowBQYDK2VwAyEAJYpWgBK4pHaKkIRKs9p8_6B01sG0SuOXLjI69Q5mGlI";

  @Test
  void readsEnrollmentFromCanonicalPublicSpkiWithoutReadingLocalCustodyPaths() throws Exception {
    var request =
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

    AttestationRegistryMutation.EnrollKey mutation =
        assertInstanceOf(
            AttestationRegistryMutation.EnrollKey.class,
            new CliRequestReader(new ByteArrayInputStream(new byte[0]))
                .readAttestationRegistryMutation(request, OperationId.ENROLL_KEY));

    assertEquals(AttestationCredentialPurpose.OPERATOR, mutation.purpose());
    assertEquals(
        CREDENTIAL_SPKI,
        Base64.getUrlEncoder().withoutPadding().encodeToString(mutation.credential().spki()));
  }

  @Test
  void readsPolicyThatChangesQuorumGrantAndWorkflowTogether() throws Exception {
    var request =
        writeNamedRequest(
            "alter-policy.json",
            """
            {
              "policyRules": [{ "capability": "close-period", "quorum": 1 }],
              "capabilityGrants": [{
                "principalId": "01234567-89ab-4cde-8fab-0123456789ab",
                "capability": "close-period",
                "state": "grant"
              }],
              "systemWorkflowPolicies": [{
                "workflowId": "11111111-2222-4333-8444-555555555555",
                "workflowKind": "interim-result-sweep",
                "resultHoldingAccountCode": "3000",
                "active": true
              }]
            }
            """);

    AttestationRegistryMutation.AlterPolicy mutation =
        assertInstanceOf(
            AttestationRegistryMutation.AlterPolicy.class,
            new CliRequestReader(new ByteArrayInputStream(new byte[0]))
                .readAttestationRegistryMutation(request, OperationId.ALTER_POLICY));

    assertEquals(
        AttestationCapability.CLOSE_PERIOD, mutation.policyRules().getFirst().capability());
    assertEquals(
        AttestationSystemWorkflowKind.INTERIM_RESULT_SWEEP,
        mutation.systemWorkflowPolicies().getFirst().workflowKind());
  }

  @Test
  void rejectsLegacyCredentialPath() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "principalId": "01234567-89ab-4cde-8fab-0123456789ab",
                  "credentialKeyFile": "candidate.fgatk",
                  "credentialPurpose": "operator"
                }
                """
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class,
            () ->
                requestReader.readAttestationRegistryMutation(
                    java.nio.file.Path.of("-"), OperationId.ENROLL_KEY));

    assertEquals("Unexpected field: credentialKeyFile", exception.getMessage());
  }

  @Test
  void rejectsNoncanonicalCredentialSpki() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "principalId": "01234567-89ab-4cde-8fab-0123456789ab",
                  "credentialSpki": "%s=",
                  "credentialPurpose": "operator"
                }
                """
                    .formatted(CREDENTIAL_SPKI)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class,
            () ->
                requestReader.readAttestationRegistryMutation(
                    java.nio.file.Path.of("-"), OperationId.ENROLL_KEY));

    assertEquals(
        "Field must be a canonical base64url Ed25519 DER SPKI: credentialSpki",
        exception.getMessage());
  }

  @Test
  void rejectsDuplicatePolicyFactsBeforeAnySigningAttempt() {
    CliRequestReader requestReader =
        new CliRequestReader(
            new ByteArrayInputStream(
                """
                {
                  "policyRules": [
                    { "capability": "close-period", "quorum": 1 },
                    { "capability": "close-period", "quorum": 2 }
                  ]
                }
                """
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

    CliRequestException exception =
        assertThrows(
            CliRequestException.class,
            () ->
                requestReader.readAttestationRegistryMutation(
                    java.nio.file.Path.of("-"), OperationId.ALTER_POLICY));

    assertEquals(
        "Attestation policy mutation must not repeat one capability rule.", exception.getMessage());
  }
}
