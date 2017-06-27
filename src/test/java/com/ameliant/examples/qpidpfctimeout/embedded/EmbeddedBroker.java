package com.ameliant.examples.qpidpfctimeout.embedded;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.jmx.DestinationView;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.junit.rules.ExternalResource;

import javax.management.ObjectName;
import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Created by jkorab on 26/06/17.
 */
public class EmbeddedBroker extends ExternalResource {

    public final static int AMQP_PORT = 5672;

    private final int memoryLimitForQueues;
    private final int timeoutForSendFailIfNoSpace;

    private BrokerService brokerService;

    public EmbeddedBroker(int memoryLimitForQueues, int timeoutForSendFailIfNoSpace) {
        this.memoryLimitForQueues = memoryLimitForQueues;
        this.timeoutForSendFailIfNoSpace = timeoutForSendFailIfNoSpace;
    }

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

        {
            // set up per-destination policies
            PolicyMap policyMap = new PolicyMap();
            {
                PolicyEntry policyEntry = new PolicyEntry();
                {
                    policyEntry.setQueue(">");
                    policyEntry.setMemoryLimit(memoryLimitForQueues);
                }
                policyMap.setDefaultEntry(policyEntry);
            }
            brokerService.setDestinationPolicy(policyMap);
        }

        brokerService.getSystemUsage().setSendFailIfNoSpaceAfterTimeout(timeoutForSendFailIfNoSpace);
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

    public DestinationView getDestinationView(String queueName) {
        try {
            Map<ObjectName, DestinationView> queueViews = brokerService.getAdminView().getBroker().getQueueViews();
            ObjectName name = queueViews.keySet().stream()
                    .filter(objectName -> objectName.getCanonicalName().contains(queueName))
                    .findFirst().get();
            return queueViews.get(name);
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }
}
