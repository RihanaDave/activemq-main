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

package org.apache.activemq.store.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import junit.framework.TestCase;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.openwire.OpenWireFormat;
import org.apache.activemq.util.ByteSequence;
import org.apache.activemq.wireformat.WireFormat;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// https://issues.apache.org/activemq/browse/AMQ-2880
public class JDBCCommitExceptionTest extends TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(JDBCCommitExceptionTest.class);

    protected static final int messagesExpected = 10;
    protected ActiveMQConnectionFactory factory;
    protected BrokerService broker;
    protected String connectionUri;
    protected BrokenPersistenceAdapter jdbc;

    @Override
    public void setUp() throws Exception {
        broker = createBroker();
        broker.start();

        factory = new ActiveMQConnectionFactory(
            connectionUri + "?jms.prefetchPolicy.all=0&jms.redeliveryPolicy.maximumRedeliveries="+messagesExpected);
    }

    @Override
    public void tearDown() throws Exception {
        broker.stop();
    }

    public void testSqlException() throws Exception {
        doTestSqlException();
    }

    public void doTestSqlException() throws Exception {
        sendMessages(messagesExpected);
        int messagesReceived = receiveMessages(messagesExpected);

        dumpMessages();
        assertEquals("Messages expected doesn't equal messages received", messagesExpected, messagesReceived);
        broker.stop();
    }

     protected int dumpMessages() throws Exception {
        int count = 0;
        WireFormat wireFormat = new OpenWireFormat();
        java.sql.Connection conn = ((JDBCPersistenceAdapter) broker.getPersistenceAdapter()).getDataSource().getConnection();
        PreparedStatement statement = conn.prepareStatement("SELECT ID, XID, MSG FROM ACTIVEMQ_MSGS");
        ResultSet result = statement.executeQuery();
        LOG.info("Messages left in broker after test");
        while(result.next()) {
            long id = result.getLong(1);
            String xid = result.getString(2);
            org.apache.activemq.command.Message message = (org.apache.activemq.command.Message)wireFormat.unmarshal(new ByteSequence(result.getBytes(3)));
            LOG.info("id: " + id + ", xid: " + xid + ", message SeqId: " + message.getMessageId().getBrokerSequenceId() + ", MSG: " + message);
            count++;
        }
        statement.close();
        conn.close();
        return count;
    }

    protected int receiveMessages(int messagesExpected) throws Exception {
        jakarta.jms.Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

        jdbc.setShouldBreak(true);

        // first try and receive these messages, they'll continually fail
        receiveMessages(messagesExpected, session);

        jdbc.setShouldBreak(false);

        // now that the store is sane, try and get all the messages sent
        return receiveMessages(messagesExpected, session);
    }

    protected int receiveMessages(int messagesExpected, Session session) throws Exception {
        int messagesReceived = 0;

        for (int i=0; i<messagesExpected; i++) {
            Destination destination = session.createQueue("TEST");
            MessageConsumer consumer = session.createConsumer(destination);
            Message message = null;
            try {
                LOG.debug("Receiving message " + (messagesReceived+1) + " of " + messagesExpected);
                message = consumer.receive(2000);
                LOG.info("Received : " + message);
                if (message != null) {
                    session.commit();
                    messagesReceived++;
                }
            } catch (Exception e) {
                LOG.debug("Caught exception " + e);
                session.rollback();
            } finally {
                if (consumer != null) {
                    consumer.close();
                }
            }
        }
        return messagesReceived;
    }

    protected void sendMessages(int messagesExpected) throws Exception {
        jakarta.jms.Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Destination destination = session.createQueue("TEST");
        MessageProducer producer = session.createProducer(destination);
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);

        for (int i=0; i<messagesExpected; i++) {
            LOG.debug("Sending message " + (i+1) + " of " + messagesExpected);
            producer.send(session.createTextMessage("test message " + (i+1)));
        }
    }

    protected BrokerService createBroker() throws Exception {

        BrokerService broker = new BrokerService();
        broker.setAdvisorySupport(false);
        jdbc = new BrokenPersistenceAdapter();

        jdbc.setUseLock(false);
        jdbc.deleteAllMessages();

        broker.setPersistenceAdapter(jdbc);
        broker.setPersistent(true);
        connectionUri = broker.addConnector("tcp://localhost:0").getPublishableConnectString();

        return broker;
    }
}



