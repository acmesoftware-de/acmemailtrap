package de.acmesoftware.mailtrap.cli;

/**
 * An expected operational error — reported as a clean message without a stack trace, with the
 * exit code that is part of the CLI contract (ADR-0002):
 * 1 no match / assertion failed, 2 usage error, 3 connection or auth error against the trap.
 */
public class CliError extends RuntimeException {

    /** No match: {@code wait} timed out, {@code extract} found nothing. An assertion failure. */
    public static final int NO_MATCH = 1;
    /** The invocation itself is wrong: unknown context, missing option, bad value. */
    public static final int USAGE = 2;
    /** The trap could not be reached, or refused us. */
    public static final int CONNECTION = 3;

    private final int exitCode;

    public CliError(String message) {
        this(CONNECTION, message);
    }

    public CliError(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public static CliError usage(String message) {
        return new CliError(USAGE, message);
    }

    public static CliError noMatch(String message) {
        return new CliError(NO_MATCH, message);
    }

    public int exitCode() {
        return exitCode;
    }
}
