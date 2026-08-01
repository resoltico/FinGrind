package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import java.io.PrintStream;
import java.nio.file.Path;

/** Focused test fixture for book-inspection and book-read response projections. */
final class CliBookReadResponseWriterFixture {
  private final CliBookReadResponseWriter writer;

  CliBookReadResponseWriterFixture(PrintStream outputStream) {
    writer = new CliBookReadResponseWriter(CliTestOutputChannels.forOutput(outputStream));
  }

  void writeBookInspection(Path bookFilePath, BookInspection inspection) {
    writeBookInspection(bookFilePath, inspection, OutputMode.JSON);
  }

  void writeBookInspection(Path bookFilePath, BookInspection inspection, OutputMode outputMode) {
    writer.writeBookInspection(bookFilePath, inspection, outputMode);
  }

  void writeListAccountsResult(ListAccountsResult result) {
    writeListAccountsResult(result, OutputMode.JSON);
  }

  void writeListAccountsResult(ListAccountsResult result, OutputMode outputMode) {
    writeListAccountsResult(result, false, outputMode);
  }

  void writeListAccountsResult(
      ListAccountsResult result, boolean withContext, OutputMode outputMode) {
    writer.writeListAccountsResult(result, withContext, outputMode);
  }

  void writeGetPostingResult(GetPostingResult result) {
    writeGetPostingResult(result, OutputMode.JSON);
  }

  void writeGetPostingResult(GetPostingResult result, OutputMode outputMode) {
    writeGetPostingResult(result, false, outputMode);
  }

  void writeGetPostingResult(GetPostingResult result, boolean withContext, OutputMode outputMode) {
    writer.writeGetPostingResult(result, withContext, outputMode);
  }

  void writeListPostingsResult(ListPostingsResult result) {
    writeListPostingsResult(result, OutputMode.JSON);
  }

  void writeListPostingsResult(ListPostingsResult result, OutputMode outputMode) {
    writeListPostingsResult(result, false, outputMode);
  }

  void writeListPostingsResult(
      ListPostingsResult result, boolean withContext, OutputMode outputMode) {
    writer.writeListPostingsResult(result, withContext, outputMode);
  }

  void writeListTaxRegistrationsResult(ListTaxRegistrationsResult result, OutputMode outputMode) {
    writeListTaxRegistrationsResult(result, false, outputMode);
  }

  void writeListTaxRegistrationsResult(
      ListTaxRegistrationsResult result, boolean withContext, OutputMode outputMode) {
    writer.writeListTaxRegistrationsResult(result, withContext, outputMode);
  }
}
