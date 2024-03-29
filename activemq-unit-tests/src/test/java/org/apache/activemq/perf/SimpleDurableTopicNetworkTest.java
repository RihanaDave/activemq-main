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
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;


public class SimpleDurableTopicNetworkTest extends SimpleNetworkTest {
    
    protected void setUp() throws Exception {
        numberofProducers=1;
        numberOfConsumers=1;
        sampleCount=1000;
        playloadSize = 1024;
        super.setUp();
    }
    
    
    protected PerfProducer createProducer(ConnectionFactory fac, Destination dest, int number, byte payload[]) throws JMSException {
        PerfProducer pp = new PerfProducer(fac, dest, payload);
        pp.setDeliveryMode(DeliveryMode.PERSISTENT);
        return pp;
    }

    protected PerfConsumer createConsumer(ConnectionFactory fac, Destination dest, int number) throws JMSException {
        return new PerfConsumer(fac, dest, "subs:" + number);
    }
}
