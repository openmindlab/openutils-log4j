package it.openutils.log4j;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;


/**
 * @author Fabrizio Giustina
 * @version $Id$
 */
public class FilteredPatternLayout extends PatternLayout
{

    /**
     * Holds the list of filtered frames.
     */
    private Set filteredFrames = new HashSet();

    /**
     * Line separator for stacktrace frames.
     */
    private static String lineSeparator = "\n";

    static
    {
        try
        {
            lineSeparator = System.getProperty("line.separator");
        }
        catch (SecurityException ex)
        {
            // ignore
        }
    }

    /**
     * @see org.apache.log4j.Layout#ignoresThrowable()
     */
    public boolean ignoresThrowable()
    {
        return false;
    }

    /**
     * @see org.apache.log4j.PatternLayout#format(org.apache.log4j.spi.LoggingEvent)
     */
    public String format(LoggingEvent event)
    {

        ThrowableInformation throwableInformation = event.getThrowableInformation();

        if (throwableInformation == null)
        {
            return super.format(event);
        }

        return super.format(event) + getFilteredStacktrace(throwableInformation);
    }

    /**
     * Adds a new filtered frame. Any stack frame starting with <code>"at "</code> + <code>filter</code> will not be
     * written to the log.
     * @param filter a class name or package name to be filtered
     */
    public void setFilter(String filter)
    {
        filteredFrames.add("at " + filter);
    }

    private String getFilteredStacktrace(ThrowableInformation throwableInformation)
    {
        StringBuffer buffer = new StringBuffer();

        String[] s = throwableInformation.getThrowableStrRep();

        for (int j = 0; j < s.length; j++)
        {
            String string = s[j];

            if (startsWithAFilteredPAttern(string))
            {
                continue;
            }
            buffer.append(string);
            buffer.append(lineSeparator);
        }

        return buffer.toString();
    }

    /**
     * Check if the given string starts with any of the filtered patterns.
     * @param string checked String
     * @return <code>true</code> if the begininning of the string matches a filtered pattern, <code>false</code>
     * otherwise
     */
    private boolean startsWithAFilteredPAttern(String string)
    {
        Iterator iterator = filteredFrames.iterator();
        while (iterator.hasNext())
        {
            if (string.trim().startsWith((String) iterator.next()))
            {
                return true;
            }
        }
        return false;
    }
}
