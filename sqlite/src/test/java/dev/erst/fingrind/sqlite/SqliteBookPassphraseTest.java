package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

/** Tests for {@link SqliteBookPassphrase}. */
class SqliteBookPassphraseTest {
  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void normalizeSourceDescription_trimsAndRejectsBlankSourceDescriptions() throws Exception {
    Method method =
        SqliteBookPassphrase.class.getDeclaredMethod("normalizeSourceDescription", String.class);
    method.setAccessible(true);

    assertEquals("secret source", method.invoke(null, "  secret source  "));

    InvocationTargetException exception =
        assertThrows(InvocationTargetException.class, () -> method.invoke(null, "   "));

    assertEquals("sourceDescription must not be blank.", exception.getCause().getMessage());
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void zeroize_overwritesArrayBackedBuffers() throws Exception {
    ByteBuffer heapBytes = ByteBuffer.wrap(new byte[] {7, 8, 9, 10});

    Method method = SqliteBookPassphrase.class.getDeclaredMethod("zeroize", ByteBuffer.class);
    method.setAccessible(true);
    method.invoke(null, heapBytes);

    assertArrayEquals(new byte[4], heapBytes.array());
  }

  @Test
  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  void zeroize_overwritesDirectBuffers() throws Exception {
    ByteBuffer directBytes = ByteBuffer.allocateDirect(4);
    directBytes.put(0, (byte) 7);
    directBytes.put(1, (byte) 8);
    directBytes.put(2, (byte) 9);
    directBytes.put(3, (byte) 10);

    Method method = SqliteBookPassphrase.class.getDeclaredMethod("zeroize", ByteBuffer.class);
    method.setAccessible(true);
    method.invoke(null, directBytes);

    byte[] actual = new byte[4];
    for (int index = 0; index < actual.length; index++) {
      actual[index] = directBytes.get(index);
    }
    assertArrayEquals(new byte[4], actual);
  }
}
