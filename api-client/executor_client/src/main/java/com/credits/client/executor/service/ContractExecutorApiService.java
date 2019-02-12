package com.credits.client.executor.service;

import com.credits.client.executor.exception.ContractExecutorClientException;
import com.credits.general.pojo.VariantData;
import com.credits.general.thrift.generated.ByteCodeObject;
import com.credits.general.thrift.generated.ExecuteByteCodeResult;

import java.util.List;

/**
 * Created by Igor Goryunov on 18.10.2018
 */
public interface ContractExecutorApiService {

    ExecuteByteCodeResult executeContractMethod(long accessId, byte[] initiatorAddress, byte[] contractAddress, List<ByteCodeObject> byteCodeObjects, byte[] objectState, String methodName, List<VariantData> params, long executionTime)
        throws ContractExecutorClientException;
}
