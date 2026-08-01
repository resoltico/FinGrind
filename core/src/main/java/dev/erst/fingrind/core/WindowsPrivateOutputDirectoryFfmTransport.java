package dev.erst.fingrind.core;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;

/** Native atomic Windows directory creation for the protected private-output namespace. */
final class WindowsPrivateOutputDirectoryFfmTransport {
  private WindowsPrivateOutputDirectoryFfmTransport() {}

  static WindowsPrivateOutputDirectoryTransport.NativeDirectoryOperations operationsFor(
      WindowsPrivateOutputFileCalls calls) {
    return new FfmDirectoryOperations(calls);
  }

  private static void createDirectory(
      Path directory, WindowsPrivateOutputFileCalls calls, WindowsPrivateOutputFileOwner owner)
      throws IOException {
    try (Arena arena = Arena.ofConfined();
        WindowsPrivateOutputFileOwner.SecurityAttributes attributes =
            owner.securityAttributes(arena)) {
      WindowsPrivateOutputFileNative.Result<Integer> result =
          calls
              .fileCalls()
              .createDirectoryW(
                  WindowsPrivateOutputFileNative.extendedWidePath(arena, directory),
                  attributes.attributes());
      if (result.value() == 0) {
        if (result.lastError() == WindowsPrivateOutputFileNative.ERROR_FILE_EXISTS
            || result.lastError() == WindowsPrivateOutputFileNative.ERROR_ALREADY_EXISTS) {
          throw new FileAlreadyExistsException(directory.toString());
        }
        throw WindowsPrivateOutputFileNative.windowsFailure("CreateDirectoryW", result.lastError());
      }
    }
  }

  private static WindowsPrivateOutputFileHandle openExistingDirectory(
      Path directory, WindowsPrivateOutputFileCalls calls) throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      WindowsPrivateOutputFileNative.Result<Long> result =
          calls
              .fileCalls()
              .createFileW(
                  WindowsPrivateOutputFileNative.extendedWidePath(arena, directory),
                  WindowsPrivateOutputFileNative.GENERIC_READ,
                  WindowsPrivateOutputFileNative.FILE_SHARE_READ_WRITE,
                  MemorySegment.NULL,
                  WindowsPrivateOutputFileNative.OPEN_EXISTING,
                  WindowsPrivateOutputFileNative.FILE_FLAG_BACKUP_SEMANTICS
                      | WindowsPrivateOutputFileNative.FILE_FLAG_OPEN_REPARSE_POINT,
                  MemorySegment.NULL);
      if (result.value() == -1L) {
        throw WindowsPrivateOutputFileNative.windowsFailure("CreateFileW", result.lastError());
      }
      return new WindowsPrivateOutputFileHandle(
          calls, new WindowsPrivateOutputFileNative.Handle(result.value()));
    }
  }

  /** Production native-directory operations backed by the Win32 FFM call table. */
  private static final class FfmDirectoryOperations
      implements WindowsPrivateOutputDirectoryTransport.NativeDirectoryOperations {
    private final WindowsPrivateOutputFileCalls calls;

    private FfmDirectoryOperations(WindowsPrivateOutputFileCalls calls) {
      this.calls = Objects.requireNonNull(calls, "calls");
    }

    @Override
    public WindowsPrivateOutputFileTransport.CurrentTokenUser acquireCurrentTokenUser()
        throws IOException {
      return WindowsPrivateOutputFileOwner.acquire(calls);
    }

    @Override
    public void createDirectory(
        Path directory, WindowsPrivateOutputFileTransport.CurrentTokenUser tokenUser)
        throws IOException {
      WindowsPrivateOutputDirectoryFfmTransport.createDirectory(
          Objects.requireNonNull(directory, "directory"), calls, requireOwner(tokenUser));
    }

    @Override
    public WindowsPrivateOutputFileTransport.NativeFile openExistingDirectory(
        Path directory, WindowsPrivateOutputFileTransport.CurrentTokenUser tokenUser)
        throws IOException {
      requireOwner(tokenUser);
      return WindowsPrivateOutputDirectoryFfmTransport.openExistingDirectory(
          Objects.requireNonNull(directory, "directory"), calls);
    }

    private static WindowsPrivateOutputFileOwner requireOwner(
        WindowsPrivateOutputFileTransport.CurrentTokenUser tokenUser) {
      if (!(tokenUser instanceof WindowsPrivateOutputFileOwner nativeOwner)) {
        throw new IllegalArgumentException(
            "The Windows directory binding received an incompatible owner.");
      }
      return nativeOwner;
    }
  }
}
