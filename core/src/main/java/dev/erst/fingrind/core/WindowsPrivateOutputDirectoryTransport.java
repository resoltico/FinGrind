package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Windows directory-creation policy, independent of the narrow FFM ABI adapter. */
final class WindowsPrivateOutputDirectoryTransport {
  private WindowsPrivateOutputDirectoryTransport() {}

  static void createNew(Path directory, NativeDirectoryOperations operations) throws IOException {
    Path checkedDirectory = Objects.requireNonNull(directory, "directory");
    NativeDirectoryOperations checkedOperations = Objects.requireNonNull(operations, "operations");
    try (WindowsPrivateOutputFileTransport.CurrentTokenUser tokenUser =
        checkedOperations.acquireCurrentTokenUser()) {
      checkedOperations.createDirectory(checkedDirectory, tokenUser);
      try (WindowsPrivateOutputFileTransport.NativeFile opened =
          checkedOperations.openExistingDirectory(checkedDirectory, tokenUser)) {
        WindowsPrivateOutputFileTransport.requireExactOwnerOnlyDirectory(
            opened.securityProof(tokenUser));
      }
    }
  }

  /** Testable boundary for one atomic directory creation and retained proof handle. */
  interface NativeDirectoryOperations {
    /** Acquires the current token user for the duration of the native proof. */
    WindowsPrivateOutputFileTransport.CurrentTokenUser acquireCurrentTokenUser() throws IOException;

    /** Atomically creates the directory using the supplied current-token-user evidence. */
    void createDirectory(
        Path directory, WindowsPrivateOutputFileTransport.CurrentTokenUser tokenUser)
        throws IOException;

    /** Opens the new directory's exact native proof handle using the supplied owner evidence. */
    WindowsPrivateOutputFileTransport.NativeFile openExistingDirectory(
        Path directory, WindowsPrivateOutputFileTransport.CurrentTokenUser tokenUser)
        throws IOException;
  }
}
