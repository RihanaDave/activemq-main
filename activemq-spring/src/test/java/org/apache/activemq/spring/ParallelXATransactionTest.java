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

package org.apache.activemq.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.test.annotation.Commit;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.Resource;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import java.util.Arrays;

@RunWith(SpringJUnit4ClassRunner.class)

@ContextConfiguration(locations = {"classpath:spring/xa.xml"})
@Transactional
@Commit
public class ParallelXATransactionTest {

    private static final Logger LOG = LoggerFactory.getLogger(ParallelXATransactionTest.class);

    @Resource(name = "transactionManager")
    PlatformTransactionManager txManager = null;

    @Resource(name = "transactionManager2")
    PlatformTransactionManager txManager2 = null;


    @Resource(name = "jmsTemplate")
    JmsTemplate jmsTemplate = null;

    @Resource(name = "jmsTemplate2")
    JmsTemplate jmsTemplate2 = null;


    public static final int NB_MSG = 100;
    public static final String BODY = Arrays.toString(new int[1024]);
    private static final String[] QUEUES = {"TEST.queue1", "TEST.queue2", "TEST.queue3", "TEST.queue4", "TEST.queue5"};
    private static final String AUDIT = "TEST.audit";
    public static final int SLEEP = 500;

    @Test
    @DirtiesContext
    public void testParalellXaTx() throws Exception {


        class ProducerThread extends Thread {

            PlatformTransactionManager txManager;
            JmsTemplate jmsTemplate;
            Exception lastException;


            public ProducerThread(JmsTemplate jmsTemplate, PlatformTransactionManager txManager) {
               this.jmsTemplate = jmsTemplate;
               this.txManager = txManager;
            }

            public void run() {
                int i = 0;
                while (i++ < 10) {

                    try {
                        Thread.sleep((long) (Math.random() * SLEEP));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    TransactionTemplate tt = new TransactionTemplate(this.txManager);


                    try {
                        tt.execute(new TransactionCallbackWithoutResult() {
                            @Override
                            protected void doInTransactionWithoutResult(TransactionStatus status) {
                                try {

                                    for (final String queue : QUEUES) {
                                        jmsTemplate.send(queue + "," + AUDIT, new MessageCreator() {
                                            public Message createMessage(Session session) throws JMSException {
                                                return session.createTextMessage("P1: " + queue + " - " + BODY);
                                            }
                                        });
                                        Thread.sleep((long) (Math.random() * SLEEP));
                                        LOG.info("P1: Send msg to " + queue + "," + AUDIT + " thread:" + Thread.currentThread().getName());
                                    }

                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Assert.fail("Exception occurred in thread: " + Thread.currentThread().getName() + " exception:" + e);
                                }
                            }
                        });
                    } catch (TransactionException e) {
                        lastException = e;
                        break;
                    }

                }
            }

            public Exception getLastException() {
                return lastException;
            }
        }


        ProducerThread t1 = new ProducerThread(jmsTemplate, txManager);
        t1.setName("XaTX");
        ProducerThread t2 = new ProducerThread(jmsTemplate2, txManager2);
        t2.setName("LocalTX");
        t1.start();
        t2.start();

        t1.join();
        t2.join();

        if (t1.getLastException() != null) {
            Assert.fail("Exception occurred " + t1.getLastException());
        }

        if (t2.getLastException() != null) {
            Assert.fail("Exception occurred " + t2.getLastException());
        }

    }

}
