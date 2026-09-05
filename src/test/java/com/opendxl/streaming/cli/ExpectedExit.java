/*---------------------------------------------------------------------------*
 * Copyright (c) 2019 McAfee, LLC - All Rights Reserved.                     *
 *---------------------------------------------------------------------------*/

package com.opendxl.streaming.cli;

import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.function.IntConsumer;

/**
 * JUnit rule to assert that the command line interface finished (and with which exit status).
 * <p>
 * It replaces the exit handler of {@link CliUtils} for the duration of a test: the first exit status is recorded
 * and every exit call throws, which unwinds the CLI code the same way as system-rules' {@code ExpectedSystemExit}
 * did. Unlike {@code ExpectedSystemExit} it does not need a {@code SecurityManager}, which JDK 18+ no longer
 * allows to install.
 */
public final class ExpectedExit implements TestRule {

    private boolean exitExpected;
    private Integer expectedStatus;

    private ExpectedExit() {
    }

    /**
     * @return a rule that fails the test if the CLI exits, unless an exit is expected later on
     */
    public static ExpectedExit none() {
        return new ExpectedExit();
    }

    /**
     * Expects the CLI to exit with any status.
     */
    public void expectSystemExit() {
        exitExpected = true;
    }

    /**
     * Expects the CLI to exit with the given status.
     *
     * @param status the expected exit status
     */
    public void expectSystemExitWithStatus(final int status) {
        exitExpected = true;
        expectedStatus = status;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final ExitRecorder recorder = new ExitRecorder();
                CliUtils.setExitHandler(recorder);
                try {
                    base.evaluate();
                } catch (ExitCalled e) {
                    // the CLI exited; verified below
                } finally {
                    CliUtils.setExitHandler(null);
                }
                if (recorder.firstStatus == null) {
                    if (exitExpected) {
                        Assert.fail("System exit was expected but not called");
                    }
                } else {
                    if (!exitExpected) {
                        Assert.fail("Unexpected system exit with status " + recorder.firstStatus);
                    }
                    if (expectedStatus != null) {
                        Assert.assertEquals("Wrong exit status", expectedStatus, recorder.firstStatus);
                    }
                }
            }
        };
    }

    /**
     * Exit handler that records the first exit status and aborts the calling code.
     */
    private static final class ExitRecorder implements IntConsumer {

        private Integer firstStatus;

        @Override
        public void accept(final int status) {
            if (firstStatus == null) {
                firstStatus = status;
            }
            throw new ExitCalled(status);
        }
    }

    /**
     * Thrown by the exit handler in place of terminating the JVM.
     */
    private static final class ExitCalled extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ExitCalled(final int status) {
            super("System exit called with status " + status);
        }
    }
}
