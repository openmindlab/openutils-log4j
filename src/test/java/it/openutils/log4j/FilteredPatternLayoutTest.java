/**
 *
 * openutils for Log4j (http://www.openmindlab.com/lab/products/openutilslog4j.html)
 * Copyright(C) ${project.inceptionYear}-2011, Openmind S.r.l. http://www.openmindonline.it
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
