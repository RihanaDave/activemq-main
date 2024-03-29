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
package org.apache.activemq.security;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.server.ldap.LdapServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith( FrameworkRunner.class )
@CreateLdapServer(transports = {@CreateTransport(protocol = "LDAP", port=1024)})
@ApplyLdifFiles(
   "org/apache/activemq/security/activemq.ldif"
)
public class LDAPAuthenticationTest extends AbstractLdapTestUnit {

    public BrokerService broker;

    public static LdapServer ldapServer;

    @Before
    public void setup() throws Exception {
        System.setProperty("ldapPort", String.valueOf(getLdapServer().getPort()));

        broker = BrokerFactory.createBroker("xbean:org/apache/activemq/security/activemq-ldap-auth.xml");
        broker.start();
        broker.waitUntilStarted();
    }

    @After
    public void shutdown() throws Exception {
        broker.stop();
        broker.waitUntilStopped();
    }

    @Test
    public void testWildcard() throws Exception {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        Connection conn = factory.createQueueConnection("*", "sunflower");
        try {
            conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        fail("Should have failed connecting");
    }
}