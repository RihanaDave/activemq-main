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
import jakarta.jms.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class SlowConsumer extends PerfConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(SlowConsumer.class);

    public SlowConsumer(ConnectionFactory fac, Destination dest, String consumerName) throws JMSException {
        super(fac, dest, consumerName);
    }

    public SlowConsumer(ConnectionFactory fac, Destination dest) throws JMSException {
        super(fac, dest, null);
    }

    public void onMessage(Message msg) {
        super.onMessage(msg);
        LOG.debug("GOT A MSG " + msg);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
