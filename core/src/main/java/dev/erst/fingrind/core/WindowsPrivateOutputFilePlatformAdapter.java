package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Production Windows channel adapter whose native call-table source remains replaceable for exact
 * ABI tests on non-Windows hosts.
 */
final class WindowsPrivateOutputFilePlatformAdapter
    implements PrivateOutputFilePlatformOperations.WindowsFileCreator,
        PrivateOutputFilePlatformOperations.WindowsFileOpener,
        PrivateOutputDirectoryCreation.WindowsDirectoryCreator {
  static final WindowsPrivateOutputFilePlatformAdapter PRODUCTION =
      new WindowsPrivateOutputFilePlatformAdapter(
          new RuntimeCallTableSource(WindowsPrivateOutputFileBindingSupport::nativeRuntime));

  private final WindowsPrivateOutputFileBindingSupport.CallTableSource callTableSource;

  WindowsPrivateOutputFilePlatformAdapter(
      WindowsPrivateOutputFileBindingSupport.CallTableSource callTableSource) {
    this.callTableSource = Objects.requireNonNull(callTableSource, "callTableSource");
  }

  @Override
  public PrivateOutputFile.OpenedFile createNew(Path file) throws IOException {
    return WindowsPrivateOutputFileTransport.createNew(
        Objects.requireNonNull(file, "file"),
        WindowsPrivateOutputFileFfmTransport.operationsFor(callTableSource.calls()));
  }

  @Override
  public PrivateOutputFile.OpenedFile openExisting(Path file, PrivateOutputFile.Access access)
      throws IOException {
    return WindowsPrivateOutputFileTransport.openExisting(
        Objects.requireNonNull(file, "file"),
        Objects.requireNonNull(access, "access"),
        WindowsPrivateOutputFileFfmTransport.operationsFor(callTableSource.calls()));
  }

  @Override
  public void createDirectory(Path directory) throws IOException {
    WindowsPrivateOutputDirectoryTransport.createNew(
        Objects.requireNonNull(directory, "directory"),
        WindowsPrivateOutputDirectoryFfmTransport.operationsFor(callTableSource.calls()));
  }

  WindowsCurrentTokenUserIdentity currentTokenUserIdentity() throws IOException {
    return WindowsCurrentTokenUserIdentity.resolve(callTableSource.calls());
  }

  /** Supplies a fresh, production-native runtime for each protected output operation. */
  @FunctionalInterface
  interface RuntimeSource {
    /** Returns the runtime that owns one fresh native call table. */
    WindowsPrivateOutputFileBindingSupport.NativeRuntime runtime();
  }

  /**
   * Adapts the runtime source to the generic call-table boundary without process-global overrides.
   */
  static final class RuntimeCallTableSource
      implements WindowsPrivateOutputFileBindingSupport.CallTableSource {
    private final RuntimeSource runtimeSource;

    RuntimeCallTableSource(RuntimeSource runtimeSource) {
      this.runtimeSource = Objects.requireNonNull(runtimeSource, "runtimeSource");
    }

    @Override
    public WindowsPrivateOutputFileCalls calls() throws IOException {
      return runtimeSource.runtime().calls();
    }
  }
}
