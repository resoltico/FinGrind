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
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Unit tests for deterministic CLI rejection payload mapping. */
class CliRejectionPayloadMapperTest {
  private static final Pattern HINT_FLAG_PATTERN = Pattern.compile("--[a-z0-9-]+");

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
            new AccountCode("3200"), AccountRole.ORDINARY, AccountRole.POLARITY_INVERTED),
        "existing account identity",
        CliRejectionJsonModels.AccountRoleConflictDetails.class);
    assertHint(
        new BookAdministrationRejection.AccountTaxonomyConflict(
            new AccountCode("3200"),
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.empty(),
                Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                Optional.empty()),
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.empty(),
                Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                Optional.empty())),
        "existing taxonomy",
        CliRejectionJsonModels.AccountTaxonomyConflictDetails.class);
    assertHint(
        new BookAdministrationRejection.ResultHoldingAccountCandidateMissing(
            FinancialPositionLineClassification.RESULT_HOLDING, List.of()),
        "Declare one active equity account",
        CliRejectionJsonModels.ResultHoldingAccountCandidateMissingDetails.class);
    assertHint(
        new BookAdministrationRejection.ResultHoldingAccountCandidateMissing(
            FinancialPositionLineClassification.RESULT_HOLDING, List.of(new AccountCode("3200"))),
        "Reactivate one of the matching equity accounts",
        CliRejectionJsonModels.ResultHoldingAccountCandidateMissingDetails.class);
    assertHint(
        new BookAdministrationRejection.ResultHoldingAccountCandidateAmbiguous(
            FinancialPositionLineClassification.OTHER_EQUITY,
            List.of(new AccountCode("3200"), new AccountCode("3210"))),
        "Leave exactly one active equity account",
        CliRejectionJsonModels.ResultHoldingAccountCandidateAmbiguousDetails.class);
    assertHint(
        new BookAdministrationRejection.PeriodResultTransferMustStartAt(
            LocalDate.parse("2026-04-01")),
        "--period-start",
        CliRejectionJsonModels.PeriodResultTransferStartDetails.class);
    assertHint(
        new BookAdministrationRejection.PeriodResultTransferFutureDate(
            LocalDate.parse("2026-04-30")),
        "--period-end",
        CliRejectionJsonModels.PeriodResultTransferFutureDateDetails.class);
    assertHint(
        new BookAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary(
            LocalDate.parse("2026-12-15"),
            LocalDate.parse("2027-01-15"),
            FiscalYearStart.parse("01-01")),
        "inside one fiscal year",
        CliRejectionJsonModels.PeriodResultTransferFiscalYearDetails.class);
    assertHint(
        new BookAdministrationRejection.ParentAccountMissing(
            new AccountCode("4100"), new AccountCode("4000")),
        "Declare the requested parent account first",
        CliRejectionJsonModels.ParentAccountDetails.class);
    assertHint(
        new BookAdministrationRejection.ParentAccountInactive(
            new AccountCode("4100"), new AccountCode("4000")),
        "Reactivate the requested parent account",
        CliRejectionJsonModels.ParentAccountDetails.class);
    assertHint(
        new BookAdministrationRejection.ParentAccountTypeConflict(
            new AccountCode("4100"),
            AccountType.EXPENSE,
            new AccountCode("4000"),
            AccountType.REVENUE),
        "same accountType as the child account",
        CliRejectionJsonModels.ParentAccountTypeConflictDetails.class);
    assertHint(
        new BookAdministrationRejection.ParentAccountRoleConflict(
            new AccountCode("4100"),
            AccountRole.ORDINARY,
            new AccountCode("4000"),
            AccountRole.POLARITY_INVERTED),
        "same accountRole as the child account",
        CliRejectionJsonModels.ParentAccountRoleConflictDetails.class);
    assertHint(
        new BookAdministrationRejection.ParentAccountNotHeader(
            new AccountCode("4100"), new AccountCode("4000"), AccountNodeKind.POSTABLE),
        "declared as HEADER",
        CliRejectionJsonModels.ParentAccountNodeKindDetails.class);
    assertHint(
        new BookAdministrationRejection.ParentAccountTaxonomyConflict(
            new AccountCode("4100"),
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.of(new AccountCode("4050")),
                Optional.empty(),
                Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE)),
            new AccountCode("4000"),
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.empty(),
                Optional.empty(),
                Optional.of(ProfitAndLossLineClassification.COST_OF_SALES))),
        "same statement-classification family",
        CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails.class);
    assertHint(
        new BookAdministrationRejection.AccountHierarchyCycle(
            new AccountCode("4100"), new AccountCode("4000")),
        "not one of its descendants",
        CliRejectionJsonModels.ParentAccountDetails.class);
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
                new AccountCode("3200"), AccountRole.ORDINARY, AccountRole.POLARITY_INVERTED));
    var taxonomyConflictEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.AccountTaxonomyConflict(
                new AccountCode("3200"),
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    Optional.empty(),
                    Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                    Optional.empty()),
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    Optional.empty(),
                    Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                    Optional.empty())));
    var resultHoldingMissingEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.ResultHoldingAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200"))));
    var resultHoldingAmbiguousEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.ResultHoldingAccountCandidateAmbiguous(
                FinancialPositionLineClassification.OTHER_EQUITY,
                List.of(new AccountCode("3200"), new AccountCode("3210"))));

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
    CliRejectionJsonModels.ResultHoldingAccountCandidateMissingDetails resultHoldingMissingDetails =
        assertInstanceOf(
            CliRejectionJsonModels.ResultHoldingAccountCandidateMissingDetails.class,
            resultHoldingMissingEnvelope.details());
    CliRejectionJsonModels.ResultHoldingAccountCandidateAmbiguousDetails
        resultHoldingAmbiguousDetails =
            assertInstanceOf(
                CliRejectionJsonModels.ResultHoldingAccountCandidateAmbiguousDetails.class,
                resultHoldingAmbiguousEnvelope.details());

    assertEquals("3200", typeDetails.accountCode());
    assertEquals("EQUITY", typeDetails.existingAccountType());
    assertEquals("LIABILITY", typeDetails.requestedAccountType());
    assertEquals("3200", roleDetails.accountCode());
    assertEquals("ORDINARY", roleDetails.existingAccountRole());
    assertEquals("POLARITY_INVERTED", roleDetails.requestedAccountRole());
    assertEquals("3200", taxonomyDetails.accountCode());
    assertEquals(
        "OTHER_EQUITY",
        taxonomyDetails.existingAccountTaxonomy().financialPositionLineClassification());
    assertEquals(null, taxonomyDetails.existingAccountTaxonomy().parentAccountCode());
    assertEquals(null, taxonomyDetails.existingAccountTaxonomy().profitAndLossLineClassification());
    assertEquals(
        "RESULT_HOLDING",
        taxonomyDetails.requestedAccountTaxonomy().financialPositionLineClassification());
    assertEquals(null, taxonomyDetails.requestedAccountTaxonomy().parentAccountCode());
    assertEquals(
        null, taxonomyDetails.requestedAccountTaxonomy().profitAndLossLineClassification());
    assertEquals(
        "RESULT_HOLDING",
        resultHoldingMissingDetails.requiredFinancialPositionLineClassification());
    assertEquals(List.of("3200"), resultHoldingMissingDetails.inactiveCandidateAccountCodes());
    assertEquals(
        "OTHER_EQUITY",
        resultHoldingAmbiguousDetails.requiredFinancialPositionLineClassification());
    assertEquals(List.of("3200", "3210"), resultHoldingAmbiguousDetails.candidateAccountCodes());
  }

  @Test
  void rejectionHints_referencePublishedFlagsForTheirTargetCommands() {
    assertHintFlagsExist(
        OperationId.TRANSFER_PERIOD_RESULT,
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
                new BookAdministrationRejection.PeriodResultTransferMustStartAt(
                    LocalDate.parse("2026-04-01")))
            .hint());
    assertHintFlagsExist(
        OperationId.TRANSFER_PERIOD_RESULT,
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
                new BookAdministrationRejection.PeriodResultTransferFutureDate(
                    LocalDate.parse("2026-04-30")))
            .hint());
    assertHintFlagsExist(
        OperationId.TRANSFER_PERIOD_RESULT,
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
                new BookAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary(
                    LocalDate.parse("2026-12-15"),
                    LocalDate.parse("2027-01-15"),
                    FiscalYearStart.parse("01-01")))
            .hint());
    assertHintFlagsExist(
        OperationId.POST_ENTRY,
        CliRejectionPayloadMapper.postingRejectedEnvelope(
                "idem-book", new PostingRejection.BookNotInitialized())
            .hint());
    assertHintFlagsExist(
        OperationId.LIST_ACCOUNTS,
        CliRejectionPayloadMapper.queryRejectedEnvelope(
                new BookQueryRejection.UnknownAccount(new AccountCode("9999")))
            .hint());
    assertHintFlagsExist(
        OperationId.GET_POSTING,
        CliRejectionPayloadMapper.queryRejectedEnvelope(
                new BookQueryRejection.PostingNotFound(new PostingId("posting-404")))
            .hint());
  }

  @Test
  void administrationRejectedEnvelope_mapsParentAndProfitAndLossTaxonomyValuesWhenPresent() {
    var taxonomyConflictEnvelope =
        CliRejectionPayloadMapper.administrationRejectedEnvelope(
            new BookAdministrationRejection.AccountTaxonomyConflict(
                new AccountCode("4100"),
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    Optional.of(new AccountCode("4000")),
                    Optional.empty(),
                    Optional.of(ProfitAndLossLineClassification.COST_OF_SALES)),
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
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
    var functionalCurrencyMismatch =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-2",
            new PostingRejection.BookFunctionalCurrencyMismatch(
                CurrencyUnit.of("EUR"), CurrencyUnit.of("USD")));
    var openingBalanceWindowClosed =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-window",
            new PostingRejection.OpenAccountingPositionWindowClosed(
                PostingKind.STANDARD, LocalDate.parse("2026-04-07")));
    var openingBalanceNominalAccount =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-3",
            new PostingRejection.OpenAccountingPositionTouchesNominalAccount(
                new AccountCode("4000"), AccountType.REVENUE));
    var reversalTargetNotFound =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-4", new PostingRejection.ReversalTargetNotFound(new PostingId("posting-1")));

    CliRejectionJsonModels.FunctionalCurrencyMismatchDetails currencyDetails =
        assertInstanceOf(
            CliRejectionJsonModels.FunctionalCurrencyMismatchDetails.class,
            functionalCurrencyMismatch.details());
    assertEquals("EUR", currencyDetails.functionalCurrency());
    assertEquals("USD", currencyDetails.attemptedCurrency());
    assertNotNull(openingBalanceWindowClosed.hint());
    assertTrue(openingBalanceWindowClosed.hint().contains("window closed with STANDARD"));

    CliRejectionJsonModels.OpenAccountingPositionWindowClosedDetails openingBalanceWindowDetails =
        assertInstanceOf(
            CliRejectionJsonModels.OpenAccountingPositionWindowClosedDetails.class,
            openingBalanceWindowClosed.details());
    assertEquals("STANDARD", openingBalanceWindowDetails.firstBlockingPostingKind());
    assertEquals("2026-04-07", openingBalanceWindowDetails.firstBlockingEffectiveDate());

    CliRejectionJsonModels.OpenAccountingPositionNominalAccountDetails openingBalanceDetails =
        assertInstanceOf(
            CliRejectionJsonModels.OpenAccountingPositionNominalAccountDetails.class,
            openingBalanceNominalAccount.details());
    assertEquals("4000", openingBalanceDetails.accountCode());
    assertEquals(AccountType.REVENUE.wireValue(), openingBalanceDetails.accountType());

    assertNotNull(reversalTargetNotFound.hint());
    assertTrue(reversalTargetNotFound.hint().contains("get-posting"));
    assertInstanceOf(
        CliRejectionJsonModels.PriorPostingDetails.class, reversalTargetNotFound.details());

    var bookNotInitialized =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-book", new PostingRejection.BookNotInitialized());
    var accountStateViolations =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-account",
            new PostingRejection.AccountStateViolations(
                List.of(
                    new PostingRejection.UnknownAccount(new AccountCode("1000")),
                    new PostingRejection.InactiveAccount(new AccountCode("2000")),
                    new PostingRejection.NonPostableAccount(
                        new AccountCode("3000"), AccountNodeKind.HEADER))));
    var duplicateIdempotencyKey =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-dup", new PostingRejection.DuplicateIdempotencyKey());
    var transferredPeriodResultViolation =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-closed",
            new PostingRejection.TransferredPeriodResultViolation(
                LocalDate.parse("2026-04-30"), LocalDate.parse("2026-05-01")));
    var resultHoldingAccountReserved =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-close",
            new PostingRejection.ResultHoldingAccountReserved(new AccountCode("3200")));
    var reversalAlreadyExists =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-exists", new PostingRejection.ReversalAlreadyExists(new PostingId("posting-2")));
    var reversalDoesNotNegateTarget =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-negate",
            new PostingRejection.ReversalDoesNotNegateTarget(new PostingId("posting-3")));

    assertTrue(Objects.requireNonNull(bookNotInitialized.hint()).contains("open-book"));
    assertNull(bookNotInitialized.details());
    CliRejectionJsonModels.AccountStateViolationsDetails accountStateDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountStateViolationsDetails.class,
            accountStateViolations.details());
    assertEquals(3, accountStateDetails.violations().size());
    assertEquals("unknown-account", accountStateDetails.violations().get(0).code());
    assertEquals("1000", accountStateDetails.violations().get(0).accountCode());
    assertEquals("inactive-account", accountStateDetails.violations().get(1).code());
    assertEquals("2000", accountStateDetails.violations().get(1).accountCode());
    assertEquals("non-postable-account", accountStateDetails.violations().get(2).code());
    assertEquals("3000", accountStateDetails.violations().get(2).accountCode());
    assertEquals("HEADER", accountStateDetails.violations().get(2).accountNodeKind());
    assertTrue(
        Objects.requireNonNull(duplicateIdempotencyKey.hint())
            .contains("fresh provenance.idempotencyKey"));
    CliRejectionJsonModels.TransferredPeriodResultViolationDetails transferredPeriodResultDetails =
        assertInstanceOf(
            CliRejectionJsonModels.TransferredPeriodResultViolationDetails.class,
            transferredPeriodResultViolation.details());
    assertEquals("2026-04-30", transferredPeriodResultDetails.transferredThroughEffectiveDate());
    assertEquals("2026-05-01", transferredPeriodResultDetails.attemptedEffectiveDate());
    CliRejectionJsonModels.ResultHoldingAccountDetails closingEquityDetails =
        assertInstanceOf(
            CliRejectionJsonModels.ResultHoldingAccountDetails.class,
            resultHoldingAccountReserved.details());
    assertEquals("3200", closingEquityDetails.accountCode());
    assertTrue(
        Objects.requireNonNull(resultHoldingAccountReserved.hint())
            .contains("transfer-period-result"));
    assertEquals(
        "posting-2",
        assertInstanceOf(
                CliRejectionJsonModels.PriorPostingDetails.class, reversalAlreadyExists.details())
            .priorPostingId());
    assertTrue(Objects.requireNonNull(reversalAlreadyExists.hint()).contains("existing reversal"));
    assertEquals(
        "posting-3",
        assertInstanceOf(
                CliRejectionJsonModels.PriorPostingDetails.class,
                reversalDoesNotNegateTarget.details())
            .priorPostingId());
    assertTrue(
        Objects.requireNonNull(reversalDoesNotNegateTarget.hint())
            .contains("full negating journal entry"));
  }

  @Test
  void postingRejectedEnvelope_mapsEntrySemanticsViolationsDetails() {
    var envelope =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-semantics",
            new PostingRejection.EntrySemanticsViolations(
                List.of(
                    new PostingRejection.EntrySemanticsViolation(
                        "account-type-mismatch",
                        "cashAccountCode",
                        "cash account must be declared as ASSET"),
                    new PostingRejection.EntrySemanticsViolation(
                        "source-document-type-not-accepted",
                        null,
                        "invoice does not prove cash receipt"))));

    assertTrue(
        Objects.requireNonNull(envelope.hint())
            .contains("Choose accounts and source-document types"));
    CliRejectionJsonModels.EntrySemanticsViolationsDetails details =
        assertInstanceOf(
            CliRejectionJsonModels.EntrySemanticsViolationsDetails.class, envelope.details());
    assertEquals(2, details.violations().size());
    assertEquals("account-type-mismatch", details.violations().get(0).code());
    assertEquals("cashAccountCode", details.violations().get(0).field());
    assertEquals("cash account must be declared as ASSET", details.violations().get(0).message());
    assertEquals("source-document-type-not-accepted", details.violations().get(1).code());
    assertNull(details.violations().get(1).field());
    assertEquals("invoice does not prove cash receipt", details.violations().get(1).message());
  }

  @Test
  void entrySemanticsCliJsonModels_validatePayloadsAndRejectEmptyOrBlankValues() {
    IllegalArgumentException emptyViolations =
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CliRejectionJsonModels.EntrySemanticsViolationsDetails(List.of()));
    IllegalArgumentException blankCode =
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliRejectionJsonModels.EntrySemanticsViolationPayload(" ", "field", "message"));
    IllegalArgumentException blankField =
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () ->
                new CliRejectionJsonModels.EntrySemanticsViolationPayload("code", " ", "message"));
    IllegalArgumentException blankMessage =
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new CliRejectionJsonModels.EntrySemanticsViolationPayload("code", null, " "));

    assertEquals("violations must not be empty.", emptyViolations.getMessage());
    assertEquals("code must not be blank.", blankCode.getMessage());
    assertEquals("field must not be blank.", blankField.getMessage());
    assertEquals("message must not be blank.", blankMessage.getMessage());
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

    var bookNotInitialized =
        CliRejectionPayloadMapper.queryRejectedEnvelope(
            new BookQueryRejection.BookNotInitialized());
    assertTrue(Objects.requireNonNull(bookNotInitialized.hint()).contains("open-book"));
    assertNull(bookNotInitialized.details());
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

  private static void assertHintFlagsExist(OperationId operationId, @Nullable String hint) {
    if (hint == null) {
      return;
    }
    Set<String> publishedOptions =
        Set.copyOf(
            MachineContract.help(
                    CliDiscoveryTestSupport.identity(),
                    CliDiscoveryTestSupport.environment(),
                    operationId)
                .commands()
                .getFirst()
                .options()
                .stream()
                .flatMap(
                    option ->
                        HINT_FLAG_PATTERN.matcher(option).results().map(match -> match.group()))
                .toList());
    List<String> hintedFlags =
        HINT_FLAG_PATTERN.matcher(hint).results().map(match -> match.group()).toList();
    assertTrue(
        publishedOptions.containsAll(hintedFlags),
        () ->
            "Hint for "
                + operationId.wireName()
                + " referenced unsupported flags "
                + hintedFlags.stream().filter(flag -> !publishedOptions.contains(flag)).toList()
                + " in: "
                + hint);
  }
}
