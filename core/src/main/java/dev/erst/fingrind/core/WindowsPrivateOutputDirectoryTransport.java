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
    try (WindowsPrivateOutputFileTransport.CurrentOwner owner =
        checkedOperations.acquireCurrentOwner()) {
      checkedOperations.createDirectory(checkedDirectory, owner);
      try (WindowsPrivateOutputFileTransport.NativeFile opened =
          checkedOperations.openExistingDirectory(checkedDirectory, owner)) {
        WindowsPrivateOutputFileTransport.requireExactOwnerOnlyDirectory(
            opened.securityProof(owner));
      }
    }
  }

  /** Testable boundary for one atomic directory creation and retained proof handle. */
  interface NativeDirectoryOperations {
    /** Acquires the current token owner for the duration of the native proof. */
    WindowsPrivateOutputFileTransport.CurrentOwner acquireCurrentOwner() throws IOException;

    /** Atomically creates the directory using the supplied current-owner evidence. */
    void createDirectory(Path directory, WindowsPrivateOutputFileTransport.CurrentOwner owner)
        throws IOException;

    /** Opens the new directory's exact native proof handle using the supplied owner evidence. */
    WindowsPrivateOutputFileTransport.NativeFile openExistingDirectory(
        Path directory, WindowsPrivateOutputFileTransport.CurrentOwner owner) throws IOException;
  }
}
