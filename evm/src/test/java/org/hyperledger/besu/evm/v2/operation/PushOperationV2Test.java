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
package org.hyperledger.besu.evm.v2.operation;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.v2.testutils.TestMessageFrameBuilderV2;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class PushOperationV2Test {

  private final GasCalculator gasCalculator = new SpuriousDragonGasCalculator();
  private final PushOperationV2 operation = new PushOperationV2(1, gasCalculator);

  @Test
  void push1ViaExecute() {
    // Test through the instance execute() path, matching the pattern from ShlOperationV2Test
    final byte[] codeBytes = new byte[] {0x60, (byte) 0xAB};
    final MessageFrame frame =
        new TestMessageFrameBuilderV2().code(new Code(Bytes.wrap(codeBytes))).build();

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(3L);
    assertThat(frame.stackTopV2()).isEqualTo(1);
    assertThat(getV2StackItem(frame, 0)).isEqualTo(new UInt256(0, 0, 0, 0xABL));
  }

  @Test
  void push1SingleByte() {
    // PUSH1 0xAB — bytecode: [0x60, 0xAB]
    final byte[] code = new byte[] {0x60, (byte) 0xAB};
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();

    final OperationResult result =
        PushOperationV2.staticOperation(frame, frame.stackDataV2(), code, 0, 1);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(3L);
    assertThat(frame.stackTopV2()).isEqualTo(1);
    assertThat(getV2StackItem(frame, 0)).isEqualTo(new UInt256(0, 0, 0, 0xABL));
    assertThat(frame.getPC()).isEqualTo(1); // pc advanced past immediate byte
  }

  @Test
  void push8FullLimb() {
    // PUSH8 0x0102030405060708 — fills exactly one limb
    final byte[] code = new byte[] {0x67, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();

    final OperationResult result =
        PushOperationV2.staticOperation(frame, frame.stackDataV2(), code, 0, 8);

    assertThat(result.getHaltReason()).isNull();
    assertThat(frame.stackTopV2()).isEqualTo(1);
    assertThat(getV2StackItem(frame, 0)).isEqualTo(new UInt256(0, 0, 0, 0x0102030405060708L));
  }

  @Test
  void push16TwoLimbs() {
    // PUSH16 with 16 bytes → fills limbs 2 and 3
    final byte[] code = new byte[17];
    code[0] = 0x6f; // PUSH16 opcode
    for (int i = 1; i <= 16; i++) {
      code[i] = (byte) i;
    }
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();

    final OperationResult result =
        PushOperationV2.staticOperation(frame, frame.stackDataV2(), code, 0, 16);

    assertThat(result.getHaltReason()).isNull();
    assertThat(frame.stackTopV2()).isEqualTo(1);
    final UInt256 item = getV2StackItem(frame, 0);
    assertThat(item.u3()).isEqualTo(0L);
    assertThat(item.u2()).isEqualTo(0L);
    assertThat(item.u1()).isEqualTo(0x0102030405060708L);
    assertThat(item.u0()).isEqualTo(0x090a0b0c0d0e0f10L);
  }

  @Test
  void push32FourLimbs() {
    // PUSH32 with 32 bytes → fills all 4 limbs
    final byte[] code = new byte[33];
    code[0] = 0x7f; // PUSH32 opcode
    for (int i = 1; i <= 32; i++) {
      code[i] = (byte) i;
    }
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();

    final OperationResult result =
        PushOperationV2.staticOperation(frame, frame.stackDataV2(), code, 0, 32);

    assertThat(result.getHaltReason()).isNull();
    assertThat(frame.stackTopV2()).isEqualTo(1);
    final UInt256 item = getV2StackItem(frame, 0);
    assertThat(item.u3()).isEqualTo(0x0102030405060708L);
    assertThat(item.u2()).isEqualTo(0x090a0b0c0d0e0f10L);
    assertThat(item.u1()).isEqualTo(0x1112131415161718L);
    assertThat(item.u0()).isEqualTo(0x191a1b1c1d1e1f20L);
  }

  @Test
  void push2TruncatedBytecodeRightPads() {
    // PUSH2 but only 1 byte available after opcode → right-padded with zero
    final byte[] code = new byte[] {0x61, (byte) 0xFF};
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();

    final OperationResult result =
        PushOperationV2.staticOperation(frame, frame.stackDataV2(), code, 0, 2);

    assertThat(result.getHaltReason()).isNull();
    assertThat(frame.stackTopV2()).isEqualTo(1);
    // 0xFF with 1 byte padding → 0xFF00
    assertThat(getV2StackItem(frame, 0)).isEqualTo(new UInt256(0, 0, 0, 0xFF00L));
  }

  @Test
  void pushBeyondCodeLengthPushesZero() {
    // Code is just the opcode, no immediate bytes
    final byte[] code = new byte[] {0x60};
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();

    final OperationResult result =
        PushOperationV2.staticOperation(frame, frame.stackDataV2(), code, 0, 1);

    assertThat(result.getHaltReason()).isNull();
    assertThat(frame.stackTopV2()).isEqualTo(1);
    assertThat(getV2StackItem(frame, 0)).isEqualTo(UInt256.ZERO);
  }

  @Test
  void pcAdvancedByPushSize() {
    final byte[] code = new byte[] {0x63, 0x01, 0x02, 0x03, 0x04}; // PUSH4
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();

    PushOperationV2.staticOperation(frame, frame.stackDataV2(), code, 0, 4);

    // PC should be set to pushSize (4), the dispatch loop adds +1 for the opcode
    assertThat(frame.getPC()).isEqualTo(4);
  }

  @Test
  void push24ThreeLimbs() {
    // PUSH24 with 24 bytes → fills limbs 1, 2, and 3
    final byte[] code = new byte[25];
    code[0] = 0x77; // PUSH24 opcode
    for (int i = 1; i <= 24; i++) {
      code[i] = (byte) i;
    }
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();

    final OperationResult result =
        PushOperationV2.staticOperation(frame, frame.stackDataV2(), code, 0, 24);

    assertThat(result.getHaltReason()).isNull();
    final UInt256 item = getV2StackItem(frame, 0);
    assertThat(item.u3()).isEqualTo(0L);
    assertThat(item.u2()).isEqualTo(0x0102030405060708L);
    assertThat(item.u1()).isEqualTo(0x090a0b0c0d0e0f10L);
    assertThat(item.u0()).isEqualTo(0x1112131415161718L);
  }

  @Test
  void dryRunDetector() {
    assertThat(true)
        .withFailMessage("This test is here so gradle --dry-run executes this class")
        .isTrue();
  }

  private static UInt256 getV2StackItem(final MessageFrame frame, final int offset) {
    final long[] s = frame.stackDataV2();
    final int idx = (frame.stackTopV2() - 1 - offset) << 2;
    return new UInt256(s[idx], s[idx + 1], s[idx + 2], s[idx + 3]);
  }
}
