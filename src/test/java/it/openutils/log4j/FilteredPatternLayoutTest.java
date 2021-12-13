/*
 *
 * openutils for Log4j (https://github.com/openmindlab/openutils-log4j)
 * Copyright(C) 2005-2021, https://github.com/openmindlab
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
package it.openutils.log4j;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.junit.Ignore;


/**
 * @author fgiust
 * @version $Revision$ ($Author$)
 */
@Ignore("Ignored due managed different classpath for one maven test classpath! Here for historical purpose.")
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
