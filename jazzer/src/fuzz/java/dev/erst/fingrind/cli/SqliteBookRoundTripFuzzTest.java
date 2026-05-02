package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.IOException;

/** Fuzzes single-book SQLite commit and reload invariants using arbitrary filesystem paths. */
public class SqliteBookRoundTripFuzzTest {
  @FuzzTest
  void roundTripSingleBook(FuzzedDataProvider data) throws IOException {
    SqliteBookRoundTripFuzzAssertions.roundTripSingleBook(data.consumeRemainingAsBytes());
  }
}
