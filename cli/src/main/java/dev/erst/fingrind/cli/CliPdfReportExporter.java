package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.report.pdf.PdfReportService;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** CLI adapter that exports successful FinGrind reports as atomic PDF artifacts. */
final class CliPdfReportExporter {
  private static final Set<PosixFilePermission> PRIVATE_PDF_POSIX_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private final PdfReportService pdfReportService;
  private final FileOperations fileOperations;

  CliPdfReportExporter(PdfReportService pdfReportService) {
    this(pdfReportService, new DefaultFileOperations(CliPdfReportExporter::normalizePrivatePdf));
  }

  CliPdfReportExporter(PdfReportService pdfReportService, FileOperations fileOperations) {
    this.pdfReportService = Objects.requireNonNull(pdfReportService, "pdfReportService");
    this.fileOperations = Objects.requireNonNull(fileOperations, "fileOperations");
  }

  void exportAccountBalance(Path outputPath, AccountBalanceSnapshot snapshot) {
    writePdf(outputPath, pdfReportService.renderAccountBalance(snapshot));
  }

  void exportTrialBalance(Path outputPath, TrialBalanceReport report) {
    writePdf(outputPath, pdfReportService.renderTrialBalance(report));
  }

  void exportAccountLedger(Path outputPath, AccountLedgerReport report) {
    writePdf(outputPath, pdfReportService.renderAccountLedger(report));
  }

  void exportPeriodSummary(Path outputPath, PeriodSummaryReport report) {
    writePdf(outputPath, pdfReportService.renderPeriodSummary(report));
  }

  void exportFinancialPosition(Path outputPath, FinancialPositionReport report) {
    writePdf(outputPath, pdfReportService.renderFinancialPosition(report));
  }

  void exportIncomeStatement(Path outputPath, IncomeStatementReport report) {
    writePdf(outputPath, pdfReportService.renderIncomeStatement(report));
  }

  void exportCashFlowStatement(Path outputPath, CashFlowStatementReport report) {
    writePdf(outputPath, pdfReportService.renderCashFlowStatement(report));
  }

  void exportChangesInEquity(Path outputPath, ChangesInEquityReport report) {
    writePdf(outputPath, pdfReportService.renderChangesInEquity(report));
  }

  private void writePdf(Path outputPath, byte[] pdfBytes) {
    Objects.requireNonNull(outputPath, "outputPath");
    Objects.requireNonNull(pdfBytes, "pdfBytes");
    Path normalizedOutputPath = outputPath.toAbsolutePath().normalize();
    Path parentDirectory = parentDirectory(normalizedOutputPath);
    if (Files.exists(normalizedOutputPath)) {
      throw new CliArtifactOutputExistsException(normalizedOutputPath, "--pdf-out");
    }
    Path temporaryFile = null;
    try {
      fileOperations.createDirectories(parentDirectory);
      temporaryFile = fileOperations.createTempFile(parentDirectory, ".fingrind-pdf-", ".tmp");
      fileOperations.write(
          temporaryFile, pdfBytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      moveAtomically(temporaryFile, normalizedOutputPath);
      fileOperations.normalizePrivatePdfPermissions(normalizedOutputPath);
    } catch (IOException exception) {
      deleteIfPresent(temporaryFile);
      throw new CliPdfExportException(normalizedOutputPath, exception);
    }
  }

  private void moveAtomically(Path source, Path target) throws IOException {
    try {
      fileOperations.moveAtomically(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      fileOperations.move(source, target);
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

  private static void normalizePrivatePdf(Path path) throws IOException {
    if (!path.getFileSystem().equals(FileSystems.getDefault())) {
      return;
    }
    File pdfFile = path.toFile();
    pdfFile.setReadable(true, true);
    pdfFile.setWritable(true, true);
    applyPrivatePosixPermissionsIfSupported(path);
    requireOwnerReadablePrivatePdf(path);
  }

  static void applyPrivatePosixPermissionsIfSupported(Path path) throws IOException {
    if (Files.notExists(path)) {
      return;
    }
    if (Files.getFileAttributeView(path, PosixFileAttributeView.class) == null) {
      return;
    }
    Files.setPosixFilePermissions(path, PRIVATE_PDF_POSIX_PERMISSIONS);
  }

  static void requireOwnerReadablePrivatePdf(Path path) throws IOException {
    if (Files.isReadable(path)) {
      return;
    }
    throw new IOException(
        "Published PDF artifact is not owner-readable after permission normalization: "
            + path.toAbsolutePath());
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

    /** Normalizes the finished PDF artifact permissions for protected report publication. */
    void normalizePrivatePdfPermissions(Path path) throws IOException;
  }

  /** One strategy seam that applies the protected-PDF permission policy for one filesystem. */
  @FunctionalInterface
  interface PrivatePdfPermissionNormalizer {
    /** Applies the protected PDF permission policy to one finished artifact path. */
    void normalize(Path path) throws IOException;
  }

  /** Default `java.nio.file.Files` implementation for real CLI PDF export. */
  static final class DefaultFileOperations implements FileOperations {
    private final PrivatePdfPermissionNormalizer privatePdfPermissionNormalizer;

    DefaultFileOperations() {
      this(CliPdfReportExporter::normalizePrivatePdf);
    }

    DefaultFileOperations(PrivatePdfPermissionNormalizer privatePdfPermissionNormalizer) {
      this.privatePdfPermissionNormalizer =
          Objects.requireNonNull(privatePdfPermissionNormalizer, "privatePdfPermissionNormalizer");
    }

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

    @Override
    public void normalizePrivatePdfPermissions(Path path) throws IOException {
      privatePdfPermissionNormalizer.normalize(path);
    }
  }
}
