package com.ameliant.examples.qpidpfctimeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.util.concurrent.CountDownLatch;

/**
 * Created by jkorab on 26/06/17.
 */
abstract class ShutdownLatchedConsumer implements Runnable {

    private final Connection connection;
    private final CountDownLatch shutdownLatch;
    private final String destinationName;

    protected Logger log = LoggerFactory.getLogger(this.getClass());

    public ShutdownLatchedConsumer(Connection connection, CountDownLatch shutdownLatch, String destinationName) {
        this.connection = connection;
        this.shutdownLatch = shutdownLatch;
        this.destinationName = destinationName;
    }

    protected void listenUntilShutdown(MessageListener listener) {
        try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {

            Destination destination;
            if (destinationName.startsWith("topic:")) {
                destination = session.createTopic(destinationName.replaceFirst("topic:", ""));
            } else {
                destination = session.createQueue(destinationName.replaceFirst("queue:", ""));
            }

            log.info("Creating consumer on {}", destinationName);

            try (MessageConsumer consumer = session.createConsumer(destination)) {
                consumer.setMessageListener(listener);
                shutdownLatch.await();
                log.info("Shutting down");
            } catch (InterruptedException e) {
                log.warn("Latch interrupted, shutting down");
            }
        } catch (JMSException ex) {
            log.error("Caught JMSException", ex);
        }
    }

}
