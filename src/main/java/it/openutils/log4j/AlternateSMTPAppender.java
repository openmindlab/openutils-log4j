/*
 * Copyright Openmind http://www.openmindonline.it
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.openutils.log4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.TriggeringEventEvaluator;


/**
 * An alternative of <code>org.apache.log4j.net.SMTPAppender</code> with few differences:
 * <ul>
 * <li>allow customizing the mail subject using a pattern</li>
 * <li>can be configured with a timeout (in seconds), it will only send messages after this timeout</li>
 * <li>it will send a mail for every single message (bufferSize is not supported), but it will aggregate any identical
 * log event received during the timeout. Identical events are log with same message and same stack trace</li>
 * </ul>
 * 
 * <pre>
 *  &lt;appender name="mail" class="it.openutils.log4j.AlternateSMTPAppender">
 *      &lt;param name="Threshold" value="ERROR" />
 *      &lt;param name="To" value="logs@example.com" />
 *      &lt;param name="From" value="info@example.com" />
 *      &lt;param name="SMTPHost" value="localhost" />
 *      &lt;param name="Timeout" value="180" />
 *      &lt;param name="Subject" value="[EXAMPLE] %m" />
 *      &lt;layout class="it.openutils.log4j.FilteredPatternLayout">
 *          &lt;param name="ConversionPattern" value="%-5p  %c %d{dd.MM.yyyy HH:mm:ss} -- %m%n" />
 *          &lt;param name="Header"
 *          value="
 *        ===================================
 *        Myapp (production environment)
 *        Date: %d{dd.MM.yyyy HH:mm:ss}
 *        ===================================
 *          " />
 *      &lt;/layout>
 *  &lt;/appender>
 * </pre>
 * 
 * @author Fabrizio Giustina
 * @version $Id: $
 */
public class AlternateSMTPAppender extends AppenderSkeleton
{

    private String to;

    private String from;

    private Layout subjectLayout;

    private String smtpHost;

    private boolean locationInfo;

    private Timer timer = new Timer("log4j mail appender", true);

    private TimerTask timerTask;

    private int timeout;

    protected Map<LoggingEventAggregator, LoggingEventAggregator> events = new LinkedHashMap<LoggingEventAggregator, LoggingEventAggregator>()
    {

        /**
         *
         */
        private static final long serialVersionUID = 1L;

        /**
         * {@inheritDoc}
         */
        @Override
        public LoggingEventAggregator put(LoggingEventAggregator key, LoggingEventAggregator value)
        {
            LoggingEventAggregator lea = this.get(key);
            if (lea != null)
            {
                lea.incrementCount();
                return lea;
            }

            return super.put(key, value);
        }

    };

    protected Message msg;

    protected TriggeringEventEvaluator evaluator;

    /**
     * The default constructor will instantiate the appender with a {@link TriggeringEventEvaluator} that will trigger
     * on events with level ERROR or higher.
     */
    public AlternateSMTPAppender()
    {
        this(new DefaultEvaluator());

        // force loading this class
        MimeBodyPart.class.getName();
        MimeUtility.class.getName();
    }

    /**
     * Use <code>evaluator</code> passed as parameter as the {@link TriggeringEventEvaluator} for this SMTPAppender.
     */
    public AlternateSMTPAppender(TriggeringEventEvaluator evaluator)
    {
        this.evaluator = evaluator;
    }

    /**
     * Activate the specified options, such as the smtp host, the recipient, from, etc.
     */
    @Override
    public void activateOptions()
    {
        Properties props = new Properties(System.getProperties());
        if (smtpHost != null)
        {
            props.put("mail.smtp.host", smtpHost);
        }

        Session session = Session.getInstance(props, null);
        // session.setDebug(true);
        msg = new MimeMessage(session);

        try
        {
            if (from != null)
            {
                msg.setFrom(getAddress(from));
            }
            else
            {
                msg.setFrom();
            }

            msg.setRecipients(Message.RecipientType.TO, parseAddress(to));
        }
        catch (MessagingException e)
        {
            LogLog.error("Could not activate SMTPAppender options.", e);
        }
    }

    /**
     * Perform SMTPAppender specific appending actions, mainly adding the event to a cyclic buffer and checking if the
     * event triggers an e-mail to be sent.
     */
    @Override
    public void append(LoggingEvent event)
    {

        if (!checkEntryConditions())
        {
            return;
        }

        event.getThreadName();
        event.getNDC();
        if (locationInfo)
        {
            event.getLocationInformation();
        }

        LoggingEventAggregator leg = new LoggingEventAggregator(event);

        if (evaluator.isTriggeringEvent(event))
        {
            if (timeout == 0)
            {
                // send immediately
                synchronized (events)
                {
                    Collection<LoggingEventAggregator> le = new ArrayList<LoggingEventAggregator>();
                    le.add(leg);
                    sendBuffer(le);
                }
                return;
            }
            else
            {
                events.put(leg, leg);

                if (timerTask == null)

                {

                    timerTask = new TimerTask()
                    {

                        @Override
                        public void run()
                        {
                            Collection<LoggingEventAggregator> le;
                            synchronized (events)
                            {
                                le = new ArrayList<LoggingEventAggregator>(events.values());
                                events.clear();
                                timerTask = null;
                            }

                            sendBuffer(le);
                        }
                    };

                    this.timer.schedule(this.timerTask, this.timeout * 1000L);

                }
            }
        }
    }

    /**
     * This method determines if there is a sense in attempting to append.
     * <p>
     * It checks whether there is a set output target and also if there is a set layout. If these checks fail, then the
     * boolean value <code>false</code> is returned.
     */
    protected boolean checkEntryConditions()
    {
        if (this.msg == null)
        {
            errorHandler.error("Message object not configured.");
            return false;
        }

        if (this.evaluator == null)
        {
            errorHandler.error("No TriggeringEventEvaluator is set for appender [" + name + "].");
            return false;
        }

        if (this.layout == null)
        {
            errorHandler.error("No layout set for appender named [" + name + "].");
            return false;
        }
        return true;
    }

    @Override
    public synchronized void close()
    {
        this.closed = true;
    }

    InternetAddress getAddress(String addressStr)
    {
        try
        {
            return new InternetAddress(addressStr);
        }
        catch (AddressException e)
        {
            errorHandler.error("Could not parse address [" + addressStr + "].", e, ErrorCode.ADDRESS_PARSE_FAILURE);
            return null;
        }
    }

    InternetAddress[] parseAddress(String addressStr)
    {
        try
        {
            return InternetAddress.parse(addressStr, true);
        }
        catch (AddressException e)
        {
            errorHandler.error("Could not parse address [" + addressStr + "].", e, ErrorCode.ADDRESS_PARSE_FAILURE);
            return null;
        }
    }

    /**
     * Returns value of the <b>To</b> option.
     */
    public String getTo()
    {
        return to;
    }

    /**
     * The <code>SMTPAppender</code> requires a {@link org.apache.log4j.Layout layout}.
     */
    @Override
    public boolean requiresLayout()
    {
        return true;
    }

    /**
     * Send the contents of the cyclic buffer as an e-mail message.
     */
    protected void sendBuffer(Collection<LoggingEventAggregator> eventsCollection)
    {

        // Note: this code already owns the monitor for this
        // appender. This frees us from needing to synchronize on 'cb'.
        try
        {

            for (LoggingEventAggregator lea : eventsCollection)
            {
                MimeBodyPart part = new MimeBodyPart();

                StringBuffer sbuf = new StringBuffer();
                String t = layout.getHeader();
                if (t != null)
                {
                    t = StringUtils.replace(t, "%o", Integer.toString(lea.getCount()));
                    t = StringUtils.replace(t, "%n", Layout.LINE_SEP);
                    sbuf.append(t);
                    sbuf.append("\n");
                }

                LoggingEvent event = lea.getLoggingEvent();

                if (this.subjectLayout != null)
                {
                    String subject = this.subjectLayout.format(event);

                    if (subject != null)
                    {
                        subject = subject.trim();
                        if (subject.indexOf("\n") > 0)
                        {
                            subject = subject.substring(0, subject.indexOf("\n"));
                        }
                    }

                    this.msg.setSubject(subject);
                }

                sbuf.append(layout.format(event));
                if (layout.ignoresThrowable())
                {
                    String[] s = event.getThrowableStrRep();
                    if (s != null)
                    {
                        for (String element : s)
                        {
                            sbuf.append(element);
                        }
                    }
                }
                t = layout.getFooter();
                if (t != null)
                {
                    t = StringUtils.replace(t, "%n", Layout.LINE_SEP);
                    sbuf.append(t);
                }
                part.setContent(sbuf.toString(), layout.getContentType());

                Multipart mp = new MimeMultipart();
                mp.addBodyPart(part);
                msg.setContent(mp);

                msg.setSentDate(new Date());
                Transport.send(msg);
            }

        }
        catch (Exception e)
        {
            LogLog.error("Error occured while sending e-mail notification.", e);
        }

    }

    /**
     * Returns value of the <b>EvaluatorClass</b> option.
     */
    public String getEvaluatorClass()
    {
        return evaluator == null ? null : evaluator.getClass().getName();
    }

    /**
     * Returns value of the <b>From</b> option.
     */
    public String getFrom()
    {
        return from;
    }

    /**
     * Returns value of the <b>Subject</b> option.
     */
    public String getSubject()
    {
        return subjectLayout.toString();
    }

    /**
     * The <b>From</b> option takes a string value which should be a e-mail address of the sender.
     */
    public void setFrom(String from)
    {
        this.from = from;
    }

    /**
     * The <b>Subject</b> option takes a string value which should be a the subject of the e-mail message.
     */
    public void setSubject(String subjectPattern)
    {
        this.subjectLayout = new PatternLayout(subjectPattern);
    }

    /**
     * This option is ignored!
     */
    @Deprecated
    public void setBufferSize(int bufferSize)
    {
        // kept as deprecated
        LogLog.warn("BufferSize property is deprecated for " + getClass().getName());
    }

    /**
     * The <b>SMTPHost</b> option takes a string value which should be a the host name of the SMTP server that will
     * send the e-mail message.
     */
    public void setSMTPHost(String smtpHost)
    {
        this.smtpHost = smtpHost;
    }

    /**
     * Returns value of the <b>SMTPHost</b> option.
     */
    public String getSMTPHost()
    {
        return smtpHost;
    }

    /**
     * The <b>To</b> option takes a string value which should be a comma separated list of e-mail address of the
     * recipients.
     */
    public void setTo(String to)
    {
        this.to = to;
    }

    /**
     * Returns value of the <b>BufferSize</b> option.
     */
    public int getBufferSize()
    {
        return 0;
    }

    /**
     * The <b>EvaluatorClass</b> option takes a string value representing the name of the class implementing the {@link
     * TriggeringEventEvaluator} interface. A corresponding object will be instantiated and assigned as the triggering
     * event evaluator for the SMTPAppender.
     */
    public void setEvaluatorClass(String value)
    {
        evaluator = (TriggeringEventEvaluator) OptionConverter.instantiateByClassName(
            value,
            TriggeringEventEvaluator.class,
            evaluator);
    }

    /**
     * @param value
     */
    public void setEvaluator(TriggeringEventEvaluator value)
    {
        evaluator = value;
    }

    /**
     * The <b>LocationInfo</b> option takes a boolean value. By default, it is set to false which means there will be
     * no effort to extract the location information related to the event. As a result, the layout that formats the
     * events as they are sent out in an e-mail is likely to place the wrong location information (if present in the
     * format).
     * <p>
     * Location information extraction is comparatively very slow and should be avoided unless performance is not a
     * concern.
     */
    public void setLocationInfo(boolean locationInfo)
    {
        this.locationInfo = locationInfo;
    }

    /**
     * Returns value of the <b>LocationInfo</b> option.
     */
    public boolean getLocationInfo()
    {
        return locationInfo;
    }

    /**
     * Returns the timeout.
     * @return the timeout
     */
    public int getTimeout()
    {
        return timeout;
    }

    /**
     * Sets the timeout.
     * @param timeout the timeout to set
     */
    public void setTimeout(int timeout)
    {
        this.timeout = timeout;
    }
}


class LoggingEventAggregator
{

    private LoggingEvent loggingEvent;

    private int count;

    public LoggingEventAggregator(LoggingEvent loggingEvent)
    {
        this.loggingEvent = loggingEvent;
        this.count = 1;
    }

    /**
     * Returns the loggingEvent.
     * @return the loggingEvent
     */
    public LoggingEvent getLoggingEvent()
    {
        return loggingEvent;
    }

    /**
     * Returns the count.
     * @return the count
     */
    public int getCount()
    {
        return count;
    }

    /**
     * Sets the count.
     * @param count the count to set
     */
    public void setCount(int count)
    {
        this.count = count;
    }

    public void incrementCount()
    {
        count++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;

        Object lem = loggingEvent.getMessage();
        String[] thstr = loggingEvent.getThrowableStrRep();
        result = prime * result + ((lem == null) ? 0 : lem.hashCode());
        result = prime * result + Arrays.hashCode(thstr);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final LoggingEventAggregator other = (LoggingEventAggregator) obj;

        Object lem = loggingEvent.getMessage();
        String[] thstr = loggingEvent.getThrowableStrRep();

        Object otherLem = other.loggingEvent.getMessage();
        String[] otherThstr = other.loggingEvent.getThrowableStrRep();

        int length = Math.min(otherThstr.length, thstr.length);
        length = Math.min(10, length);

        otherThstr = (String[]) ArrayUtils.subarray(otherThstr, 0, length);
        String[] thisThstr = (String[]) ArrayUtils.subarray(thstr, 0, length);

        if (lem == null)
        {
            if (otherLem != null)
            {
                return false;
            }
        }

        else if (!lem.equals(otherLem))
        {
            return false;
        }
        if (!Arrays.equals(thisThstr, otherThstr))
        {
            return false;
        }
        return true;
    }

}


class DefaultEvaluator implements TriggeringEventEvaluator
{

    public boolean isTriggeringEvent(LoggingEvent event)
    {
        return event.getLevel().isGreaterOrEqual(Level.ERROR);
    }
}