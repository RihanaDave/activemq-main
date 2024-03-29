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
package org.apache.activemq.jndi;

import jakarta.jms.XAConnectionFactory;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.InitialContextFactory;

import org.apache.activemq.ActiveMQXASslConnectionFactory;

public class XASslConnectionFactoryTest extends ActiveMQInitialContextFactoryTest {

    public void testConnectionFactoriesIsXA() throws NamingException {
        Object factory = context.lookup(getConnectionFactoryLookupName());
        assertTrue("connection factory implements XA", factory instanceof XAConnectionFactory);
        assertTrue("is always sync send", ((ActiveMQXASslConnectionFactory)factory).isAlwaysSyncSend());
    }

    @Override
    protected void configureEnvironment() {
        environment.put("xa", "true");
        environment.put(Context.PROVIDER_URL, "vm://locahost?jms.alwaysSyncSend=true");
        super.configureEnvironment();
    }

    @Override
    protected InitialContextFactory getInitialContextFactory() {
        return new ActiveMQSslInitialContextFactory();
    }
}
