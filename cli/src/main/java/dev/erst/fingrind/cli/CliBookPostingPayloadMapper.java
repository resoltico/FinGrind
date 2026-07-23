package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliAttestationJsonModels;
import dev.erst.fingrind.cli.json.CliAttestationJsonModels.AttestationCommitPayload;
import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.SourceDocumentReference;
import java.util.List;

/** Maps posting-shaped and evidence-shaped payloads into CLI JSON models. */
final class CliBookPostingPayloadMapper {
  private CliBookPostingPayloadMapper() {}

  static CliBookQueryJsonModels.PostingPayload postingPayload(PostingFact postingFact) {
    return postingPayload(postingFact, null, null);
  }

  static CliBookQueryJsonModels.PostingPayload postingPayload(
      PostingFact postingFact,
      @org.jspecify.annotations.Nullable String reversedByPostingId,
      @org.jspecify.annotations.Nullable AttestationCommit attestationCommit) {
    return new CliBookQueryJsonModels.PostingPayload(
        postingFact.postingId().value(),
        postingFact.postingKind().wireValue(),
        postingFact.postingOriginKind().wireValue(),
        postingFact.reversalReference().isPresent() ? "reversal" : "direct",
        reversesPostingId(postingFact),
        reversedByPostingId,
        attestationCommitPayload(attestationCommit),
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.provenance().requestProvenance().commandId().value(),
        postingFact.provenance().requestProvenance().idempotencyKey().value(),
        postingFact.provenance().requestProvenance().causationId().value(),
        postingFact
            .provenance()
            .requestProvenance()
            .correlationId()
            .map(value -> value.value())
            .orElse(null),
        postingFact.provenance().sourceChannel().wireValue(),
        evidencePayload(postingFact.evidence()),
        CliPostingEntryPayloadSupport.entryPayload(postingFact.callerAuthoredEntry().orElse(null)),
        postingFact
            .postingLineage()
            .reversalReference()
            .map(
                reference ->
                    new CliBookQueryJsonModels.ReversalPayload(
                        reference.priorPostingId().value(),
                        postingFact.postingLineage().reversalReason().orElseThrow().value()))
            .orElse(null),
        postingFact.journalEntry().lines().stream()
            .map(CliBookPostingPayloadMapper::linePayload)
            .toList());
  }

  static CliBookQueryJsonModels.PostingSummaryPayload postingSummaryPayload(
      PostingFact postingFact) {
    return postingSummaryPayload(postingFact, null);
  }

  static CliBookQueryJsonModels.PostingSummaryPayload postingSummaryPayload(
      PostingFact postingFact, @org.jspecify.annotations.Nullable String reversedByPostingId) {
    return postingSummaryPayload(postingFact, reversedByPostingId, null);
  }

  static CliBookQueryJsonModels.PostingSummaryPayload postingSummaryPayload(
      PostingFact postingFact,
      @org.jspecify.annotations.Nullable String reversedByPostingId,
      @org.jspecify.annotations.Nullable AttestationCommit attestationCommit) {
    return new CliBookQueryJsonModels.PostingSummaryPayload(
        postingFact.postingId().value(),
        postingFact.postingKind().wireValue(),
        postingFact.postingOriginKind().wireValue(),
        postingFact.reversalReference().isPresent() ? "reversal" : "direct",
        reversesPostingId(postingFact),
        reversedByPostingId,
        attestationCommitPayload(attestationCommit),
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        MonetaryAmount.of(postingDebitTotal(postingFact)),
        MonetaryAmount.of(postingCreditTotal(postingFact)),
        postingFact.journalEntry().lines().stream()
            .map(line -> line.accountCode().value())
            .distinct()
            .toList(),
        postingFact.evidence().sourceDocuments().stream()
            .map(sourceDocument -> sourceDocument.sourceDocumentId().value())
            .toList(),
        postingFact.evidence().approvals().stream()
            .map(approval -> approval.approvalId().value())
            .toList());
  }

  private static @org.jspecify.annotations.Nullable AttestationCommitPayload
      attestationCommitPayload(
          @org.jspecify.annotations.Nullable AttestationCommit attestationCommit) {
    return attestationCommit == null
        ? null
        : new CliAttestationJsonModels.AttestationCommitPayload(
            attestationCommit.operationOrder().toString(), attestationCommit.operationHeadHex());
  }

  static CliBookQueryJsonModels.AccountingEvidencePayload evidencePayload(
      AccountingEvidence evidence) {
    return new CliBookQueryJsonModels.AccountingEvidencePayload(
        evidence.sourceDocuments().stream()
            .map(CliBookPostingPayloadMapper::sourceDocumentPayload)
            .toList(),
        evidence.approvals().stream().map(CliBookPostingPayloadMapper::approvalPayload).toList());
  }

  static List<String> counterpartAccounts(DeclaredAccount account, PostingFact postingFact) {
    return postingFact.journalEntry().lines().stream()
        .map(line -> line.accountCode().value())
        .filter(accountCode -> !accountCode.equals(account.accountCode().value()))
        .distinct()
        .toList();
  }

  private static CliBookQueryJsonModels.JournalLinePayload linePayload(
      dev.erst.fingrind.core.JournalLine line) {
    return new CliBookQueryJsonModels.JournalLinePayload(
        line.accountCode().value(),
        line.side().wireValue(),
        MonetaryAmount.of(line.amount().money()));
  }

  private static CliBookQueryJsonModels.SourceDocumentPayload sourceDocumentPayload(
      SourceDocumentReference sourceDocument) {
    return new CliBookQueryJsonModels.SourceDocumentPayload(
        sourceDocument.sourceDocumentId().value(),
        sourceDocument.sourceDocumentType().value(),
        sourceDocument.documentDate().toString());
  }

  private static CliBookQueryJsonModels.ApprovalPayload approvalPayload(
      ApprovalReference approval) {
    return new CliBookQueryJsonModels.ApprovalPayload(
        approval.approvalId().value(),
        approval.approvalType().value(),
        approval.approverReference(),
        approval.approverType(),
        approval.decision().wireValue(),
        approval.approvedAt().toString());
  }

  private static dev.erst.fingrind.core.Money postingDebitTotal(PostingFact postingFact) {
    long debitMinorUnits =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT)
            .mapToLong(line -> line.amount().minorUnits())
            .sum();
    return dev.erst.fingrind.core.Money.ofMinorUnits(
        postingFact.journalEntry().currencyUnit(), debitMinorUnits);
  }

  private static dev.erst.fingrind.core.Money postingCreditTotal(PostingFact postingFact) {
    long creditMinorUnits =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT)
            .mapToLong(line -> line.amount().minorUnits())
            .sum();
    return dev.erst.fingrind.core.Money.ofMinorUnits(
        postingFact.journalEntry().currencyUnit(), creditMinorUnits);
  }

  private static @org.jspecify.annotations.Nullable String reversesPostingId(
      PostingFact postingFact) {
    return postingFact
        .reversalReference()
        .map(reference -> reference.priorPostingId().value())
        .orElse(null);
  }
}
