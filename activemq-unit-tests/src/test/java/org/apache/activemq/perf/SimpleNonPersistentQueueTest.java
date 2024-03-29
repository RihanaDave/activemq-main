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

import java.util.ArrayList;
import java.util.List;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;

/**
 * 
 */
public class SimpleNonPersistentQueueTest extends SimpleQueueTest {

    @Override
    protected void setUp() throws Exception {
        numberOfConsumers = 1;
        numberofProducers = 1;
        super.setUp();
    }
    @Override
    protected PerfProducer createProducer(ConnectionFactory fac, Destination dest, int number, byte[] payload) throws JMSException {
        PerfProducer pp = new PerfProducer(fac, dest, payload);
        pp.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        //pp.setTimeToLive(100);
        return pp;
    }
    
    @Override
    protected PerfConsumer createConsumer(ConnectionFactory fac, Destination dest, int number) throws JMSException {
        PerfConsumer result =  new PerfConsumer(fac, dest);
        result.setInitialDelay(10*1000);
        boolean enableAudit = numberOfConsumers <= 1;
        System.err.println("Enable Audit = " + enableAudit);
        result.setEnableAudit(enableAudit);

        return result;
    }
    
    
    @Override
    protected void configureBroker(BrokerService answer,String uri) throws Exception {
       // answer.setPersistent(false);
        final List<PolicyEntry> policyEntries = new ArrayList<PolicyEntry>();
        final PolicyEntry entry = new PolicyEntry();
        entry.setQueue(">");
        entry.setMemoryLimit(1024 * 1024 * 1); // Set to 1 MB
        entry.setOptimizedDispatch(true);
        entry.setLazyDispatch(true);
        policyEntries.add(entry);

        
        final PolicyMap policyMap = new PolicyMap();
        policyMap.setPolicyEntries(policyEntries);
        answer.setDestinationPolicy(policyMap);
        super.configureBroker(answer, uri);
    }
    
}
