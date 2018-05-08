/**
 * ﻿============LICENSE_START=======================================================
 * org.onap.aai
 * ================================================================================
 * Copyright © 2017-2018 AT&T Intellectual Property. All rights reserved.
 * Copyright © 2017-2018 European Software Marketing Ltd.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */
package org.onap.aai.babel.logging;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.Arrays;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.onap.aai.babel.logging.LogHelper.TriConsumer;
import org.onap.aai.cl.api.LogFields;
import org.onap.aai.cl.api.Logger;
import org.onap.aai.cl.mdc.MdcOverride;

/**
 * Simple test to log each of the validation messages in turn.
 * 
 * This version tests only the error logger at INFO level.
 *
 */
public class TestApplicationLogger {

    @BeforeClass
    public static void setupClass() {
        System.setProperty("APP_HOME", ".");
    }

    /**
     * Check that each message can be logged and that (by implication of successful logging) there is a corresponding
     * resource (message format).
     * 
     * @throws IOException
     */
    @Test
    public void logAllMessages() throws IOException {
        Logger logger = LogHelper.INSTANCE;
        LogReader errorReader = new LogReader(LogHelper.getLogDirectory(), "error");
        LogReader debugReader = new LogReader(LogHelper.getLogDirectory(), "debug");
        String[] args = {"1", "2", "3", "4"};
        for (ApplicationMsgs msg : Arrays.asList(ApplicationMsgs.values())) {
            if (msg.name().endsWith("ERROR")) {
                logger.error(msg, args);
                validateLoggedMessage(msg, errorReader, "ERROR");

                logger.error(msg, new RuntimeException("fred"), args);
                validateLoggedMessage(msg, errorReader, "fred");
            } else {
                logger.info(msg, args);
                validateLoggedMessage(msg, errorReader, "INFO");

                logger.warn(msg, args);
                validateLoggedMessage(msg, errorReader, "WARN");
            }

            logger.debug(msg, args);
            validateLoggedMessage(msg, debugReader, "DEBUG");

            // The trace level is not enabled
            logger.trace(msg, args);
        }
    }

    /**
     * Check that each message can be logged and that (by implication of successful logging) there is a corresponding
     * resource (message format).
     * 
     * @throws IOException
     */
    @Test
    public void logDebugMessages() throws IOException {
        LogReader reader = new LogReader(LogHelper.getLogDirectory(), "debug");
        LogHelper.INSTANCE.debug("a message");
        String s = reader.getNewLines();
        assertThat(s, is(notNullValue()));
    }

    /**
     * Check logAudit with HTTP headers
     * 
     * @throws IOException
     */
    @Test
    public void logAuditMessage() throws IOException {
        LogHelper logger = LogHelper.INSTANCE;
        LogReader reader = new LogReader(LogHelper.getLogDirectory(), "audit");

        HttpHeaders headers = Mockito.mock(HttpHeaders.class);
        Mockito.when(headers.getHeaderString("X-ECOMP-RequestID")).thenReturn("ecomp-request-id");
        Mockito.when(headers.getHeaderString("X-FromAppId")).thenReturn("app-id");

        // Call logAudit without first calling startAudit
        logger.logAuditSuccess("first call: bob");
        String s = reader.getNewLines();
        assertThat(s, is(notNullValue()));
        assertThat("audit message log level", s, containsString("INFO"));
        assertThat("audit message content", s, containsString("bob"));

        // This time call the start method
        logger.startAudit(headers, null);
        logger.logAuditSuccess("second call: foo");
        s = reader.getNewLines();
        assertThat(s, is(notNullValue()));
        assertThat("audit message log level", s, containsString("INFO"));
        assertThat("audit message content", s, containsString("foo"));
        assertThat("audit message content", s, containsString("ecomp-request-id"));
        assertThat("audit message content", s, containsString("app-id"));
    }

    /**
     * Check logAudit with no HTTP headers
     * 
     * @throws IOException
     */
    @Test
    public void logAuditMessageWithoutHeaders() throws IOException {
        LogHelper logger = LogHelper.INSTANCE;
        LogReader reader = new LogReader(LogHelper.getLogDirectory(), "audit");
        logger.startAudit(null, null);
        logger.logAuditSuccess("foo");
        String s = reader.getNewLines();
        assertThat(s, is(notNullValue()));
        assertThat("audit message log level", s, containsString("INFO"));
        assertThat("audit message content", s, containsString("foo"));
    }

    /**
     * Check logMetrics
     * 
     * @throws IOException
     */
    @Test
    public void logMetricsMessage() throws IOException {
        LogReader reader = new LogReader(LogHelper.getLogDirectory(), "metrics");
        LogHelper logger = LogHelper.INSTANCE;
        logger.logMetrics("metrics: fred");
        String s = reader.getNewLines();
        assertThat(s, is(notNullValue()));
        assertThat("metrics message log level", s, containsString("INFO"));
        assertThat("metrics message content", s, containsString("fred"));
    }

    @Test
    public void logMetricsMessageWithStopwatch() throws IOException {
        LogReader reader = new LogReader(LogHelper.getLogDirectory(), "metrics");
        LogHelper logger = LogHelper.INSTANCE;
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        logger.logMetrics(stopWatch, "joe", "bloggs");
        String logLine = reader.getNewLines();
        assertThat(logLine, is(notNullValue()));
        assertThat("metrics message log level", logLine, containsString("INFO"));
        assertThat("metrics message content", logLine, containsString("joe"));
    }

    @Test
    public void callUnsupportedMethods() throws IOException {
        LogHelper logger = LogHelper.INSTANCE;
        ApplicationMsgs dummyMsg = ApplicationMsgs.LOAD_PROPERTIES;
        callUnsupportedOperationMethod(logger::error, dummyMsg);
        callUnsupportedOperationMethod(logger::info, dummyMsg);
        callUnsupportedOperationMethod(logger::warn, dummyMsg);
        callUnsupportedOperationMethod(logger::debug, dummyMsg);
        callUnsupportedOperationMethod(logger::trace, dummyMsg);
        try {
            logger.error(dummyMsg, new LogFields(), new RuntimeException("test"), "");
        } catch (UnsupportedOperationException e) {
            // Expected to reach here
        }
        try {
            logger.info(dummyMsg, new LogFields(), new MdcOverride(), "");
        } catch (UnsupportedOperationException e) {
            // Expected to reach here
        }
        try {
            logger.formatMsg(dummyMsg, "");
        } catch (UnsupportedOperationException e) {
            // Expected to reach here
        }
    }

    /**
     * Call a logger method which is expected to throw an UnsupportedOperationException
     * 
     * @param logMethod
     * @param dummyMsg
     */
    private void callUnsupportedOperationMethod(TriConsumer<Enum<?>, LogFields, String[]> logMethod,
            ApplicationMsgs dummyMsg) {
        try {
            logMethod.accept(dummyMsg, new LogFields(), new String[] {""});
            org.junit.Assert.fail("method should have thrown execption"); // NOSONAR as code not reached
        } catch (UnsupportedOperationException e) {
            // Expected to reach here
        }
    }

    /**
     * Assert that a log message was logged to the expected log file at the expected severity
     * 
     * @param msg
     * @param reader
     * @param severity
     * @throws IOException
     */
    private void validateLoggedMessage(ApplicationMsgs msg, LogReader reader, String severity) throws IOException {
        String s = reader.getNewLines();
        assertThat(s, is(notNullValue()));
        assertThat(msg.toString() + " log level", s, containsString(severity));
    }
}