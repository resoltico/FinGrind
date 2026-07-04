package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import java.io.PrintStream;
import java.nio.file.Path;

/** Read-side portion of the split test-only response writer compatibility chain. */
class CliResponseWriterBookReadSupport extends CliResponseWriterMutationSupport {
  CliResponseWriterBookReadSupport(PrintStream outputStream) {
    super(outputStream);
  }

  CliResponseWriterBookReadSupport(PrintStream outputStream, PrintStream diagnosticsStream) {
    super(outputStream, diagnosticsStream);
  }

  void writeBookInspection(Path bookFilePath, BookInspection inspection) {
    writeBookInspection(bookFilePath, inspection, OutputMode.JSON);
  }

  void writeBookInspection(Path bookFilePath, BookInspection inspection, OutputMode outputMode) {
    bookReadWriter.writeBookInspection(bookFilePath, inspection, outputMode);
  }

  void writeListAccountsResult(ListAccountsResult result) {
    writeListAccountsResult(result, OutputMode.JSON);
  }

  void writeListAccountsResult(ListAccountsResult result, OutputMode outputMode) {
    writeListAccountsResult(result, false, outputMode);
  }

  void writeListAccountsResult(
      ListAccountsResult result, boolean withContext, OutputMode outputMode) {
    bookReadWriter.writeListAccountsResult(result, withContext, outputMode);
  }

  void writeGetPostingResult(GetPostingResult result) {
    writeGetPostingResult(result, OutputMode.JSON);
  }

  void writeGetPostingResult(GetPostingResult result, OutputMode outputMode) {
    writeGetPostingResult(result, false, outputMode);
  }

  void writeGetPostingResult(GetPostingResult result, boolean withContext, OutputMode outputMode) {
    bookReadWriter.writeGetPostingResult(result, withContext, outputMode);
  }

  void writeListPostingsResult(ListPostingsResult result) {
    writeListPostingsResult(result, OutputMode.JSON);
  }

  void writeListPostingsResult(ListPostingsResult result, OutputMode outputMode) {
    writeListPostingsResult(result, false, outputMode);
  }

  void writeListPostingsResult(
      ListPostingsResult result, boolean withContext, OutputMode outputMode) {
    bookReadWriter.writeListPostingsResult(result, withContext, outputMode);
  }

  void writeListTaxRegistrationsResult(ListTaxRegistrationsResult result, OutputMode outputMode) {
    writeListTaxRegistrationsResult(result, false, outputMode);
  }

  void writeListTaxRegistrationsResult(
      ListTaxRegistrationsResult result, boolean withContext, OutputMode outputMode) {
    bookReadWriter.writeListTaxRegistrationsResult(result, withContext, outputMode);
  }
}
