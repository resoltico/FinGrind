package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsResult;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
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
    bookReadWriter.writeListAccountsResult(result, outputMode);
  }

  void writeGetPostingResult(GetPostingResult result) {
    writeGetPostingResult(result, OutputMode.JSON);
  }

  void writeGetPostingResult(GetPostingResult result, OutputMode outputMode) {
    bookReadWriter.writeGetPostingResult(result, outputMode);
  }

  void writeListPostingsResult(ListPostingsResult result) {
    writeListPostingsResult(result, OutputMode.JSON);
  }

  void writeListPostingsResult(ListPostingsResult result, OutputMode outputMode) {
    bookReadWriter.writeListPostingsResult(result, outputMode);
  }

  void writeListTaxRegistrationsResult(ListTaxRegistrationsResult result, OutputMode outputMode) {
    bookReadWriter.writeListTaxRegistrationsResult(result, outputMode);
  }

  void writeTaxObligationResult(TaxObligationResult result, OutputMode outputMode) {
    bookReadWriter.writeTaxObligationResult(result, outputMode);
  }
}
