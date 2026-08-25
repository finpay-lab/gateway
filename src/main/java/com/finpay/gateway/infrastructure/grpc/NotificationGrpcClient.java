package com.finpay.gateway.infrastructure.grpc;

import com.finpay.notification.v1.NotificationServiceGrpc;
import com.finpay.notification.v1.SendNotificationRequest;
import com.finpay.notification.v1.SendNotificationResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** gRPC client: gateway -> notification-service (ADR-0014). */
@Component
public class NotificationGrpcClient {

    private final ManagedChannel channel;
    private final NotificationServiceGrpc.NotificationServiceBlockingStub stub;

    public NotificationGrpcClient(@Value("${finpay.grpc.notification-service-address:notification-service.finpay.svc.cluster.local:9095}") String address) {
        this.channel = ManagedChannelBuilder.forTarget(address)
                .defaultLoadBalancingPolicy("round_robin").usePlaintext().build();
        this.stub = NotificationServiceGrpc.newBlockingStub(channel);
    }

    public SendNotificationResponse send(String channel, String recipient, String template, String subject, String body) {
        return stub.sendNotification(SendNotificationRequest.newBuilder()
                .setChannel(channel).setRecipient(recipient).setTemplate(template)
                .setSubject(subject).setBody(body)
                .setIdempotency(com.finpay.common.v1.IdempotencyKey.newBuilder()
                        .setKey(UUID.randomUUID().toString()).build())
                .build());
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
