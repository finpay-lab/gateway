package com.finpay.gateway.infrastructure.grpc;

import com.finpay.ledger.v1.Balance;
import com.finpay.ledger.v1.GetAccountBalanceRequest;
import com.finpay.ledger.v1.LedgerEntry;
import com.finpay.ledger.v1.LedgerServiceGrpc;
import com.finpay.ledger.v1.PostEntriesRequest;
import com.finpay.ledger.v1.PostEntriesResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** gRPC client: gateway -> ledger-service (ADR-0014). */
@Component
public class LedgerGrpcClient {

    private final ManagedChannel channel;
    private final LedgerServiceGrpc.LedgerServiceBlockingStub stub;

    public LedgerGrpcClient(@Value("${finpay.grpc.ledger-service-address:ledger-service.finpay.svc.cluster.local:9093}") String address) {
        this.channel = ManagedChannelBuilder.forTarget(address)
                .defaultLoadBalancingPolicy("round_robin").usePlaintext().build();
        this.stub = LedgerServiceGrpc.newBlockingStub(channel);
    }

    public PostEntriesResponse postEntries(String transferId, List<LedgerEntry> entries) {
        return stub.postEntries(PostEntriesRequest.newBuilder().setTransferId(transferId).addAllEntries(entries).build());
    }

    public Balance getAccountBalance(String accountId) {
        return stub.getAccountBalance(GetAccountBalanceRequest.newBuilder().setAccountId(accountId).build());
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
