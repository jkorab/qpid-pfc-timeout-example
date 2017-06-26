package com.ameliant.examples.qpidpfctimeout;

import com.ameliant.examples.qpidpfctimeout.embedded.EmbeddedBroker;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;

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

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Rule
    public EmbeddedBroker broker = new EmbeddedBroker();

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
            connection.start();

            {
                // set up test consumers
                Executor executor = Executors.newFixedThreadPool(2);
                executor.execute(new TtlExpirationConsumer(connection, shutdownLatch, messageExpiredLatch));

                // messages can only be TTLed when there is a consumer - subscribe, and then shutdown
                CountDownLatch consumerShutdownLatch = new CountDownLatch(1);
                executor.execute(new CountingConsumer(connection, consumerShutdownLatch, "foo"));
                consumerShutdownLatch.countDown();
                // TODO the paging for TTL doesn't seem to be happening - confirm via standalone broker
            }

            try (Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                Queue foo = session.createQueue("foo");
                try (MessageProducer producer = session.createProducer(foo)) {
                    byte[] bytes = new PayloadGenerator().generatePayload(1_000_000);

                    for (int i = 0; i < 3; i++) {
                        BytesMessage message = session.createBytesMessage();
                        message.writeBytes(bytes);
                        // expire in one second
                        message.setJMSExpiration(new Date().getTime() + 1000);

                        producer.send(message);
                    }

                    if (messageExpiredLatch.await(60, TimeUnit.SECONDS)) {
                        log.info("Message expired !!!");
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