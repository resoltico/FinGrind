package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/** Mutation portion of the split test-only response writer compatibility chain. */
class CliResponseWriterMutationSupport extends CliResponseWriterDiscoverySupport {
  CliResponseWriterMutationSupport(PrintStream outputStream) {
    super(outputStream);
  }

  CliResponseWriterMutationSupport(PrintStream outputStream, PrintStream diagnosticsStream) {
    super(outputStream, diagnosticsStream);
  }

  void writePostEntryResult(PostEntryResult result) {
    writePostEntryResult(result, OutputMode.JSON);
  }

  void writePostEntryResult(PostEntryResult result, OutputMode outputMode) {
    mutationWriter.writePostEntryResult(result, outputMode);
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result) {
    writeOpenBookResult(bookFilePath, result, OutputMode.JSON);
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result, OutputMode outputMode) {
    mutationWriter.writeOpenBookResult(bookFilePath, List.of(), result, outputMode);
  }

  void writeGenerateBookKeyFileResult(GeneratedBookKeyFile generatedKeyFile) {
    writeGenerateBookKeyFileResult(generatedKeyFile, OutputMode.JSON);
  }

  void writeGenerateBookKeyFileResult(
      GeneratedBookKeyFile generatedKeyFile, OutputMode outputMode) {
    mutationWriter.writeGenerateBookKeyFileResult(generatedKeyFile, List.of(), outputMode);
  }

  void writeRekeyBookResult(
      RekeyBookResult result, BookAccess.PassphraseSource replacementPassphraseSource) {
    writeRekeyBookResult(result, replacementPassphraseSource, OutputMode.JSON);
  }

  void writeRekeyBookResult(
      RekeyBookResult result,
      BookAccess.PassphraseSource replacementPassphraseSource,
      OutputMode outputMode) {
    mutationWriter.writeRekeyBookResult(result, replacementPassphraseSource, outputMode);
  }

  void writeBackupBookResult(BackupBookResult result, OutputMode outputMode) {
    mutationWriter.writeBackupBookResult(result, outputMode);
  }

  void writeRestoreBookResult(RestoreBookResult result, OutputMode outputMode) {
    mutationWriter.writeRestoreBookResult(result, outputMode);
  }

  void writeInspectRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    mutationWriter.writeInspectRekeyRollbackResult(result, outputMode);
  }

  void writeRestoreRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    mutationWriter.writeRestoreRekeyRollbackResult(result, outputMode);
  }

  void writeDeleteRekeyRollbackResult(RekeyRollbackResult result, OutputMode outputMode) {
    mutationWriter.writeDeleteRekeyRollbackResult(result, outputMode);
  }

  void writeDeclareAccountResult(DeclareAccountResult result) {
    writeDeclareAccountResult(result, OutputMode.JSON);
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    mutationWriter.writeDeclareAccountResult(result, outputMode);
  }

  void writeDeclareTaxRegistrationResult(
      DeclareTaxRegistrationResult result, OutputMode outputMode) {
    mutationWriter.writeDeclareTaxRegistrationResult(result, outputMode);
  }

  void writeInterimResultSweepResult(InterimResultSweepResult result, OutputMode outputMode) {
    mutationWriter.writeInterimResultSweepResult(result, outputMode);
  }

  void writeFiscalYearCloseResult(FiscalYearCloseResult result, OutputMode outputMode) {
    mutationWriter.writeFiscalYearCloseResult(result, outputMode);
  }
}
