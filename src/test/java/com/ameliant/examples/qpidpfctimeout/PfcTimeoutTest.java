package com.ameliant.examples.qpidpfctimeout;

import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

/**
 * @author jkorab
 */
public class PfcTimeoutTest {

    public static final String BROKER_URL = "failover:(amqp://localhost:5678)";
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Test
    public void testTimeoutBehaviour() {
        // set memory limit for queue, messages in storage
        // fire in a few messages, one that gets expired
        // wait for TTL expiry

        ConnectionFactory connectionFactory = new JmsConnectionFactory(BROKER_URL);
        try (Connection connection = connectionFactory.createConnection()) {
            connection.start();

            Executor executor = Executors.newSingleThreadExecutor();

        } catch (JMSException e) {
            e.printStackTrace(); // TODO
        }
    }

}