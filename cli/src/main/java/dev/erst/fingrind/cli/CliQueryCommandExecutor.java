package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Executes query-only CLI commands that read book state without producing PDF artifacts. */
final class CliQueryCommandExecutor {
  private final CliBookReadResponseWriter responseWriter;
  private final CliFailureResponseWriter failureWriter;
  private final CliBookReadWorkflow readWorkflow;

  CliQueryCommandExecutor(
      CliBookReadResponseWriter responseWriter,
      CliFailureResponseWriter failureWriter,
      CliBookReadWorkflow readWorkflow) {
    this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter");
    this.failureWriter = Objects.requireNonNull(failureWriter, "failureWriter");
    this.readWorkflow = Objects.requireNonNull(readWorkflow, "readWorkflow");
  }

  int runInspectBookCommand(BookAccess bookAccess, OutputMode outputMode) {
    return runPromptedQuery(
        bookAccess,
        outputMode,
        ignored -> readWorkflow.inspectBook(bookAccess),
        inspection ->
            responseWriter.writeBookInspection(bookAccess.bookFilePath(), inspection, outputMode),
        ignored -> 0);
  }

  int runVerifyBookAttestationCommand(
      BookAccess bookAccess,
      List<AttestationCompromiseReview> compromiseReviews,
      boolean requireCleanAttestation,
      OutputMode outputMode) {
    return runPromptedQuery(
        bookAccess,
        outputMode,
        ignored -> readWorkflow.verifyBookAttestation(bookAccess, compromiseReviews),
        result ->
            responseWriter.writeVerifyBookAttestation(result, requireCleanAttestation, outputMode),
        result ->
            switch (result) {
              case dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult.Valid valid ->
                  requireCleanAttestation && valid.reviewRequired() ? 2 : 0;
              case dev.erst.fingrind.contract.bookkeeping.VerifyBookAttestationResult.Invalid _ ->
                  2;
            });
  }

  int runAttestationReviewCommand(
      BookAccess bookAccess,
      List<AttestationCompromiseReview> compromiseReviews,
      OutputMode outputMode) {
    return runPromptedQuery(
        bookAccess,
        outputMode,
        ignored -> readWorkflow.reviewAttestation(bookAccess, compromiseReviews),
        result -> responseWriter.writeAttestationReview(result, outputMode),
        ignored -> 0);
  }

  int runExportAttestationReceiptCommand(
      BookAccess bookAccess, Path receiptFilePath, OutputMode outputMode) {
    return runPromptedQuery(
        bookAccess,
        outputMode,
        ignored -> readWorkflow.exportAttestationReceipt(bookAccess, receiptFilePath),
        result -> responseWriter.writeExportAttestationReceipt(result, outputMode),
        CliAttestationExitCodes::exitCodeFor);
  }

  int runVerifyAttestationReceiptCommand(
      BookAccess bookAccess, Path receiptFilePath, OutputMode outputMode) {
    return runPromptedQuery(
        bookAccess,
        outputMode,
        ignored -> readWorkflow.verifyAttestationReceipt(bookAccess, receiptFilePath),
        result -> responseWriter.writeVerifyAttestationReceipt(result, outputMode),
        result ->
            switch (result) {
              case dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult.Valid _ ->
                  0;
              case dev.erst.fingrind.contract.bookkeeping.VerifyAttestationReceiptResult.Invalid
                      _ ->
                  2;
            });
  }

  int runListAccountsCommand(
      BookAccess bookAccess, ListAccountsQuery query, boolean withContext, OutputMode outputMode) {
    return runPromptedQuery(
        bookAccess,
        outputMode,
        ignored -> readWorkflow.listAccounts(bookAccess, query),
        result -> responseWriter.writeListAccountsResult(result, withContext, outputMode),
        CliBookQueryExitCodes::exitCodeFor);
  }

  int runListTaxRegistrationsCommand(
      BookAccess bookAccess,
      ListTaxRegistrationsQuery query,
      boolean withContext,
      OutputMode outputMode) {
    return runPromptedQuery(
        bookAccess,
        outputMode,
        ignored -> readWorkflow.listTaxRegistrations(bookAccess, query),
        result -> responseWriter.writeListTaxRegistrationsResult(result, withContext, outputMode),
        CliBookQueryExitCodes::exitCodeFor);
  }

  int runGetPostingCommand(
      BookAccess bookAccess, PostingId postingId, boolean withContext, OutputMode outputMode) {
    return runPromptedQuery(
        bookAccess,
        outputMode,
        ignored -> readWorkflow.getPosting(bookAccess, postingId),
        result -> responseWriter.writeGetPostingResult(result, withContext, outputMode),
        CliBookQueryExitCodes::exitCodeFor);
  }

  int runListPostingsCommand(
      BookAccess bookAccess, ListPostingsQuery query, boolean withContext, OutputMode outputMode) {
    return runPromptedQuery(
        bookAccess,
        outputMode,
        ignored -> readWorkflow.listPostings(bookAccess, query),
        result -> responseWriter.writeListPostingsResult(result, withContext, outputMode),
        CliBookQueryExitCodes::exitCodeFor);
  }

  private <RESULT> int runPromptedQuery(
      BookAccess bookAccess,
      OutputMode outputMode,
      Function<BookAccess, ContractDecision<RESULT>> queryRunner,
      Consumer<RESULT> resultWriter,
      ToIntFunction<RESULT> exitCodeProvider) {
    Optional<Integer> promptFailure =
        CliExecutionPolicy.interactivePromptOutputFailure(outputMode, bookAccess.passphraseSource())
            .map(
                failure ->
                    CliCommandOutcomeWriter.writeDeterministicFailure(
                        failure, failureWriter, outputMode));
    if (promptFailure.isPresent()) {
      return promptFailure.orElseThrow();
    }
    return CliCommandOutcomeWriter.writeResolvedResult(
        queryRunner.apply(bookAccess), resultWriter, exitCodeProvider, failureWriter, outputMode);
  }
}
