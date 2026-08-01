package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.TEST_AUTHORIZER;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.applicationService;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.conflictingPosting;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.declareDefaultAccounts;
import static dev.erst.fingrind.executor.PostingApplicationServiceTestSupport.existingPosting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome;
import dev.erst.fingrind.executor.spi.PlanPostingCommitResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct admission and result-projection coverage for the three aggregate-plan child services. */
class PlanChildApplicationServiceTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-04-07T10:15:30Z"), ZoneOffset.UTC);
  private static final AttestationPlanOperationAuthorizer PLAN_AUTHORIZER =
      new AttestationPlanOperationAuthorizer(ExecutorAccountingTestSupport.TEST_AUTHORIZER);

  @Test
  void planAccountDeclaration_rejectsUninitializedBooksAndInvalidChartHierarchy() {
    try (InMemoryBookSession uninitialized = new InMemoryBookSession();
        InMemoryBookSession initialized = initializedBook()) {
      PlanAccountDeclarationService uninitializedService =
          new PlanAccountDeclarationService(uninitialized, uninitialized, uninitialized, CLOCK);
      PlanAccountDeclarationOutcome.Rejected missingBook =
          assertInstanceOf(
              PlanAccountDeclarationOutcome.Rejected.class,
              uninitializedService.declareAccount(
                  accountDeclaration("1000", "Cash"), PLAN_AUTHORIZER));

      PlanAccountDeclarationService initializedService =
          new PlanAccountDeclarationService(initialized, initialized, initialized, CLOCK);
      AccountDeclaration invalidChild =
          new AccountDeclaration(
              new AccountCode("1010"),
              new AccountName("Missing Parent"),
              AccountType.ASSET,
              new AccountTaxonomy(
                  AccountNodeKind.POSTABLE,
                  Optional.of(new AccountCode("9999")),
                  Optional.empty(),
                  Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                  Optional.empty(),
                  Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)));
      PlanAccountDeclarationOutcome.Rejected invalid =
          assertInstanceOf(
              PlanAccountDeclarationOutcome.Rejected.class,
              initializedService.declareAccount(invalidChild, PLAN_AUTHORIZER));

      assertInstanceOf(
          BookkeepingAdministrationRejection.BookNotInitialized.class, missingBook.rejection());
      assertInstanceOf(
          BookkeepingAdministrationRejection.ParentAccountMissing.class, invalid.rejection());
    }
  }

  @Test
  void planTaxRegistration_rejectsUninitializedBooksAndDefinitionsBeforeDelegating() {
    try (InMemoryBookSession uninitialized = new InMemoryBookSession();
        InMemoryBookSession initialized = initializedBook()) {
      PlanTaxRegistrationService uninitializedService =
          new PlanTaxRegistrationService(uninitialized, uninitialized, uninitialized, CLOCK);
      PlanTaxRegistrationMutationOutcome.Rejected missingBook =
          assertInstanceOf(
              PlanTaxRegistrationMutationOutcome.Rejected.class,
              uninitializedService.declareTaxRegistration(validTaxRegistration(), PLAN_AUTHORIZER));

      PlanTaxRegistrationService initializedService =
          new PlanTaxRegistrationService(initialized, initialized, initialized, CLOCK);
      PlanTaxRegistrationMutationOutcome.Rejected invalid =
          assertInstanceOf(
              PlanTaxRegistrationMutationOutcome.Rejected.class,
              initializedService.declareTaxRegistration(validTaxRegistration(), PLAN_AUTHORIZER));

      assertInstanceOf(TaxDeclarationRejection.BookNotInitialized.class, missingBook.rejection());
      assertInstanceOf(TaxDeclarationRejection.DefinitionViolations.class, invalid.rejection());
    }
  }

  @Test
  void planPosting_preflightsAndProjectsEveryPlanStoreOutcome() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PlanPostingApplicationService deferredService =
          planPostingService(
              bookSession,
              new PlanPostingCommitResult.Deferred(
                  existingPosting("plan-deferred", "idem-deferred")));
      PlanPostingApplicationService replayedService =
          planPostingService(
              bookSession,
              new PlanPostingCommitResult.Replayed(
                  existingPosting("plan-replayed", "idem-replayed")));
      PlanPostingApplicationService rejectedService =
          planPostingService(
              bookSession,
              new PlanPostingCommitResult.Rejected(
                  new BookkeepingPostingRejection.BookNotInitialized()));

      assertInstanceOf(
          PostEntryResult.PreflightAccepted.class,
          deferredService.preflight(
              LedgerPlanServiceTestSupport.postEntryCommand("idem-preflight")));
      PlanPostEntryOutcome.Committed deferred =
          assertInstanceOf(
              PlanPostEntryOutcome.Committed.class,
              deferredService.commit(
                  LedgerPlanServiceTestSupport.postEntryCommand("idem-deferred"), PLAN_AUTHORIZER));
      PlanPostEntryOutcome.Committed replayed =
          assertInstanceOf(
              PlanPostEntryOutcome.Committed.class,
              replayedService.commit(
                  LedgerPlanServiceTestSupport.postEntryCommand("idem-replayed"), PLAN_AUTHORIZER));
      PlanPostEntryOutcome.Rejected rejected =
          assertInstanceOf(
              PlanPostEntryOutcome.Rejected.class,
              rejectedService.commit(
                  LedgerPlanServiceTestSupport.postEntryCommand("idem-rejected"), PLAN_AUTHORIZER));

      assertFalse(deferred.idempotentReplay());
      assertTrue(replayed.idempotentReplay());
      assertInstanceOf(
          dev.erst.fingrind.contract.bookkeeping.PostingRejection.BookNotInitialized.class,
          rejected.rejection());
    }
  }

  @Test
  void planPosting_rejectsBeforeConsultingItsPlanStoreWhenTheBookIsNotInitialized() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PlanPostingApplicationService service =
          planPostingService(
              bookSession,
              new PlanPostingCommitResult.Deferred(existingPosting("unexpected", "unexpected")));

      PlanPostEntryOutcome.Rejected rejected =
          assertInstanceOf(
              PlanPostEntryOutcome.Rejected.class,
              service.commit(
                  LedgerPlanServiceTestSupport.postEntryCommand("idem-uninitialized"),
                  PLAN_AUTHORIZER));

      assertInstanceOf(
          dev.erst.fingrind.contract.bookkeeping.PostingRejection.BookNotInitialized.class,
          rejected.rejection());
    }
  }

  @Test
  void planPosting_replaysAnExactExistingRequestWithoutOpeningAPlanChildMutation() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostEntryResult.Committed originallyCommitted =
          assertInstanceOf(
              PostEntryResult.Committed.class,
              applicationService(bookSession)
                  .commit(
                      LedgerPlanServiceTestSupport.postEntryCommand("idem-plan-replay"),
                      TEST_AUTHORIZER));
      PlanPostingApplicationService service =
          new PlanPostingApplicationService(
              bookSession,
              (postingDraft, postingIdGenerator, attestationAuthorizer) -> {
                throw new AssertionError("An exact replay must not create a plan child mutation.");
              },
              () -> new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
              CLOCK);

      PlanPostEntryOutcome.Committed replayed =
          assertInstanceOf(
              PlanPostEntryOutcome.Committed.class,
              service.commit(
                  LedgerPlanServiceTestSupport.postEntryCommand("idem-plan-replay"),
                  PLAN_AUTHORIZER));

      assertEquals(originallyCommitted.postingId(), replayed.postingFact().postingId());
      assertTrue(replayed.idempotentReplay());
    }
  }

  @Test
  void planPosting_rejectsAConflictingExistingRequestWithoutOpeningAPlanChildMutation() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      bookSession.commit(conflictingPosting("plan-conflicting", "idem-plan-conflicting"));
      PlanPostingApplicationService service =
          new PlanPostingApplicationService(
              bookSession,
              (postingDraft, postingIdGenerator, attestationAuthorizer) -> {
                throw new AssertionError(
                    "A conflicting request must not create a plan child mutation.");
              },
              () -> new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
              CLOCK);

      PlanPostEntryOutcome.Rejected rejected =
          assertInstanceOf(
              PlanPostEntryOutcome.Rejected.class,
              service.commit(
                  LedgerPlanServiceTestSupport.postEntryCommand("idem-plan-conflicting"),
                  PLAN_AUTHORIZER));

      assertInstanceOf(
          dev.erst.fingrind.contract.bookkeeping.PostingRejection.IdempotencyKeyConflict.class,
          rejected.rejection());
    }
  }

  private static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(CLOCK.instant(), bookIdentity(), List.of());
    return bookSession;
  }

  private static AccountDeclaration accountDeclaration(String accountCode, String accountName) {
    return new AccountDeclaration(
        new AccountCode(accountCode),
        new AccountName(accountName),
        AccountType.ASSET,
        accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT));
  }

  private static DeclareTaxRegistrationCommand validTaxRegistration() {
    return new DeclareTaxRegistrationCommand(
        new TaxRegistrationId("plan-vat-lv"),
        new TaxRegistrationName("Plan Latvia VAT"),
        new TaxJurisdiction("LV"),
        null,
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("plan-vat-standard"),
                new TaxCodeName("Plan VAT Standard"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)));
  }

  private static PlanPostingApplicationService planPostingService(
      InMemoryBookSession validationStore, PlanPostingCommitResult outcome) {
    return new PlanPostingApplicationService(
        validationStore,
        (postingDraft, postingIdGenerator, attestationAuthorizer) -> outcome,
        () -> new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
        CLOCK);
  }
}
