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
package org.apache.activemq.ra;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.QueueConnection;
import jakarta.jms.QueueConnectionFactory;
import jakarta.jms.TopicConnection;
import jakarta.jms.TopicConnectionFactory;

/**
 * A {@link ConnectionFactory} implementation which creates connections which can
 * be used with the ActiveMQ JCA Resource Adapter to publish messages using the
 * same underlying JMS session that is used to dispatch messages.
 *
 * 
 */
public class InboundConnectionProxyFactory implements ConnectionFactory, QueueConnectionFactory, TopicConnectionFactory {

    public Connection createConnection() throws JMSException {
        return new InboundConnectionProxy();
    }

    public Connection createConnection(String userName, String password) throws JMSException {
        return createConnection();
    }

    public QueueConnection createQueueConnection() throws JMSException {
        return new InboundConnectionProxy();
    }

    public QueueConnection createQueueConnection(String userName, String password) throws JMSException {
        return createQueueConnection();
    }

    public TopicConnection createTopicConnection() throws JMSException {
        return new InboundConnectionProxy();
    }

    public TopicConnection createTopicConnection(String userName, String password) throws JMSException {
        return createTopicConnection();
    }
    
    /**
     * @return Returns the JMSContext.
     */
    @Override
    public JMSContext createContext() {
        throw new UnsupportedOperationException("createContext() is not supported");
    }

    /**
     * @return Returns the JMSContext.
     */
    @Override
    public JMSContext createContext(String userName, String password) {
        throw new UnsupportedOperationException("createContext(userName, password) is not supported");
    }

    /**
     * @return Returns the JMSContext.
     */
    @Override
    public JMSContext createContext(String userName, String password, int sessionMode) {
        throw new UnsupportedOperationException("createContext(userName, password, sessionMode) is not supported");
    }

    /**
     * @return Returns the JMSContext.
     */
    @Override
    public JMSContext createContext(int sessionMode) {
        throw new UnsupportedOperationException("createContext(sessionMode) is not supported");
    }
}
