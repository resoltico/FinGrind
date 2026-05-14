package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
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
        new BookAdministrationRejection.RetainedEarningsAccountMissing(new AccountCode("3200")),
        "--retained-earnings-account",
        CliRejectionJsonModels.RetainedEarningsAccountDetails.class);
    assertHint(
        new BookAdministrationRejection.RetainedEarningsAccountRoleMismatch(
            new AccountCode("3200"), AccountRole.ORDINARY),
        "accountRole is RETAINED_EARNINGS",
        CliRejectionJsonModels.RetainedEarningsAccountRoleMismatchDetails.class);
    assertHint(
        new BookAdministrationRejection.RetainedEarningsAccountInactive(new AccountCode("3200")),
        "Redeclare the retained-earnings account",
        CliRejectionJsonModels.RetainedEarningsAccountDetails.class);
    assertHint(
        new BookAdministrationRejection.PeriodCloseMustStartAt(LocalDate.parse("2026-04-01")),
        "--effective-date-from",
        CliRejectionJsonModels.PeriodCloseStartDetails.class);
    assertHint(
        new BookAdministrationRejection.PeriodCloseFutureDate(LocalDate.parse("2026-04-30")),
        "--effective-date-to",
        CliRejectionJsonModels.PeriodCloseFutureDateDetails.class);
    assertHint(
        new BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary(
            LocalDate.parse("2026-12-15"),
            LocalDate.parse("2027-01-15"),
            FiscalYearStart.parse("01-01")),
        "inside one fiscal year",
        CliRejectionJsonModels.PeriodCloseFiscalYearDetails.class);
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
    var retainedEarningsMissingEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.RetainedEarningsAccountMissing(
                new AccountCode("3200")));
    var retainedEarningsRoleMismatchEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.RetainedEarningsAccountRoleMismatch(
                new AccountCode("3200"), AccountRole.ORDINARY));

    CliRejectionJsonModels.AccountTypeConflictDetails typeDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountTypeConflictDetails.class,
            typeConflictEnvelope.details());
    CliRejectionJsonModels.AccountRoleConflictDetails roleDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountRoleConflictDetails.class,
            roleConflictEnvelope.details());
    CliRejectionJsonModels.RetainedEarningsAccountDetails retainedEarningsMissingDetails =
        assertInstanceOf(
            CliRejectionJsonModels.RetainedEarningsAccountDetails.class,
            retainedEarningsMissingEnvelope.details());
    CliRejectionJsonModels.RetainedEarningsAccountRoleMismatchDetails
        retainedEarningsRoleMismatchDetails =
            assertInstanceOf(
                CliRejectionJsonModels.RetainedEarningsAccountRoleMismatchDetails.class,
                retainedEarningsRoleMismatchEnvelope.details());

    assertEquals("3200", typeDetails.accountCode());
    assertEquals("EQUITY", typeDetails.existingAccountType());
    assertEquals("LIABILITY", typeDetails.requestedAccountType());
    assertEquals("3200", roleDetails.accountCode());
    assertEquals("ORDINARY", roleDetails.existingAccountRole());
    assertEquals("RETAINED_EARNINGS", roleDetails.requestedAccountRole());
    assertEquals("3200", retainedEarningsMissingDetails.accountCode());
    assertEquals("3200", retainedEarningsRoleMismatchDetails.accountCode());
    assertEquals("ORDINARY", retainedEarningsRoleMismatchDetails.actualAccountRole());
  }

  @Test
  void postingRejectedEnvelope_coversHintsAndStructuredDetailsForNewPostingDoctrineBranches() {
    var postingKindReserved =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-1", new PostingRejection.PostingKindReserved(PostingKind.PERIOD_CLOSE));
    var functionalCurrencyMismatch =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-2",
            new PostingRejection.BookFunctionalCurrencyMismatch(
                CurrencyUnit.of("EUR"), CurrencyUnit.of("USD")));
    var openingBalanceWindowClosed =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-window",
            new PostingRejection.OpeningBalanceWindowClosed(
                PostingKind.STANDARD, LocalDate.parse("2026-04-07")));
    var openingBalanceNominalAccount =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-3",
            new PostingRejection.OpeningBalanceTouchesNominalAccount(
                new AccountCode("4000"), AccountType.REVENUE));
    var reversalTargetNotFound =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-4", new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1")));

    assertNotNull(postingKindReserved.hint());
    assertTrue(postingKindReserved.hint().contains("STANDARD or OPENING_BALANCE"));
    assertInstanceOf(
        CliRejectionJsonModels.PostingKindDetails.class, postingKindReserved.details());

    CliRejectionJsonModels.FunctionalCurrencyMismatchDetails currencyDetails =
        assertInstanceOf(
            CliRejectionJsonModels.FunctionalCurrencyMismatchDetails.class,
            functionalCurrencyMismatch.details());
    assertEquals("EUR", currencyDetails.functionalCurrency());
    assertEquals("USD", currencyDetails.attemptedCurrency());
    assertNotNull(openingBalanceWindowClosed.hint());
    assertTrue(openingBalanceWindowClosed.hint().contains("window closed with STANDARD"));

    CliRejectionJsonModels.OpeningBalanceWindowClosedDetails openingBalanceWindowDetails =
        assertInstanceOf(
            CliRejectionJsonModels.OpeningBalanceWindowClosedDetails.class,
            openingBalanceWindowClosed.details());
    assertEquals("STANDARD", openingBalanceWindowDetails.firstBlockingPostingKind());
    assertEquals("2026-04-07", openingBalanceWindowDetails.firstBlockingEffectiveDate());

    CliRejectionJsonModels.OpeningBalanceNominalAccountDetails openingBalanceDetails =
        assertInstanceOf(
            CliRejectionJsonModels.OpeningBalanceNominalAccountDetails.class,
            openingBalanceNominalAccount.details());
    assertEquals("4000", openingBalanceDetails.accountCode());
    assertEquals(AccountType.REVENUE.wireValue(), openingBalanceDetails.accountType());

    assertNotNull(reversalTargetNotFound.hint());
    assertTrue(reversalTargetNotFound.hint().contains("get-posting"));
    assertInstanceOf(
        CliRejectionJsonModels.PriorPostingDetails.class, reversalTargetNotFound.details());
  }

  @Test
  void queryRejectedEnvelope_preservesUnknownAccountAndPostingNotFoundPayloads() {
    var unknownAccount =
        CliRejectionPayloadMapper.queryRejectedEnvelope(
            new BookQueryRejection.UnknownAccount(new AccountCode("9999")));
    var postingNotFound =
        CliRejectionPayloadMapper.queryRejectedEnvelope(
            new BookQueryRejection.PostingNotFound(new PostingId("posting-404")));

    assertNotNull(unknownAccount.hint());
    assertTrue(unknownAccount.hint().contains("list-accounts"));
    assertInstanceOf(CliRejectionJsonModels.UnknownAccountDetails.class, unknownAccount.details());
    assertNotNull(postingNotFound.hint());
    assertTrue(postingNotFound.hint().contains("list-postings"));
    assertInstanceOf(
        CliRejectionJsonModels.PostingNotFoundDetails.class, postingNotFound.details());
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
