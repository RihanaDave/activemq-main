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
package org.apache.activemq.pool;

import jakarta.jms.Connection;

import org.apache.activemq.test.JmsTopicSendReceiveWithTwoConnectionsTest;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class JmsSendReceiveTwoConnectionsWithSenderUsingPoolTest extends JmsTopicSendReceiveWithTwoConnectionsTest {
    protected static final Logger LOG = LoggerFactory.getLogger(JmsSendReceiveTwoConnectionsWithSenderUsingPoolTest.class);
    protected PooledConnectionFactory senderConnectionFactory = new PooledConnectionFactory("vm://localhost?broker.persistent=false");

    protected Connection createSendConnection() throws Exception {
        return senderConnectionFactory.createConnection();
    }

    protected void setUp() throws Exception {
        verbose = true;
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        senderConnectionFactory.stop();
    }
}
