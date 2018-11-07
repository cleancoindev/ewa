package com.credits.wallet.desktop.utils;

import com.credits.client.node.exception.NodeClientException;
import com.credits.client.node.pojo.SmartContractInvocationData;
import com.credits.client.node.pojo.SmartContractTransactionFlowData;
import com.credits.client.node.pojo.TransactionFlowData;
import com.credits.client.node.util.NodePojoConverter;
import com.credits.general.pojo.ApiResponseData;
import com.credits.general.pojo.SmartContractData;
import com.credits.general.pojo.TransactionRoundData;
import com.credits.general.util.Utils;
import com.credits.general.util.exception.ConverterException;
import com.credits.wallet.desktop.AppState;
import com.credits.wallet.desktop.utils.struct.CalcTransactionIdSourceTargetResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.credits.client.node.util.NodeClientUtils.serializeByThrift;
import static com.credits.general.util.Converter.decodeFromBASE58;
import static com.credits.general.util.Converter.encodeToBASE58;
import static com.credits.general.util.Converter.toBitSet;
import static com.credits.general.util.Converter.toByteArray;
import static com.credits.general.util.Converter.toByteArrayLittleEndian;
import static com.credits.general.util.Converter.toLong;
import static com.credits.wallet.desktop.AppState.account;
import static com.credits.wallet.desktop.AppState.nodeApiService;
import static com.credits.wallet.desktop.AppState.transactionOfferedMaxFeeValue;
import static com.credits.wallet.desktop.AppState.walletLastTransactionIdCache;
import static java.math.BigDecimal.ZERO;

/**
 * Created by Rustem Saidaliyev on 20-Mar-18.
 */
public class ApiUtils {

    private final static Logger LOGGER = LoggerFactory.getLogger(ApiUtils.class);

    public static ApiResponseData createTransaction(String targetBase58, BigDecimal amount)
        throws NodeClientException, ConverterException {
        return nodeApiService.transactionFlow(getTransactionFlowData(targetBase58, amount, null));
    }

    public static ApiResponseData createSmartContractTransaction(SmartContractData smartContractData)
        throws NodeClientException, ConverterException {

        SmartContractInvocationData smartContractInvocationData =
            new SmartContractInvocationData(smartContractData.getSourceCode(), smartContractData.getByteCode(),
                smartContractData.getHashState(), smartContractData.getMethod(), smartContractData.getParams(), false);

        SmartContractTransactionFlowData scData = new SmartContractTransactionFlowData(
            getTransactionFlowData(smartContractData.getBase58Address(), ZERO,
                serializeByThrift(smartContractInvocationData)), smartContractInvocationData);

        return nodeApiService.smartContractTransactionFlow(scData);
    }

    private static TransactionFlowData getTransactionFlowData(String targetBase58, BigDecimal amount,
        byte[] smartContractBytes) {
        CalcTransactionIdSourceTargetResult transactionData =
            ApiUtils.calcTransactionIdSourceTarget(account, targetBase58);

        long id = transactionData.getTransactionId();
        byte[] source = transactionData.getByteSource();
        byte[] target = transactionData.getByteTarget();
        short offeredMaxFee = transactionOfferedMaxFeeValue;
        byte currency = 1;

        saveTransactionIntoMap(id, account, targetBase58, amount.toString(),
            String.valueOf(currency));

        TransactionFlowData transactionFlowData =
            new TransactionFlowData(id, source, target, amount, offeredMaxFee, currency, smartContractBytes);
        SignUtils.signTransaction(transactionFlowData);
        return transactionFlowData;
    }

    private static void saveTransactionIntoMap(long id, String source, String target, String amount, String currency) {
        Utils.sourceMap.computeIfAbsent(AppState.account, key -> new ConcurrentHashMap<>());
        Map<Long, TransactionRoundData> sourceMap = Utils.sourceMap.get(AppState.account);
        long shortTransactionId = NodePojoConverter.getShortTransactionId(id);
        TransactionRoundData transactionRoundData =
            new TransactionRoundData(String.valueOf(shortTransactionId), source, target, amount, currency);
        sourceMap.put(shortTransactionId, transactionRoundData);
    }

    private static long createTransactionId(boolean senderIndexExists, boolean receiverIndexExists, long transactionId)
        throws ConverterException {

        byte[] transactionIdBytes = toByteArray(transactionId);
        BitSet transactionIdBitSet = toBitSet(transactionIdBytes);
        for (int i = 63; i > 45; i--) {
            transactionIdBitSet.set(i, false);
        }
        transactionIdBitSet.set(47, senderIndexExists);
        transactionIdBitSet.set(46, receiverIndexExists);
        return toLong(transactionIdBitSet);
    }

    private static CalcTransactionIdSourceTargetResult calcTransactionIdSourceTarget(String sourceBase58,
        String targetBase58) throws NodeClientException, ConverterException {

        // get transactions count from Node and increment it
        Long transactionId = nodeApiService.getWalletTransactionsCount(sourceBase58) + 1;
        // get last transaction id from cache
        AtomicLong lastTransactionId = walletLastTransactionIdCache.get(sourceBase58);

        if(lastTransactionId==null || transactionId > lastTransactionId.get()) {
            walletLastTransactionIdCache.put(sourceBase58,new AtomicLong(transactionId));
        } else {
            transactionId = lastTransactionId.incrementAndGet();
        }

        LOGGER.info("transaction ID = {}",transactionId);

        boolean sourceIndexExists = false;
        boolean targetIndexExists = false;

        Integer sourceWalletId = nodeApiService.getWalletId(sourceBase58);
        if (sourceWalletId != 0) {
            sourceIndexExists = true;
            sourceBase58 = encodeToBASE58(toByteArrayLittleEndian(sourceWalletId, 4));
        }
        Integer targetWalletId = nodeApiService.getWalletId(targetBase58);
        if (targetWalletId != 0) {
            targetIndexExists = true;
            targetBase58 = encodeToBASE58(toByteArrayLittleEndian(targetWalletId, 4));
        }

        return new CalcTransactionIdSourceTargetResult(
            ApiUtils.createTransactionId(sourceIndexExists, targetIndexExists, transactionId), sourceBase58,
            targetBase58);
    }

    public static ApiResponseData deploySmartContractProcess(String javaCode, byte[] byteCode,
        byte[] smartContractAddress) throws NodeClientException, ConverterException {
        SmartContractData scData =
            new SmartContractData(smartContractAddress, decodeFromBASE58(account), javaCode, byteCode, null);
        return createSmartContractTransaction(scData);
    }


}