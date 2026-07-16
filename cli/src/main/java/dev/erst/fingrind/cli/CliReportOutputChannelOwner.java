package dev.erst.fingrind.cli;

/** Supplies the output channel shared by focused report-family result writers. */
@FunctionalInterface
interface CliReportOutputChannelOwner {
  /** Returns the channel that owns report envelope and artifact publication. */
  CliOutputChannel reportOutputChannel();
}
