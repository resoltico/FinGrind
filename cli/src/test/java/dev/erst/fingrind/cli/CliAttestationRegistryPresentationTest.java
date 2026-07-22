package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Covers presentation of the complete inspected attestation authority state. */
class CliAttestationRegistryPresentationTest {
  private static final UUID BOOK_ID = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
  private static final UUID PRINCIPAL_ID = UUID.fromString("10213243-5465-7687-98a9-babcbddceeff");
  private static final UUID WORKFLOW_ID = UUID.fromString("20314253-6475-7689-9a0b-bcddceeff001");
  private static final String OPERATION_HEAD = "a".repeat(64);

  @Test
  void verifyBook_presentsPrincipalCapabilitiesAndSystemWorkflowPoliciesInTextAndJson() {
    VerifyBookAttestationResult.Valid result =
        new VerifyBookAttestationResult.Valid(
            BOOK_ID, BigInteger.ONE, OPERATION_HEAD, List.of(), registry());

    ByteArrayOutputStream textOutput = new ByteArrayOutputStream();
    new CliAttestationReadResponseWriter(outputChannel(textOutput))
        .writeVerifyBook(result, OutputMode.TEXT);
    String text = textOutput.toString(StandardCharsets.UTF_8);
    assertTrue(text.contains("principalId=" + PRINCIPAL_ID + ", capability=post, eligible=true"));
    assertTrue(text.contains("workflowId=" + WORKFLOW_ID + ", kind=year-end-close, active=true"));

    ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();
    new CliAttestationReadResponseWriter(outputChannel(jsonOutput))
        .writeVerifyBook(result, OutputMode.JSON);
    String json = jsonOutput.toString(StandardCharsets.UTF_8);
    assertTrue(json.contains("\"workflowId\":\"" + WORKFLOW_ID + "\""));
    assertTrue(json.contains("\"resultHoldingAccountCode\":\"3900\""));
    assertTrue(json.contains("\"acceptedOrder\":\"1\""));
  }

  private static CliOutputChannel outputChannel(ByteArrayOutputStream output) {
    return new CliOutputChannel(new PrintStream(output, true, StandardCharsets.UTF_8));
  }

  private static AttestationRegistryInspection registry() {
    return new AttestationRegistryInspection(
        BOOK_ID,
        BigInteger.ONE,
        OPERATION_HEAD,
        List.of(
            new AttestationRegistryInspection.Credential(
                PRINCIPAL_ID,
                "a".repeat(64),
                "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A",
                "operator",
                "enrolled",
                BigInteger.ZERO,
                null,
                "active")),
        List.of(new AttestationRegistryInspection.CapabilityPolicy("post", 1, 1, 1, 0)),
        List.of(new AttestationRegistryInspection.PrincipalCapability(PRINCIPAL_ID, "post", true)),
        List.of(
            new AttestationRegistryInspection.SystemWorkflowPolicy(
                WORKFLOW_ID, "year-end-close", "3900", "3000", "3100", true, BigInteger.ONE)));
  }
}
