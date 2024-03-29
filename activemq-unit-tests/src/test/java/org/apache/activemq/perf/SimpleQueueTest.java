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
package org.apache.activemq.perf;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Session;

/**
 * 
 */
public class SimpleQueueTest extends SimpleTopicTest {

    protected long initialConsumerDelay = 0;
    protected long consumerSleep = 0;
    @Override
    protected Destination createDestination(Session s, String destinationName) throws JMSException {
        return s.createQueue(destinationName);
    }
    
    @Override
    protected void setUp() throws Exception {
        numberOfConsumers = 1;
        super.setUp();
    }
    
    @Override
    protected PerfConsumer createConsumer(ConnectionFactory fac, Destination dest, int number) throws JMSException {
        PerfConsumer consumer =  new PerfConsumer(fac, dest);
        consumer.setInitialDelay(this.initialConsumerDelay);
        consumer.setSleepDuration(this.consumerSleep);
        boolean enableAudit = numberOfConsumers <= 1;
        System.err.println("Enable Audit = " + enableAudit);
        consumer.setEnableAudit(enableAudit);
        return consumer;
    }

}
