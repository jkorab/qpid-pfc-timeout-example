package com.ameliant.examples.qpidpfctimeout;

import com.ameliant.examples.qpidpfctimeout.embedded.EmbeddedBroker;
import org.apache.activemq.broker.jmx.QueueView;
import org.apache.activemq.broker.jmx.QueueViewMBean;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;

import java.lang.management.ManagementFactory;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author jkorab
 */
public class PfcTimeoutTest {

    public static final String BROKER_URL = String.format("failover:(amqp://localhost:%d?" +
            "amqp.idleTimeout=25000&amqp.maxFrameSize=1048576)" +
            "?jms.connectTimeout=15000" +
            //"&jms.sendTimeout=15000" +
            //"&jms.prefetchPolicy=1000" +
            //"&jms.maxRedeliveries=-1" +
            //"&jms.localMessageExpiry=true" +
            //"&jms.localMessagePriority=false" +
            //"&jms.closeTimeout=15000" +
            "&failover.randomize=true" +
            "&failover.initialReconnectDelay=0" +
            "&failover.reconnectDelay=10" +
            "&failover.maxReconnectDelay=30000" +
            "&failover.useReconnectBackOff=true" +
            "&failover.reconnectBackOffMultiplier=2.0" +
            "&failover.maxReconnectAttempts=-1" +
            "&failover.startupMaxReconnectAttempts=-1" +
            "&failover.warnAfterReconnectAttempts=10", EmbeddedBroker.AMQP_PORT);
    public static final String FOO = "foo";
    public static final int PAYLOAD_SIZE = 1_000_000;

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Rule
    public EmbeddedBroker broker = new EmbeddedBroker( 2* PAYLOAD_SIZE,
            30_000);

    @Test
    public void testTimeoutBehaviour() {
        CountDownLatch messageExpiredLatch = new CountDownLatch(1);
        CountDownLatch shutdownLatch = new CountDownLatch(1);

        // set memory limit for queue, messages in storage
        // fire in a few messages, one that gets expired
        // wait for TTL expiry
        // send in another message

        ConnectionFactory connectionFactory = new JmsConnectionFactory(BROKER_URL);
        try (Connection connection = connectionFactory.createConnection()) {
            connection.setExceptionListener(ex ->
                log.info("Caught exception on connection: {}", ex)
            );
            connection.start();

            {
                // set up test consumers
                Executor executor = Executors.newFixedThreadPool(2);
                executor.execute(new TtlExpirationConsumer(connection, shutdownLatch, messageExpiredLatch));

                // messages can only be TTLed when there is a consumer - subscribe, and then immediately shutdown
                CountDownLatch consumerShutdownLatch = new CountDownLatch(1);
                executor.execute(new CountingConsumer(connection, consumerShutdownLatch, FOO));
                consumerShutdownLatch.countDown();
            }

            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Queue foo = session.createQueue(FOO);
                try (MessageProducer producer = session.createProducer(foo)) {
                    byte[] bytes = new PayloadGenerator().generatePayload(PAYLOAD_SIZE);

                    for (int i = 0; i < 3; i++) {
                        BytesMessage message = session.createBytesMessage();
                        message.writeBytes(bytes);

                        // expire the first message
                        long expiry = (i == 0) ? 1000 : Message.DEFAULT_TIME_TO_LIVE;
                        log.debug("Setting message expiry for message[" + i + "] to " + expiry);
                        producer.send(message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, expiry);
                        log.info("Sent message[{}]", i);
                    }
                    log.info("Pre-load phase messages sent");

                    log.info("Waiting until a message expires");
                    if (messageExpiredLatch.await(60, TimeUnit.SECONDS)) {
                        log.info("Message expired - attempting to send");

                        // verify that the memory being used on the queue is >70%
                        // at this time PFC should be triggered
                        int memoryPercentUsage = broker.getDestinationView("foo").getMemoryPercentUsage();
                        assertTrue("Memory used did not exceed 70%, was " + memoryPercentUsage + "%",
                                memoryPercentUsage > 70);
                        log.info("Memory for {} shows {}% usage", FOO, memoryPercentUsage);

                        BytesMessage message = session.createBytesMessage();
                        message.writeBytes(bytes);

                        log.info("Attempting to send a message when PFC is on - producer should block");
                        try {
                            producer.send(message);
                            fail("Send completed without producer flow control");
                        } catch (JMSException ex) {
                            log.info("Detected producer flow control on send - timed out on broker side");
                        }
                    } else {
                        fail("Latch wait time elapsed before message expired from queue");
                    }
                }
            }

        } catch (JMSException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            shutdownLatch.countDown();
        }
    }

}