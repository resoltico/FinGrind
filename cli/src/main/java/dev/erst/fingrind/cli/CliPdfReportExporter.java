package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
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
  private static final Set<PosixFilePermission> HOST_READABLE_PDF_POSIX_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.OTHERS_READ);

  private final PdfReportService pdfReportService;
  private final FileOperations fileOperations;

  CliPdfReportExporter(PdfReportService pdfReportService) {
    this(
        pdfReportService,
        new DefaultFileOperations(CliPdfReportExporter::normalizePublishedPdfPermissions));
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

  void exportChangesInEquity(Path outputPath, ChangesInEquityReport report) {
    writePdf(outputPath, pdfReportService.renderChangesInEquity(report));
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
      fileOperations.normalizePublishedPdfPermissions(normalizedOutputPath);
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

  private static void normalizePublishedPdfPermissions(Path path) throws IOException {
    if (!path.getFileSystem().equals(FileSystems.getDefault())) {
      return;
    }
    File pdfFile = path.toFile();
    pdfFile.setReadable(true, false);
    pdfFile.setWritable(true, true);
    applyHostReadablePosixPermissionsIfSupported(path);
    requireHostReadablePublishedPdf(path);
  }

  static void applyHostReadablePosixPermissionsIfSupported(Path path) throws IOException {
    if (Files.notExists(path)) {
      return;
    }
    if (Files.getFileAttributeView(path, PosixFileAttributeView.class) == null) {
      return;
    }
    Files.setPosixFilePermissions(path, HOST_READABLE_PDF_POSIX_PERMISSIONS);
  }

  static void requireHostReadablePublishedPdf(Path path) throws IOException {
    if (Files.isReadable(path)) {
      return;
    }
    throw new IOException(
        "Published PDF artifact is not host-readable after permission normalization: "
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

    /** Normalizes the finished PDF artifact permissions for public mounted-volume workflows. */
    void normalizePublishedPdfPermissions(Path path) throws IOException;
  }

  /** One strategy seam that applies the published-PDF permission policy for one filesystem. */
  @FunctionalInterface
  interface PublishedPdfPermissionNormalizer {
    /** Applies the mounted-volume PDF permission policy to one finished artifact path. */
    void normalize(Path path) throws IOException;
  }

  /** Default `java.nio.file.Files` implementation for real CLI PDF export. */
  static final class DefaultFileOperations implements FileOperations {
    private final PublishedPdfPermissionNormalizer publishedPdfPermissionNormalizer;

    DefaultFileOperations() {
      this(CliPdfReportExporter::normalizePublishedPdfPermissions);
    }

    DefaultFileOperations(PublishedPdfPermissionNormalizer publishedPdfPermissionNormalizer) {
      this.publishedPdfPermissionNormalizer =
          Objects.requireNonNull(
              publishedPdfPermissionNormalizer, "publishedPdfPermissionNormalizer");
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
    public void normalizePublishedPdfPermissions(Path path) throws IOException {
      publishedPdfPermissionNormalizer.normalize(path);
    }
  }
}
