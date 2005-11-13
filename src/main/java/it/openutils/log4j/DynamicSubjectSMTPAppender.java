/*
 * Copyright 2005 Fabrizio Giustina.
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

import java.util.Date;

import javax.mail.Multipart;
import javax.mail.Transport;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.apache.log4j.Layout;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.net.SMTPAppender;
import org.apache.log4j.spi.LoggingEvent;


/**
 * An extension of <code>org.apache.log4j.net.SMTPAppender</code> which let you customize the mail subject using a
 * pattern. Sample configuration:
 *
 * <pre>
 *  &lt;appender name="mail" class="it.openutils.log4j.DynamicSubjectSMTPAppender">
 *      &lt;param name="Threshold" value="ERROR" />
 *      &lt;param name="To" value="logs@example.com" />
 *      &lt;param name="From" value="info@example.com" />
 *      &lt;param name="SMTPHost" value="localhost" />
 *      &lt;param name="BufferSize" value="1" />
 *      &lt;param name="Subject" value="[EXAMPLE] %m" />
 *      &lt;layout class="org.apache.log4j.PatternLayout">
 *          &lt;param name="ConversionPattern" value="%-5p  %c %d{dd.MM.yyyy HH:mm:ss} -- %m%n" />
 *      &lt;/layout>
 *  &lt;/appender>
 * </pre>
 *
 * @author Fabrizio Giustina
 * @version $Id$
 */
public class DynamicSubjectSMTPAppender extends SMTPAppender
{

    private Layout subjectLayout;

    /**
     * Setter for <code>subject</code>.
     * @param subjectPattern The subjectPattern to set.
     */
    public void setSubject(String subjectPattern)
    {
        this.subjectLayout = new PatternLayout(subjectPattern);
    }

    /**
     * Send the contents of the cyclic buffer as an e-mail message.
     */
    protected void sendBuffer()
    {
        try
        {
            MimeBodyPart part = new MimeBodyPart();

            StringBuffer sbuf = new StringBuffer();
            String t = layout.getHeader();
            if (t != null)
                sbuf.append(t);
            int len = cb.length();
            for (int i = 0; i < len; i++)
            {
                LoggingEvent event = cb.get();

                if (this.subjectLayout != null)
                {
                    String subject = this.subjectLayout.format(event);
                    this.msg.setSubject(subject);
                }

                sbuf.append(layout.format(event));
                if (layout.ignoresThrowable())
                {
                    String[] s = event.getThrowableStrRep();
                    if (s != null)
                    {
                        for (int j = 0; j < s.length; j++)
                        {
                            sbuf.append(s[j]);
                        }
                    }
                }
            }
            t = layout.getFooter();
            if (t != null)
                sbuf.append(t);
            part.setContent(sbuf.toString(), layout.getContentType());

            Multipart mp = new MimeMultipart();
            mp.addBodyPart(part);
            msg.setContent(mp);

            msg.setSentDate(new Date());
            Transport.send(msg);
        }
        catch (Exception e)
        {
            LogLog.error("Error occured while sending e-mail notification.", e);
        }

    }

}
