package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Unit tests for deterministic CLI rejection payload mapping. */
class CliRejectionPayloadMapperTest {
  @Test
  void administrationRejectedEnvelope_coversEveryHintBranchAndDetailShape() {
    assertHint(new BookAdministrationRejection.BookAlreadyInitialized(), "inspect-book", null);
    assertHint(new BookAdministrationRejection.BookNotInitialized(), "open-book", null);
    assertHint(new BookAdministrationRejection.BookContainsSchema(), "empty target path", null);
    assertHint(
        new BookAdministrationRejection.AccountTypeConflict(
            new AccountCode("3200"), AccountType.EQUITY, AccountType.LIABILITY),
        "existing account identity",
        CliRejectionJsonModels.AccountTypeConflictDetails.class);
    assertHint(
        new BookAdministrationRejection.AccountRoleConflict(
            new AccountCode("3200"), AccountRole.ORDINARY, AccountRole.RETAINED_EARNINGS),
        "existing account identity",
        CliRejectionJsonModels.AccountRoleConflictDetails.class);
    assertHint(
        new BookAdministrationRejection.RetainedEarningsAccountMissing(),
        "Declare exactly one active retained-earnings account",
        null);
    assertHint(
        new BookAdministrationRejection.RetainedEarningsAccountInactive(new AccountCode("3200")),
        "Redeclare the retained-earnings account",
        CliRejectionJsonModels.RetainedEarningsAccountDetails.class);
    assertHint(
        new BookAdministrationRejection.PeriodCloseMustStartAt(LocalDate.parse("2026-04-01")),
        "--effective-date-from",
        CliRejectionJsonModels.PeriodCloseStartDetails.class);
  }

  @Test
  void administrationRejectedEnvelope_preservesConflictingAccountMetadata() {
    var typeConflictEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.AccountTypeConflict(
                new AccountCode("3200"), AccountType.EQUITY, AccountType.LIABILITY));
    var roleConflictEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.AccountRoleConflict(
                new AccountCode("3200"), AccountRole.ORDINARY, AccountRole.RETAINED_EARNINGS));

    CliRejectionJsonModels.AccountTypeConflictDetails typeDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountTypeConflictDetails.class,
            typeConflictEnvelope.details());
    CliRejectionJsonModels.AccountRoleConflictDetails roleDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountRoleConflictDetails.class,
            roleConflictEnvelope.details());

    assertEquals("3200", typeDetails.accountCode());
    assertEquals("EQUITY", typeDetails.existingAccountType());
    assertEquals("LIABILITY", typeDetails.requestedAccountType());
    assertEquals("3200", roleDetails.accountCode());
    assertEquals("ORDINARY", roleDetails.existingAccountRole());
    assertEquals("RETAINED_EARNINGS", roleDetails.requestedAccountRole());
  }

  private static void assertHint(
      BookAdministrationRejection rejection,
      String expectedHintFragment,
      @Nullable Class<? extends CliRejectionJsonModels.RejectionDetails> expectedDetailType) {
    var envelope = CliRejectionPayloadMapper.administrationRejectedEnvelope(rejection);
    assertNotNull(envelope.hint());
    assertTrue(envelope.hint().contains(expectedHintFragment));
    if (expectedDetailType == null) {
      assertNull(envelope.details());
      return;
    }
    assertInstanceOf(expectedDetailType, envelope.details());
  }
}
