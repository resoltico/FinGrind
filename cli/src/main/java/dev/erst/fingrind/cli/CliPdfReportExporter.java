package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** CLI adapter that exports successful FinGrind reports as atomic PDF artifacts. */
final class CliPdfReportExporter {
  private final PdfReportService pdfReportService;
  private final FileOperations fileOperations;

  CliPdfReportExporter(PdfReportService pdfReportService) {
    this(pdfReportService, new DefaultFileOperations());
  }

  CliPdfReportExporter(PdfReportService pdfReportService, FileOperations fileOperations) {
    this.pdfReportService = Objects.requireNonNull(pdfReportService, "pdfReportService");
    this.fileOperations = Objects.requireNonNull(fileOperations, "fileOperations");
  }

  void exportAccountBalance(Path outputPath, Path bookFilePath, AccountBalanceSnapshot snapshot) {
    writePdf(outputPath, pdfReportService.renderAccountBalance(bookFilePath, snapshot));
  }

  void exportTrialBalance(Path outputPath, Path bookFilePath, TrialBalanceReport report) {
    writePdf(outputPath, pdfReportService.renderTrialBalance(bookFilePath, report));
  }

  void exportAccountLedger(Path outputPath, Path bookFilePath, AccountLedgerReport report) {
    writePdf(outputPath, pdfReportService.renderAccountLedger(bookFilePath, report));
  }

  void exportPeriodSummary(Path outputPath, Path bookFilePath, PeriodSummaryReport report) {
    writePdf(outputPath, pdfReportService.renderPeriodSummary(bookFilePath, report));
  }

  private void writePdf(Path outputPath, byte[] pdfBytes) {
    Objects.requireNonNull(outputPath, "outputPath");
    Objects.requireNonNull(pdfBytes, "pdfBytes");
    Path normalizedOutputPath = outputPath.toAbsolutePath().normalize();
    Path parentDirectory = parentDirectory(normalizedOutputPath);
    Path temporaryFile = null;
    try {
      fileOperations.createDirectories(parentDirectory);
      temporaryFile = fileOperations.createTempFile(parentDirectory, ".fingrind-pdf-", ".tmp");
      fileOperations.write(
          temporaryFile, pdfBytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      moveAtomically(temporaryFile, normalizedOutputPath);
    } catch (IOException exception) {
      deleteIfPresent(temporaryFile);
      throw new CliPdfExportException(normalizedOutputPath, exception);
    }
  }

  private void moveAtomically(Path source, Path target) throws IOException {
    try {
      fileOperations.moveAtomically(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      fileOperations.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  static Path parentDirectory(Path outputPath) {
    Path parent = outputPath.getParent();
    return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
  }

  void deleteIfPresent(@Nullable Path path) {
    if (path == null) {
      return;
    }
    try {
      fileOperations.deleteIfExists(path);
    } catch (IOException exception) {
      ignoreCleanupFailure(exception);
    }
  }

  private static void ignoreCleanupFailure(IOException exception) {
    java.util.Objects.requireNonNull(exception, "exception");
  }

  /** One filesystem adapter used by PDF export and its focused unit tests. */
  interface FileOperations {
    /** Creates parent directories for the target PDF artifact. */
    void createDirectories(Path directory) throws IOException;

    /** Creates one temporary file in the supplied directory. */
    Path createTempFile(Path directory, String prefix, String suffix) throws IOException;

    /** Writes the finished PDF bytes into the temporary file. */
    void write(Path path, byte[] bytes, StandardOpenOption... options) throws IOException;

    /** Moves the temporary file into place with the supplied move options. */
    Path move(Path source, Path target, StandardCopyOption... options) throws IOException;

    /** Deletes one temporary file during best-effort cleanup. */
    boolean deleteIfExists(Path path) throws IOException;

    /** Moves the temporary file into place using an atomic replacement when supported. */
    default Path moveAtomically(Path source, Path target, StandardCopyOption... options)
        throws IOException {
      return move(source, target, options);
    }
  }

  /** Default `java.nio.file.Files` implementation for real CLI PDF export. */
  private static final class DefaultFileOperations implements FileOperations {
    @Override
    public void createDirectories(Path directory) throws IOException {
      Files.createDirectories(directory);
    }

    @Override
    public Path createTempFile(Path directory, String prefix, String suffix) throws IOException {
      return Files.createTempFile(directory, prefix, suffix);
    }

    @Override
    public void write(Path path, byte[] bytes, StandardOpenOption... options) throws IOException {
      Files.write(path, bytes, options);
    }

    @Override
    public Path move(Path source, Path target, StandardCopyOption... options) throws IOException {
      return Files.move(source, target, options);
    }

    @Override
    public boolean deleteIfExists(Path path) throws IOException {
      return Files.deleteIfExists(path);
    }
  }
}
