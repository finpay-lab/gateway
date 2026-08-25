package com.finpay.gateway.infrastructure.grpc;

import com.finpay.customer.v1.Customer;
import com.finpay.customer.v1.CustomerServiceGrpc;
import com.finpay.customer.v1.GetCustomerRequest;
import com.finpay.customer.v1.ValidationResult;
import com.finpay.customer.v1.ValidateCustomerRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/** gRPC client: gateway -> customer-service (ADR-0014). */
@Component
public class CustomerGrpcClient {

    private final ManagedChannel channel;
    private final CustomerServiceGrpc.CustomerServiceBlockingStub stub;

    public CustomerGrpcClient(@Value("${finpay.grpc.customer-service-address:customer-service.finpay.svc.cluster.local:9092}") String address) {
        this.channel = ManagedChannelBuilder.forTarget(address)
                .defaultLoadBalancingPolicy("round_robin").usePlaintext().build();
        this.stub = CustomerServiceGrpc.newBlockingStub(channel);
    }

    public Customer getCustomer(String customerId) {
        return stub.getCustomer(GetCustomerRequest.newBuilder().setCustomerId(customerId).build());
    }

    public ValidationResult validateCustomer(String customerId) {
        return stub.validateCustomer(ValidateCustomerRequest.newBuilder().setCustomerId(customerId).build());
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
