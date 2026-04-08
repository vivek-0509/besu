/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.evm.v2;

/**
 * Static utility operating directly on the flat {@code long[]} operand stack. Each slot occupies 4
 * consecutive longs in big-endian limb order: {@code [u3, u2, u1, u0]} where u3 is the most
 * significant limb.
 *
 * <p>All methods take {@code (long[] s, int top)} and return the new {@code top}. The caller
 * (operation) is responsible for underflow/overflow checks before calling.
 */
public class StackArithmetic {

  /** Utility class — not instantiable. */
  private StackArithmetic() {}

  /** Big-endian VarHandle for reading 8 bytes from a byte[] as a long. */
  private static final java.lang.invoke.VarHandle LONG_BE =
      java.lang.invoke.MethodHandles.byteArrayViewVarHandle(
          long[].class, java.nio.ByteOrder.BIG_ENDIAN);

  // region SHL (Shift Left)
  // ---------------------------------------------------------------------------

  /**
   * Performs EVM SHL (shift left) on the two top stack items.
   *
   * <p>Reads the shift amount (unsigned) and the value from the top two stack slots, writes {@code
   * value << shift} back into the value slot and decrements the top. Shifts >= 256 or a zero value
   * produce 0.
   *
   * @param stack the flat limb array
   * @param top current stack-top (item count)
   * @return the new stack-top after consuming one item
   */
  public static int shl(final long[] stack, final int top) {
    final int shiftOffset = (top - 1) << 2;
    final int valueOffset = (top - 2) << 2;
    // If shift amount > 255 or value is zero, result is zero
    if (stack[shiftOffset] != 0
        || stack[shiftOffset + 1] != 0
        || stack[shiftOffset + 2] != 0
        || Long.compareUnsigned(stack[shiftOffset + 3], 256) >= 0
        || (stack[valueOffset] == 0
            && stack[valueOffset + 1] == 0
            && stack[valueOffset + 2] == 0
            && stack[valueOffset + 3] == 0)) {
      stack[valueOffset] = 0;
      stack[valueOffset + 1] = 0;
      stack[valueOffset + 2] = 0;
      stack[valueOffset + 3] = 0;
      return top - 1;
    }
    int shift = (int) stack[shiftOffset + 3];
    shiftLeftInPlace(stack, valueOffset, shift);
    return top - 1;
  }

  /**
   * Left-shifts a 256-bit value in place by 1..255 bits, zero-filling from the right.
   *
   * @param stack the flat limb array
   * @param valueOffset index of the value's most-significant limb
   * @param shift number of bits to shift (must be in [1, 255])
   */
  private static void shiftLeftInPlace(final long[] stack, final int valueOffset, final int shift) {
    if (shift == 0) return;
    long w0 = stack[valueOffset],
        w1 = stack[valueOffset + 1],
        w2 = stack[valueOffset + 2],
        w3 = stack[valueOffset + 3];
    // Number of whole 64-bit words to shift (shift / 64)
    final int wordShift = shift >>> 6;
    // Remaining intra-word bit shift (shift % 64)
    final int bitShift = shift & 63;
    switch (wordShift) {
      case 0:
        w0 = shiftLeftWord(w0, w1, bitShift);
        w1 = shiftLeftWord(w1, w2, bitShift);
        w2 = shiftLeftWord(w2, w3, bitShift);
        w3 = shiftLeftWord(w3, 0, bitShift);
        break;
      case 1:
        w0 = shiftLeftWord(w1, w2, bitShift);
        w1 = shiftLeftWord(w2, w3, bitShift);
        w2 = shiftLeftWord(w3, 0, bitShift);
        w3 = 0;
        break;
      case 2:
        w0 = shiftLeftWord(w2, w3, bitShift);
        w1 = shiftLeftWord(w3, 0, bitShift);
        w2 = 0;
        w3 = 0;
        break;
      case 3:
        w0 = shiftLeftWord(w3, 0, bitShift);
        w1 = 0;
        w2 = 0;
        w3 = 0;
        break;
    }
    stack[valueOffset] = w0;
    stack[valueOffset + 1] = w1;
    stack[valueOffset + 2] = w2;
    stack[valueOffset + 3] = w3;
  }

  /**
   * Shifts a 64-bit word left and carries in bits from the next less-significant word.
   *
   * @param value the current word
   * @param nextValue the next less-significant word (bits carry in from its top)
   * @param bitShift the intra-word shift amount in [0, 63]; 0 returns {@code value} unchanged to
   *     avoid Java's mod-64 shift semantics on {@code nextValue >>> 64}
   * @return the shifted word
   */
  private static long shiftLeftWord(final long value, final long nextValue, final int bitShift) {
    if (bitShift == 0) return value;
    return (value << bitShift) | (nextValue >>> (64 - bitShift));
  }

  // endregion

  // region SHR (Shift Right)
  // ---------------------------------------------------------------------------

  /**
   * Performs EVM SHR (logical shift right) on the two top stack items.
   *
   * <p>Reads the shift amount (unsigned) and the value from the top two stack slots, writes {@code
   * value >>> shift} back into the value slot and decrements the top. Shifts >= 256 or a zero value
   * produce 0.
   *
   * @param stack the flat limb array
   * @param top current stack-top (item count)
   * @return the new stack-top after consuming one item
   */
  public static int shr(final long[] stack, final int top) {
    final int shiftOffset = (top - 1) << 2;
    final int valueOffset = (top - 2) << 2;
    if (stack[shiftOffset] != 0
        || stack[shiftOffset + 1] != 0
        || stack[shiftOffset + 2] != 0
        || Long.compareUnsigned(stack[shiftOffset + 3], 256) >= 0
        || (stack[valueOffset] == 0
            && stack[valueOffset + 1] == 0
            && stack[valueOffset + 2] == 0
            && stack[valueOffset + 3] == 0)) {
      stack[valueOffset] = 0;
      stack[valueOffset + 1] = 0;
      stack[valueOffset + 2] = 0;
      stack[valueOffset + 3] = 0;
      return top - 1;
    }
    int shift = (int) stack[shiftOffset + 3];
    shiftRightInPlace(stack, valueOffset, shift);
    return top - 1;
  }

  /**
   * Logically right-shifts a 256-bit value in place by 1..255 bits, zero-filling from the left.
   *
   * @param stack the flat limb array
   * @param valueOffset index of the value's most-significant limb
   * @param shift number of bits to shift (must be in [1, 255])
   */
  private static void shiftRightInPlace(
      final long[] stack, final int valueOffset, final int shift) {
    if (shift == 0) return;
    long w0 = stack[valueOffset],
        w1 = stack[valueOffset + 1],
        w2 = stack[valueOffset + 2],
        w3 = stack[valueOffset + 3];
    // Number of whole 64-bit words to shift (shift / 64)
    final int wordShift = shift >>> 6;
    // Remaining intra-word bit shift (shift % 64)
    final int bitShift = shift & 63;
    switch (wordShift) {
      case 0:
        w3 = shiftRightWord(w3, w2, bitShift);
        w2 = shiftRightWord(w2, w1, bitShift);
        w1 = shiftRightWord(w1, w0, bitShift);
        w0 = shiftRightWord(w0, 0, bitShift);
        break;
      case 1:
        w3 = shiftRightWord(w2, w1, bitShift);
        w2 = shiftRightWord(w1, w0, bitShift);
        w1 = shiftRightWord(w0, 0, bitShift);
        w0 = 0;
        break;
      case 2:
        w3 = shiftRightWord(w1, w0, bitShift);
        w2 = shiftRightWord(w0, 0, bitShift);
        w1 = 0;
        w0 = 0;
        break;
      case 3:
        w3 = shiftRightWord(w0, 0, bitShift);
        w2 = 0;
        w1 = 0;
        w0 = 0;
        break;
    }
    stack[valueOffset] = w0;
    stack[valueOffset + 1] = w1;
    stack[valueOffset + 2] = w2;
    stack[valueOffset + 3] = w3;
  }

  // endregion

  // region SAR (Shift Arithmetic Right)
  // ---------------------------------------------------------------------------

  /**
   * Performs EVM SAR (arithmetic shift right) on the two top stack items.
   *
   * <p>Reads the shift amount (unsigned) and the value (signed) from the top two stack slots,
   * writes {@code value >> shift} back into the value slot and decrements the top. Shifts >= 256
   * produce 0 for positive values and -1 for negative values.
   *
   * @param stack the flat limb array
   * @param top current stack-top (item count)
   * @return the new stack-top after consuming one item
   */
  public static int sar(final long[] stack, final int top) {
    final int shiftOffset = (top - 1) << 2;
    final int valueOffset = (top - 2) << 2;
    boolean negative = stack[valueOffset] < 0;
    if (stack[shiftOffset] != 0
        || stack[shiftOffset + 1] != 0
        || stack[shiftOffset + 2] != 0
        || Long.compareUnsigned(stack[shiftOffset + 3], 256) >= 0) {
      long fill = negative ? -1L : 0L;
      stack[valueOffset] = fill;
      stack[valueOffset + 1] = fill;
      stack[valueOffset + 2] = fill;
      stack[valueOffset + 3] = fill;
      return top - 1;
    }
    int shift = (int) stack[shiftOffset + 3];
    sarInPlace(stack, valueOffset, shift, negative);
    return top - 1;
  }

  /**
   * Arithmetic right-shifts a 256-bit value in place by 0..255 bits, sign-extending with {@code
   * fill}.
   *
   * @param stack the flat limb array
   * @param valueOffset index of the value's most-significant limb
   * @param shift number of bits to shift (must be in [0, 255])
   * @param negative true if the original value is negative (fill = -1)
   */
  private static void sarInPlace(
      final long[] stack, final int valueOffset, final int shift, final boolean negative) {
    if (shift == 0) return;
    long w0 = stack[valueOffset],
        w1 = stack[valueOffset + 1],
        w2 = stack[valueOffset + 2],
        w3 = stack[valueOffset + 3];
    final long fill = negative ? -1L : 0L;
    // Number of whole 64-bit words to shift (shift / 64)
    final int wordShift = shift >>> 6;
    // Remaining intra-word bit shift (shift % 64)
    final int bitShift = shift & 63;
    switch (wordShift) {
      case 0:
        w3 = shiftRightWord(w3, w2, bitShift);
        w2 = shiftRightWord(w2, w1, bitShift);
        w1 = shiftRightWord(w1, w0, bitShift);
        w0 = shiftRightWord(w0, fill, bitShift);
        break;
      case 1:
        w3 = shiftRightWord(w2, w1, bitShift);
        w2 = shiftRightWord(w1, w0, bitShift);
        w1 = shiftRightWord(w0, fill, bitShift);
        w0 = fill;
        break;
      case 2:
        w3 = shiftRightWord(w1, w0, bitShift);
        w2 = shiftRightWord(w0, fill, bitShift);
        w1 = fill;
        w0 = fill;
        break;
      case 3:
        w3 = shiftRightWord(w0, fill, bitShift);
        w2 = fill;
        w1 = fill;
        w0 = fill;
        break;
    }
    stack[valueOffset] = w0;
    stack[valueOffset + 1] = w1;
    stack[valueOffset + 2] = w2;
    stack[valueOffset + 3] = w3;
  }

  /**
   * Shifts a 64-bit word right and carries in bits from the previous more-significant word.
   *
   * <p>The {@code bitShift == 0} fast path avoids Java long-shift masking, where a shift by 64 is
   * treated as a shift by 0.
   *
   * @param value the current word
   * @param prevValue the previous more-significant word
   * @param bitShift the intra-word shift amount in the range {@code [0..63]}
   * @return the shifted word
   */
  private static long shiftRightWord(final long value, final long prevValue, final int bitShift) {
    if (bitShift == 0) return value;
    return (value >>> bitShift) | (prevValue << (64 - bitShift));
  }

  // endregion

  // region Stack Manipulation (PUSH / POP)
  // ---------------------------------------------------------------------------

  /**
   * Specialized PUSH1: reads a single byte from bytecode and pushes it as a 256-bit value. Avoids
   * the generic pushFromBytes dispatch for the most frequently executed opcode (~14% of all
   * instructions). Mirrors Geth's dedicated opPush1 function.
   *
   * @param stack the flat limb array
   * @param top current stack-top (item count)
   * @param code the raw bytecode array
   * @param pc the current program counter (pointing at the PUSH1 opcode)
   * @return the new stack-top
   */
  public static int push1(final long[] stack, final int top, final byte[] code, final int pc) {
    final int dst = top << 2;
    stack[dst] = 0;
    stack[dst + 1] = 0;
    stack[dst + 2] = 0;
    stack[dst + 3] = (pc + 1 < code.length) ? (code[pc + 1] & 0xFFL) : 0L;
    return top + 1;
  }

  /**
   * Specialized PUSH2: reads two bytes from bytecode and pushes them as a 256-bit value. The second
   * most common PUSH variant, used for jump destinations. Mirrors Geth's dedicated opPush2
   * function.
   *
   * @param stack the flat limb array
   * @param top current stack-top (item count)
   * @param code the raw bytecode array
   * @param pc the current program counter (pointing at the PUSH2 opcode)
   * @return the new stack-top
   */
  public static int push2(final long[] stack, final int top, final byte[] code, final int pc) {
    final int dst = top << 2;
    stack[dst] = 0;
    stack[dst + 1] = 0;
    stack[dst + 2] = 0;
    final int start = pc + 1;
    if (start + 1 < code.length) {
      stack[dst + 3] = (code[start] & 0xFFL) << 8 | (code[start + 1] & 0xFFL);
    } else if (start < code.length) {
      stack[dst + 3] = (code[start] & 0xFFL) << 8;
    } else {
      stack[dst + 3] = 0;
    }
    return top + 1;
  }

  /**
   * Pushes a zero-valued 256-bit word onto the stack.
   *
   * @param stack the flat limb array
   * @param top current stack-top (item count)
   * @return the new stack-top
   */
  public static int pushZero(final long[] stack, final int top) {
    final int dst = top << 2;
    stack[dst] = 0;
    stack[dst + 1] = 0;
    stack[dst + 2] = 0;
    stack[dst + 3] = 0;
    return top + 1;
  }

  /**
   * Reads {@code len} bytes from the bytecode at position {@code start} and pushes them as a
   * right-aligned (big-endian) 256-bit value onto the stack.
   *
   * <p>The fast path branches on the number of bytes to minimize work: values fitting in one limb
   * (1-8 bytes) write only limb 3, two-limb values (9-16) write limbs 2-3, and so on. A slow path
   * handles truncated bytecode at the end of the code array.
   *
   * @param stack the flat limb array
   * @param top current stack-top (item count)
   * @param code the raw bytecode array
   * @param start the index in code of the first byte to read
   * @param len the number of bytes to read (1..32)
   * @return the new stack-top
   */
  public static int pushFromBytes(
      final long[] stack, final int top, final byte[] code, final int start, final int len) {
    final int dst = top << 2;

    // Out-of-bounds: push zero
    if (start >= code.length) {
      stack[dst] = 0;
      stack[dst + 1] = 0;
      stack[dst + 2] = 0;
      stack[dst + 3] = 0;
      return top + 1;
    }
    final int copyLen = Math.min(len, code.length - start);

    if (copyLen == len) {
      // Fast path: all bytes available in the code array.
      // Each branch writes exactly the limbs it needs and zeros the rest,
      // avoiding a separate zeroing pass.
      if (len <= 8) {
        // Fits in one limb (u0)
        stack[dst] = 0;
        stack[dst + 1] = 0;
        stack[dst + 2] = 0;
        stack[dst + 3] = buildLong(code, start, len);
      } else if (len <= 16) {
        // Two limbs (u1, u0)
        final int hiLen = len - 8;
        stack[dst] = 0;
        stack[dst + 1] = 0;
        stack[dst + 2] = buildLong(code, start, hiLen);
        stack[dst + 3] = bytesToLong(code, start + hiLen);
      } else if (len <= 24) {
        // Three limbs (u2, u1, u0)
        final int hiLen = len - 16;
        stack[dst] = 0;
        stack[dst + 1] = buildLong(code, start, hiLen);
        stack[dst + 2] = bytesToLong(code, start + hiLen);
        stack[dst + 3] = bytesToLong(code, start + hiLen + 8);
      } else if (len == 32) {
        // All four limbs full — use VarHandle for all reads, no buildLong loop
        stack[dst] = bytesToLong(code, start);
        stack[dst + 1] = bytesToLong(code, start + 8);
        stack[dst + 2] = bytesToLong(code, start + 16);
        stack[dst + 3] = bytesToLong(code, start + 24);
      } else {
        // 25-31 bytes: partial first limb + three full limbs
        final int hiLen = len - 24;
        stack[dst] = buildLong(code, start, hiLen);
        stack[dst + 1] = bytesToLong(code, start + hiLen);
        stack[dst + 2] = bytesToLong(code, start + hiLen + 8);
        stack[dst + 3] = bytesToLong(code, start + hiLen + 16);
      }
    } else {
      // Slow path: bytecode is truncated (partial push at end of code).
      // Zero all limbs first, then place available bytes right-aligned
      // within the 256-bit value, padding with zeros on the right.
      stack[dst] = 0;
      stack[dst + 1] = 0;
      stack[dst + 2] = 0;
      stack[dst + 3] = 0;
      int bytePos = len - 1;
      for (int i = 0; i < copyLen; i++) {
        final int limbOffset = 3 - (bytePos >> 3);
        final int shift = (bytePos & 7) << 3;
        stack[dst + limbOffset] |= (code[start + i] & 0xFFL) << shift;
        bytePos--;
      }
    }
    return top + 1;
  }

  /**
   * Builds a {@code long} from 1..8 bytes in big-endian order. Uses a switch on length to avoid
   * loop overhead, following the same pattern as Geth's SetBytes1..SetBytes8 specializations in
   * holiman/uint256.
   *
   * @param src the source byte array
   * @param off the start offset in src
   * @param len the number of bytes (1..8)
   * @return the assembled long value
   */
  private static long buildLong(final byte[] src, final int off, final int len) {
    return switch (len) {
      case 1 -> src[off] & 0xFFL;
      case 2 -> (src[off] & 0xFFL) << 8 | (src[off + 1] & 0xFFL);
      case 3 -> (src[off] & 0xFFL) << 16 | (src[off + 1] & 0xFFL) << 8 | (src[off + 2] & 0xFFL);
      case 4 ->
          (src[off] & 0xFFL) << 24
              | (src[off + 1] & 0xFFL) << 16
              | (src[off + 2] & 0xFFL) << 8
              | (src[off + 3] & 0xFFL);
      case 5 ->
          (src[off] & 0xFFL) << 32
              | (src[off + 1] & 0xFFL) << 24
              | (src[off + 2] & 0xFFL) << 16
              | (src[off + 3] & 0xFFL) << 8
              | (src[off + 4] & 0xFFL);
      case 6 ->
          (src[off] & 0xFFL) << 40
              | (src[off + 1] & 0xFFL) << 32
              | (src[off + 2] & 0xFFL) << 24
              | (src[off + 3] & 0xFFL) << 16
              | (src[off + 4] & 0xFFL) << 8
              | (src[off + 5] & 0xFFL);
      case 7 ->
          (src[off] & 0xFFL) << 48
              | (src[off + 1] & 0xFFL) << 40
              | (src[off + 2] & 0xFFL) << 32
              | (src[off + 3] & 0xFFL) << 24
              | (src[off + 4] & 0xFFL) << 16
              | (src[off + 5] & 0xFFL) << 8
              | (src[off + 6] & 0xFFL);
      case 8 -> bytesToLong(src, off);
      default -> throw new IllegalArgumentException("buildLong len must be 1..8, got " + len);
    };
  }

  /**
   * Reads exactly 8 bytes in big-endian order as a {@code long} using a VarHandle for performance.
   *
   * @param src the source byte array
   * @param off the start offset
   * @return the 8-byte long value
   */
  private static long bytesToLong(final byte[] src, final int off) {
    return (long) LONG_BE.get(src, off);
  }

  // endregion
}
