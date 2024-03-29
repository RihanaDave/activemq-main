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
package org.apache.activemq.transport.tcp;

import jakarta.jms.JMSException;

import junit.framework.TestCase;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.Test;

public class TransportConnectorInvalidSocketOptionsTest extends TestCase {

    @Test
    public void testClientParameters() throws Exception {
        try {
            new ActiveMQConnectionFactory("tcp://localhost:42?foo=bar").createConnection();
            fail("Should have thrown an exception");
        } catch (Exception e) {
            assertEquals(JMSException.class, e.getClass());
            assertEquals(IllegalArgumentException.class, e.getCause().getClass());
            assertEquals("Invalid connect parameters: {foo=bar}", e.getCause().getMessage());
        }
    }

    @Test
    public void testClientSocketParameters() throws Exception {
        BrokerService broker = null;

        try {
            broker = new BrokerService();
            broker.setPersistent(false);
            broker.addConnector("tcp://localhost:61616");
            broker.start();

            try {
                new ActiveMQConnectionFactory("tcp://localhost:61616?socket.foo=bar").createConnection();
                fail("Should have thrown an exception");
            } catch (Exception e) {
                assertEquals(JMSException.class, e.getClass());
                assertEquals(IllegalArgumentException.class, e.getCause().getClass());
                assertEquals("Invalid socket parameters: {foo=bar}", e.getCause().getMessage());
            }
        } finally {
            if (broker != null) {
                broker.stop();
            }
        }
    }

}
