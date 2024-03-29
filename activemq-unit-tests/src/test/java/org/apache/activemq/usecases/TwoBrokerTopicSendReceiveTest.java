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
package org.apache.activemq.usecases;

import java.util.HashMap;
import java.util.Iterator;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.test.JmsTopicSendReceiveWithTwoConnectionsTest;
import org.apache.activemq.util.ServiceSupport;
import org.apache.activemq.xbean.BrokerFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 *
 */
public class TwoBrokerTopicSendReceiveTest extends JmsTopicSendReceiveWithTwoConnectionsTest {
    private static final Logger LOG = LoggerFactory.getLogger(TwoBrokerTopicSendReceiveTest.class);

    protected ActiveMQConnectionFactory sendFactory;
    protected ActiveMQConnectionFactory receiveFactory;
    protected HashMap<String, BrokerService> brokers = new HashMap<String, BrokerService>();

    @Override
    protected void setUp() throws Exception {
        sendFactory = createSenderConnectionFactory();
        receiveFactory = createReceiverConnectionFactory();

        // Give server enough time to setup,
        // so we don't lose messages when connection fails
        LOG.info("Waiting for brokers Initialize.");
        Thread.sleep(5000);
        LOG.info("Brokers should be initialized by now.. starting test.");

        super.setUp();
    }

    protected ActiveMQConnectionFactory createReceiverConnectionFactory() throws JMSException {
        return createConnectionFactory("org/apache/activemq/usecases/receiver.xml", "receiver",
                                       "vm://receiver");
    }

    protected ActiveMQConnectionFactory createSenderConnectionFactory() throws JMSException {
        return createConnectionFactory("org/apache/activemq/usecases/sender.xml", "sender", "vm://sender");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (Iterator<BrokerService> iter = brokers.values().iterator(); iter.hasNext();) {
            BrokerService broker = iter.next();
            ServiceSupport.dispose(broker);
            iter.remove();
        }
    }

    @Override
    protected Connection createReceiveConnection() throws JMSException {
        return receiveFactory.createConnection();
    }

    @Override
    protected Connection createSendConnection() throws JMSException {
        return sendFactory.createConnection();
    }

    protected ActiveMQConnectionFactory createConnectionFactory(String config, String brokerName,
                                                                String connectUrl) throws JMSException {
        try {
            BrokerFactoryBean brokerFactory = new BrokerFactoryBean(new ClassPathResource(config));
            brokerFactory.afterPropertiesSet();
            BrokerService broker = brokerFactory.getBroker();
            brokers.put(brokerName, broker);

            return new ActiveMQConnectionFactory(connectUrl);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
