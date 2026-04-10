package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Executes one sqlite3 command against the selected book file. */
final class SqliteCommandExecutor {
  static final String SQLITE3_BINARY_ENV = "FINGRIND_SQLITE3_BINARY";
  private static final String SQLITE3_BINARY = "sqlite3";
  private static final String SESSION_PREAMBLE =
      """
        pragma foreign_keys = on;
        """;

  private final Path bookPath;

  SqliteCommandExecutor(Path bookPath) {
    this.bookPath = bookPath;
  }

  /** Runs one query and returns sqlite3 stdout and stderr without interpreting them further. */
  SqliteCommandResult query(String querySql) {
    return runSqlite3(List.of("-batch", "-bail", "-json", bookPath.toString()), querySql);
  }

  /** Runs one script and returns sqlite3 stdout and stderr without interpreting them further. */
  SqliteCommandResult script(String sqlScript) {
    return runSqlite3(List.of("-batch", "-bail", bookPath.toString()), sqlScript);
  }

  private SqliteCommandResult runSqlite3(List<String> arguments, String sql) {
    return runSqlite3(arguments, sql, command -> new ProcessBuilder(command).start());
  }

  @SuppressWarnings("PMD.DoNotUseThreads")
  static SqliteCommandResult runSqlite3(
      List<String> arguments, String sql, ProcessStarter processStarter) {
    List<String> command = sqlite3Command(System.getenv(SQLITE3_BINARY_ENV));
    command.addAll(arguments);
    Process process;
    try {
      process = processStarter.start(command);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to start sqlite3.", exception);
    }
    try (OutputStreamWriter writer =
        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
      writer.write(SESSION_PREAMBLE);
      writer.write(sql);
      if (!sql.endsWith(System.lineSeparator())) {
        writer.write(System.lineSeparator());
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to write sqlite3 input.", exception);
    }
    String standardOutput = readStream(process.getInputStream(), "stdout");
    String standardError = readStream(process.getErrorStream(), "stderr");
    int exitCode;
    try {
      exitCode = process.waitFor();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for sqlite3.", exception);
    }
    return new SqliteCommandResult(exitCode, standardOutput, standardError);
  }

  static String sqlite3Binary(String overrideBinary) {
    if (overrideBinary == null || overrideBinary.isBlank()) {
      return SQLITE3_BINARY;
    }
    return overrideBinary;
  }

  static List<String> sqlite3Command(String overrideBinary) {
    String configuredBinary = sqlite3Binary(overrideBinary);
    if (configuredBinary.endsWith(".sh")) {
      return new ArrayList<>(List.of("bash", configuredBinary));
    }
    return new ArrayList<>(List.of(configuredBinary));
  }

  private static String readStream(InputStream inputStream, String streamName) {
    try {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read sqlite3 " + streamName + ".", exception);
    }
  }

  /** Starts one sqlite3 process for the supplied command line. */
  @FunctionalInterface
  interface ProcessStarter {
    /** Launches one process for the supplied command line. */
    Process start(List<String> command) throws IOException;
  }

  /** Captured exit code, stdout, and stderr from one sqlite3 process invocation. */
  record SqliteCommandResult(int exitCode, String standardOutput, String standardError) {}
}
