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
package org.hyperledger.besu.ethereum.vm.operations.v2;

import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.PushOperation;
import org.hyperledger.besu.evm.v2.operation.PushOperationV2;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmark comparing v1 and v2 PUSH operations.
 *
 * <p>Each iteration pushes a value from random bytecode onto the stack, then resets the stack
 * pointer to avoid overflow. Parameterized by push size to cover different limb-filling code paths.
 */
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class PushOperationBenchmarkV2 {

  private static final int SAMPLE_SIZE = 30_000;

  @Param({"1", "2", "8", "16", "32"})
  private int pushSize;

  private byte[][] codePool;
  private int index;

  private MessageFrame v1Frame;
  private MessageFrame v2Frame;

  @Setup(Level.Iteration)
  public void setUp() {
    v1Frame = createFrame(false);
    v2Frame = createFrame(true);

    final Random random = new Random();
    codePool = new byte[SAMPLE_SIZE][];
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      // bytecode: opcode byte + pushSize random immediate bytes
      final byte[] code = new byte[1 + pushSize];
      code[0] = (byte) (0x5F + pushSize); // PUSH opcode
      for (int j = 1; j <= pushSize; j++) {
        code[j] = (byte) random.nextInt(256);
      }
      codePool[i] = code;
    }
    index = 0;
  }

  @Benchmark
  public void v1Push(final Blackhole blackhole) {
    final byte[] code = codePool[index];
    blackhole.consume(PushOperation.staticOperation(v1Frame, code, 0, pushSize));
    v1Frame.popStackItem();
    index = (index + 1) % SAMPLE_SIZE;
  }

  @Benchmark
  public void v2Push(final Blackhole blackhole) {
    final byte[] code = codePool[index];
    // Use specialized paths for PUSH1/PUSH2, matching the EVM dispatch
    blackhole.consume(
        switch (pushSize) {
          case 1 -> PushOperationV2.staticPush1(v2Frame, v2Frame.stackDataV2(), code, 0);
          case 2 -> PushOperationV2.staticPush2(v2Frame, v2Frame.stackDataV2(), code, 0);
          default ->
              PushOperationV2.staticOperation(v2Frame, v2Frame.stackDataV2(), code, 0, pushSize);
        });
    v2Frame.setTopV2(v2Frame.stackTopV2() - 1); // reset stack
    v2Frame.setPC(0); // reset PC
    index = (index + 1) % SAMPLE_SIZE;
  }

  private static MessageFrame createFrame(final boolean enableV2) {
    return MessageFrame.builder()
        .enableEvmV2(enableV2)
        .worldUpdater(mock(WorldUpdater.class))
        .originator(Address.ZERO)
        .gasPrice(Wei.ONE)
        .blobGasPrice(Wei.ONE)
        .blockValues(mock(BlockValues.class))
        .miningBeneficiary(Address.ZERO)
        .blockHashLookup((__, ___) -> Hash.ZERO)
        .type(MessageFrame.Type.MESSAGE_CALL)
        .initialGas(Long.MAX_VALUE)
        .address(Address.ZERO)
        .contract(Address.ZERO)
        .inputData(Bytes.EMPTY)
        .sender(Address.ZERO)
        .value(Wei.ZERO)
        .apparentValue(Wei.ZERO)
        .code(Code.EMPTY_CODE)
        .completer(__ -> {})
        .build();
  }
}
