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
package org.apache.activemq.broker.policy;

import jakarta.jms.Destination;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.IndividualDeadLetterStrategy;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.command.ActiveMQQueue;

/**
 * for durable subs, allow a dlq per subscriber such that poison messages are not duplicates
 * on the dlq and such that rejecting consumers can be identified
 * https://issues.apache.org/jira/browse/AMQ-3003
 */
public class PerDurableConsumerDeadLetterTest extends DeadLetterTest {

    private static final String CLIENT_ID = "george";

    @Override
    protected BrokerService createBroker() throws Exception {
        BrokerService broker = super.createBroker();

        PolicyEntry policy = new PolicyEntry();
        IndividualDeadLetterStrategy strategy = new IndividualDeadLetterStrategy();
        strategy.setProcessNonPersistent(true);
        strategy.setDestinationPerDurableSubscriber(true);
        policy.setDeadLetterStrategy(strategy);

        PolicyMap pMap = new PolicyMap();
        pMap.setDefaultEntry(policy);

        broker.setDestinationPolicy(pMap);

        return broker;
    }

    @Override
    protected String createClientId() {
        return CLIENT_ID;
    }

    @Override
    protected Destination createDlqDestination() {
        String prefix = topic ? "ActiveMQ.DLQ.Topic." : "ActiveMQ.DLQ.Queue.";
        String destinationName = prefix + getClass().getName() + "." + getName();
        if (durableSubscriber) {
            String subName = // connectionId:SubName
                CLIENT_ID + ":" + getDestination().toString();
            destinationName += "." + subName ;
        }
        return new ActiveMQQueue(destinationName);
    }
}
