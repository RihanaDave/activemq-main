/*
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
package org.apache.activemq.junit;

import java.net.URI;
import jakarta.jms.JMSException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQDestination;

public class ActiveMQTopicPublisherResource extends AbstractActiveMQProducerResource {
    public ActiveMQTopicPublisherResource(String destinationName, ActiveMQConnectionFactory connectionFactory) {
        super(destinationName, connectionFactory);
    }

    public ActiveMQTopicPublisherResource(String destinationName, URI brokerURI) {
        super(destinationName, brokerURI);
    }

    public ActiveMQTopicPublisherResource(String destinationName, EmbeddedActiveMQBroker embeddedActiveMQBroker) {
        super(destinationName, embeddedActiveMQBroker);
    }

    public ActiveMQTopicPublisherResource(String destinationName, URI brokerURI, String userName, String password) {
        super(destinationName, brokerURI, userName, password);
    }

    @Override
    public String getDestinationName() {
        try {
            if (producer != null && producer.getDestination() != null) {
                return producer.getDestination().toString();
            }
        } catch (JMSException e) {
            // eat this
        }

        return null;
    }

    @Override
    public byte getDestinationType() {
        return ActiveMQDestination.TOPIC_TYPE;
    }

    @Override
    protected void createClient() throws JMSException {
        producer = session.createProducer(destination);
    }
}
