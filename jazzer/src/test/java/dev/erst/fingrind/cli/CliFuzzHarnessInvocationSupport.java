package dev.erst.fingrind.cli;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

public final class CliFuzzHarnessInvocationSupport {
  private CliFuzzHarnessInvocationSupport() {}

  static FuzzedDataProvider fuzzedBytes(byte[] input) {
    InvocationHandler handler = new FuzzedBytesDataProviderHandler(input);
    return (FuzzedDataProvider)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {FuzzedDataProvider.class},
            handler);
  }

  static void invokeFuzzHarness(String className, String methodName, byte[] input) {
    try {
      Class<?> harnessClass =
          Class.forName(className, true, Thread.currentThread().getContextClassLoader());
      Object harness = harnessClass.getDeclaredConstructor().newInstance();
      Method method = harnessClass.getDeclaredMethod(methodName, FuzzedDataProvider.class);
      method.setAccessible(true);
      method.invoke(harness, fuzzedBytes(input));
    } catch (InvocationTargetException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      if (cause instanceof Error error) {
        throw error;
      }
      throw new IllegalStateException("Fuzz harness invocation failed.", cause);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to invoke compiled fuzz harness.", exception);
    }
  }

  private static final class FuzzedBytesDataProviderHandler implements InvocationHandler {
    private final byte[] input;

    private FuzzedBytesDataProviderHandler(byte[] input) {
      this.input = input.clone();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return switch (method.getName()) {
        case "consumeRemainingAsBytes" -> input.clone();
        case "toString" -> "FuzzedBytesDataProvider";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" ->
            args != null
                && args.length == 1
                && args[0] != null
                && Proxy.isProxyClass(args[0].getClass())
                && Objects.equals(Proxy.getInvocationHandler(args[0]), this);
        default ->
            throw new UnsupportedOperationException(
                "Unsupported FuzzedDataProvider method: " + method.getName());
      };
    }
  }
}
