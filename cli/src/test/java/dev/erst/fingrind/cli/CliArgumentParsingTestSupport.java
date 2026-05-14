package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.runtime.BookAccess;

/** Shared helpers for split CLI argument parsing tests. */
class CliArgumentParsingTestSupport extends CliIoFixtureSupport {
  protected CliArgumentParsingTestSupport() {}

  protected final BookAccess.PassphraseSource.KeyFile assertKeyFileSource(BookAccess bookAccess) {
    return assertInstanceOf(
        BookAccess.PassphraseSource.KeyFile.class, bookAccess.passphraseSource());
  }
}
