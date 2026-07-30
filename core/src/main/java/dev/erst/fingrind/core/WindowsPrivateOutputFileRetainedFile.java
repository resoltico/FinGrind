package dev.erst.fingrind.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/** One admitted native file whose security proof and lifecycle remain inseparable. */
final class WindowsPrivateOutputFileRetainedFile implements PrivateOutputFile.OpenedFile {
  private final WindowsPrivateOutputFileTransport.NativeFile nativeFile;
  private final boolean created;
  private final ReentrantLock lifecycleLock = new ReentrantLock();
  private boolean closed;

  WindowsPrivateOutputFileRetainedFile(
      WindowsPrivateOutputFileTransport.NativeFile nativeFile, boolean created) {
    this.nativeFile = Objects.requireNonNull(nativeFile, "nativeFile");
    this.created = created;
  }

  @Override
  public boolean created() {
    return created;
  }

  @Override
  public int read(ByteBuffer destination) throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      return nativeFile.read(Objects.requireNonNull(destination, "destination"));
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public int write(ByteBuffer source) throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      return nativeFile.write(Objects.requireNonNull(source, "source"));
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public boolean isOpen() {
    lifecycleLock.lock();
    try {
      return !closed;
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public long size() throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      return nativeFile.size();
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public void truncate(long size) throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      nativeFile.truncate(size);
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public void position(long position) throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      nativeFile.position(position);
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public void force() throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      nativeFile.force();
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public PrivateOutputFile.@Nullable HeldLock tryExclusiveLock(long position, long size)
      throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      return nativeFile.tryExclusiveLock(position, size);
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public String physicalObjectIdentity() throws IOException {
    lifecycleLock.lock();
    try {
      requireOpenLocked();
      return nativeFile.physicalObjectIdentity();
    } finally {
      lifecycleLock.unlock();
    }
  }

  @Override
  public void close() throws IOException {
    lifecycleLock.lock();
    try {
      if (closed) {
        return;
      }
      closed = true;
      nativeFile.close();
    } finally {
      lifecycleLock.unlock();
    }
  }

  private void requireOpenLocked() throws IOException {
    if (closed) {
      throw new IOException("The retained Windows private-output file is already closed.");
    }
  }
}
