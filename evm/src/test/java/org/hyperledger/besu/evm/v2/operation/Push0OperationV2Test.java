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

import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;
import org.hyperledger.besu.evm.v2.testutils.TestMessageFrameBuilderV2;

import org.junit.jupiter.api.Test;

class Push0OperationV2Test {

  private final GasCalculator gasCalculator = new SpuriousDragonGasCalculator();
  private final Push0OperationV2 operation = new Push0OperationV2(gasCalculator);

  @Test
  void push0PushesZeroOntoStack() {
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();
    assertThat(frame.stackTopV2()).isEqualTo(0);

    final OperationResult result = operation.execute(frame, null);

    assertThat(result.getHaltReason()).isNull();
    assertThat(result.getGasCost()).isEqualTo(2L);
    assertThat(frame.stackTopV2()).isEqualTo(1);
    assertThat(getV2StackItem(frame, 0)).isEqualTo(UInt256.ZERO);
  }

  @Test
  void push0AllLimbsAreZero() {
    final MessageFrame frame = new TestMessageFrameBuilderV2().build();

    Push0OperationV2.staticOperation(frame, frame.stackDataV2());

    final long[] s = frame.stackDataV2();
    assertThat(s[0]).isEqualTo(0L);
    assertThat(s[1]).isEqualTo(0L);
    assertThat(s[2]).isEqualTo(0L);
    assertThat(s[3]).isEqualTo(0L);
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
