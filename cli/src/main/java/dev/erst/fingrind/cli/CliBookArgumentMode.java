package dev.erst.fingrind.cli;

/** Supported parser shapes for commands that address one selected book file. */
enum CliBookArgumentMode {
  REQUEST_BOUND(true, false),
  REQUEST_BOUND_WITH_COMMAND_ARGUMENTS(true, true),
  BOOK_WITH_COMMAND_ARGUMENTS(false, true);

  private final boolean acceptsRequestFile;
  private final boolean collectsCommandArguments;

  CliBookArgumentMode(boolean acceptsRequestFile, boolean collectsCommandArguments) {
    this.acceptsRequestFile = acceptsRequestFile;
    this.collectsCommandArguments = collectsCommandArguments;
  }

  boolean acceptsRequestFile() {
    return acceptsRequestFile;
  }

  boolean collectsCommandArguments() {
    return collectsCommandArguments;
  }
}
