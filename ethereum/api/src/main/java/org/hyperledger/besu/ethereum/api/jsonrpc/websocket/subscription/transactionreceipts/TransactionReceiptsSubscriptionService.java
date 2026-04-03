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
package org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.transactionreceipts;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptListResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptRootResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.TransactionReceiptStatusResult;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.SubscriptionManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.subscription.request.SubscriptionType;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionReceiptWithMetadata;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TransactionReceiptType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Suppliers;

public class TransactionReceiptsSubscriptionService implements BlockAddedObserver {

  private final SubscriptionManager subscriptionManager;
  private final BlockchainQueries blockchainQueries;
  private final ProtocolSchedule protocolSchedule;

  public TransactionReceiptsSubscriptionService(
      final SubscriptionManager subscriptionManager,
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule) {
    this.subscriptionManager = subscriptionManager;
    this.blockchainQueries = blockchainQueries;
    this.protocolSchedule = protocolSchedule;
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    if (event.isNewCanonicalHead()) {
      final List<Hash> blockHashes = new ArrayList<>();
      BlockHeader blockPtr = event.getHeader();

      while (!blockPtr.getHash().equals(event.getCommonAncestorHash())) {
        blockHashes.add(blockPtr.getHash());
        blockPtr =
            blockchainQueries
                .getBlockchain()
                .getBlockHeader(blockPtr.getParentHash())
                .orElseThrow(
                    () -> new IllegalStateException("The block was on an orphaned chain."));
      }

      Collections.reverse(blockHashes);
      blockHashes.forEach(this::notifySubscribers);
    }
  }

  private void notifySubscribers(final Hash blockHash) {
    subscriptionManager.notifySubscribersOnWorkerThread(
        SubscriptionType.TRANSACTION_RECEIPTS,
        TransactionReceiptsSubscription.class,
        subscribers -> {
          final var receiptList = Suppliers.memoize(() -> buildReceiptResults(blockHash));

          for (final TransactionReceiptsSubscription subscription : subscribers) {
            final List<TransactionReceiptResult> allReceipts = receiptList.get();
            if (allReceipts == null || allReceipts.isEmpty()) {
              continue;
            }

            final List<TransactionReceiptResult> filtered;
            if (subscription.hasFilter()) {
              filtered =
                  allReceipts.stream()
                      .filter(
                          r ->
                              subscription
                                  .getTransactionHashesFilter()
                                  .contains(Hash.fromHexString(r.getTransactionHash())))
                      .toList();
              if (filtered.isEmpty()) {
                continue;
              }
            } else {
              filtered = allReceipts;
            }

            subscriptionManager.sendMessage(
                subscription.getSubscriptionId(), new TransactionReceiptListResult(filtered));
          }
        });
  }

  private List<TransactionReceiptResult> buildReceiptResults(final Hash blockHash) {
    final Optional<Block> maybeBlock = blockchainQueries.getBlockchain().getBlockByHash(blockHash);
    if (maybeBlock.isEmpty()) {
      return null;
    }

    final Block block = maybeBlock.get();
    final BlockHeader header = block.getHeader();
    final List<Transaction> transactions = block.getBody().getTransactions();
    final Optional<List<TransactionReceipt>> maybeReceipts =
        blockchainQueries.getBlockchain().getTxReceipts(blockHash);
    if (maybeReceipts.isEmpty()) {
      return null;
    }

    final List<TransactionReceipt> receipts = maybeReceipts.get();
    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(header);
    final List<TransactionReceiptResult> results = new ArrayList<>(receipts.size());

    int logIndexOffset = 0;
    for (int i = 0; i < receipts.size(); i++) {
      final TransactionReceipt receipt = receipts.get(i);
      final Transaction transaction = transactions.get(i);

      long gasUsed = receipt.getCumulativeGasUsed();
      if (i > 0) {
        gasUsed -= receipts.get(i - 1).getCumulativeGasUsed();
      }

      final Optional<Long> blobGasUsed =
          transaction.getType().supportsBlob()
              ? Optional.of(protocolSpec.getGasCalculator().blobGasCost(transaction.getBlobCount()))
              : Optional.empty();

      final Optional<Wei> blobGasPrice =
          transaction.getType().supportsBlob()
              ? header
                  .getExcessBlobGas()
                  .map(
                      excessBlobGas ->
                          protocolSpec.getFeeMarket().blobGasPricePerGas(excessBlobGas))
              : Optional.empty();

      final TransactionReceiptWithMetadata receiptWithMetadata =
          TransactionReceiptWithMetadata.create(
              receipt,
              transaction,
              transaction.getHash(),
              i,
              gasUsed,
              header.getBaseFee(),
              blockHash,
              header.getTimestamp(),
              header.getNumber(),
              blobGasUsed,
              blobGasPrice,
              logIndexOffset);

      if (receipt.getTransactionReceiptType() == TransactionReceiptType.ROOT) {
        results.add(new TransactionReceiptRootResult(receiptWithMetadata));
      } else {
        results.add(new TransactionReceiptStatusResult(receiptWithMetadata));
      }

      logIndexOffset += receipt.getLogsList().size();
    }

    return results;
  }
}
