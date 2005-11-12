package it.openutils.log4j;

import junit.framework.TestCase;

import org.apache.log4j.Logger;


/**
 * @author fgiust
 * @version $Revision$ ($Author$)
 */
public class FilteredPatternLayoutTest extends TestCase
{

    /**
     * Logger.
     */
    private static Logger log = Logger.getLogger(FilteredPatternLayoutTest.class);

    /**
     * Quick test for filtered frames.
     */
    public void testFilter()
    {
        log.error("test message\n aaaa", new Throwable());
    }
}
