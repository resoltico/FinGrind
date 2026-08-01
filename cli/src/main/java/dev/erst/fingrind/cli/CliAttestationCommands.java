package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.core.attestation.AttestationCompromiseReview;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Non-mutating CLI command that verifies one complete persisted attestation chain. */
record VerifyBookAttestation(
    BookAccess bookAccess,
    List<AttestationCompromiseReview> compromiseReviews,
    boolean requireCleanAttestation,
    OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  VerifyBookAttestation {
    Objects.requireNonNull(bookAccess, "bookAccess");
    compromiseReviews = List.copyOf(Objects.requireNonNull(compromiseReviews, "compromiseReviews"));
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .query()
        .runVerifyBookAttestationCommand(
            bookAccess, compromiseReviews, requireCleanAttestation, outputMode);
  }
}

/** Non-mutating CLI command that reports compromise-review findings for one valid book. */
record AttestationReview(
    BookAccess bookAccess,
    List<AttestationCompromiseReview> compromiseReviews,
    OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  AttestationReview {
    Objects.requireNonNull(bookAccess, "bookAccess");
    compromiseReviews = List.copyOf(Objects.requireNonNull(compromiseReviews, "compromiseReviews"));
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .query()
        .runAttestationReviewCommand(bookAccess, compromiseReviews, outputMode);
  }
}

/** Non-mutating CLI command that exports one no-clobber quorum-signed receipt. */
record ExportAttestationReceipt(BookAccess bookAccess, Path receiptFilePath, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  ExportAttestationReceipt {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(receiptFilePath, "receiptFilePath");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .query()
        .runExportAttestationReceiptCommand(bookAccess, receiptFilePath, outputMode);
  }
}

/** Non-mutating CLI command that verifies one receipt against one selected book. */
record VerifyAttestationReceipt(BookAccess bookAccess, Path receiptFilePath, OutputMode outputMode)
    implements CliCommand.OutputModeCommand {
  VerifyAttestationReceipt {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(receiptFilePath, "receiptFilePath");
    Objects.requireNonNull(outputMode, "outputMode");
  }

  @Override
  public int execute(CliExecutionContext executionContext) {
    return Objects.requireNonNull(executionContext, "executionContext")
        .query()
        .runVerifyAttestationReceiptCommand(bookAccess, receiptFilePath, outputMode);
  }
}
