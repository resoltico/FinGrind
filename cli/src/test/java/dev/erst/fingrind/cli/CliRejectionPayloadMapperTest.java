package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliEntrySemanticsViolationPayload;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AccountRegistryLifecycleRejection;
import dev.erst.fingrind.contract.bookkeeping.AttestationVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing;
import dev.erst.fingrind.contract.bookkeeping.ContraAccountInvalid;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseRequiresGeneratedPostings;
import dev.erst.fingrind.contract.bookkeeping.PostingEffectiveDateBeforeBookStart;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejectionSemantics;
import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountRegistryDependency;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ContraAccountRelationshipViolation;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.SourceDocumentType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for deterministic CLI rejection payload mapping. */
class CliRejectionPayloadMapperTest {
  private static final Pattern HINT_FLAG_PATTERN = Pattern.compile("--[a-z0-9-]+");

  @Test
  void attestationRegistryMutationRejectedEnvelope_keepsTargetDiagnosticsExact() {
    assertRegistryMutationDiagnostic(
        AttestationVerificationFailure.DUPLICATE_PRINCIPAL, "repeats a principal", "rollover-key");
    assertRegistryMutationDiagnostic(
        AttestationVerificationFailure.DUPLICATE_KEY,
        "already represented",
        "different credential");
    assertRegistryMutationDiagnostic(
        AttestationVerificationFailure.KEY_NOT_ENROLLED, "not enrolled", "enrolled credential");
    assertRegistryMutationDiagnostic(
        AttestationVerificationFailure.KEY_REVOKED,
        "already revoked",
        "active enrolled credential");
    assertRegistryMutationDiagnostic(
        AttestationVerificationFailure.KEY_PRINCIPAL_MISMATCH,
        "different principal",
        "principal ID bound");
    assertRegistryMutationDiagnostic(
        AttestationVerificationFailure.POLICY_CAPACITY_INVALID,
        "effective quorums impossible",
        "eligible principals");
    assertRegistryMutationDiagnostic(
        AttestationVerificationFailure.QUORUM_BELOW,
        "signing credentials do not authorize",
        "capability grant");
  }

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
        new BookAdministrationRejection.AccountTaxonomyConflict(
            new AccountCode("3200"),
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.empty(),
                Optional.empty(),
                Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                Optional.empty(),
                Optional.empty()),
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.empty(),
                Optional.empty(),
                Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                Optional.empty(),
                Optional.empty())),
        "existing taxonomy",
        CliRejectionJsonModels.AccountTaxonomyConflictDetails.class);
    assertHint(
        new ContraAccountInvalid(
            new AccountCode("4090"),
            new AccountCode("4000"),
            ContraAccountRelationshipViolation.STATEMENT_TAXONOMY_MISMATCH),
        "same accountType and statement taxonomy",
        CliRejectionJsonModels.ContraAccountDetails.class);
    assertHint(
        new AccountRegistryLifecycleRejection.AccountNotFound(new AccountCode("3200")),
        "declare-account",
        CliRejectionJsonModels.AccountCodeDetails.class);
    assertHint(
        new AccountRegistryLifecycleRejection.AccountHasDependents(
            new AccountCode("3200"), List.of(AccountRegistryDependency.POSTINGS)),
        "current definition and active state",
        CliRejectionJsonModels.AccountDependenciesDetails.class);
    assertHint(
        new AccountRegistryLifecycleRejection.AccountBalanceNotZero(new AccountCode("3200")),
        "current balance to zero",
        CliRejectionJsonModels.AccountCodeDetails.class);
    assertHint(
        new CloseTargetAccountCandidateMissing(
            FinancialPositionLineClassification.RESULT_HOLDING, List.of()),
        "Declare exactly one active equity account",
        CliRejectionJsonModels.CloseTargetAccountCandidateMissingDetails.class);
    assertHint(
        new CloseTargetAccountCandidateMissing(
            FinancialPositionLineClassification.RESULT_HOLDING, List.of(new AccountCode("3200"))),
        "Reactivate one of the matching equity accounts",
        CliRejectionJsonModels.CloseTargetAccountCandidateMissingDetails.class);
    assertHint(
        new CloseTargetAccountCandidateAmbiguous(
            FinancialPositionLineClassification.RETAINED_ACCUMULATED,
            List.of(new AccountCode("3200"), new AccountCode("3210"))),
        "Leave exactly one active equity account",
        CliRejectionJsonModels.CloseTargetAccountCandidateAmbiguousDetails.class);
    assertHint(
        new BookAdministrationRejection.InterimResultSweepMustStartAt(
            LocalDate.parse("2026-04-01")),
        "starts at '2026-04-01'",
        CliRejectionJsonModels.InterimResultSweepStartDetails.class);
    assertHint(
        new BookAdministrationRejection.InterimResultSweepFutureDate(LocalDate.parse("2026-04-30")),
        "--through",
        CliRejectionJsonModels.InterimResultSweepFutureDateDetails.class);
    assertHint(
        new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
            LocalDate.parse("2026-12-15"),
            LocalDate.parse("2027-01-15"),
            FiscalYearStart.parse("01-01")),
        "inside one fiscal year",
        CliRejectionJsonModels.InterimResultSweepFiscalYearDetails.class);
    assertHint(
        new BookAdministrationRejection.FiscalYearCloseMustStartAt(LocalDate.parse("2026-01-01")),
        "--year",
        CliRejectionJsonModels.FiscalYearCloseStartDetails.class);
    assertHint(
        new BookAdministrationRejection.FiscalYearCloseMustEndAt(LocalDate.parse("2026-12-31")),
        "--year",
        CliRejectionJsonModels.FiscalYearCloseEndDetails.class);
    assertHint(
        new BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
            LocalDate.parse("2025-12-31"), LocalDate.parse("2026-03-31")),
        "transferred-through horizon",
        CliRejectionJsonModels.FiscalYearCloseTransferredThroughDetails.class);
    assertHint(
        new BookAdministrationRejection.FiscalYearCloseFutureDate(LocalDate.parse("2027-01-01")),
        "--year",
        CliRejectionJsonModels.FiscalYearCloseFutureDateDetails.class);
    assertHint(
        new FiscalYearCloseRequiresGeneratedPostings(),
        "Post the accounting activity needed to produce a year-end close posting",
        null);
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
                Optional.empty(),
                Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE),
                Optional.empty()),
            new AccountCode("4000"),
            new AccountTaxonomy(
                dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(ProfitAndLossLineClassification.COST_OF_SALES),
                Optional.empty())),
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
        administrationRejectedEnvelope(
            new BookAdministrationRejection.AccountTypeConflict(
                new AccountCode("3200"), AccountType.EQUITY, AccountType.LIABILITY));
    var taxonomyConflictEnvelope =
        administrationRejectedEnvelope(
            new BookAdministrationRejection.AccountTaxonomyConflict(
                new AccountCode("3200"),
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
                    Optional.empty(),
                    Optional.empty()),
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                    Optional.empty(),
                    Optional.empty())));
    var resultHoldingMissingEnvelope =
        administrationRejectedEnvelope(
            new CloseTargetAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("3200"))));
    var resultHoldingAmbiguousEnvelope =
        administrationRejectedEnvelope(
            new CloseTargetAccountCandidateAmbiguous(
                FinancialPositionLineClassification.RETAINED_ACCUMULATED,
                List.of(new AccountCode("3200"), new AccountCode("3210"))));

    CliRejectionJsonModels.AccountTypeConflictDetails typeDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountTypeConflictDetails.class,
            typeConflictEnvelope.details());
    CliRejectionJsonModels.AccountTaxonomyConflictDetails taxonomyDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountTaxonomyConflictDetails.class,
            taxonomyConflictEnvelope.details());
    CliRejectionJsonModels.CloseTargetAccountCandidateMissingDetails resultHoldingMissingDetails =
        assertInstanceOf(
            CliRejectionJsonModels.CloseTargetAccountCandidateMissingDetails.class,
            resultHoldingMissingEnvelope.details());
    CliRejectionJsonModels.CloseTargetAccountCandidateAmbiguousDetails
        resultHoldingAmbiguousDetails =
            assertInstanceOf(
                CliRejectionJsonModels.CloseTargetAccountCandidateAmbiguousDetails.class,
                resultHoldingAmbiguousEnvelope.details());

    assertEquals("3200", typeDetails.accountCode());
    assertEquals("EQUITY", typeDetails.existingAccountType());
    assertEquals("LIABILITY", typeDetails.requestedAccountType());
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
        "RETAINED_ACCUMULATED",
        resultHoldingAmbiguousDetails.requiredFinancialPositionLineClassification());
    assertEquals(List.of("3200", "3210"), resultHoldingAmbiguousDetails.candidateAccountCodes());
  }

  @Test
  void rejectionHints_referencePublishedFlagsForTheirTargetCommands() {
    assertHintFlagsExist(
        OperationId.INTERIM_RESULT_SWEEP,
        administrationRejectedEnvelope(
                new BookAdministrationRejection.InterimResultSweepMustStartAt(
                    LocalDate.parse("2026-04-01")))
            .hint());
    assertHintFlagsExist(
        OperationId.INTERIM_RESULT_SWEEP,
        administrationRejectedEnvelope(
                new BookAdministrationRejection.InterimResultSweepFutureDate(
                    LocalDate.parse("2026-04-30")))
            .hint());
    assertHintFlagsExist(
        OperationId.INTERIM_RESULT_SWEEP,
        administrationRejectedEnvelope(
                new BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
                    LocalDate.parse("2026-12-15"),
                    LocalDate.parse("2027-01-15"),
                    FiscalYearStart.parse("01-01")))
            .hint());
    assertHintFlagsExist(
        OperationId.FISCAL_YEAR_CLOSE,
        administrationRejectedEnvelope(
                new BookAdministrationRejection.FiscalYearCloseMustStartAt(
                    LocalDate.parse("2026-01-01")))
            .hint());
    assertHintFlagsExist(
        OperationId.FISCAL_YEAR_CLOSE,
        administrationRejectedEnvelope(
                new BookAdministrationRejection.FiscalYearCloseMustEndAt(
                    LocalDate.parse("2026-12-31")))
            .hint());
    assertHintFlagsExist(
        OperationId.FISCAL_YEAR_CLOSE,
        administrationRejectedEnvelope(
                new BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
                    LocalDate.parse("2025-12-31"), LocalDate.parse("2026-03-31")))
            .hint());
    assertHintFlagsExist(
        OperationId.FISCAL_YEAR_CLOSE,
        administrationRejectedEnvelope(
                new BookAdministrationRejection.FiscalYearCloseFutureDate(
                    LocalDate.parse("2027-01-01")))
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
                new BookQueryRejection.PostingNotFound(
                    new PostingId("1681e9f3-01c4-33d7-8345-24c657998982")))
            .hint());
  }

  @Test
  void administrationRejectedEnvelope_mapsParentAndProfitAndLossTaxonomyValuesWhenPresent() {
    var taxonomyConflictEnvelope =
        administrationRejectedEnvelope(
            new BookAdministrationRejection.AccountTaxonomyConflict(
                new AccountCode("4100"),
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    Optional.of(new AccountCode("4000")),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(ProfitAndLossLineClassification.COST_OF_SALES),
                    Optional.empty()),
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    Optional.of(new AccountCode("4050")),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE),
                    Optional.empty())));

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
    var postingFutureDate =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-future",
            new PostingRejection.PostingEffectiveDateInFuture(
                LocalDate.parse("2026-07-01"), LocalDate.parse("2026-06-30")));
    var postingBeforeBookStart =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-before-start",
            new PostingEffectiveDateBeforeBookStart(
                LocalDate.parse("2026-06-29"), LocalDate.parse("2026-06-30")));
    var openingBalanceWindowClosed =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-window",
            new PostingRejection.OpeningPositionWindowClosed(
                PostingKind.STANDARD, LocalDate.parse("2026-04-07")));
    var openingBalanceNominalAccount =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-3",
            new PostingRejection.OpeningPositionTouchesNominalAccount(
                new AccountCode("4000"), AccountType.REVENUE));
    var reversalTargetNotFound =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-4",
            new PostingRejection.ReversalTargetNotFound(
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")));
    var reversalTargetIsReversal =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-reroll",
            new dev.erst.fingrind.contract.bookkeeping.ReversalTargetIsReversal(
                new PostingId("d335bf0a-b735-3860-ba2e-fcb74daf48d5")));

    CliRejectionJsonModels.FunctionalCurrencyMismatchDetails currencyDetails =
        assertInstanceOf(
            CliRejectionJsonModels.FunctionalCurrencyMismatchDetails.class,
            functionalCurrencyMismatch.details());
    assertEquals("EUR", currencyDetails.functionalCurrency());
    assertEquals("USD", currencyDetails.attemptedCurrency());
    CliRejectionJsonModels.PostingEffectiveDateInFutureDetails futureDateDetails =
        assertInstanceOf(
            CliRejectionJsonModels.PostingEffectiveDateInFutureDetails.class,
            postingFutureDate.details());
    assertEquals("2026-07-01", futureDateDetails.attemptedEffectiveDate());
    assertEquals("2026-06-30", futureDateDetails.currentUtcDate());
    CliRejectionJsonModels.PostingEffectiveDateBeforeBookStartDetails beforeBookStartDetails =
        assertInstanceOf(
            CliRejectionJsonModels.PostingEffectiveDateBeforeBookStartDetails.class,
            postingBeforeBookStart.details());
    assertEquals("2026-06-29", beforeBookStartDetails.attemptedEffectiveDate());
    assertEquals("2026-06-30", beforeBookStartDetails.bookStartEffectiveDate());
    assertTrue(Objects.requireNonNull(postingBeforeBookStart.hint()).contains("on or after"));
    assertNotNull(openingBalanceWindowClosed.hint());
    assertTrue(openingBalanceWindowClosed.hint().contains("window closed with STANDARD"));

    CliRejectionJsonModels.OpeningPositionWindowClosedDetails openingBalanceWindowDetails =
        assertInstanceOf(
            CliRejectionJsonModels.OpeningPositionWindowClosedDetails.class,
            openingBalanceWindowClosed.details());
    assertEquals("STANDARD", openingBalanceWindowDetails.firstBlockingPostingKind());
    assertEquals("2026-04-07", openingBalanceWindowDetails.firstBlockingEffectiveDate());

    CliRejectionJsonModels.OpeningPositionNominalAccountDetails openingBalanceDetails =
        assertInstanceOf(
            CliRejectionJsonModels.OpeningPositionNominalAccountDetails.class,
            openingBalanceNominalAccount.details());
    assertEquals("4000", openingBalanceDetails.accountCode());
    assertEquals(AccountType.REVENUE.wireValue(), openingBalanceDetails.accountType());

    assertNotNull(reversalTargetNotFound.hint());
    assertTrue(reversalTargetNotFound.hint().contains("get-posting"));
    assertInstanceOf(
        CliRejectionJsonModels.PriorPostingDetails.class, reversalTargetNotFound.details());
    assertTrue(
        Objects.requireNonNull(reversalTargetIsReversal.hint())
            .contains("fresh operational entry"));
    assertEquals(
        "d335bf0a-b735-3860-ba2e-fcb74daf48d5",
        assertInstanceOf(
                CliRejectionJsonModels.PriorPostingDetails.class,
                reversalTargetIsReversal.details())
            .priorPostingId());

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
            "idem-dup", new PostingRejection.IdempotencyKeyConflict());
    var transferredPeriodResultViolation =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-closed",
            new PostingRejection.SweptInterimResultViolation(
                LocalDate.parse("2026-04-30"), LocalDate.parse("2026-05-01")));
    var resultHoldingAccountReserved =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-close",
            new PostingRejection.ReservedResultClassification(
                new AccountCode("3200"), FinancialPositionLineClassification.RESULT_HOLDING));
    var reversalAlreadyExists =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-exists",
            new PostingRejection.ReversalAlreadyExists(
                new PostingId("41a95cd2-4a5f-3ef3-8a33-c2771905f362")));
    var reversalDoesNotNegateTarget =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-negate",
            new PostingRejection.ReversalDoesNotNegateTarget(
                new PostingId("6d857901-cb53-3986-a1d7-2f64319c76ce")));

    assertTrue(Objects.requireNonNull(bookNotInitialized.hint()).contains("open-book"));
    assertNull(bookNotInitialized.details());
    CliRejectionJsonModels.AccountStateViolationsDetails accountStateDetails =
        assertInstanceOf(
            CliRejectionJsonModels.AccountStateViolationsDetails.class,
            accountStateViolations.details());
    assertEquals(3, accountStateDetails.violations().size());
    assertEquals("unknown-account", accountStateDetails.violations().get(0).code());
    assertEquals("lines[].accountCode", accountStateDetails.violations().get(0).field());
    assertEquals("account-registry", accountStateDetails.violations().get(0).category());
    assertEquals("1000", accountStateDetails.violations().get(0).accountCode());
    assertEquals("inactive-account", accountStateDetails.violations().get(1).code());
    assertEquals("account-activation", accountStateDetails.violations().get(1).category());
    assertEquals("2000", accountStateDetails.violations().get(1).accountCode());
    assertEquals("non-postable-account", accountStateDetails.violations().get(2).code());
    assertEquals("account-node-kind", accountStateDetails.violations().get(2).category());
    assertEquals("3000", accountStateDetails.violations().get(2).accountCode());
    assertEquals("HEADER", accountStateDetails.violations().get(2).accountNodeKind());
    assertTrue(
        Objects.requireNonNull(duplicateIdempotencyKey.hint())
            .contains("fresh provenance.idempotencyKey"));
    CliRejectionJsonModels.SweptInterimResultViolationDetails transferredPeriodResultDetails =
        assertInstanceOf(
            CliRejectionJsonModels.SweptInterimResultViolationDetails.class,
            transferredPeriodResultViolation.details());
    assertEquals("2026-04-30", transferredPeriodResultDetails.transferredThroughEffectiveDate());
    assertEquals("2026-05-01", transferredPeriodResultDetails.attemptedEffectiveDate());
    CliRejectionJsonModels.ReservedResultClassificationDetails closingEquityDetails =
        assertInstanceOf(
            CliRejectionJsonModels.ReservedResultClassificationDetails.class,
            resultHoldingAccountReserved.details());
    assertEquals("3200", closingEquityDetails.accountCode());
    assertEquals("RESULT_HOLDING", closingEquityDetails.financialPositionLineClassification());
    assertTrue(
        Objects.requireNonNull(resultHoldingAccountReserved.hint())
            .contains("generated close operations"));
    assertEquals(
        "41a95cd2-4a5f-3ef3-8a33-c2771905f362",
        assertInstanceOf(
                CliRejectionJsonModels.PriorPostingDetails.class, reversalAlreadyExists.details())
            .priorPostingId());
    assertTrue(Objects.requireNonNull(reversalAlreadyExists.hint()).contains("existing reversal"));
    assertEquals(
        "6d857901-cb53-3986-a1d7-2f64319c76ce",
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
                    PostingRejectionSemantics.accountTypeMismatch(
                        "SALE_SETTLED",
                        "cashAccountCode",
                        new AccountCode("1000"),
                        AccountType.ASSET,
                        AccountType.REVENUE),
                    PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
                        "SALE_SETTLED",
                        new SourceDocumentType("invoice"),
                        List.of("cash-receipt", "bank-deposit", "card-settlement")))));

    assertEquals("Posting rejected with 2 entry-semantics issues.", envelope.message());
    assertNull(envelope.hint());
    CliRejectionJsonModels.EntrySemanticsViolationsDetails details =
        assertInstanceOf(
            CliRejectionJsonModels.EntrySemanticsViolationsDetails.class, envelope.details());
    assertEquals(2, details.violations().size());
    assertEquals(
        List.of("code", "field", "message", "category", "repair"),
        recordComponentNames(CliEntrySemanticsViolationPayload.class));
  }

  @Test
  void entrySemanticsCliJsonModels_validatePayloadsAndRejectEmptyOrBlankValues() {
    assertEquals(
        List.of("code", "field", "message", "category", "repair"),
        recordComponentNames(CliEntrySemanticsViolationPayload.class));

    IllegalArgumentException emptyViolations =
        assertThrows(
            IllegalArgumentException.class,
            () -> new CliRejectionJsonModels.EntrySemanticsViolationsDetails(List.of()));
    IllegalArgumentException blankCode =
        assertEntrySemanticsPayloadValidationFailure(
            " ", "field", "message", "classification", "repair");
    IllegalArgumentException blankField =
        assertEntrySemanticsPayloadValidationFailure(
            "code", " ", "message", "classification", "repair");
    IllegalArgumentException blankMessage =
        assertEntrySemanticsPayloadValidationFailure("code", null, " ", "classification", "repair");
    IllegalArgumentException blankCategory =
        assertEntrySemanticsPayloadValidationFailure("code", null, "message", " ", "repair");
    IllegalArgumentException blankRepair =
        assertEntrySemanticsPayloadValidationFailure(
            "code", null, "message", "classification", " ");

    assertEquals("violations must not be empty.", emptyViolations.getMessage());
    assertEquals("code must not be blank.", blankCode.getMessage());
    assertEquals("field must not be blank.", blankField.getMessage());
    assertEquals("message must not be blank.", blankMessage.getMessage());
    assertEquals("category must not be blank.", blankCategory.getMessage());
    assertEquals("repair must not be blank.", blankRepair.getMessage());
  }

  @Test
  void accountStateViolationsPayload_isPreparedForTheUniformRepairableViolationCore()
      throws Exception {
    var accountStateViolations =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-account",
            new PostingRejection.AccountStateViolations(
                List.of(
                    new PostingRejection.UnknownAccount(new AccountCode("1000")),
                    new PostingRejection.NonPostableAccount(
                        new AccountCode("3000"), AccountNodeKind.HEADER))));
    assertEquals("Posting rejected with 2 account-state issues.", accountStateViolations.message());
    assertNull(accountStateViolations.hint());
    JsonNode payload =
        new ObjectMapper()
            .readTree(
                CliWireJson.jsonText(Objects.requireNonNull(accountStateViolations.details())));
    JsonNode firstViolation = payload.path("violations").get(0);

    assertTrue(firstViolation.has("code"), payload.toPrettyString());
    assertTrue(firstViolation.has("field"), payload.toPrettyString());
    assertTrue(firstViolation.has("message"), payload.toPrettyString());
    assertTrue(firstViolation.has("category"), payload.toPrettyString());
    assertTrue(firstViolation.has("repair"), payload.toPrettyString());
    assertTrue(firstViolation.has("accountCode"), payload.toPrettyString());
  }

  @Test
  void singletonPostingRejectionPayloadsRemainSingleIssueEnvelopes() throws Exception {
    var duplicateIdempotencyKey =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-dup", new PostingRejection.IdempotencyKeyConflict());
    var functionalCurrencyMismatch =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-2",
            new PostingRejection.BookFunctionalCurrencyMismatch(
                CurrencyUnit.of("EUR"), CurrencyUnit.of("USD")));

    assertNull(duplicateIdempotencyKey.details());
    JsonNode functionalCurrencyDetails =
        new ObjectMapper()
            .readTree(
                CliWireJson.jsonText(Objects.requireNonNull(functionalCurrencyMismatch.details())));
    assertFalse(
        functionalCurrencyDetails.has("violations"), functionalCurrencyDetails.toPrettyString());
  }

  @Test
  void postingRejectedEnvelope_composesEntrySemanticsNarrativeInCanonicalOwnerOrder() {
    var envelope =
        CliRejectionPayloadMapper.postingRejectedEnvelope(
            "idem-owner-order",
            new PostingRejection.EntrySemanticsViolations(
                List.of(
                    PostingRejectionSemantics.sourceDocumentTypeNotAccepted(
                        "SALE_SETTLED",
                        new SourceDocumentType("invoice"),
                        List.of("cash-receipt", "bank-deposit", "card-settlement")),
                    PostingRejectionSemantics.accountTypeMismatch(
                        "SALE_SETTLED",
                        "revenueAccountCode",
                        new AccountCode("1000"),
                        AccountType.REVENUE,
                        AccountType.ASSET),
                    PostingRejectionSemantics.distinctRoleAccountsRequired(
                        "SALE_SETTLED",
                        "cashAccountCode",
                        "revenueAccountCode",
                        new AccountCode("1000")))));

    String message =
        java.util.Objects.requireNonNull(envelope.message(), "message").toLowerCase(Locale.ROOT);
    assertFalse(message.contains("published semantics"), message);
    assertEquals("posting rejected with 3 entry-semantics issues.", message);
    assertNull(envelope.hint());
  }

  @Test
  void queryRejectedEnvelope_preservesUnknownAccountAndPostingNotFoundPayloads() {
    var unknownAccount =
        CliRejectionPayloadMapper.queryRejectedEnvelope(
            new BookQueryRejection.UnknownAccount(new AccountCode("9999")));
    var postingNotFound =
        CliRejectionPayloadMapper.queryRejectedEnvelope(
            new BookQueryRejection.PostingNotFound(
                new PostingId("1681e9f3-01c4-33d7-8345-24c657998982")));

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

  @Test
  void administrationRejectedEnvelope_usesAccountRetryForCloseTargetRejectionsOnDeclareAccount() {
    var missing =
        CliAdministrationRejectionPayloadMapper.rejectedEnvelope(
            OperationId.DECLARE_ACCOUNT,
            new CloseTargetAccountCandidateMissing(
                FinancialPositionLineClassification.RESULT_HOLDING, List.of()));
    var ambiguous =
        CliAdministrationRejectionPayloadMapper.rejectedEnvelope(
            OperationId.DECLARE_ACCOUNT,
            new CloseTargetAccountCandidateAmbiguous(
                FinancialPositionLineClassification.RESULT_HOLDING,
                List.of(new AccountCode("result-holding"), new AccountCode("3200"))));

    assertEquals("close-target-account-candidate-missing", missing.code());
    assertTrue(Objects.requireNonNull(missing.hint()).contains("retry the account declaration"));
    assertEquals("close-target-account-candidate-ambiguous", ambiguous.code());
    assertTrue(Objects.requireNonNull(ambiguous.hint()).contains("retry the account declaration"));
  }

  @Test
  void
      administrationRejectedEnvelope_usesTheOriginatingAccountOperationForOtherCloseTargetRejections() {
    var envelope =
        CliAdministrationRejectionPayloadMapper.rejectedEnvelope(
            OperationId.AMEND_ACCOUNT,
            new CloseTargetAccountCandidateMissing(
                FinancialPositionLineClassification.RETAINED_ACCUMULATED, List.of()));

    assertEquals("close-target-account-candidate-missing", envelope.code());
    assertTrue(Objects.requireNonNull(envelope.hint()).contains("retry amend-account"));
  }

  private static void assertRegistryMutationDiagnostic(
      AttestationVerificationFailure failure, String expectedMessage, String expectedHint) {
    var envelope = CliRejectionPayloadMapper.attestationRegistryMutationRejectedEnvelope(failure);

    assertEquals(failure.wireCode(), envelope.code());
    assertTrue(Objects.requireNonNull(envelope.message()).contains(expectedMessage));
    assertTrue(Objects.requireNonNull(envelope.hint()).contains(expectedHint));
  }

  private static void assertHint(
      BookAdministrationRejection rejection,
      String expectedHintFragment,
      @Nullable Class<? extends CliRejectionJsonModels.RejectionDetails> expectedDetailType) {
    var envelope = administrationRejectedEnvelope(rejection);
    assertNotNull(envelope.hint());
    assertTrue(envelope.hint().contains(expectedHintFragment));
    if (expectedDetailType == null) {
      assertNull(envelope.details());
      return;
    }
    assertInstanceOf(expectedDetailType, envelope.details());
  }

  private static dev.erst.fingrind.cli.json.CliEnvelopeJsonModels.Envelope<?>
      administrationRejectedEnvelope(BookAdministrationRejection rejection) {
    OperationId operationId;
    if (rejection instanceof CloseTargetAccountCandidateMissing missing) {
      operationId =
          missing.requiredFinancialPositionLineClassification()
                  == FinancialPositionLineClassification.RESULT_HOLDING
              ? OperationId.INTERIM_RESULT_SWEEP
              : OperationId.FISCAL_YEAR_CLOSE;
    } else if (rejection instanceof CloseTargetAccountCandidateAmbiguous ambiguous) {
      operationId =
          ambiguous.requiredFinancialPositionLineClassification()
                  == FinancialPositionLineClassification.RESULT_HOLDING
              ? OperationId.INTERIM_RESULT_SWEEP
              : OperationId.FISCAL_YEAR_CLOSE;
    } else {
      operationId =
          switch (rejection) {
            case BookAdministrationRejection.InterimResultSweepMustStartAt _,
                BookAdministrationRejection.InterimResultSweepFutureDate _,
                BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary _ ->
                OperationId.INTERIM_RESULT_SWEEP;
            case BookAdministrationRejection.FiscalYearCloseMustStartAt _,
                BookAdministrationRejection.FiscalYearCloseMustEndAt _,
                BookAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon _,
                BookAdministrationRejection.FiscalYearCloseFutureDate _ ->
                OperationId.FISCAL_YEAR_CLOSE;
            default -> OperationId.DECLARE_ACCOUNT;
          };
    }
    return CliRejectionPayloadMapper.administrationRejectedEnvelope(operationId, rejection);
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

  private static List<String> recordComponentNames(Class<?> recordType) {
    return List.of(recordType.getRecordComponents()).stream()
        .map(RecordComponent::getName)
        .toList();
  }

  private static IllegalArgumentException assertEntrySemanticsPayloadValidationFailure(
      String code, @Nullable String field, String message, String category, String repair) {
    Constructor<CliEntrySemanticsViolationPayload> constructor =
        assertDoesNotThrow(
            () ->
                CliEntrySemanticsViolationPayload.class.getDeclaredConstructor(
                    String.class, String.class, String.class, String.class, String.class));
    InvocationTargetException invocationTargetException =
        assertThrows(
            InvocationTargetException.class,
            () -> constructor.newInstance(code, field, message, category, repair));
    return assertInstanceOf(IllegalArgumentException.class, invocationTargetException.getCause());
  }
}
