/**
 *
 * openutils for Log4j (https://github.com/openmindlab/openutils-log4j)
 * Copyright(C) 2005-2019, Openmind S.r.l. http://www.openmindonline.it
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package it.openutils.log4j2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.activation.DataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LoggingException;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.appender.ManagerFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.net.MimeMessageBuilder;
import org.apache.logging.log4j.core.util.CyclicBuffer;
import org.apache.logging.log4j.core.util.NameUtil;
import org.apache.logging.log4j.core.util.NetUtils;
import org.apache.logging.log4j.core.util.datetime.FastDateFormat;
import org.apache.logging.log4j.util.PropertiesUtil;


/**
 * see https://issues.apache.org/jira/browse/LOG4J2-1192
 * Like standard org.apache.logging.log4j.core.appender.SmtpManager but with some additional features (subject with
 * layout, burst summarizing).
 *
 * This class is bases on copy&paste of original code because SmtpManager contains a lot of private stuff which needs
 * slight changes.
 *
 * @see ExtendedSmtpAppender
 * @author Thies Wellpott
 */
public class ExtendedSmtpManager extends AbstractManager
{
    private static SMTPManagerFactory FACTORY = new SMTPManagerFactory();

    private FactoryData data;

    private PatternLayout subjectLayout;

    private Thread summarySender;

    private CyclicBuffer<LogEvent> buffer;

    private Session session;

    private volatile MimeMessage message;

    /** Create instance. Internal use, for public creation use getSMTPManager() */
    protected ExtendedSmtpManager(String name, Session session, MimeMessage message, FactoryData data)
    {
        super(null, name);
        this.session = session;
        this.message = message;
        this.data = data;
        this.subjectLayout = PatternLayout.newBuilder().withPattern(data.subject).withAlwaysWriteExceptions(false).build();

        this.buffer = new CyclicBuffer<>(LogEvent.class, data.numElements);
        // create and start background thread
        this.summarySender = startSummarySenderBackgroundThread();
    }

    /**
     * Factory method: get existing or create a new manager for SMTP messages.
     * 
     * @param data
     *            parameter data
     * @param contentType
     *            MIME content type of the layout, e.g. "text/plain" or "text/html". May be null.
     */
    public static ExtendedSmtpManager getSMTPManager(FactoryData data, String filterName, String contentType)
    {
        StringBuilder sb = new StringBuilder();
        if (data.to != null)
        {
            sb.append(data.to);
        }
        sb.append(':');
        if (data.cc != null)
        {
            sb.append(data.cc);
        }
        sb.append(':');
        if (data.bcc != null)
        {
            sb.append(data.bcc);
        }
        sb.append(':');
        if (data.from != null)
        {
            sb.append(data.from);
        }
        sb.append(':');
        if (data.replyto != null)
        {
            sb.append(data.replyto);
        }
        sb.append(':');
        if (data.subject != null)
        {
            sb.append(data.subject);
        }
        sb.append(':').append(data.burstSummarizingMillis);
        sb.append(':').append(data.bsCountInSubject);
        sb.append(':').append(data.bsMessagePrefixLength).append(data.bsMessageMaskDigits);
        sb.append(':').append(data.bsLoggername).append(data.bsExceptionClass).append(data.bsExceptionOrigin)
                .append(data.bsRootExceptionClass);
        sb.append(':');
        sb.append(data.protocol).append(':').append(data.host).append(':').append(data.port);
        sb.append(':').append(data.username).append(':').append(data.password); // values may be null
        sb.append(data.isDebug ? ":debug:" : "::");
        sb.append(filterName);

        String name = "SMTP:" + NameUtil.md5(sb.toString());

        ExtendedSmtpManager manager = getManager(name, FACTORY, data);
        if (contentType != null && contentType.length() > 0)
        {
            manager.lastContentType = contentType; // init from current layout
        }
        return manager;
    }

    /** "Close" the manager: stop the background thread and wait for its end (typically is very fast). */
    @Override
    protected boolean releaseSub(long timeout, TimeUnit timeUnit)
    {
        summarySender.interrupt();
        try
        {
            summarySender.join(2000); // if thead is currently sending emails, give it some time to finish
        }
        catch (InterruptedException e)
        {
            // ignore
        }
        checkSendSummary(null); // sending of remaining buffered emails
        return true;
    }

    /** Add event to internal buffer. */
    public void add(LogEvent event)
    {
        buffer.add(event);
    }

    /**
     * Send the contents of the cyclic buffer as an e-mail message.
     * 
     * @param layout
     *            The layout for formatting the events.
     * @param appendEvent
     *            The event that triggered the send.
     */
    public void sendEvents(Layout<?> layout, LogEvent appendEvent)
    {
        checkSendSummary(layout); // always send buffered emails before new events; also update layout/content type
        if (message == null)
        {
            connect();
        }
        try
        {
            // always empty the buffered events, create message text and subject
            LogEvent[] priorEvents = buffer.removeAll();
            byte[] rawBytes = formatContentToBytes(priorEvents, appendEvent, layout);
            String newSubject = null;
            if (subjectLayout != null)
            {
                newSubject = subjectLayout.toSerializable(appendEvent);
            }

            SummarizeData sumData = summarizeEvent(appendEvent);
            if (sumData != null)
            {
                // record data
                if (sumData.secondEventMsg == null)
                {
                    sumData.secondEventMsg = rawBytes;
                }
                sumData.lastEventMsg = rawBytes;
                sumData.lastSubject = newSubject; // note: this may be null
            }
            else
            {
                // send message
                String contentType = layout.getContentType();
                String encoding = getEncoding(rawBytes, contentType);
                byte[] encodedBytes = encodeContentToBytes(rawBytes, encoding);
                InternetHeaders headers = getHeaders(contentType, encoding);
                MimeMultipart mp = getMimeMultipart(encodedBytes, headers);
                sendMultipartMessage(message, StringUtils.substringBefore(newSubject, "\n"), mp);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error occurred while sending e-mail notification.", e);
            // throw new LoggingException("Error occurred while sending email", e);
        }
    }

    protected byte[] formatContentToBytes(LogEvent[] priorEvents, LogEvent appendEvent, Layout<?> layout) throws IOException
    {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        writeContent(priorEvents, appendEvent, layout, raw);
        return raw.toByteArray();
    }

    private void writeContent(LogEvent[] priorEvents, LogEvent appendEvent, Layout<?> layout, ByteArrayOutputStream out)
            throws IOException
    {
        writeHeader(layout, out);
        writeBuffer(priorEvents, appendEvent, layout, out);
        writeFooter(layout, out);
    }

    protected void writeHeader(Layout<?> layout, OutputStream out) throws IOException
    {
        byte[] header = layout.getHeader();
        if (header != null)
        {
            out.write(header);
        }
    }

    protected void writeBuffer(LogEvent[] priorEvents, LogEvent appendEvent, Layout<?> layout, OutputStream out)
            throws IOException
    {
        for (LogEvent priorEvent : priorEvents)
        {
            byte[] bytes = layout.toByteArray(priorEvent);
            out.write(bytes);
        }

        byte[] bytes = layout.toByteArray(appendEvent);
        out.write(bytes);
    }

    protected void writeFooter(Layout<?> layout, OutputStream out) throws IOException
    {
        byte[] footer = layout.getFooter();
        if (footer != null)
        {
            out.write(footer);
        }
    }

    protected String getEncoding(byte[] rawBytes, String contentType)
    {
        DataSource dataSource = new ByteArrayDataSource(rawBytes, contentType);
        return MimeUtility.getEncoding(dataSource);
    }

    protected byte[] encodeContentToBytes(byte[] rawBytes, String encoding) throws MessagingException, IOException
    {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        encodeContent(rawBytes, encoding, encoded);
        return encoded.toByteArray();
    }

    protected void encodeContent(byte[] bytes, String encoding, ByteArrayOutputStream out) throws MessagingException, IOException
    {
        try (OutputStream encoder = MimeUtility.encode(out, encoding))
        {
            encoder.write(bytes);
        }
    }

    protected InternetHeaders getHeaders(String contentType, String encoding)
    {
        InternetHeaders headers = new InternetHeaders();
        headers.setHeader("Content-Type", contentType + "; charset=UTF-8"); //XXX layout.getCharset() would be great here, this way the layout explicitly must use this charset!
        headers.setHeader("Content-Transfer-Encoding", encoding);
        return headers;
    }

    protected MimeMultipart getMimeMultipart(byte[] encodedBytes, InternetHeaders headers) throws MessagingException
    {
        MimeMultipart mp = new MimeMultipart();
        MimeBodyPart part = new MimeBodyPart(headers, encodedBytes);
        mp.addBodyPart(part);
        return mp;
    }

    /**
     */
    protected void addMimeMultipart(MimeMultipart mp, byte[] rawBytes, String contentType) throws MessagingException, IOException
    {
        String encoding = getEncoding(rawBytes, contentType);
        byte[] encodedBytes = encodeContentToBytes(rawBytes, encoding);
        InternetHeaders headers = getHeaders(contentType, encoding);
        mp.addBodyPart(new MimeBodyPart(headers, encodedBytes));
    }

    /** Send the email message. Set subject if not null. */
    protected void sendMultipartMessage(MimeMessage msg, String subject, MimeMultipart mp) throws MessagingException
    {
        synchronized (msg)
        {
            String prevSubjectHeader = null;
            if (subject != null)
            {
                prevSubjectHeader = msg.getHeader("Subject", null); // use encoded header value instead of getSubject() 
                msg.setSubject(StringUtils.substringBefore(subject, "\n"));
            }
            msg.setContent(mp);
            msg.setSentDate(new Date());
            Transport.send(msg);

            // reset subject to old value, but only when no layout-subject is used (each msg gets its own subject then)
            msg.setHeader("Subject", prevSubjectHeader);

        }
    }

    /** Create message initialized with static data. But only, if not already present. */
    private synchronized void connect()
    {
        if (message != null)
        {
            return;
        }
        try
        {
            message = new MimeMessageBuilder(session).setFrom(data.from).setReplyTo(data.replyto)
                    .setRecipients(Message.RecipientType.TO, data.to).setRecipients(Message.RecipientType.CC, data.cc)
                    .setRecipients(Message.RecipientType.BCC, data.bcc).setSubject(data.subject).build();
        }
        catch (MessagingException e)
        {
            LOGGER.error("Could not set SmtpAppender message options.", e);
            message = null;
        }
    }

    // for getSummaryText()
    private static FastDateFormat dfTime = FastDateFormat.getTimeInstance(FastDateFormat.MEDIUM);

    /** Collects the email data. Key is getEventSummarizeKey(), value is data for this summary. */
    private Map<String, SummarizeData> summarizeDataCollector = new HashMap<>();

    // for getEventSummarizeKey(): find all consecutive digits
    private static Pattern digitPattern = Pattern.compile("\\d+");

    /** Create and start background thread that periodically calls checkSendSummary(). */
    private Thread startSummarySenderBackgroundThread()
    {
        // do not summarize if not requested
        if (data.burstSummarizingMillis <= 0)
            return null;

        Thread thread = new Thread()
        {
            @Override
            public void run()
            {
                long sleepTime = Math.max(data.burstSummarizingMillis / 10, 500);
                LOGGER.debug("SMTPx background thread {} started, sleep ms {}", getName(), sleepTime);
                try
                {
                    // at beginning sleep for collection time (nothing to do during this)
                    sleep(data.burstSummarizingMillis);
                    while (!interrupted())
                    {
                        sleep(sleepTime);
                        checkSendSummary(null);
                    }
                }
                catch (InterruptedException e)
                {
                    // ignore, silently end the thread in this case
                }
                LOGGER.debug("SMTPx background thread {} ended.", getName());
            } // run()
        };
        thread.setDaemon(true);
        thread.setName(this.getClass().getSimpleName() + "-" + getName());
        thread.start();
        return thread;
    }

    /**
     * Generate string key for the logging event.
     */
    private String getEventSummarizeKey(LogEvent event)
    {
        StringBuilder sb = new StringBuilder(300);

        // logger name
        if (data.bsLoggername)
        {
            sb.append("~~LgNm:").append(event.getLoggerName()); // may be null
        }
        // first part of message text
        if (data.bsMessagePrefixLength > 0)
        {
            String msgText = event.getMessage().getFormattedMessage();
            if (msgText.length() > data.bsMessagePrefixLength)
            {
                msgText = msgText.substring(0, data.bsMessagePrefixLength);
            }
            if (data.bsMessageMaskDigits)
            {
                msgText = digitPattern.matcher(msgText).replaceAll("#"); // mask out all digits
            }
            sb.append("~~Msg:").append(msgText);
        }
        Throwable eventEx = event.getThrown(); // note: getMessage().getThrowable() is always null for simple string
        if (eventEx != null)
        {
            if (data.bsExceptionClass)
            {
                // exception class
                sb.append("~~ExCl:").append(eventEx.getClass().getName());
            }
            if (data.bsExceptionOrigin)
            {
                // exception occurence: first stacktrace line
                StackTraceElement[] stackTrace = eventEx.getStackTrace();
                if (stackTrace != null && stackTrace.length > 0 && stackTrace[0] != null)
                {
                    sb.append("~~ExO:").append(stackTrace[0].toString());
                }
            }
            if (data.bsRootExceptionClass)
            {
                // root exception class
                Throwable rootEx = eventEx;
                while (rootEx != null)
                {
                    Throwable cause = rootEx.getCause();
                    if (cause == null || cause == rootEx)
                        break;
                    rootEx = cause;
                }
                sb.append("~~RExCl:").append(rootEx.getClass().getName());
            }
        }

        return sb.toString();
    }

    /**
     * Summarize event data. Creates new sum data if needed. Returns null, when event should be sent out directly.
     * Otherwise sum data is returned, whose data should be set/updated from the current event.
     */
    private SummarizeData summarizeEvent(LogEvent logEvent)
    {
        // do not summarize if not requested
        if (data.burstSummarizingMillis <= 0)
        {
            return null;
        }

        String eventKey = getEventSummarizeKey(logEvent);
        synchronized (summarizeDataCollector)
        {
            SummarizeData sumData = summarizeDataCollector.get(eventKey);
            if (sumData == null)
            {
                // first event with this key, create new summary data (with numOfMsg == 0)
                sumData = new SummarizeData(eventKey, logEvent.getTimeMillis());
                summarizeDataCollector.put(eventKey, sumData);
                return null; // first event should always be sent
            }
            else
            {
                sumData.lastEventMillis = logEvent.getTimeMillis();
                sumData.numOfMsg++;
                if (sumData.numOfMsg == 1)
                {
                    sumData.secondEventMillis = sumData.lastEventMillis;
                }
                return sumData;
            }
        } // synchronized
    }

    /**
     * Check if summary has to be sent and do so. Performs cleanup of summarizeDataCollector, too (e.g. old messages).
     * Contains fast check to not execute very often, so can be called very often without penalty.
     */
    private void checkSendSummary(Layout<?> layout)
    {
        if (data.burstSummarizingMillis <= 0)
            return;
        long now = System.currentTimeMillis();
        if (now - lastSummaryCheckMillis < data.burstSummarizingMillis / 10)
            return;
        lastSummaryCheckMillis = now;
        LOGGER.trace("SMTPx.checkSendSummary() exec - {}={}", now, dfTime.format(now));
        if (layout != null)
        { // init this value here
            lastContentType = layout.getContentType();
        }
        List<SummarizeData> toSend = new ArrayList<>();
        LOGGER.trace("  - collector before: {}", summarizeDataCollector);
        synchronized (summarizeDataCollector)
        {
            for (Iterator<Entry<String, SummarizeData>> iter = summarizeDataCollector.entrySet().iterator(); iter.hasNext();)
            {
                SummarizeData sumData = iter.next().getValue();
                if (sumData.numOfMsg == 0 && now - sumData.firstEventMillis > data.burstSummarizingMillis)
                {
                    // only first message recorded which is too old now, simply remove entry
                    iter.remove();
                }
                else if (sumData.numOfMsg > 0 && now - sumData.secondEventMillis > data.burstSummarizingMillis)
                {
                    // time from first collected message to now is too long, send message (see
                    // below) and remove collected info
                    toSend.add(sumData);
                    iter.remove();
                }
            } // for
              // for all emails to send, add a new info with last message as first message time, so
              // next message with same content will not be sent as first one on its own
            for (SummarizeData sumData : toSend)
            {
                summarizeDataCollector.put(sumData.sumKey, new SummarizeData(sumData.sumKey, sumData.lastEventMillis));
            }
        } // synchronized
        LOGGER.trace("  - collector afterwards: {}, toSend=#{}", summarizeDataCollector, toSend.size());
        // perform (expensive) sending outside of sync. block
        // sort toSend according to sumData.secondEventMillis
        toSend.sort((sd1, sd2) -> Long.compare(sd1.secondEventMillis, sd2.secondEventMillis));
        for (SummarizeData sumData : toSend)
        {
            sendSummary(sumData);
        }
    }

    private volatile long lastSummaryCheckMillis = System.currentTimeMillis(); // creation time is the starting point

    private String lastContentType = "text/plain"; // reasonable default value, will be overwritten in most cases

    /** Send summary email. */
    public void sendSummary(SummarizeData sumData)
    {
        LOGGER.debug("SMTPx.sendSummary() - {}", sumData);
        if (message == null)
        {
            connect();
        }
        try
        {
            String eventContentType = lastContentType;
            MimeMultipart mp = new MimeMultipart();
            if (sumData.numOfMsg >= 2)
            {
                // append summary header information text (if at least two messages present)
                String str = "*** Summarized " + sumData.numOfMsg + " similar log events ***\nDuring "
                        + ((sumData.lastEventMillis - sumData.secondEventMillis + 500) / 1000) + " seconds:  first at "
                        + dfTime.format(sumData.secondEventMillis) + ",  last at " + dfTime.format(sumData.lastEventMillis)
                        + ".\nFirst and last event message follow.\n" + "(summary based on:  " + sumData.sumKey + ")\n";
                addMimeMultipart(mp, str.getBytes(StandardCharsets.UTF_8), "text/plain");
                // append second log event
                addMimeMultipart(mp, sumData.secondEventMsg, eventContentType);
            } // if
              // and the last (or only) log event
            addMimeMultipart(mp, sumData.lastEventMsg, eventContentType);

            String newSubject = sumData.lastSubject; // is only set for subject with layout
            if (data.bsCountInSubject != '\0' && sumData.numOfMsg > 1)
            {
                if (newSubject == null)
                {
                    newSubject = data.subject; // do not use message.getSubject() because it is expensive
                }
                if (data.bsCountInSubject == 'F' || data.bsCountInSubject == 'S')
                {
                    newSubject = sumData.numOfMsg + "x  " + newSubject;
                }
                else
                {
                    newSubject = newSubject + "  [" + sumData.numOfMsg + "x]";
                }
            } // if

            sendMultipartMessage(message, newSubject, mp);
        }
        catch (Exception e)
        {
            LOGGER.error("Error occurred while sending summary e-mail notification.", e);
            throw new LoggingException("Error occurred while sending summary email", e);
        }
    }

    /** Summary data. Directly access the fields. */
    private static class SummarizeData
    {
        /** Key in the collector map. */
        String sumKey;

        /** Time when first (the always sent) event occurred. */
        long firstEventMillis;

        /** Number of collected messages (the first that is always sent does not count here). */
        int numOfMsg;

        /** Time when second event occurred. */
        long secondEventMillis;

        /** Formatted message text of the second log event. */
        byte[] secondEventMsg;

        /** Time when second event occurred. */
        long lastEventMillis;

        /** Formatted message text of the last collected log event. */
        byte[] lastEventMsg;

        /** Subject of last message; may be null if constant. */
        String lastSubject;

        SummarizeData(String sumKey, long firstEventMillis)
        {
            this.sumKey = sumKey;
            this.firstEventMillis = firstEventMillis;
        }

        @Override
        public String toString()
        {
            return "SumData[#" + numOfMsg + " / " + firstEventMillis + "=" + dfTime.format(firstEventMillis) + " / "
                    + secondEventMillis + "=" + dfTime.format(secondEventMillis) + " / " + lastEventMillis + "="
                    + dfTime.format(lastEventMillis) + " / " + lastSubject + "]";
        }

    } // inner class

    /** Factory data: simple data collection of config attributes. */
    static class FactoryData
    {
        private String to;

        private String cc;

        private String bcc;

        private String from;

        private String replyto;

        private String subject;

        private String protocol;

        private String host;

        private int port;

        private String username;

        private String password;

        private boolean isDebug;

        private int numElements;

        /** <= 0 for no burst summarizing. */
        private long burstSummarizingMillis;

        /** \0 for no count info, F/S for front/start, other (B/E) for behind/end. */
        private char bsCountInSubject;

        // config which parameters of a log event to consider
        private boolean bsLoggername;

        /** <= 0 for no message. */
        private int bsMessagePrefixLength;

        private boolean bsMessageMaskDigits;

        private boolean bsExceptionClass;

        private boolean bsExceptionOrigin;

        private boolean bsRootExceptionClass;

        public FactoryData(String to, String cc, String bcc, String from, String replyto, String subject, String protocol,
                String host, int port, String username, String password, boolean isDebug, int numElements,
                long burstSummarizingMillis, char bsCountInSubject, boolean bsLoggername, int bsMessagePrefixLength,
                boolean bsMessageMaskDigits, boolean bsExceptionClass, boolean bsExceptionOrigin, boolean bsRootExceptionClass)
        {
            this.to = to;
            this.cc = cc;
            this.bcc = bcc;
            this.from = from;
            this.replyto = replyto;
            this.subject = subject;
            this.protocol = protocol;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
            this.isDebug = isDebug;
            this.numElements = numElements;
            this.burstSummarizingMillis = burstSummarizingMillis;
            this.bsCountInSubject = Character.toUpperCase(bsCountInSubject);
            this.bsLoggername = bsLoggername;
            this.bsMessagePrefixLength = bsMessagePrefixLength;
            this.bsMessageMaskDigits = bsMessageMaskDigits;
            this.bsExceptionClass = bsExceptionClass;
            this.bsExceptionOrigin = bsExceptionOrigin;
            this.bsRootExceptionClass = bsRootExceptionClass;
        }

    } // inner class

    /**
     * Factory to create the SMTP Manager.
     */
    private static class SMTPManagerFactory implements ManagerFactory<ExtendedSmtpManager, FactoryData>
    {
        @Override
        public ExtendedSmtpManager createManager(String name, FactoryData data)
        {
            String prefix = "mail." + data.protocol;

            Properties properties = PropertiesUtil.getSystemProperties();
            properties.put("mail.transport.protocol", data.protocol);
            if (properties.getProperty("mail.host") == null)
            {
                // Prevent an UnknownHostException in Java 7
                properties.put("mail.host", NetUtils.getLocalHostname());
            }

            if (null != data.host)
            {
                properties.put(prefix + ".host", data.host);
            }
            if (data.port > 0)
            {
                properties.put(prefix + ".port", String.valueOf(data.port));
            }

            Authenticator authenticator = buildAuthenticator(data.username, data.password);
            if (null != authenticator)
            {
                properties.put(prefix + ".auth", "true");
            }

            if (data.port > 25) // tipically port 587 for tls
            {
                properties.put(prefix + ".starttls.enable", "true");
            }

            Session session = Session.getInstance(properties, authenticator);
            session.setProtocolForAddress("rfc822", data.protocol);
            session.setDebug(data.isDebug);
            MimeMessage message;

            try
            {
                message = new MimeMessageBuilder(session).setFrom(data.from).setReplyTo(data.replyto)
                        .setRecipients(Message.RecipientType.TO, data.to).setRecipients(Message.RecipientType.CC, data.cc)
                        .setRecipients(Message.RecipientType.BCC, data.bcc).setSubject(data.subject).build();
            }
            catch (MessagingException e)
            {
                LOGGER.error("Could not set SmtpAppender message options.", e);
                message = null;
            }

            return new ExtendedSmtpManager(name, session, message, data);
        }

        private Authenticator buildAuthenticator(String username, String password)
        {
            if (null != password && null != username)
            {
                return new Authenticator()
                {
                    private PasswordAuthentication passwordAuthentication = new PasswordAuthentication(username, password);

                    @Override
                    protected PasswordAuthentication getPasswordAuthentication()
                    {
                        return passwordAuthentication;
                    }
                };
            }
            return null;
        }

    } // inner class Factory

}
