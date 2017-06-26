package com.ameliant.examples.qpidpfctimeout;

import javax.jms.*;
import java.util.concurrent.CountDownLatch;

/**
 * Triggers a latch when an advisory message is received for an expired queue message.
 */
public class TtlExpirationConsumer extends ShutdownLatchedConsumer {

    private CountDownLatch messageExpiredLatch;

    /**
     * @param connection A started connection.
     * @param shutdownLatch A latch that this notifier waits on for a shutdown signal.
     * @param messageExpiredLatch A latch to trigger when an expired message advisory is received.
     */
    public TtlExpirationConsumer(Connection connection, CountDownLatch shutdownLatch, CountDownLatch messageExpiredLatch) {
        super(connection, shutdownLatch, "topic:ActiveMQ.Advisory.Expired.Queue.>");
        this.messageExpiredLatch = messageExpiredLatch;
    }

    @Override
    public void run() {
        listenUntilShutdown(message -> {
            log.info("Captured expired message");
            messageExpiredLatch.countDown();
        });
    }

}
