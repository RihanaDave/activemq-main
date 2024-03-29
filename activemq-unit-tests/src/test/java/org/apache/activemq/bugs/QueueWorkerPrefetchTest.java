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

package org.apache.activemq.bugs;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import junit.framework.TestCase;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test case demonstrating situation where messages are not delivered to
 * consumers.
 */
public class QueueWorkerPrefetchTest extends TestCase implements
        MessageListener {
    private static final Logger LOG = LoggerFactory
            .getLogger(QueueWorkerPrefetchTest.class);
    private static final int BATCH_SIZE = 10;
    private static final long WAIT_TIMEOUT = 1000 * 10;

    /** The connection URL. */
    private static final String BROKER_BIND_ADDRESS = "tcp://localhost:0";

    /**
     * The queue prefetch size to use. A value greater than 1 seems to make
     * things work.
     */
    private static final int QUEUE_PREFETCH_SIZE = 1;

    /**
     * The number of workers to use. A single worker with a prefetch of 1 works.
     */
    private static final int NUM_WORKERS = 2;

    /** Embedded JMS broker. */
    private BrokerService broker;

    /** The master's producer object for creating work items. */
    private MessageProducer workItemProducer;

    /** The master's consumer object for consuming ack messages from workers. */
    private MessageConsumer masterItemConsumer;

    /** The number of acks received by the master. */
    private final AtomicLong acksReceived = new AtomicLong(0);

    private final AtomicReference<CountDownLatch> latch = new AtomicReference<CountDownLatch>();

    private String connectionUri;

    /** Messages sent to the work-item queue. */
    private static class WorkMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int id;

        public WorkMessage(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Work: " + id;
        }
    }

    /**
     * The worker process. Consume messages from the work-item queue, possibly
     * creating more messages to submit to the work-item queue. For each work
     * item, send an ack to the master.
     */
    private static class Worker implements MessageListener {
        /**
         * Counter shared between workers to decided when new work-item messages
         * are created.
         */
        private static AtomicInteger counter = new AtomicInteger(0);

        /** Session to use. */
        private Session session;

        /** Producer for sending ack messages to the master. */
        private MessageProducer masterItemProducer;

        /** Producer for sending new work items to the work-items queue. */
        private MessageProducer workItemProducer;

        public Worker(Session session) throws JMSException {
            this.session = session;
            masterItemProducer = session.createProducer(session
                    .createQueue("master-item"));
            Queue workItemQueue = session.createQueue("work-item");
            workItemProducer = session.createProducer(workItemQueue);
            MessageConsumer workItemConsumer = session
                    .createConsumer(workItemQueue);
            workItemConsumer.setMessageListener(this);
        }

        public void onMessage(jakarta.jms.Message message) {
            try {
                WorkMessage work = (WorkMessage) ((ObjectMessage) message)
                        .getObject();

                long c = counter.incrementAndGet();

                // Don't create a new work item for every BATCH_SIZE message. */
                if (c % BATCH_SIZE != 0) {
                    // Send new work item to work-item queue.
                    workItemProducer.send(session
                            .createObjectMessage(new WorkMessage(work.id + 1)));
                }

                // Send ack to master.
                masterItemProducer.send(session.createObjectMessage(work));
            } catch (JMSException e) {
                throw new IllegalStateException("Something has gone wrong", e);
            }
        }

        /** Close of JMS resources used by worker. */
        public void close() throws JMSException {
            masterItemProducer.close();
            workItemProducer.close();
            session.close();
        }
    }

    /** Master message handler. Process ack messages. */
    public void onMessage(jakarta.jms.Message message) {
        long acks = acksReceived.incrementAndGet();
        latch.get().countDown();
        if (acks % 1 == 0) {
            LOG.info("Master now has ack count of: " + acksReceived);
        }
    }

    protected void setUp() throws Exception {
        // Create the message broker.
        super.setUp();
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.setUseJmx(true);
        broker.addConnector(BROKER_BIND_ADDRESS);
        broker.start();
        broker.waitUntilStarted();

        connectionUri = broker.getTransportConnectors().get(0).getPublishableConnectString();
    }

    protected void tearDown() throws Exception {
        // Shut down the message broker.
        broker.deleteAllMessages();
        broker.stop();
        super.tearDown();
    }

    public void testActiveMQ() throws Exception {
        // Create the connection to the broker.
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(connectionUri);
        ActiveMQPrefetchPolicy prefetchPolicy = new ActiveMQPrefetchPolicy();
        prefetchPolicy.setQueuePrefetch(QUEUE_PREFETCH_SIZE);
        connectionFactory.setPrefetchPolicy(prefetchPolicy);
        Connection connection = connectionFactory.createConnection();
        connection.start();

        Session masterSession = connection.createSession(false,
                Session.AUTO_ACKNOWLEDGE);
        workItemProducer = masterSession.createProducer(masterSession
                .createQueue("work-item"));
        masterItemConsumer = masterSession.createConsumer(masterSession
                .createQueue("master-item"));
        masterItemConsumer.setMessageListener(this);

        // Create the workers.
        Worker[] workers = new Worker[NUM_WORKERS];
        for (int i = 0; i < NUM_WORKERS; i++) {
            workers[i] = new Worker(connection.createSession(false,
                    Session.AUTO_ACKNOWLEDGE));
        }

        // Send a message to the work queue, and wait for the BATCH_SIZE acks
        // from the workers.
        acksReceived.set(0);
        latch.set(new CountDownLatch(BATCH_SIZE));
        workItemProducer.send(masterSession
                .createObjectMessage(new WorkMessage(1)));

        if (!latch.get().await(WAIT_TIMEOUT, TimeUnit.MILLISECONDS)) {
            fail("First batch only received " + acksReceived + " messages");
        }

        LOG.info("First batch received");

        // Send another message to the work queue, and wait for the next 1000 acks. It is
        // at this point where the workers never get notified of this message, as they
        // have a large pending queue. Creating a new worker at this point however will
        // receive this new message.
        acksReceived.set(0);
        latch.set(new CountDownLatch(BATCH_SIZE));
        workItemProducer.send(masterSession
                .createObjectMessage(new WorkMessage(1)));

        if (!latch.get().await(WAIT_TIMEOUT, TimeUnit.MILLISECONDS)) {
            fail("Second batch only received " + acksReceived + " messages");
        }

        LOG.info("Second batch received");

        // Cleanup all JMS resources.
        for (int i = 0; i < NUM_WORKERS; i++) {
            workers[i].close();
        }
        masterSession.close();
        connection.close();
    }
}
