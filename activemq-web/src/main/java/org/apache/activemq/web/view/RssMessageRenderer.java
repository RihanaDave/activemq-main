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
package org.apache.activemq.web.view;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.QueueBrowser;
import jakarta.jms.TextMessage;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedOutput;

/**
 * This renderer uses XStream to render messages on a queue as full XML elements
 * 
 * 
 */
public class RssMessageRenderer extends SimpleMessageRenderer {

    // private String feedType = "atom_0.3";
    private String feedType = "rss_2.0";
    private SyndFeed feed;
    private String description = "This feed is auto-generated by Apache ActiveMQ";
    private String entryContentType = "text/plain";

    public void renderMessage(PrintWriter writer, HttpServletRequest request, HttpServletResponse response, QueueBrowser browser, Message message) throws JMSException {
        SyndFeed feed = getFeed(browser, request);
        List<SyndEntry> entries = feed.getEntries();
        SyndEntry entry = createEntry(browser, message, request);
        SyndContent description = createEntryContent(browser, message, request);
        entry.setDescription(description);
        entries.add(entry);
    }

    // Properties
    // -------------------------------------------------------------------------
    public String getDescription() {
        return description;
    }

    public void setDescription(String feedDescription) {
        this.description = feedDescription;
    }

    public String getFeedType() {
        return feedType;
    }

    public void setFeedType(String feedType) {
        this.feedType = feedType;
    }

    public String getEntryContentType() {
        return entryContentType;
    }

    public void setEntryContentType(String entryContentType) {
        this.entryContentType = entryContentType;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    protected void printFooter(PrintWriter writer, QueueBrowser browser, HttpServletRequest request) throws IOException, JMSException, ServletException {
        // now lets actually write out the content
        SyndFeed feed = getFeed(browser, request);
        SyndFeedOutput output = new SyndFeedOutput();
        try {
            output.output(feed, writer);
        } catch (FeedException e) {
            throw new ServletException(e);
        }
    }

    protected void printHeader(PrintWriter writer, QueueBrowser browser, HttpServletRequest request) throws IOException, JMSException {
    }

    public SyndFeed getFeed(QueueBrowser browser, HttpServletRequest request) throws JMSException {
        if (feed == null) {
            feed = createFeed(browser, request);
        }
        return feed;
    }

    protected SyndEntry createEntry(QueueBrowser browser, Message message, HttpServletRequest request) throws JMSException {
        SyndEntry entry = new SyndEntryImpl();
        String title = message.getJMSMessageID();
        entry.setTitle(title);
        String link = request.getRequestURI() + "?msgId=" + title;
        entry.setLink(link);
        entry.setPublishedDate(new Date(message.getJMSTimestamp()));
        return entry;
    }

    protected SyndContent createEntryContent(QueueBrowser browser, Message message, HttpServletRequest request) throws JMSException {
        SyndContent description = new SyndContentImpl();
        description.setType(entryContentType);

        if (message instanceof TextMessage) {
            String text = ((TextMessage)message).getText();
            description.setValue(text);
        }
        return description;
    }

    protected SyndFeed createFeed(QueueBrowser browser, HttpServletRequest request) throws JMSException {
        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType(feedType);

        String title = browser.getQueue().toString();
        String selector = browser.getMessageSelector();
        if (selector != null) {
            title += " with selector: " + selector;
        }
        feed.setTitle(title);
        feed.setLink(request.getRequestURI());
        feed.setDescription(getDescription());
        return feed;
    }

}
