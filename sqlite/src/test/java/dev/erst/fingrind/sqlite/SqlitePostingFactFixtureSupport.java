package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared SQLite posting/book fixtures and native-handle doubles for split store tests. */
class SqlitePostingFactFixtureSupport extends SqliteStoreFixtureSupport {
  static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        java.time.LocalDate.parse("2026-01-01"));
  }

  static BookOpeningOutcome.Opened openedBook(Instant initializedAt) {
    return new BookOpeningOutcome.Opened(initializedAt, bookIdentity());
  }

  static BookLifecycleInspection.Initialized initializedLifecycleInspection(
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      Instant initializedAt) {
    return new BookLifecycleInspection.Initialized(
        applicationId,
        detectedBookFormatVersion,
        supportedBookFormatVersion,
        initializedAt,
        bookIdentity());
  }

  static PostingCoverage allPostingKinds() {
    return PostingCoverage.ALL_POSTING_KINDS;
  }

  static CommittedPosting postingFact(
      String postingId,
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    return postingFactWithEvidence(
        postingId, idempotencyKey, reversalReference, reason, accountingEvidence(idempotencyKey));
  }

  static CommittedPosting postingFactWithEvidence(
      String postingId,
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason,
      AccountingEvidence evidence) {
    JournalEntry journalEntry = journalEntry(reversalReference);
    PostingLineageModel postingLineage = postingLineage(reversalReference, reason);
    return new CommittedPosting(
        new PostingId(
            java.util
                .UUID
                .nameUUIDFromBytes(
                    ("fingrind-test-postingid:" + postingId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()),
        journalEntry,
        postingLineage,
        PostingKind.STANDARD,
        reversalReference
            .map(ignored -> dev.erst.fingrind.core.PostingOriginKind.REVERSAL)
            .orElse(dev.erst.fingrind.core.PostingOriginKind.SALE_SETTLED),
        evidence,
        new CommittedProvenance(
            new RequestProvenance(
                SqliteTestCommandIds.fromLabel("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI),
        originatingEntry(journalEntry, postingLineage),
        originatingEntry(journalEntry, postingLineage));
  }

  static CommittedPosting postingFact(
      String postingId,
      String idempotencyKey,
      LocalDate effectiveDate,
      Instant recordedAt,
      List<JournalLine> lines) {
    JournalEntry journalEntry = new JournalEntry(effectiveDate, lines);
    return new CommittedPosting(
        new PostingId(
            java.util
                .UUID
                .nameUUIDFromBytes(
                    ("fingrind-test-postingid:" + postingId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()),
        journalEntry,
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.DIRECT_JOURNAL,
        accountingEvidence(idempotencyKey),
        new CommittedProvenance(
            new RequestProvenance(
                SqliteTestCommandIds.fromLabel("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            recordedAt,
            SourceChannel.CLI),
        new BookkeepingEntry.DirectJournal(journalEntry, null),
        new BookkeepingEntry.DirectJournal(journalEntry, null));
  }

  private static BookkeepingEntry originatingEntry(
      JournalEntry journalEntry, PostingLineageModel postingLineage) {
    return switch (postingLineage) {
      case PostingLineageModel.Direct _ ->
          new BookkeepingEntry.SaleSettled(
              journalEntry.effectiveDate(),
              new AccountCode("1000"),
              new AccountCode("2000"),
              MonetaryAmount.of(money("EUR", "10.00")),
              null,
              null,
              null,
              null,
              null);
      case PostingLineageModel.Reversal reversal ->
          new BookkeepingEntry.Reversal(
              journalEntry.effectiveDate(),
              new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                  reversal.reference(), reversal.reason()),
              null,
              journalEntry);
    };
  }

  static AccountingEvidence accountingEvidence(String token) {
    return new AccountingEvidence(
        List.of(sourceDocument("document-" + token, "cash-receipt")), List.of());
  }

  static AccountingEvidence accountingEvidenceWithApproval(String token) {
    return new AccountingEvidence(
        List.of(sourceDocument("document-" + token, "cash-receipt")),
        List.of(approval("approval-" + token, "manager-signoff")));
  }

  static AccountingEvidence generatedEvidence(String token, String sourceDocumentType) {
    return new AccountingEvidence(
        List.of(sourceDocument("generated-" + token, sourceDocumentType)), List.of());
  }

  private static SourceDocumentReference sourceDocument(
      String sourceDocumentId, String sourceDocumentType) {
    return new SourceDocumentReference(
        new SourceDocumentId(sourceDocumentId),
        new SourceDocumentType(sourceDocumentType),
        LocalDate.parse("2026-04-07"));
  }

  private static ApprovalReference approval(String approvalId, String approvalType) {
    return new ApprovalReference(
        new ApprovalId(approvalId),
        new ApprovalType(approvalType),
        "approver-" + approvalId,
        "person",
        ApprovalDecision.APPROVED,
        Instant.parse("2026-04-07T10:20:30Z"));
  }

  static PostingLineageModel postingLineage(
      Optional<ReversalReference> reversalReference, Optional<ReversalReason> reason) {
    if (reversalReference.isEmpty()) {
      return PostingLineageModel.direct();
    }
    return PostingLineageModel.reversal(reversalReference.orElseThrow(), reason.orElseThrow());
  }

  static dev.erst.fingrind.contract.bookkeeping.PostingFact publishedPostingFact(
      CommittedPosting postingFact) {
    return BookkeepingPublishedLanguageTranslator.toPublished(postingFact);
  }

  static PostingDraft postingDraft(CommittedPosting postingFact) {
    return new PostingDraft(
        postingFact.journalEntry(),
        postingFact.postingLineage(),
        postingFact.postingKind(),
        postingFact.postingOriginKind(),
        postingFact.evidence(),
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
        postingFact.provenance(),
        postingFact.callerAuthoredEntry().orElse(null),
        postingFact.resolvedOriginatingEntry().orElse(null));
  }

  static StoredRequestPosting storedRequestPosting(CommittedPosting postingFact) {
    return new StoredRequestPosting(postingFact, semanticRequestFingerprint(postingFact));
  }

  private static RequestFingerprint semanticRequestFingerprint(CommittedPosting postingFact) {
    PostingAcceptancePolicy.Decision decision =
        PostingAcceptancePolicy.currentKernel()
            .decisionFor(
                postingDraft(postingFact), new FingerprintExpectationValidationBook(postingFact));
    if (decision instanceof PostingAcceptancePolicy.Decision.Accepted accepted) {
      return accepted.requestFingerprint();
    }
    if (decision instanceof PostingAcceptancePolicy.Decision.Rejected rejected) {
      throw new IllegalStateException(
          "SQLite test support could not derive an accepted request fingerprint: "
              + rejected.rejection());
    }
    throw new IllegalStateException(
        "SQLite test support unexpectedly treated a fresh posting expectation as replay.");
  }

  /**
   * Minimal initialized validation-book double for deriving semantic fingerprints in sqlite tests.
   */
  private static final class FingerprintExpectationValidationBook
      implements PostingValidationStore {
    private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");
    private final CommittedPosting postingFact;
    private final Map<AccountCode, RegisteredAccount> accounts;

    private FingerprintExpectationValidationBook(CommittedPosting postingFact) {
      this.postingFact = postingFact;
      this.accounts =
          Map.of(
              new AccountCode("1000"),
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  DECLARED_AT),
              new AccountCode("2000"),
              registeredAccount(
                  new AccountCode("2000"),
                  new AccountName("Revenue"),
                  AccountType.REVENUE,
                  NormalBalance.CREDIT,
                  true,
                  DECLARED_AT));
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return initializedLifecycleInspection(
          SqliteBookContract.APPLICATION_ID,
          SqliteBookContract.FORMAT_VERSION,
          SqliteBookContract.FORMAT_VERSION,
          DECLARED_AT);
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.ofNullable(accounts.get(accountCode));
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return accountCodes.stream()
          .filter(accounts::containsKey)
          .collect(java.util.stream.Collectors.toUnmodifiableMap(code -> code, accounts::get));
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return postingFact.postingLineage() instanceof PostingLineageModel.Reversal reversal
              && reversal.reference().priorPostingId().equals(postingId)
          ? Optional.of(
              postingFact(
                  postingId.value(),
                  "fingerprint-existing-" + postingId.value(),
                  Optional.empty(),
                  Optional.empty()))
          : Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return Optional.empty();
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return List.of();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }
  }

  static DeclaredAccount publishedAccount(RegisteredAccount account) {
    return BookkeepingPublishedLanguageTranslator.toPublished(account);
  }

  static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty(),
              Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
      case LIABILITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty(),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty(),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
              Optional.empty());
      case EXPENSE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE),
              Optional.empty());
    };
  }

  static AccountTaxonomy accountTaxonomy(AccountType accountType, NormalBalance normalBalance) {
    return SqlitePostingTaxonomyFixtures.accountTaxonomy(accountType, normalBalance);
  }

  static AccountTaxonomy financialPositionTaxonomy(
      FinancialPositionLineClassification lineClassification) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(lineClassification),
        Optional.empty(),
        lineClassification.accountType() == AccountType.ASSET
            ? Optional.of(CashFlowAssetClassification.NON_CASH)
            : Optional.empty());
  }

  static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return registeredAccount(
        accountCode,
        accountName,
        accountType,
        accountTaxonomy(accountType, normalBalance),
        active,
        declaredAt);
  }

  static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      boolean active,
      Instant declaredAt) {
    return new RegisteredAccount(
        accountCode,
        accountName,
        accountType,
        accountTaxonomy,
        defaultUnitOfMeasure(accountTaxonomy).orElse(null),
        active,
        declaredAt);
  }

  private static Optional<UnitOfMeasure> defaultUnitOfMeasure(AccountTaxonomy accountTaxonomy) {
    return accountTaxonomy
        .financialPositionLineClassification()
        .filter(classification -> classification == FinancialPositionLineClassification.INVENTORY)
        .map(ignored -> new UnitOfMeasure("unit", 0));
  }

  static DeclaredAccount declaredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      boolean active,
      Instant declaredAt) {
    return publishedAccount(
        registeredAccount(
            accountCode, accountName, accountType, normalBalance, active, declaredAt));
  }

  static AccountDeclarationOutcome declareAccount(
      SqlitePostingFactStore postingFactStore,
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      NormalBalance normalBalance,
      Instant declaredAt) {
    return declareAccount(
        postingFactStore,
        accountCode,
        accountName,
        accountType,
        accountTaxonomy(accountType, normalBalance),
        declaredAt);
  }

  static AccountDeclarationOutcome declareAccount(
      SqlitePostingFactStore postingFactStore,
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    return postingFactStore.declareAccount(
        new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
            accountCode, accountName, accountType, accountTaxonomy),
        declaredAt,
        SqliteAttestationTestSupport.authorizer());
  }

  static void openBookWithNoDeclaredAccounts(SqlitePostingFactStore postingFactStore) {
    Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
    postingFactStore.openAttestedBook(
        initializedAt,
        bookIdentity(),
        List.of(),
        SqliteAttestationTestSupport.genesis(bookIdentity(), initializedAt));
  }

  static void openBookWithStarterTemplateAccounts(SqlitePostingFactStore postingFactStore) {
    Instant initializedAt = Instant.parse("2026-04-07T10:15:30Z");
    postingFactStore.openAttestedBook(
        initializedAt,
        bookIdentity(),
        dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
            bookIdentity().bookDoctrine()),
        SqliteAttestationTestSupport.genesis(bookIdentity(), initializedAt));
  }

  static void initializeBookWithMinimalNumericAccounts(SqlitePostingFactStore postingFactStore) {
    openBookWithNoDeclaredAccounts(postingFactStore);
    declareMinimalNumericAccounts(postingFactStore);
  }

  static void declareMinimalNumericAccounts(SqlitePostingFactStore postingFactStore) {
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            registeredAccount(
                new AccountCode("1000"),
                new AccountName("Cash"),
                dev.erst.fingrind.core.AccountType.ASSET,
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        declareAccount(
            postingFactStore,
            new AccountCode("1000"),
            new AccountName("Cash"),
            dev.erst.fingrind.core.AccountType.ASSET,
            NormalBalance.DEBIT,
            Instant.parse("2026-04-07T10:15:30Z")));
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            registeredAccount(
                new AccountCode("2000"),
                new AccountName("Revenue"),
                dev.erst.fingrind.core.AccountType.REVENUE,
                NormalBalance.CREDIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        declareAccount(
            postingFactStore,
            new AccountCode("2000"),
            new AccountName("Revenue"),
            dev.erst.fingrind.core.AccountType.REVENUE,
            NormalBalance.CREDIT,
            Instant.parse("2026-04-07T10:15:30Z")));
  }

  static JournalEntry journalEntry(Optional<ReversalReference> reversalReference) {
    if (reversalReference.isPresent()) {
      return new JournalEntry(
          LocalDate.parse("2026-04-07"),
          List.of(
              line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
              line("2000", JournalLine.EntrySide.DEBIT, "10.00")));
    }
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, Money.parse("EUR", amount));
  }

  static JournalLine line(
      String accountCode, JournalLine.EntrySide side, String currencyCode, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, money(currencyCode, amount));
  }

  static Money money(String currencyCode, String amount) {
    return Money.parse(currencyCode, amount);
  }

  static void insertPostingFactRow(
      SqliteNativeDatabase database, String postingId, String idempotencyKey) {
    String postingOriginKind = PostingOriginKind.DIRECT_JOURNAL.wireValue();
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            posting_kind,
            posting_origin_kind,
            effective_date,
            recorded_at,
            command_id,
            idempotency_key,
            causation_id,
            correlation_id,
            reason,
            source_channel,
            prior_posting_id,
            request_fingerprint_version,
            request_fingerprint_sha256
        ) values (
            '%s',
            'STANDARD',
            '%s',
            '2026-04-07',
            '2026-04-07T10:15:30Z',
            '019e26ff-0000-7002-8000-000000000001',
            '%s',
            'cause-1',
            null,
            null,
            '%s',
            null,
            %d,
            '%s'
        )
        """
            .formatted(
                SqliteTestPostingIds.valueForLabel(postingId),
                postingOriginKind,
                idempotencyKey,
                SourceChannel.CLI.wireValue(),
                RequestFingerprint.CURRENT_VERSION,
                "0".repeat(64)));
  }

  static dev.erst.fingrind.executor.spi.PostingDraft postingDraft(
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      PostingKind postingKind,
      dev.erst.fingrind.core.PostingOriginKind postingOriginKind,
      AccountingEvidence evidence,
      CommittedProvenance provenance) {
    return new PostingDraft(
        journalEntry,
        postingLineage,
        postingKind,
        postingOriginKind,
        evidence,
        new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
        provenance);
  }
}
