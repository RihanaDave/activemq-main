/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.usecases;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.cursors.AbstractStoreCursor;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.layout.MessageLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.TopicSubscriber;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class DurableSubCacheTest {
    private static final Logger LOG = LoggerFactory.getLogger(DurableSubCacheTest.class);


    private final ActiveMQTopic topic = new ActiveMQTopic("T1");
    private BrokerService broker;

    @Before
    public void setUp() throws Exception {

        broker = createAndStartBroker();
        broker.waitUntilStarted();
    }


    private BrokerService createAndStartBroker()
            throws Exception {
        BrokerService broker = new BrokerService();
        broker.setDeleteAllMessagesOnStartup(true);
        broker.setUseJmx(false);
        broker.setAdvisorySupport(false);
        broker.getSystemUsage().getMemoryUsage().setLimit(100 * 1024);

        PolicyMap policyMap = new PolicyMap();
        PolicyEntry policy = new PolicyEntry();
        policy.setCursorMemoryHighWaterMark(20);
        policyMap.put(topic, policy);
        broker.setDestinationPolicy(policyMap);

        broker.start();

        return broker;
    }

    @After
    public void tearDown() throws Exception {
        broker.stop();
    }

    @Test
    public void testCacheExhaustion() throws Exception {
        doTestCacheExhaustion(1000);
    }

    @Test
    public void testCacheExhaustionPrefetch0() throws Exception {
        doTestCacheExhaustion(0);
    }

    public void doTestCacheExhaustion(int prefetch) throws Exception {

        createDurableSub(topic, "my_sub_1");

        publishMesssages(topic, 20);

        final AtomicBoolean failed = new AtomicBoolean(false);
        final var logger = org.apache.logging.log4j.core.Logger.class.cast(LogManager.getLogger(AbstractStoreCursor.class));
        final var appender = new AbstractAppender("testAppender", new AbstractFilter() {}, new MessageLayout(), false, new Property[0]) {
            @Override
            public void append(LogEvent event) {
                if (Level.WARN.equals(event.getLevel())) {
                    LOG.info("Got warn event:" + event.getMessage().getFormattedMessage());
                    failed.set(true);
                }
            }
        };
        appender.start();

        logger.get().addAppender(appender, Level.DEBUG, new AbstractFilter() {});
        logger.addAppender(appender);

        try {
            consumeDurableSub(topic, "my_sub_1", 20, prefetch);
        } finally {
            logger.removeAppender(appender);
        }

        assertFalse("no warning from the cursor", failed.get());
    }

    private void publishMesssages(ActiveMQTopic topic, int messageCount) throws Exception {

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(broker.getVmConnectorURI());
        connectionFactory.setWatchTopicAdvisories(false);
        Connection con = connectionFactory.createConnection();
        con.start();

        Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = session.createProducer(topic);

        try {
            String textMessage = new String(new byte[1024]);
            TextMessage msg = session.createTextMessage(textMessage);

            for (int i = 0; i < messageCount; i++) {
                producer.send(msg);
            }
        } finally {
            con.close();
        }

    }


    private void createDurableSub(ActiveMQTopic topic, String subID) throws Exception {


        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(broker.getVmConnectorURI());
        connectionFactory.setWatchTopicAdvisories(false);
        Connection con = connectionFactory.createConnection();
        con.setClientID("CONNECTION-" + subID);
        con.start();

        Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

        session.createDurableSubscriber(topic, subID, null, true);
        session.close();
        con.close();
    }

    private void consumeDurableSub(ActiveMQTopic topic, String subID, int messageCount) throws Exception {
        consumeDurableSub(topic, subID, messageCount, 1000);
    }

    private void consumeDurableSub(ActiveMQTopic topic, String subID, int messageCount, int prefetch) throws Exception {

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(broker.getVmConnectorURI());
        ActiveMQConnection con = (ActiveMQConnection) connectionFactory.createConnection();
        con.setClientID("CONNECTION-" + subID);
        con.getPrefetchPolicy().setAll(prefetch);
        con.start();

        Session session = con.createSession(false, Session.AUTO_ACKNOWLEDGE);

        TopicSubscriber topicSubscriber
                = session.createDurableSubscriber(topic, subID, null, true);

        try {

            for (int i = 0; i < messageCount; i++) {
                jakarta.jms.Message message = topicSubscriber.receive(4000l);
                if (message == null) {
                    fail("should have received a message");
                }
            }

        } finally {
            con.close();
        }
    }


}