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
package org.apache.activemq.test.retroactive;

import java.net.URI;
import java.util.Date;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.EmbeddedBrokerTestSupport;
import org.apache.activemq.broker.BrokerFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.util.MessageIdList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class RetroactiveConsumerWithMessageQueryTest extends EmbeddedBrokerTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(RetroactiveConsumerWithMessageQueryTest.class);

    protected int messageCount = 20;
    protected Connection connection;
    protected Session session;

    public void testConsumeAndReceiveInitialQueryBeforeUpdates() throws Exception {

        // lets some messages
        connection = createConnection();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        connection.start();

        MessageConsumer consumer = session.createConsumer(destination);
        MessageIdList listener = new MessageIdList();
        listener.setVerbose(true);
        consumer.setMessageListener(listener);

        MessageProducer producer = session.createProducer(destination);
        int updateMessageCount = messageCount - DummyMessageQuery.MESSAGE_COUNT;
        for (int i = 0; i < updateMessageCount; i++) {
            TextMessage message = session.createTextMessage("Update Message: " + i + " sent at: " + new Date());
            producer.send(message);
        }
        producer.close();
        LOG.info("Sent: " + updateMessageCount + " update messages");

        listener.assertMessagesReceived(messageCount);
    }

    @Override
    protected void setUp() throws Exception {
        useTopic = true;
        bindAddress = "vm://localhost";
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        if (session != null) {
            session.close();
            session = null;
        }
        if (connection != null) {
            connection.close();
        }
        super.tearDown();
    }

    @Override
    protected ConnectionFactory createConnectionFactory() throws Exception {
        ActiveMQConnectionFactory answer = new ActiveMQConnectionFactory(bindAddress);
        // answer.setUseRetroactiveConsumer(true);
        // option applied via destination policy alwaysRetroactive
        return answer;
    }

    @Override
    protected BrokerService createBroker() throws Exception {
        String uri = getBrokerXml();
        LOG.info("Loading broker configuration from the classpath with URI: " + uri);
        return BrokerFactory.createBroker(new URI("xbean:" + uri));
    }

    @Override
    protected void startBroker() throws Exception {
        // broker already started by XBean
    }

    protected String getBrokerXml() {
        return "org/apache/activemq/test/retroactive/activemq-message-query.xml";
    }

}
