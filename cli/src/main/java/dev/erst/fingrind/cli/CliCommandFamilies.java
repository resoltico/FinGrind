package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared base for book-bound mutation commands that consume a request file and output mode. */
abstract non-sealed class CliBookRequestOutputModeCommand implements CliCommand.OutputModeCommand {
  private final BookAccess bookAccess;
  private final Path requestFile;
  private final OutputMode outputMode;

  CliBookRequestOutputModeCommand(BookAccess bookAccess, Path requestFile, OutputMode outputMode) {
    this.bookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    this.requestFile = Objects.requireNonNull(requestFile, "requestFile");
    this.outputMode = Objects.requireNonNull(outputMode, "outputMode");
  }

  final BookAccess bookAccess() {
    return bookAccess;
  }

  final Path requestFile() {
    return requestFile;
  }

  @Override
  public final OutputMode outputMode() {
    return outputMode;
  }

  @Override
  public final int execute(CliExecutionContext executionContext) {
    return executeCommand(
        Objects.requireNonNull(executionContext, "executionContext"),
        bookAccess,
        requestFile,
        outputMode);
  }

  protected abstract int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      Path requestFile,
      OutputMode outputMode);
}

/** Shared base for book-bound query commands that accept a query payload and output mode. */
abstract non-sealed class CliBookQueryOutputModeCommand<Q> implements CliCommand.OutputModeCommand {
  private final BookAccess bookAccess;
  private final Q query;
  private final OutputMode outputMode;

  CliBookQueryOutputModeCommand(BookAccess bookAccess, Q query, OutputMode outputMode) {
    this.bookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    this.query = Objects.requireNonNull(query, "query");
    this.outputMode = Objects.requireNonNull(outputMode, "outputMode");
  }

  final BookAccess bookAccess() {
    return bookAccess;
  }

  final Q query() {
    return query;
  }

  @Override
  public final OutputMode outputMode() {
    return outputMode;
  }

  @Override
  public final int execute(CliExecutionContext executionContext) {
    return executeCommand(
        Objects.requireNonNull(executionContext, "executionContext"),
        bookAccess,
        query,
        outputMode);
  }

  protected abstract int executeCommand(
      CliExecutionContext executionContext, BookAccess bookAccess, Q query, OutputMode outputMode);
}

/** Shared base for book-bound report commands that carry a typed query and report output target. */
abstract non-sealed class CliBookQueryReportCommand<Q> implements CliCommand.ReportCommand {
  private final BookAccess bookAccess;
  private final Q query;
  private final CliCommand.ReportOutput output;

  CliBookQueryReportCommand(BookAccess bookAccess, Q query, CliCommand.ReportOutput output) {
    this.bookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    this.query = Objects.requireNonNull(query, "query");
    this.output = Objects.requireNonNull(output, "output");
  }

  final BookAccess bookAccess() {
    return bookAccess;
  }

  final Q query() {
    return query;
  }

  @Override
  public final CliCommand.ReportOutput output() {
    return output;
  }

  @Override
  public final int execute(CliExecutionContext executionContext) {
    return executeCommand(
        Objects.requireNonNull(executionContext, "executionContext"), bookAccess, query, output);
  }

  protected abstract int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      Q query,
      CliCommand.ReportOutput output);
}

/** Shared base for book-bound commands that accept an optional artifact path and output mode. */
abstract non-sealed class CliBookNullablePathOutputModeCommand
    implements CliCommand.OutputModeCommand {
  private final BookAccess bookAccess;
  private final @Nullable Path path;
  private final OutputMode outputMode;

  CliBookNullablePathOutputModeCommand(
      BookAccess bookAccess, @Nullable Path path, OutputMode outputMode) {
    this.bookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    this.path = path;
    this.outputMode = Objects.requireNonNull(outputMode, "outputMode");
  }

  final BookAccess bookAccess() {
    return bookAccess;
  }

  final @Nullable Path rollbackArtifactPath() {
    return path;
  }

  @Override
  public final OutputMode outputMode() {
    return outputMode;
  }

  @Override
  public final int execute(CliExecutionContext executionContext) {
    return executeCommand(
        Objects.requireNonNull(executionContext, "executionContext"), bookAccess, path, outputMode);
  }

  protected abstract int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      @Nullable Path path,
      OutputMode outputMode);
}
