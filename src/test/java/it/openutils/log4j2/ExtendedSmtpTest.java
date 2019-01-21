/**
 *
 * openutils for Log4j (http://www.openmindlab.com/lab/products/openutilslog4j.html)
 * Copyright(C) ${project.inceptionYear}-2012, Openmind S.r.l. http://www.openmindonline.it
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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.util.List;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.internet.MimeMultipart;

import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.jvnet.mock_javamail.Mailbox;

/**
 * @author fgiust
 */
public class ExtendedSmtpTest {

	private static Logger log = null;
	
	@BeforeClass
    public static void setLogger(){
		System.setProperty("log4j.configurationFile", "log4j2.xml");
        log = LogManager.getLogger(ExtendedSmtpTest.class);
    }
	
	@Before
	public void setUp() throws URISyntaxException {
		// clear Mock JavaMail box
		Mailbox.clearAll();
	}

	@Test
	public void testThrowable() throws Exception {

		System.setProperty("systemproperty", "***system***");
		final String MDC1_VALUE = "***testmdc***";
		final String MATCHING_HEADER = "\n ===================================\n MDC1=" + MDC1_VALUE
				+ "\n ===================================";
		final String MATCHING_FOOTER = "===================================\n";
		final String ERROR_MESSAGE = "test message aaaa";
		final Throwable THROWABLE_OBJECT = new Throwable("this is for test");

		try (final CloseableThreadContext.Instance ctc = CloseableThreadContext.put("MDC1", "***testmdc***")) {

			log.error(ERROR_MESSAGE, THROWABLE_OBJECT);

			log.info("waiting 2 seconds for mail");

			Thread.sleep(2000);
			List<Message> inbox = Mailbox.get("none@example.com");

			log.info("number of messages: {}", inbox.size());

			assertThat(inbox).isNotEmpty();
			log.info("subject: {}", inbox.get(0).getSubject());
			assertThat(inbox.get(0).getSubject()).isEqualTo(String.format("[TEST %s] %s %s", System.getProperty("systemproperty"),MDC1_VALUE,ERROR_MESSAGE));
			javax.mail.internet.MimeMultipart content = (MimeMultipart) inbox.get(0).getContent();
			BodyPart bodyPart = content.getBodyPart(0);
			assertThat(bodyPart.getContent()).isNotNull();
			log.info("body: {}", bodyPart.getContent());

			assertThat((String) bodyPart.getContent()).startsWith(MATCHING_HEADER);
			assertThat((String) bodyPart.getContent()).endsWith(MATCHING_FOOTER);
		}

	}
}