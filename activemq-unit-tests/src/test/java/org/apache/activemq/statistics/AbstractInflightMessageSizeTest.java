/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.statistics;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.TestSupport;
import org.apache.activemq.broker.BrokerFilter;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.broker.region.Destination;
import org.apache.activemq.broker.region.Subscription;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.MessageDispatch;
import org.apache.activemq.util.Wait;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test shows Inflight Message sizes are correct for various acknowledgement modes.
 */
public abstract class AbstractInflightMessageSizeTest {

    protected BrokerService brokerService;
    protected Connection connection;
    protected String brokerUrlString;
    protected Session session;
    protected jakarta.jms.Destination dest;
    protected Destination amqDestination;
    protected MessageConsumer consumer;
    protected int prefetch = 100;
    protected boolean useTopicSubscriptionInflightStats;
    final protected int ackType;
    final protected boolean optimizeAcknowledge;
    final protected String destNamePrefix = "testDest";
    final protected String destName = "testDest.1";
    final protected String destName2 = "testDest.2";

    //use 10 second wait for assertions instead of the 30 default
    protected final long WAIT_DURATION = 10 * 1000;
    protected final long SLEEP_DURATION =  500;
    protected final AtomicBoolean failOnDispatch = new AtomicBoolean();

    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {ActiveMQSession.SESSION_TRANSACTED, true, true},
                {ActiveMQSession.AUTO_ACKNOWLEDGE, true, true},
                {ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE, true, true},
                {ActiveMQSession.CLIENT_ACKNOWLEDGE, true, true},
                {ActiveMQSession.SESSION_TRANSACTED, false, true},
                {ActiveMQSession.AUTO_ACKNOWLEDGE, false, true},
                {ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE, false, true},
                {ActiveMQSession.CLIENT_ACKNOWLEDGE, false, true},
                {ActiveMQSession.SESSION_TRANSACTED, true, false},
                {ActiveMQSession.AUTO_ACKNOWLEDGE, true, false},
                {ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE, true, false},
                {ActiveMQSession.CLIENT_ACKNOWLEDGE, true, false},
                {ActiveMQSession.SESSION_TRANSACTED, false, false},
                {ActiveMQSession.AUTO_ACKNOWLEDGE, false, false},
                {ActiveMQSession.INDIVIDUAL_ACKNOWLEDGE, false, false},
                {ActiveMQSession.CLIENT_ACKNOWLEDGE, false, false}
        });
    }

    public AbstractInflightMessageSizeTest(int ackType, boolean optimizeAcknowledge, boolean useTopicSubscriptionInflightStats) {
        this.ackType = ackType;
        this.optimizeAcknowledge = optimizeAcknowledge;
        this.useTopicSubscriptionInflightStats = useTopicSubscriptionInflightStats;
    }

    @Before
    public void setUp() throws Exception {
        failOnDispatch.set(false);
        brokerService = new BrokerService();
        brokerService.setDeleteAllMessagesOnStartup(true);
        TransportConnector tcp = brokerService
                .addConnector("tcp://localhost:0");
        PolicyEntry policy = new PolicyEntry();
        policy.setUseTopicSubscriptionInflightStats(useTopicSubscriptionInflightStats);
        PolicyMap pMap = new PolicyMap();
        pMap.setDefaultEntry(policy);
        brokerService.setDestinationPolicy(pMap);
        brokerService.setPlugins(new BrokerPlugin[]{broker -> new BrokerFilter(broker) {
            @Override
            public void preProcessDispatch(MessageDispatch messageDispatch) {
                super.preProcessDispatch(messageDispatch);
                if (failOnDispatch.get()) {
                    throw new RuntimeException("fail dispatch");
                }
            }
        }});

        brokerService.start();
        //used to test optimizeAcknowledge works
        String optAckString = optimizeAcknowledge ? "?jms.optimizeAcknowledge=true&jms.optimizedAckScheduledAckInterval=2000" : "";
        brokerUrlString = tcp.getPublishableConnectString() + optAckString;
        connection = createConnectionFactory().createConnection();
        connection.setClientID("client1");
        connection.start();
        session = connection.createSession(ackType == ActiveMQSession.SESSION_TRANSACTED, ackType);
        dest = getDestination();
        consumer = getMessageConsumer();
        amqDestination = TestSupport.getDestination(brokerService, getActiveMQDestination());
    }

    protected ActiveMQConnectionFactory createConnectionFactory()
            throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrlString);
        ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
        prefetchPolicy.setTopicPrefetch(prefetch);
        prefetchPolicy.setQueuePrefetch(prefetch);
        prefetchPolicy.setOptimizeDurableTopicPrefetch(prefetch);
        factory.setPrefetchPolicy(prefetchPolicy);
        return factory;
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
        brokerService.stop();
    }

    /**
     * Tests that inflight message size goes up and comes back down to 0 after
     * messages are consumed
     *
     * @throws jakarta.jms.JMSException
     * @throws InterruptedException
     */
    @Test(timeout=60000)
    public void testInflightMessageSize() throws Exception {
        Assume.assumeTrue(useTopicSubscriptionInflightStats);

        final long size = sendMessages(10);

        assertTrue("Inflight message size should be greater than the content length sent",
                Wait.waitFor(() -> getSubscription().getInFlightMessageSize() > size, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight sub dispatched message count should equal number of messages sent",
                Wait.waitFor(() -> getSubscription().getDispatchedQueueSize() == 10, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Destination inflight message count should equal number of messages sent",
                Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == 10, WAIT_DURATION, SLEEP_DURATION));

        receiveMessages(10);

        assertTrue("Destination inflight message count should be 0",
                Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight sub dispatched message count should be 0",
                Wait.waitFor(() -> getSubscription().getDispatchedQueueSize() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should be 0",
                Wait.waitFor(() -> getSubscription().getInFlightMessageSize() == 0, WAIT_DURATION, SLEEP_DURATION));
    }

    /**
     * Test that the in flight message size won't rise after prefetch is filled
     *
     * @throws Exception
     */
    @Test(timeout=60000)
    public void testInflightMessageSizePrefetchFilled() throws Exception {
        Assume.assumeTrue(useTopicSubscriptionInflightStats);

        final long size = sendMessages(prefetch);

        assertTrue("Inflight message size should be greater than content length",
                Wait.waitFor(() -> getSubscription().getInFlightMessageSize() > size, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight sub dispatched message count should equal number of messages sent",
                Wait.waitFor(() -> getSubscription().getDispatchedQueueSize() == prefetch, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Destination inflight message count should equal number of messages sent",
                Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == prefetch, WAIT_DURATION, SLEEP_DURATION));

        final long inFlightSize = getSubscription().getInFlightMessageSize();
        sendMessages(10);

        //Prefetch has been filled, so the size should not change with 10 more messages
        assertTrue("Destination inflight message count should equal number of messages sent",
                Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == prefetch, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight sub dispatched message count should equal number of messages sent",
                Wait.waitFor(() -> getSubscription().getDispatchedQueueSize() == prefetch, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should not change", Wait.waitFor(
                () -> getSubscription().getInFlightMessageSize() == inFlightSize, WAIT_DURATION, SLEEP_DURATION));
        receiveMessages(prefetch + 10);

        assertTrue("Destination inflight message count should be 0",
                Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight sub dispatched message count should be 0",
                Wait.waitFor(() -> getSubscription().getDispatchedQueueSize() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should be 0",
                Wait.waitFor(() -> getSubscription().getInFlightMessageSize() == 0, WAIT_DURATION, SLEEP_DURATION));
    }

    /**
     * Test that the in flight message size will still rise if prefetch is not filled
     *
     * @throws Exception
     */
    @Test(timeout=60000)
    public void testInflightMessageSizePrefetchNotFilled() throws Exception {
        Assume.assumeTrue(useTopicSubscriptionInflightStats);

        final long size = sendMessages(prefetch - 10);

        assertTrue("Inflight message size should be greater than content length",
                Wait.waitFor(() -> getSubscription().getInFlightMessageSize() > size, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight sub dispatched message count should equal number of messages sent",
                Wait.waitFor(() -> getSubscription().getDispatchedQueueSize() == prefetch - 10, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Destination inflight message count should equal number of messages sent",
                Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == prefetch - 10, WAIT_DURATION, SLEEP_DURATION));

        //capture the inflight size and send 10 more messages
        final long inFlightSize = getSubscription().getInFlightMessageSize();
        sendMessages(10);

        //Prefetch has NOT been filled, so the size should rise with 10 more messages
        assertTrue("Inflight message size should be greater than previous inlight size",
                Wait.waitFor(() -> getSubscription().getInFlightMessageSize() > inFlightSize, WAIT_DURATION, SLEEP_DURATION));

        receiveMessages(prefetch);

        assertTrue("Destination inflight message count should be 0",
                Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight sub dispatched message count should be 0",
                Wait.waitFor(() -> getSubscription().getDispatchedQueueSize() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should be 0",
                Wait.waitFor(() -> getSubscription().getInFlightMessageSize() == 0, WAIT_DURATION, SLEEP_DURATION));
    }


    /**
     * Tests that inflight message size goes up and doesn't go down if receive is rolledback
     *
     * @throws jakarta.jms.JMSException
     * @throws InterruptedException
     */
    @Test(timeout=60000)
    public void testInflightMessageSizeRollback() throws Exception {
        Assume.assumeTrue(useTopicSubscriptionInflightStats);
        Assume.assumeTrue(ackType == ActiveMQSession.SESSION_TRANSACTED);

        final long size = sendMessages(10);

        assertTrue("Inflight message size should be greater than the content length sent",
                Wait.waitFor(() -> getSubscription().getInFlightMessageSize() > size, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight sub dispatched message count should equal number of messages sent",
                Wait.waitFor(() -> getSubscription().getDispatchedQueueSize() == 10, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Destination inflight message count should equal number of messages sent",
                Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == 10, WAIT_DURATION, SLEEP_DURATION));

       long inFlightSize = getSubscription().getInFlightMessageSize();

        for (int i = 0; i < 10; i++) {
            consumer.receive();
        }
        session.rollback();

        assertTrue("Destination inflight message count should not change on rollback",
                Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == 10, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight sub dispatched message count should not change on rollback",
                Wait.waitFor(() -> getSubscription().getDispatchedQueueSize() == 10, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should not change on rollback",
                Wait.waitFor(() -> getSubscription().getInFlightMessageSize() == inFlightSize, WAIT_DURATION, SLEEP_DURATION));
    }

    @Test(timeout=60000)
    public void testInflightMessageSizeConsumerExpiration() throws Exception {
        Assume.assumeTrue(useTopicSubscriptionInflightStats);

        int ttl = 500;
        int messageCount = 10;
        //Send 10 messages with a TTL of 500 ms which is long enough to be paged in and then wait for TTL to pass
        sendMessages(10, ttl);
        Thread.sleep(ttl * 2);

        //Make sure we can't receive and all 10 messages were expired
        //verify in flight size and count is now 0
        assertNull(consumer.receive(10));
        assertTrue("Expired count is wrong", Wait.waitFor(() -> brokerService.getDestination(getActiveMQDestination())
                .getDestinationStatistics().getExpired().getCount() == messageCount, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Destination inflight message count should be 0",
                Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight sub dispatched message count should be 0",
                Wait.waitFor(() -> getSubscription().getDispatchedQueueSize() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should be 0",
                Wait.waitFor(() -> getSubscription().getInFlightMessageSize() == 0, WAIT_DURATION, SLEEP_DURATION));
    }

    @Test(timeout=60000)
    public void testInflightMessageSizeDispatchFailure() throws Exception {
        Assume.assumeTrue(useTopicSubscriptionInflightStats);

        //Fail on all dispatches
        failOnDispatch.set(true);

        //Need to reset each time here on send because dispatch will cause the connection to close
        try {
            sendMessages(1);
        } catch (Exception e) {
            //expected as session should close
        }

        //Wait for session to fail
        assertTrue(Wait.waitFor(() -> ((ActiveMQSession) session).isClosed(), WAIT_DURATION, SLEEP_DURATION));

        //Make sure all the stats are cleaned up on failure of dispatches
        assertTrue("Destination inflight message count should be 0",
            Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Consumers size should be 0 due to failure or Inflight sub dispatched message count should be 0 for durable sub",
            Wait.waitFor(() -> amqDestination.getConsumers().size() == 0 ||
                getSubscription().getDispatchedQueueSize() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Consumers size should be 0 due to failure or Inflight message size should be 0 for durable sub",
            Wait.waitFor(() -> amqDestination.getConsumers().size() == 0 ||
                getSubscription().getInFlightMessageSize() == 0, WAIT_DURATION, SLEEP_DURATION));
    }

    @Test(timeout=60000)
    public void testInflightMessageSizeConsumerClosed() throws Exception {
        Assume.assumeTrue(useTopicSubscriptionInflightStats);
        sendMessages(10);

        //Wait for the 10 messages to get dispatched and then close the consumer to test cleanup
        assertTrue("Should be 10 in flight messages",
            Wait.waitFor(() ->  amqDestination.getDestinationStatistics().getInflight().getCount() == 10, WAIT_DURATION, SLEEP_DURATION));
        consumer.close();

        //Make sure all the stats are cleaned up on failure of dispatches
        assertTrue("Destination inflight message count should be 0",
            Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Consumers size should be 0 due to failure or Inflight sub dispatched message count should be 0 for durable sub",
            Wait.waitFor(() -> amqDestination.getConsumers().size() == 0 ||
                getSubscription().getDispatchedQueueSize() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Consumers size should be 0 due to failure or Inflight message size should be 0 for durable sub",
            Wait.waitFor(() -> amqDestination.getConsumers().size() == 0 ||
                getSubscription().getInFlightMessageSize() == 0, WAIT_DURATION, SLEEP_DURATION));
    }

    @Test(timeout=60000)
    public void testInflightMessageSizeRemoveDestination() throws Exception {
        Assume.assumeTrue(useTopicSubscriptionInflightStats);
        //Close as we will re-create with a wildcard sub to get messages from 2 destinations
        consumer.close();

        consumer = getMessageConsumer(destNamePrefix + ".>");
        sendMessages(10);
        sendMessages(10, getActiveMQDestination(destName2));
        Destination amqDestination2 = TestSupport.getDestination(brokerService, getActiveMQDestination(destName2));
        final Subscription subscription = getSubscription();

        //Wait for the 10 messages to get dispatched and then close the consumer to test cleanup
        assertTrue("Should be 10 in flight messages",
            Wait.waitFor(() ->  amqDestination.getDestinationStatistics().getInflight().getCount() == 10, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Should be 10 in flight messages",
            Wait.waitFor(() ->  amqDestination2.getDestinationStatistics().getInflight().getCount() == 10, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should be 20",
            Wait.waitFor(() -> subscription.getInFlightSize() == 20, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should be greater than 0",
            Wait.waitFor(() -> subscription.getInFlightMessageSize() > 0, WAIT_DURATION, SLEEP_DURATION));

        //remove 1 destination, leaving 10 in flight
        brokerService.getBroker().removeDestination(brokerService.getAdminConnectionContext(), getActiveMQDestination(), 1000);

        //Make sure all the stats are updated after 1 destination removal
        assertTrue("Destination inflight message count should be 0",
            Wait.waitFor(() -> amqDestination.getDestinationStatistics().getInflight().getCount() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Destination inflight message count should still be 10",
            Wait.waitFor(() -> amqDestination2.getDestinationStatistics().getInflight().getCount() == 10, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should be 10",
            Wait.waitFor(() -> subscription.getInFlightSize() == 10, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should be greater than 0",
            Wait.waitFor(() -> subscription.getInFlightMessageSize() > 0, WAIT_DURATION, SLEEP_DURATION));

        //remove second dest
        brokerService.getBroker().removeDestination(brokerService.getAdminConnectionContext(), getActiveMQDestination(destName2), 1000);

        assertTrue("Destination inflight message count should be 0",
            Wait.waitFor(() -> amqDestination2.getDestinationStatistics().getInflight().getCount() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should be 0",
            Wait.waitFor(() -> subscription.getInFlightSize() == 0, WAIT_DURATION, SLEEP_DURATION));
        assertTrue("Inflight message size should be 0",
            Wait.waitFor(() -> subscription.getInFlightMessageSize() == 0, WAIT_DURATION, SLEEP_DURATION));
    }

    protected long sendMessages(int count) throws JMSException {
        return sendMessages(count, null, dest);
    }

    protected long sendMessages(int count, jakarta.jms.Destination dest) throws JMSException {
        return sendMessages(count, null, dest);
    }

    protected long sendMessages(int count, Integer ttl) throws JMSException {
        return sendMessages(count, ttl, dest);
    }

    /**
     * This method will generate random sized messages up to 150000 bytes.
     *
     * @param count
     * @throws JMSException
     */
    protected long sendMessages(int count, Integer ttl, jakarta.jms.Destination dest) throws JMSException {
        MessageProducer producer = session.createProducer(dest);
        if (ttl != null) {
            producer.setTimeToLive(ttl);
        }
        long totalSize = 0;
        for (int i = 0; i < count; i++) {
            Random r = new Random();
            int size = r.nextInt(150000);
            totalSize += size;
            byte[] bytes = new byte[size > 0 ? size : 1];
            r.nextBytes(bytes);
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(bytes);
            producer.send(bytesMessage);
        }
        if (session.getTransacted()) {
            session.commit();
        }
        return totalSize;
    }

    protected void receiveMessages(int count) throws JMSException {
        for (int i = 0; i < count; i++) {
            jakarta.jms.Message m = consumer.receive();
            if (ackType == ActiveMQSession.SESSION_TRANSACTED) {
                session.commit();
            } else if (ackType != ActiveMQSession.AUTO_ACKNOWLEDGE) {
                m.acknowledge();
            }
        }
    }

    protected abstract Subscription getSubscription();

    protected ActiveMQDestination getActiveMQDestination() {
        return getActiveMQDestination(destName);
    }

    protected abstract ActiveMQDestination getActiveMQDestination(String destName);

    protected MessageConsumer getMessageConsumer() throws JMSException {
        return getMessageConsumer(destName);
    }

    protected abstract MessageConsumer getMessageConsumer(String destName) throws JMSException;

    protected jakarta.jms.Destination getDestination() throws JMSException {
        return getDestination(destName);
    }

    protected abstract jakarta.jms.Destination getDestination(String destName) throws JMSException;

}
