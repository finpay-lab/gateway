package com.finpay.gateway.infrastructure.grpc;

import com.finpay.identity.v1.GetPrincipalRequest;
import com.finpay.identity.v1.IdentityServiceGrpc;
import com.finpay.identity.v1.ListRolesRequest;
import com.finpay.identity.v1.Principal;
import com.finpay.identity.v1.RoleList;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/** gRPC client: gateway -> identity-service (ADR-0014). */
@Component
public class IdentityGrpcClient {

    private final ManagedChannel channel;
    private final IdentityServiceGrpc.IdentityServiceBlockingStub stub;

    public IdentityGrpcClient(@Value("${finpay.grpc.identity-service-address:identity-service.finpay.svc.cluster.local:9091}") String address) {
        this.channel = ManagedChannelBuilder.forTarget(address)
                .defaultLoadBalancingPolicy("round_robin").usePlaintext().build();
        this.stub = IdentityServiceGrpc.newBlockingStub(channel);
    }

    public Principal getPrincipal(String principalId) {
        return stub.getPrincipal(GetPrincipalRequest.newBuilder().setPrincipalId(principalId).build());
    }

    public RoleList listRoles(String principalId) {
        return stub.listRoles(ListRolesRequest.newBuilder().setPrincipalId(principalId).build());
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
