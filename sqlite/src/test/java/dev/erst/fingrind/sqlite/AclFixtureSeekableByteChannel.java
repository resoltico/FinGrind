package dev.erst.fingrind.sqlite;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;

/** Minimal writable channel for Files.createFile on the test ACL filesystem. */
final class AclFixtureSeekableByteChannel implements SeekableByteChannel {
  private boolean open = true;

  @Override
  public int read(ByteBuffer dst) {
    return -1;
  }

  @Override
  public int write(ByteBuffer src) {
    int remaining = src.remaining();
    src.position(src.limit());
    return remaining;
  }

  @Override
  public long position() {
    return 0;
  }

  @Override
  public SeekableByteChannel position(long newPosition) {
    return this;
  }

  @Override
  public long size() {
    return 0;
  }

  @Override
  public SeekableByteChannel truncate(long size) {
    return this;
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    open = false;
  }
}
