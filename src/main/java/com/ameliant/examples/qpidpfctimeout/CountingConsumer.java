package com.ameliant.examples.qpidpfctimeout;

import javax.jms.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by jkorab on 26/06/17.
 */
class CountingConsumer extends ShutdownLatchedConsumer {
    private final AtomicInteger counter = new AtomicInteger();

    CountingConsumer(Connection connection, CountDownLatch shutdownLatch, String queueName) {
        super(connection, shutdownLatch, queueName);
    }

    @Override
    public void run() {
        listenUntilShutdown(message -> {
            counter.incrementAndGet();
        });
    }

    int getReceivedCount() {
        return counter.get();
    }
}
