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
 * @author fgiust
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

        // Note: this code already owns the monitor for this
        // appender. This frees us from needing to synchronize on 'cb'.
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
                // sbuf.append(MimeUtility.encodeText(layout.format(cb.get())));
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
