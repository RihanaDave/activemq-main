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

import static org.junit.Assert.assertTrue;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.DeadLetterStrategy;
import org.apache.activemq.broker.region.policy.IndividualDeadLetterStrategy;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AMQ4513Test {

    private BrokerService brokerService;
    private String connectionUri;

    @Before
    public void setup() throws Exception {
        brokerService = new BrokerService();

        connectionUri = brokerService.addConnector("tcp://localhost:0").getPublishableConnectString();

        // Configure Dead Letter Strategy
        DeadLetterStrategy strategy = new IndividualDeadLetterStrategy();
        ((IndividualDeadLetterStrategy)strategy).setUseQueueForQueueMessages(true);
        ((IndividualDeadLetterStrategy)strategy).setQueuePrefix("DLQ.");
        strategy.setProcessNonPersistent(false);
        strategy.setProcessExpired(false);

        // Add policy and individual DLQ strategy
        PolicyEntry policy = new PolicyEntry();
        policy.setTimeBeforeDispatchStarts(3000);
        policy.setDeadLetterStrategy(strategy);

        PolicyMap pMap = new PolicyMap();
        pMap.setDefaultEntry(policy);

        brokerService.setDestinationPolicy(pMap);

        brokerService.setPersistent(false);
        brokerService.start();
    }

    @After
    public void stop() throws Exception {
        brokerService.stop();
    }

    @Test(timeout=360000)
    public void test() throws Exception {

        final ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(connectionUri);

        ExecutorService service = Executors.newFixedThreadPool(25);

        final Random ripple = new Random(System.currentTimeMillis());

        for (int i = 0; i < 1000; ++i) {
            service.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        ActiveMQConnection connection = (ActiveMQConnection) cf.createConnection();
                        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                        Destination destination = session.createTemporaryQueue();
                        session.createProducer(destination);
                        connection.close();
                        TimeUnit.MILLISECONDS.sleep(ripple.nextInt(20));
                    } catch (Exception e) {
                    }
                }
            });

            service.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        ActiveMQConnection connection = (ActiveMQConnection) cf.createConnection();
                        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                        Destination destination = session.createTemporaryQueue();
                        MessageProducer producer = session.createProducer(destination);
                        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                        producer.setTimeToLive(400);
                        producer.send(session.createTextMessage());
                        producer.send(session.createTextMessage());
                        TimeUnit.MILLISECONDS.sleep(500);
                        connection.close();
                    } catch (Exception e) {
                    }
                }
            });

            service.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        ActiveMQConnection connection = (ActiveMQConnection) cf.createConnection();
                        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                        Destination destination = session.createTemporaryQueue();
                        session.createProducer(destination);
                        connection.close();
                        TimeUnit.MILLISECONDS.sleep(ripple.nextInt(20));
                    } catch (Exception e) {
                    }
                }
            });
        }

        service.shutdown();
        assertTrue(service.awaitTermination(5, TimeUnit.MINUTES));
    }
}
