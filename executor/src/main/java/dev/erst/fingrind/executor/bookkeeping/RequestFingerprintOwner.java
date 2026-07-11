package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.SourceChannel;
import java.util.Objects;

/** Single owner for semantic request fingerprint derivation over normalized posting models. */
final class RequestFingerprintOwner {
  private RequestFingerprintOwner() {}

  static RequestFingerprint fingerprint(PostingRequestModel postingRequest) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    StringBuilder canonical = new StringBuilder();
    appendFingerprintHeader(canonical, postingRequest);
    appendJournalEntry(canonical, postingRequest);
    appendPostingLineage(canonical, postingRequest.postingLineage());
    appendCallerAuthoredEntry(canonical, postingRequest);
    appendProvenance(canonical, postingRequest);
    appendEvidence(canonical, postingRequest);
    return new RequestFingerprint(
        RequestFingerprint.CURRENT_VERSION,
        InterimResultSweepDraftFactory.sha256Hex(canonical.toString()));
  }

  private static SourceChannel sourceChannel(PostingRequestModel postingRequest) {
    return switch (postingRequest) {
      case AcceptedPosting acceptedPosting -> acceptedPosting.sourceChannel();
      case PostingCommand command -> command.sourceChannel();
      case dev.erst.fingrind.executor.spi.PostingDraft draft -> draft.sourceChannel();
    };
  }

  private static void appendFingerprintHeader(
      StringBuilder canonical, PostingRequestModel postingRequest) {
    append(canonical, "version", Integer.toString(RequestFingerprint.CURRENT_VERSION));
    append(canonical, "postingKind", postingRequest.postingKind().wireValue());
    append(canonical, "postingOriginKind", postingRequest.postingOriginKind().wireValue());
    append(canonical, "sourceChannel", sourceChannel(postingRequest).wireValue());
  }

  private static void appendJournalEntry(
      StringBuilder canonical, PostingRequestModel postingRequest) {
    append(canonical, "effectiveDate", postingRequest.journalEntry().effectiveDate().toString());
    if (postingRequest.callerAuthoredEntry().isPresent()) {
      append(canonical, "journalShape", "caller-authored-entry");
      return;
    }
    append(canonical, "journalShape", "journal-entry");
    append(canonical, "lineCount", Integer.toString(postingRequest.journalEntry().lines().size()));
    for (int index = 0; index < postingRequest.journalEntry().lines().size(); index++) {
      var line = postingRequest.journalEntry().lines().get(index);
      append(canonical, "line[" + index + "].accountCode", line.accountCode().value());
      append(canonical, "line[" + index + "].side", line.side().wireValue());
      append(canonical, "line[" + index + "].currency", line.amount().currencyUnit().code());
      append(
          canonical, "line[" + index + "].minorUnits", Long.toString(line.amount().minorUnits()));
    }
  }

  private static void appendPostingLineage(
      StringBuilder canonical, PostingLineageModel postingLineage) {
    switch (postingLineage) {
      case PostingLineageModel.Direct _ -> append(canonical, "lineage", "direct");
      case PostingLineageModel.Reversal reversal -> {
        append(canonical, "lineage", "reversal");
        append(canonical, "lineage.priorPostingId", reversal.reference().priorPostingId().value());
        append(canonical, "lineage.reason", reversal.reason().value());
      }
    }
  }

  private static void appendCallerAuthoredEntry(
      StringBuilder canonical, PostingRequestModel postingRequest) {
    append(
        canonical,
        "callerAuthoredEntry.present",
        Boolean.toString(postingRequest.callerAuthoredEntry().isPresent()));
    postingRequest
        .callerAuthoredEntry()
        .ifPresent(entry -> RequestFingerprintCallerAuthoredEntryWriter.append(canonical, entry));
  }

  private static void appendProvenance(
      StringBuilder canonical, PostingRequestModel postingRequest) {
    append(canonical, "actorId", postingRequest.requestProvenance().actorId().value());
    append(canonical, "actorType", postingRequest.requestProvenance().actorType().wireValue());
    append(canonical, "commandId", postingRequest.requestProvenance().commandId().value());
    append(
        canonical, "idempotencyKey", postingRequest.requestProvenance().idempotencyKey().value());
    append(canonical, "causationId", postingRequest.requestProvenance().causationId().value());
    append(
        canonical,
        "correlationId",
        postingRequest.requestProvenance().correlationId().map(value -> value.value()).orElse(""));
  }

  private static void appendEvidence(StringBuilder canonical, PostingRequestModel postingRequest) {
    append(
        canonical,
        "sourceDocumentCount",
        Integer.toString(postingRequest.evidence().sourceDocuments().size()));
    for (int index = 0; index < postingRequest.evidence().sourceDocuments().size(); index++) {
      var sourceDocument = postingRequest.evidence().sourceDocuments().get(index);
      append(
          canonical, "sourceDocument[" + index + "].id", sourceDocument.sourceDocumentId().value());
      append(
          canonical,
          "sourceDocument[" + index + "].type",
          sourceDocument.sourceDocumentType().value());
      append(
          canonical,
          "sourceDocument[" + index + "].documentDate",
          sourceDocument.documentDate().toString());
    }
    append(
        canonical, "approvalCount", Integer.toString(postingRequest.evidence().approvals().size()));
    for (int index = 0; index < postingRequest.evidence().approvals().size(); index++) {
      var approval = postingRequest.evidence().approvals().get(index);
      append(canonical, "approval[" + index + "].approvalId", approval.approvalId().value());
      append(canonical, "approval[" + index + "].approvalType", approval.approvalType().value());
      append(canonical, "approval[" + index + "].approverId", approval.approverId().value());
      append(
          canonical, "approval[" + index + "].approverType", approval.approverType().wireValue());
      append(canonical, "approval[" + index + "].decision", approval.decision().wireValue());
      append(canonical, "approval[" + index + "].approvedAt", approval.approvedAt().toString());
    }
  }

  static void append(StringBuilder canonical, String key, String value) {
    canonical.append(key).append('=').append(Objects.requireNonNull(value, key)).append('\n');
  }
}
