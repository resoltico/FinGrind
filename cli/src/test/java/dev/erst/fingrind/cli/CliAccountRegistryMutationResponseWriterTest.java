package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection;
import dev.erst.fingrind.contract.bookkeeping.AmendAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.RetireAccountResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRegistryDependency;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract coverage for Account Registry lifecycle response projection. */
class CliAccountRegistryMutationResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writeDeclareAccountResult_preservesReactivatedAndRenamedOutcomes() {
    DeclaredAccount account = account(true);

    ByteArrayOutputStream reactivated = new ByteArrayOutputStream();
    new CliAccountRegistryMutationResponseWriter(outputChannel(reactivated))
        .writeDeclareAccountResult(new DeclareAccountResult.Reactivated(account), OutputMode.TEXT);
    assertTrue(reactivated.toString(StandardCharsets.UTF_8).contains("Account Reactivated"));

    ByteArrayOutputStream renamed = new ByteArrayOutputStream();
    new CliAccountRegistryMutationResponseWriter(outputChannel(renamed))
        .writeDeclareAccountResult(new DeclareAccountResult.Renamed(account), OutputMode.JSON);
    assertJsonContains(renamed, "\"outcome\":\"renamed\"");

    assertEquals(
        0, CliAdministrativeExitCodes.exitCodeFor(new DeclareAccountResult.Reactivated(account)));
    assertEquals(
        0, CliAdministrativeExitCodes.exitCodeFor(new DeclareAccountResult.Renamed(account)));
  }

  @Test
  void writeLifecycleResults_preservesOutcomeAndRejectionContractsAcrossOutputModes() {
    DeclaredAccount activeAccount = account(true);
    DeclaredAccount retiredAccount = account(false);

    ByteArrayOutputStream amendedTextOutput = new ByteArrayOutputStream();
    new CliAccountRegistryMutationResponseWriter(outputChannel(amendedTextOutput))
        .writeAmendAccountResult(new AmendAccountResult.Amended(activeAccount), OutputMode.TEXT);
    assertTrue(amendedTextOutput.toString(StandardCharsets.UTF_8).contains("Account Amended"));

    ByteArrayOutputStream unchangedAmendJsonOutput = new ByteArrayOutputStream();
    new CliAccountRegistryMutationResponseWriter(outputChannel(unchangedAmendJsonOutput))
        .writeAmendAccountResult(new AmendAccountResult.Unchanged(activeAccount), OutputMode.JSON);
    assertJsonContains(unchangedAmendJsonOutput, "\"outcome\":\"unchanged\"");

    ByteArrayOutputStream retiredTextOutput = new ByteArrayOutputStream();
    new CliAccountRegistryMutationResponseWriter(outputChannel(retiredTextOutput))
        .writeRetireAccountResult(new RetireAccountResult.Retired(retiredAccount), OutputMode.TEXT);
    assertTrue(retiredTextOutput.toString(StandardCharsets.UTF_8).contains("Account Retired"));

    ByteArrayOutputStream unchangedRetirementJsonOutput = new ByteArrayOutputStream();
    new CliAccountRegistryMutationResponseWriter(outputChannel(unchangedRetirementJsonOutput))
        .writeRetireAccountResult(
            new RetireAccountResult.Unchanged(retiredAccount), OutputMode.JSON);
    assertJsonContains(unchangedRetirementJsonOutput, "\"outcome\":\"unchanged\"");
    assertJsonContains(unchangedRetirementJsonOutput, "\"active\":false");

    AccountRegistryLifecycleRejection.AccountHasDependents dependents =
        new AccountRegistryLifecycleRejection.AccountHasDependents(
            new AccountCode("1100"),
            List.of(
                AccountRegistryDependency.POSTINGS, AccountRegistryDependency.TAX_REGISTRATIONS));
    ByteArrayOutputStream rejectedAmendmentOutput = new ByteArrayOutputStream();
    new CliAccountRegistryMutationResponseWriter(outputChannel(rejectedAmendmentOutput))
        .writeAmendAccountResult(new AmendAccountResult.Rejected(dependents), OutputMode.TEXT);
    String rejectedAmendmentText = rejectedAmendmentOutput.toString(StandardCharsets.UTF_8);
    assertTrue(rejectedAmendmentText.contains("account-has-dependents"), rejectedAmendmentText);
    assertTrue(rejectedAmendmentText.contains("Durable dependencies"), rejectedAmendmentText);
    assertTrue(rejectedAmendmentText.contains("postings"), rejectedAmendmentText);
    assertTrue(rejectedAmendmentText.contains("tax-registrations"), rejectedAmendmentText);

    ByteArrayOutputStream rejectedRetirementOutput = new ByteArrayOutputStream();
    new CliAccountRegistryMutationResponseWriter(outputChannel(rejectedRetirementOutput))
        .writeRetireAccountResult(
            new RetireAccountResult.Rejected(
                new AccountRegistryLifecycleRejection.AccountBalanceNotZero(
                    new AccountCode("1100"))),
            OutputMode.JSON);
    assertJsonContains(rejectedRetirementOutput, "\"code\":\"account-balance-not-zero\"");
    assertJsonContains(rejectedRetirementOutput, "\"accountCode\":\"1100\"");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAccountRegistryMutationResponseWriter(outputChannel(new ByteArrayOutputStream()))
                .writeAmendAccountResult(
                    new AmendAccountResult.Amended(activeAccount), OutputMode.CSV));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CliAccountRegistryMutationResponseWriter(outputChannel(new ByteArrayOutputStream()))
                .writeRetireAccountResult(
                    new RetireAccountResult.Retired(retiredAccount), OutputMode.CSV));

    assertEquals(
        0, CliAdministrativeExitCodes.exitCodeFor(new AmendAccountResult.Amended(activeAccount)));
    assertEquals(
        0, CliAdministrativeExitCodes.exitCodeFor(new AmendAccountResult.Unchanged(activeAccount)));
    assertEquals(
        2, CliAdministrativeExitCodes.exitCodeFor(new AmendAccountResult.Rejected(dependents)));
    assertEquals(
        0, CliAdministrativeExitCodes.exitCodeFor(new RetireAccountResult.Retired(retiredAccount)));
    assertEquals(
        0,
        CliAdministrativeExitCodes.exitCodeFor(new RetireAccountResult.Unchanged(retiredAccount)));
    assertEquals(
        2,
        CliAdministrativeExitCodes.exitCodeFor(
            new RetireAccountResult.Rejected(
                new AccountRegistryLifecycleRejection.AccountNotFound(new AccountCode("1100")))));
  }

  private static DeclaredAccount account(boolean active) {
    return CliIoFixtureSupport.declaredAccount(
        "1100",
        "Operating Cash",
        AccountType.ASSET,
        NormalBalance.DEBIT,
        active,
        Instant.parse("2026-04-17T10:20:30Z"));
  }
}
