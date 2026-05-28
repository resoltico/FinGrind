package dev.erst.fingrind.sqlite.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.util.Objects;

/** Adapts raw SQLite method handles into one typed call interface at a time. */
public final class SqliteNativeCallAdapter {
  private SqliteNativeCallAdapter() {}

  /** Adapts one raw method handle to the requested typed native-call interface. */
  public static <T> T adapt(Class<T> interfaceType, MethodHandle handle) {
    Objects.requireNonNull(interfaceType, "interfaceType");
    Objects.requireNonNull(handle, "handle");
    return interfaceType.cast(MethodHandleProxies.asInterfaceInstance(interfaceType, handle));
  }
}
