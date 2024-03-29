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
package org.apache.activemq.transport.udp;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.EmbeddedBrokerTestSupport;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;

public class UpdTransportBindTest extends EmbeddedBrokerTestSupport{
    final String addr = "udp://localhost:61625";

    protected void setUp() throws Exception {
        bindAddress = addr + "?soTimeout=1000";
        super.setUp();
    }

    public void testConnect() throws Exception {
        try {
            Connection connection = new ActiveMQConnectionFactory(addr).createConnection();
            connection.start();
        } catch (JMSException e) {
            fail("Could not start the connection for a UDP Transport. " +
                 "Check that the port and connector are available.");
        }
    }

}
