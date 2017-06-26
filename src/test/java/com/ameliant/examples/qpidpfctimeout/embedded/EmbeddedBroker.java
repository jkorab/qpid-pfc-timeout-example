package com.ameliant.examples.qpidpfctimeout.embedded;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jkorab on 26/06/17.
 */
public class EmbeddedBroker extends ExternalResource {

    public final static int AMQP_PORT = 5672;
    private BrokerService brokerService;

    @Override
    protected void before() throws Throwable {
        brokerService = new BrokerService();
        brokerService.setBrokerName("embeddedBroker");

        {
            KahaDBPersistenceAdapter kahaDB = new KahaDBPersistenceAdapter();
            String tmpDir = System.getProperty("java.io.tmpdir");
            LocalDateTime now = LocalDateTime.now();
            File workingDir = new File(tmpDir + "/embedded-kahadb-"
                    + now.getYear() + now.getMonthValue() + now.getDayOfMonth()
                    + now.getHour() + now.getMinute() + now.getSecond());
            kahaDB.setDirectory(workingDir);
            brokerService.setPersistenceAdapter(kahaDB);
        }

        brokerService.addConnector(String.format("amqp://0.0.0.0:%d?maximumConnections=1000&amp;wireFormat.maxFrameSize=104857600", AMQP_PORT));

        brokerService.start();
    }

    @Override
    protected void after() {
        try {
            brokerService.stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
