package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountSemantics;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StorageLocator;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared SQLite posting/book fixtures and native-handle doubles for split store tests. */
class SqlitePostingFactFixtureSupport extends SqliteStoreFixtureSupport {
  static BookIdentity bookIdentity() {
    return new BookIdentity(
        new EntityProfile(
            new BookEntityName("Acme Studio"),
            EntityForm.COMPANY,
            OwnerModel.MULTI_OWNER,
            List.of()),
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1);
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
    return new CommittedPosting(
        new PostingId(postingId),
        journalEntry(reversalReference),
        postingLineage(reversalReference, reason),
        PostingKind.STANDARD,
        evidence,
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  static CommittedPosting postingFact(
      String postingId,
      String idempotencyKey,
      LocalDate effectiveDate,
      Instant recordedAt,
      List<JournalLine> lines) {
    return new CommittedPosting(
        new PostingId(postingId),
        new JournalEntry(effectiveDate, lines),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        accountingEvidence(idempotencyKey),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            recordedAt,
            SourceChannel.CLI));
  }

  static AccountingEvidence accountingEvidence(String token) {
    return new AccountingEvidence(
        List.of(sourceDocument("document-" + token, "invoice")), List.of());
  }

  static AccountingEvidence accountingEvidenceWithApproval(String token) {
    return new AccountingEvidence(
        List.of(sourceDocument("document-" + token, "invoice")),
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
        LocalDate.parse("2026-04-07"),
        Instant.parse("2026-04-07T10:15:30Z"),
        new StorageLocator("vault://fixtures/" + sourceDocumentId),
        new ContentSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
  }

  private static ApprovalReference approval(String approvalId, String approvalType) {
    return new ApprovalReference(
        new ApprovalId(approvalId),
        new ApprovalType(approvalType),
        new ActorId("approver-" + approvalId),
        ActorType.HUMAN,
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

  static DeclaredAccount publishedAccount(RegisteredAccount account) {
    return BookkeepingPublishedLanguageTranslator.toPublished(account);
  }

  static AccountRole accountRole(AccountType accountType, NormalBalance normalBalance) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(normalBalance, "normalBalance");
    return AccountSemantics.normalBalance(accountType, AccountRole.ORDINARY) == normalBalance
        ? AccountRole.ORDINARY
        : AccountRole.CONTRA;
  }

  static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
              Optional.empty());
      case LIABILITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
              Optional.empty());
      case EQUITY ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.of(FinancialPositionLineClassification.OTHER_EQUITY),
              Optional.empty());
      case REVENUE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE));
      case EXPENSE ->
          new AccountTaxonomy(
              dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
              Optional.empty(),
              Optional.empty(),
              Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    };
  }

  static AccountTaxonomy financialPositionTaxonomy(
      FinancialPositionLineClassification lineClassification) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(lineClassification),
        Optional.empty());
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
        accountRole(accountType, normalBalance),
        accountTaxonomy(accountType),
        active,
        declaredAt);
  }

  static RegisteredAccount registeredAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      boolean active,
      Instant declaredAt) {
    return new RegisteredAccount(
        accountCode, accountName, accountType, accountRole, accountTaxonomy, active, declaredAt);
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
        accountRole(accountType, normalBalance),
        accountTaxonomy(accountType),
        declaredAt);
  }

  static AccountDeclarationOutcome declareAccount(
      SqlitePostingFactStore postingFactStore,
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    return postingFactStore.declareAccount(
        accountCode, accountName, accountType, accountRole, accountTaxonomy, declaredAt);
  }

  static void initializeBookWithDefaultAccounts(SqlitePostingFactStore postingFactStore) {
    postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity());
    declareDefaultAccounts(postingFactStore);
  }

  static void declareDefaultAccounts(SqlitePostingFactStore postingFactStore) {
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
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            posting_kind,
            effective_date,
            recorded_at,
            actor_id,
            actor_type,
            command_id,
            idempotency_key,
            causation_id,
            correlation_id,
            reason,
            source_channel,
            prior_posting_id
        ) values (
            '%s',
            'STANDARD',
            '2026-04-07',
            '2026-04-07T10:15:30Z',
            'actor-1',
            'AGENT',
            'command-%s',
            '%s',
            'cause-1',
            null,
            null,
            '%s',
            null
        )
        """
            .formatted(postingId, postingId, idempotencyKey, SourceChannel.CLI.wireValue()));
  }
}
