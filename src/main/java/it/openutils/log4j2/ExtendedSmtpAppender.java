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

import java.io.Serializable;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.HtmlLayout;


/**
 * // see https://issues.apache.org/jira/browse/LOG4J2-1192
 * Like standard org.apache.logging.log4j.core.appender.SmtpAppender but with some additional features like
 * PatternLayout for subject, so subject changes with each log event, and burst summarizing. Plugin name "SMTPx" is for
 * "SMPT extended".
 *
 * Burst summarizing (must be enabled by setting parameter burstSummarizingSeconds): - the first occurrence is emailed
 * immediately - all following similar ERROR logs are buffered for burstSummarizingSeconds (similarity is configurable
 * with bs* parameters) - after burstSummarizingSeconds passed, a summary email with summary info (number of events,
 * time) togehter with the first and last event is send.
 *
 * This class is nearly copy&paste of original code because SmtpAppender is final (why?).
 *
 * @see #createAppender(String, String, String, String, String, String, String, boolean, String, String, int, String,
 *      String, boolean, int, Layout, Filter, boolean, int, char, boolean, int, boolean, boolean, boolean, boolean)
 * @see ExtendedSmtpManager
 *
 * @author Thies Wellpott
 */
@Plugin(name = "SMTPx", category = "Core", elementType = "appender", printObject = true)
public class ExtendedSmtpAppender extends AbstractAppender
{
    private static long serialVersionUID = 2L;

    /** The SMTP Manager */
    private ExtendedSmtpManager manager;

    private ExtendedSmtpAppender(String name, Filter filter, Layout<? extends Serializable> layout, ExtendedSmtpManager manager,
            boolean ignoreExceptions)
    {
        super(name, filter, layout, ignoreExceptions);
        this.manager = manager;
    }

    /**
     * Create an ExtendedSmtpAppender.
     *
     * @param name
     *            The name of the Appender. Required.
     * @param to
     *            The comma-separated list of recipient email addresses.
     * @param cc
     *            The comma-separated list of CC email addresses.
     * @param bcc
     *            The comma-separated list of BCC email addresses.
     * @param from
     *            The email address of the sender. Required.
     * @param replyTo
     *            The comma-separated list of reply-to email addresses.
     * @param subject
     *            The subject as plain text or as pattern for PatternLayout (see subjectWithLayout). Required.
     * @param subjectWithLayout
     *            If true, the subject is used as pattern for PatternLayout; default is false.
     * @param smtpProtocol
     *            The SMTP transport protocol (such as "smtps", defaults to "smtp").
     * @param smtpHost
     *            The SMTP hostname to send to. Required.
     * @param smtpPortStr
     *            The SMTP port to send to.
     * @param smtpUsername
     *            The username required to authenticate against the SMTP server.
     * @param smtpPassword
     *            The password required to authenticate against the SMTP server.
     * @param smtpDebug
     *            Enable mail session debuging on STDOUT.
     * @param bufferSize
     *            How many log events should be buffered for inclusion in the message? Default is 10.
     * @param layout
     *            The layout to use (defaults to HtmlLayout).
     * @param filter
     *            The Filter or null (defaults to ThresholdFilter, level of ERROR).
     * @param ignoreExceptions
     *            If {@code "true"} (the default) exceptions encountered when appending events are logged; otherwise
     *            they are propagated to the caller.
     * @param burstSummarizingSeconds
     *            Number of seconds to summarize similar log messages over. <= 0 to disable this feature (the default).
     * @param bsCountInSubject
     *            Shall the number of summarized events be put in the subject? F or S for at front/start, B or E for
     *            behind/at end; default is no count in subject.
     * @param bsLoggername
     *            For summarizing the logger name is relevant; default: false.
     * @param bsMessagePrefixLength
     *            For summarizing this number of characters from the beginning of the message text are relevant;
     *            default: 30.
     * @param bsMessageMaskDigits
     *            For summarizing, digits in the message text shall be masked, so their concrete value is irrelevant;
     *            default: false.
     * @param bsExceptionClass
     *            For summarizing the exception class name is relevant; default: true.
     * @param bsExceptionOrigin
     *            For summarizing the first line of the exception stack trace is relevant; default: false.
     * @param bsRootExceptionClass
     *            For summarizing the class name of the root cause is relevant; default: false.
     *
     * @return The newly created ExtendedSmtpAppender. null on error.
     */
    @PluginFactory
    public static ExtendedSmtpAppender createAppender(
            @PluginAttribute("name") @Required(message = "SMTP.name is missing") String name, @PluginAttribute("to") String to,
            @PluginAttribute("cc") String cc, @PluginAttribute("bcc") String bcc,
            @PluginAttribute("from") @Required(message = "SMTP.from is missing") String from,
            @PluginAttribute("replyTo") String replyTo,
            @PluginAttribute("subject") @Required(message = "SMTP.subject is missing") String subject,
            @PluginAttribute(value = "smtpProtocol", defaultString = "smtp") String smtpProtocol,
            @PluginAttribute("smtpHost") @Required(message = "SMTP.smtpHost is missing") String smtpHost,
            @PluginAttribute("smtpPort") int smtpPort, @PluginAttribute("smtpUsername") String smtpUsername,
            @PluginAttribute("smtpPassword") String smtpPassword, @PluginAttribute("smtpDebug") boolean smtpDebug,
            @PluginAttribute(value = "bufferSize", defaultInt = 10) int bufferSize,
            @PluginElement("Layout") Layout<? extends Serializable> layout, @PluginElement("Filter") Filter filter,
            @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) boolean ignoreExceptions,
            @PluginAttribute("burstSummarizingSeconds") int burstSummarizingSeconds,
            @PluginAttribute("bsCountInSubject") char bsCountInSubject, @PluginAttribute("bsLoggername") boolean bsLoggername,
            @PluginAttribute(value = "bsMessagePrefixLength", defaultInt = 1000) int bsMessagePrefixLength,
            @PluginAttribute("bsMessageMaskDigits") boolean bsMessageMaskDigits,
            @PluginAttribute(value = "bsExceptionClass", defaultBoolean = true) boolean bsExceptionClass,
            @PluginAttribute("bsExceptionOrigin") boolean bsExceptionOrigin,
            @PluginAttribute("bsRootExceptionClass") boolean bsRootExceptionClass)
    {

        if (layout == null)
        {
            layout = HtmlLayout.createDefaultLayout();
        }
        if (filter == null)
        {
            filter = ThresholdFilter.createFilter(null, null, null);
        }

        ExtendedSmtpManager manager = ExtendedSmtpManager.getSMTPManager(new ExtendedSmtpManager.FactoryData(to, cc, bcc, from,
                replyTo, subject, smtpProtocol, smtpHost, smtpPort, smtpUsername, smtpPassword, smtpDebug, bufferSize,
                burstSummarizingSeconds * 1000L, bsCountInSubject, bsLoggername, bsMessagePrefixLength, bsMessageMaskDigits,
                bsExceptionClass, bsExceptionOrigin, bsRootExceptionClass), filter.toString(), layout.getContentType());
        if (manager == null)
        {
            return null;
        }

        return new ExtendedSmtpAppender(name, filter, layout, manager, ignoreExceptions);
    }

    @Override
    public void stop()
    {
        super.stop();
        manager.release(); // important here to allow stopping the background thread
    }

    /**
     * Capture all events in CyclicBuffer.
     * 
     * @param event
     *            The Log event.
     * @return true if the event should be filtered.
     */
    @Override
    public boolean isFiltered(LogEvent event)
    {
        boolean filtered = super.isFiltered(event);
        if (filtered)
        {
            manager.add(event);
        }
        return filtered;
    }

    /**
     * Perform SmtpAppender specific appending actions, mainly adding the event to a cyclic buffer and checking if the
     * event triggers an e-mail to be sent.
     * 
     * @param event
     *            The Log event.
     */
    @Override
    public void append(LogEvent event)
    {
        manager.sendEvents(getLayout(), event);
    }

}
