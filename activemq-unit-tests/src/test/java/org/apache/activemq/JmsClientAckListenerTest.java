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
package org.apache.activemq;

import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;

/**
 * 
 */
public class JmsClientAckListenerTest extends TestSupport implements MessageListener {

    private Connection connection;
    private boolean dontAck;

    protected void setUp() throws Exception {
        super.setUp();
        connection = createConnection();
    }

    /**
     * @see junit.framework.TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
            connection = null;
        }
        super.tearDown();
    }

    /**
     * Tests if acknowleged messages are being consumed.
     * 
     * @throws jakarta.jms.JMSException
     */
    public void testAckedMessageAreConsumed() throws Exception {
        connection.start();
        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue = session.createQueue("test");
        MessageProducer producer = session.createProducer(queue);
        producer.send(session.createTextMessage("Hello"));

        // Consume the message...
        MessageConsumer consumer = session.createConsumer(queue);
        consumer.setMessageListener(this);

        Thread.sleep(10000);

        // Reset the session.
        session.close();

        session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);

        // Attempt to Consume the message...
        consumer = session.createConsumer(queue);
        Message msg = consumer.receive(1000);
        assertNull(msg);

        session.close();
    }

    /**
     * Tests if unacknowleged messages are being redelivered when the consumer
     * connects again.
     * 
     * @throws jakarta.jms.JMSException
     */
    public void testUnAckedMessageAreNotConsumedOnSessionClose() throws Exception {
        connection.start();
        // don't aknowledge message on onMessage() call
        dontAck = true;
        Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue = session.createQueue("test");
        MessageProducer producer = session.createProducer(queue);
        producer.send(session.createTextMessage("Hello"));

        // Consume the message...
        MessageConsumer consumer = session.createConsumer(queue);
        consumer.setMessageListener(this);
        // Don't ack the message.

        // Reset the session. This should cause the Unacked message to be
        // redelivered.
        session.close();

        Thread.sleep(10000);
        session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        // Attempt to Consume the message...
        consumer = session.createConsumer(queue);
        Message msg = consumer.receive(2000);
        assertNotNull(msg);
        msg.acknowledge();

        session.close();
    }

    public void onMessage(Message message) {

        assertNotNull(message);
        if (!dontAck) {
            try {
                message.acknowledge();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

    }
}
