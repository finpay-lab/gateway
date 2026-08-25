package com.finpay.gateway.infrastructure.grpc;

import com.finpay.transfer.v1.CreateTransferRequest;
import com.finpay.transfer.v1.GetTransferRequest;
import com.finpay.transfer.v1.Transfer;
import com.finpay.transfer.v1.TransferServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/** gRPC client: gateway -> transfer-service (ADR-0014). */
@Component
public class TransferGrpcClient {

    private final ManagedChannel channel;
    private final TransferServiceGrpc.TransferServiceBlockingStub stub;

    public TransferGrpcClient(@Value("${finpay.grpc.transfer-service-address:transfer-service.finpay.svc.cluster.local:9094}") String address) {
        this.channel = ManagedChannelBuilder.forTarget(address)
                .defaultLoadBalancingPolicy("round_robin").usePlaintext().build();
        this.stub = TransferServiceGrpc.newBlockingStub(channel);
    }

    public Transfer createTransfer(CreateTransferRequest request) {
        return stub.createTransfer(request);
    }

    public Transfer getTransfer(String transferId) {
        return stub.getTransfer(GetTransferRequest.newBuilder().setTransferId(transferId).build());
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
