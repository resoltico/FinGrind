package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.code_intelligence.jazzer.third_party.net.bytebuddy.agent.Installer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Covers the project-owned premain bridge used by active Jazzer runs. */
class JazzerPremainAgentTest {
  private static final VarHandle INSTALLER_INSTRUMENTATION = installerInstrumentationHandle();

  @Test
  void premain_registersInstrumentationWithByteBuddyInstaller() {
    Instrumentation instrumentation = instrumentationProxy();
    Instrumentation installed =
        withClearedInstallerInstrumentation(
            () -> {
              JazzerPremainAgent.premain("", instrumentation);
              return Installer.getInstrumentation();
            });

    assertSame(instrumentation, installed);
  }

  @Test
  void agentmain_registersInstrumentationWithByteBuddyInstaller() {
    Instrumentation instrumentation = instrumentationProxy();

    assertDoesNotThrow(() -> JazzerPremainAgent.agentmain("", instrumentation));
  }

  private static Instrumentation instrumentationProxy() {
    return (Instrumentation)
        Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {Instrumentation.class},
            (proxy, method, arguments) -> defaultValue(method.getReturnType()));
  }

  private static VarHandle installerInstrumentationHandle() {
    try {
      return MethodHandles.privateLookupIn(Installer.class, MethodHandles.lookup())
          .findStaticVarHandle(Installer.class, "instrumentation", Instrumentation.class);
    } catch (ReflectiveOperationException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static Instrumentation withClearedInstallerInstrumentation(
      java.util.concurrent.Callable<Instrumentation> action) {
    Instrumentation previous = (Instrumentation) INSTALLER_INSTRUMENTATION.get();
    INSTALLER_INSTRUMENTATION.set((Instrumentation) null);
    try {
      return action.call();
    } catch (Exception exception) {
      throw new AssertionError(exception);
    } finally {
      INSTALLER_INSTRUMENTATION.set(previous);
    }
  }

  private static @Nullable Object defaultValue(Class<?> returnType) {
    if (Void.TYPE.equals(returnType)) {
      return null;
    }
    if (!returnType.isPrimitive()) {
      return returnType.isArray() ? Array.newInstance(returnType.componentType(), 0) : null;
    }
    if (Boolean.TYPE.equals(returnType)) {
      return false;
    }
    if (Character.TYPE.equals(returnType)) {
      return Character.valueOf('\0');
    }
    if (Float.TYPE.equals(returnType)) {
      return 0.0F;
    }
    if (Double.TYPE.equals(returnType)) {
      return 0.0D;
    }
    if (Long.TYPE.equals(returnType)) {
      return 0L;
    }
    return 0;
  }
}
