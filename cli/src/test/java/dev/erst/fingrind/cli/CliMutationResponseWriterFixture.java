package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FiscalYearCloseResult;
import dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.GeneratedBookKeyFile;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import java.io.PrintStream;
import java.nio.file.Path;

/** Focused test fixture for command mutations and their attested result projections. */
final class CliMutationResponseWriterFixture {
  private final CliMutationResponseWriter writer;

  CliMutationResponseWriterFixture(PrintStream outputStream) {
    writer = new CliMutationResponseWriter(CliTestOutputChannels.forOutput(outputStream));
  }

  void writePostEntryResult(PostEntryResult result) {
    writePostEntryResult(result, OutputMode.JSON);
  }

  void writePostEntryResult(PostEntryResult result, OutputMode outputMode) {
    writer.writePostEntryResult(result, outputMode);
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result) {
    writeOpenBookResult(bookFilePath, result, OutputMode.JSON);
  }

  void writeOpenBookResult(Path bookFilePath, OpenBookResult result, OutputMode outputMode) {
    writer.writeOpenBookResult(bookFilePath, result, outputMode);
  }

  void writeGenerateBookKeyFileResult(GeneratedBookKeyFile generatedKeyFile) {
    writeGenerateBookKeyFileResult(generatedKeyFile, OutputMode.JSON);
  }

  void writeGenerateBookKeyFileResult(
      GeneratedBookKeyFile generatedKeyFile, OutputMode outputMode) {
    writer.writeGenerateBookKeyFileResult(generatedKeyFile, outputMode);
  }

  void writeRekeyBookResult(RekeyBookResult result) {
    writeRekeyBookResult(result, OutputMode.JSON);
  }

  void writeRekeyBookResult(RekeyBookResult result, OutputMode outputMode) {
    writer.writeRekeyBookResult(result, outputMode);
  }

  void writeBackupBookResult(BackupBookResult result, OutputMode outputMode) {
    writer.writeBackupBookResult(result, outputMode);
  }

  void writeRestoreBookResult(RestoreBookResult result, OutputMode outputMode) {
    writer.writeRestoreBookResult(result, outputMode);
  }

  void writeDeclareAccountResult(DeclareAccountResult result) {
    writeDeclareAccountResult(result, OutputMode.JSON);
  }

  void writeDeclareAccountResult(DeclareAccountResult result, OutputMode outputMode) {
    writer.writeDeclareAccountResult(result, outputMode);
  }

  void writeDeclareTaxRegistrationResult(
      DeclareTaxRegistrationResult result, OutputMode outputMode) {
    writer.writeDeclareTaxRegistrationResult(result, outputMode);
  }

  void writeInterimResultSweepResult(InterimResultSweepResult result, OutputMode outputMode) {
    writer.writeInterimResultSweepResult(result, outputMode);
  }

  void writeFiscalYearCloseResult(FiscalYearCloseResult result, OutputMode outputMode) {
    writer.writeFiscalYearCloseResult(result, outputMode);
  }
}
