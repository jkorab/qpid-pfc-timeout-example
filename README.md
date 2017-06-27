# Qpid PFC Timeout Example

This project presents a simulation of a production issue. It checks whether a Qpid JMS client which uses the `failover:`
protocol will attempt to disconnect and reconnect repeatedly while blocked on sending by ActiveMQ's producer flow control.
 
## Method
To simulate the situation, a test performs the following steps:

1. Embeds an ActiveMQ broker with enough space in memory to page in only 2 messages
2. Sets up a subscriber to detect when messages are expired
3. Sets up and immediately tears down a subscriber on a queue called 'foo' - this will initialise paging into memory for that queue
4. Posts 3 messages into 'foo', the last of which has a TTL that will expire the message on the next run. When the 
 expiry mechanism next runs it will browse the queue to work out what to expire. This will cause the memory limit for the queue to be exceeded.
 At this time, the main thread will be blocked until it receives a signal from the advisory consumer that a message was expired.
5. Send a message in, which will be blocked by producer flow control.

## Results
The behaviour seen in production is not seen when the JMS API is used appropriately.

When Qpid times out during the send due to `jms.sendTimeout`, the following exception is thrown:

    org.apache.qpid.jms.JmsSendTimedOutException: Timed out waiting for disposition of sent Message

When the broker times out due to `sendFailIfNoSpaceAfterTimeout`, the following exception is thrown:

    javax.jms.ResourceAllocationException: Usage Manager Memory Limit reached. Stopping producer (ID:Monolith-39030-1498573449788-3:1:2:0) to prevent flooding queue://foo. See http://activemq.apache.org/producer-flow-control.html for more info [condition = amqp:resource-limit-exceeded]
    
There is a timing issue with the test where not all of the 3 initial messages will be able to be sent in before PFC is exceeded. 
Rerunning the test resolves this. The following lines should be visible in the console output when step 4 (above) completes successfully:

    [main] INFO com.ameliant.examples.qpidpfctimeout.PfcTimeoutTest - Sent message[0]
    [main] INFO com.ameliant.examples.qpidpfctimeout.PfcTimeoutTest - Sent message[1]
    [main] INFO com.ameliant.examples.qpidpfctimeout.PfcTimeoutTest - Sent message[2]