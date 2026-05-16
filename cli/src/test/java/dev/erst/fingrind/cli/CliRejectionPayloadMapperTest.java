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
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.time.LocalDate;
import java.util.Optional;
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
            new AccountCode("3200"), AccountRole.ORDINARY, AccountRole.CONTRA),
        "existing account identity",
        CliRejectionJsonModels.AccountRoleConflictDetails.class);
    assertHint(
        new BookAdministrationRejection.AccountTaxonomyConflict(
            new AccountCode("3200"),
            new AccountTaxonomy(
                Optional.empty(),
                Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                Optional.empty()),
            new AccountTaxonomy(
                Optional.empty(),
                Optional.of(FinancialPositionLineClassification.RETAINED_EARNINGS),
                Optional.empty())),
        "existing taxonomy",
        CliRejectionJsonModels.AccountTaxonomyConflictDetails.class);
    assertHint(
        new BookAdministrationRejection.ClosingEquityAccountMissing(new AccountCode("3200")),
        "--closing-equity-account",
        CliRejectionJsonModels.ClosingEquityAccountDetails.class);
    assertHint(
        new BookAdministrationRejection.ClosingEquityAccountClassificationMismatch(
            new AccountCode("3200"),
            FinancialPositionLineClassification.RETAINED_EARNINGS,
            FinancialPositionLineClassification.OTHER_EQUITY),
        "closing classification RETAINED_EARNINGS",
        CliRejectionJsonModels.ClosingEquityAccountClassificationMismatchDetails.class);
    assertHint(
        new BookAdministrationRejection.ClosingEquityAccountInactive(new AccountCode("3200")),
        "Redeclare the closing equity account",
        CliRejectionJsonModels.ClosingEquityAccountDetails.class);
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
                new AccountCode("3200"), AccountRole.ORDINARY, AccountRole.CONTRA));
    var taxonomyConflictEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.AccountTaxonomyConflict(
                new AccountCode("3200"),
                new AccountTaxonomy(
                    Optional.empty(),
                    Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                    Optional.empty()),
                new AccountTaxonomy(
                    Optional.empty(),
                    Optional.of(FinancialPositionLineClassification.RETAINED_EARNINGS),
                    Optional.empty())));
    var retainedEarningsMissingEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.ClosingEquityAccountMissing(new AccountCode("3200")));
    var retainedEarningsClassificationMismatchEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.ClosingEquityAccountClassificationMismatch(
                new AccountCode("3200"),
                FinancialPositionLineClassification.RETAINED_EARNINGS,
                FinancialPositionLineClassification.OTHER_EQUITY));

    CliRejectionJsonModels.AccountTypeConflictDetails typeDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountTypeConflictDetails.class,
            typeConflictEnvelope.details());
    CliRejectionJsonModels.AccountRoleConflictDetails roleDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountRoleConflictDetails.class,
            roleConflictEnvelope.details());
    CliRejectionJsonModels.AccountTaxonomyConflictDetails taxonomyDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountTaxonomyConflictDetails.class,
            taxonomyConflictEnvelope.details());
    CliRejectionJsonModels.ClosingEquityAccountDetails retainedEarningsMissingDetails =
        assertInstanceOf(
            CliRejectionJsonModels.ClosingEquityAccountDetails.class,
            retainedEarningsMissingEnvelope.details());
    CliRejectionJsonModels.ClosingEquityAccountClassificationMismatchDetails
        retainedEarningsClassificationMismatchDetails =
            assertInstanceOf(
                CliRejectionJsonModels.ClosingEquityAccountClassificationMismatchDetails.class,
                retainedEarningsClassificationMismatchEnvelope.details());

    assertEquals("3200", typeDetails.accountCode());
    assertEquals("EQUITY", typeDetails.existingAccountType());
    assertEquals("LIABILITY", typeDetails.requestedAccountType());
    assertEquals("3200", roleDetails.accountCode());
    assertEquals("ORDINARY", roleDetails.existingAccountRole());
    assertEquals("CONTRA", roleDetails.requestedAccountRole());
    assertEquals("3200", taxonomyDetails.accountCode());
    assertEquals(
        "OTHER_EQUITY",
        taxonomyDetails.existingAccountTaxonomy().financialPositionLineClassification());
    assertEquals(null, taxonomyDetails.existingAccountTaxonomy().parentAccountCode());
    assertEquals(null, taxonomyDetails.existingAccountTaxonomy().profitAndLossLineClassification());
    assertEquals(
        "RETAINED_EARNINGS",
        taxonomyDetails.requestedAccountTaxonomy().financialPositionLineClassification());
    assertEquals(null, taxonomyDetails.requestedAccountTaxonomy().parentAccountCode());
    assertEquals(
        null, taxonomyDetails.requestedAccountTaxonomy().profitAndLossLineClassification());
    assertEquals("3200", retainedEarningsMissingDetails.accountCode());
    assertEquals("3200", retainedEarningsClassificationMismatchDetails.accountCode());
    assertEquals(
        "RETAINED_EARNINGS",
        retainedEarningsClassificationMismatchDetails
            .requiredFinancialPositionLineClassification());
    assertEquals(
        "OTHER_EQUITY",
        retainedEarningsClassificationMismatchDetails.actualFinancialPositionLineClassification());
  }

  @Test
  void administrationRejectedEnvelope_mapsParentAndProfitAndLossTaxonomyValuesWhenPresent() {
    var taxonomyConflictEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.AccountTaxonomyConflict(
                new AccountCode("4100"),
                new AccountTaxonomy(
                    Optional.of(new AccountCode("4000")),
                    Optional.empty(),
                    Optional.of(ProfitAndLossLineClassification.COST_OF_SALES)),
                new AccountTaxonomy(
                    Optional.of(new AccountCode("4050")),
                    Optional.empty(),
                    Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE))));

    CliRejectionJsonModels.AccountTaxonomyConflictDetails taxonomyDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountTaxonomyConflictDetails.class,
            taxonomyConflictEnvelope.details());

    assertEquals("4000", taxonomyDetails.existingAccountTaxonomy().parentAccountCode());
    assertEquals(
        "COST_OF_SALES",
        taxonomyDetails.existingAccountTaxonomy().profitAndLossLineClassification());
    assertEquals("4050", taxonomyDetails.requestedAccountTaxonomy().parentAccountCode());
    assertEquals(
        "OPERATING_EXPENSE",
        taxonomyDetails.requestedAccountTaxonomy().profitAndLossLineClassification());
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
