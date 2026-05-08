package com.tradacoms.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Defines error recovery strategies for EDI parsing and validation.
 * 
 * <p>Three main strategies are supported:
 * <ul>
 *   <li><b>STRICT</b>: Fail immediately on any error</li>
 *   <li><b>LENIENT</b>: Skip malformed segments and continue parsing</li>
 *   <li><b>CONTINUE_ON_ERROR</b>: Collect errors up to a limit, then fail</li>
 * </ul>
 * 
 * <p>Requirements: 9.4, 9.7
 */
public final class ErrorRecoveryStrategy {

    /**
     * Strict mode: fail immediately on any error.
     * No error recovery is attempted.
     */
    public static final ErrorRecoveryStrategy STRICT = new ErrorRecoveryStrategy(Mode.STRICT, 1);

    /**
     * Lenient mode: skip malformed segments and continue parsing.
     * Errors are logged but do not stop processing.
     */
    public static final ErrorRecoveryStrategy LENIENT = new ErrorRecoveryStrategy(Mode.LENIENT, Integer.MAX_VALUE);

    /**
     * Default continue-on-error mode: collect up to 100 errors before failing.
     */
    public static final ErrorRecoveryStrategy CONTINUE_ON_ERROR = new ErrorRecoveryStrategy(Mode.CONTINUE_ON_ERROR, 100);

    private final Mode mode;
    private final int maxErrors;

    private ErrorRecoveryStrategy(Mode mode, int maxErrors) {
        this.mode = mode;
        this.maxErrors = maxErrors;
    }

    /**
     * Creates a continue-on-error strategy with a custom error limit.
     * 
     * @param maxErrors the maximum number of errors to collect before failing
     * @return a new ErrorRecoveryStrategy
     */
    public static ErrorRecoveryStrategy continueOnError(int maxErrors) {
        if (maxErrors <= 0) {
            throw new IllegalArgumentException("maxErrors must be positive");
        }
        return new ErrorRecoveryStrategy(Mode.CONTINUE_ON_ERROR, maxErrors);
    }

    /**
     * Creates an error recovery strategy from ParserConfig settings.
     * 
     * @param config the parser configuration
     * @return the appropriate ErrorRecoveryStrategy
     */
    public static ErrorRecoveryStrategy fromParserConfig(ParserConfig config) {
        if (config.isStopOnError()) {
            return STRICT;
        }
        if (config.isLenientMode()) {
            return LENIENT;
        }
        if (config.isContinueOnError()) {
            return CONTINUE_ON_ERROR;
        }
        return STRICT; // Default to strict
    }

    /**
     * Returns the recovery mode.
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Returns the maximum number of errors to collect before failing.
     */
    public int getMaxErrors() {
        return maxErrors;
    }

    /**
     * Returns true if this strategy is strict mode.
     */
    public boolean isStrict() {
        return mode == Mode.STRICT;
    }

    /**
     * Returns true if this strategy is lenient mode.
     */
    public boolean isLenient() {
        return mode == Mode.LENIENT;
    }

    /**
     * Returns true if this strategy is continue-on-error mode.
     */
    public boolean isContinueOnError() {
        return mode == Mode.CONTINUE_ON_ERROR;
    }

    /**
     * Creates a new ErrorCollector for tracking errors with this strategy.
     */
    public ErrorCollector createCollector() {
        return new ErrorCollector(this);
    }

    @Override
    public String toString() {
        return "ErrorRecoveryStrategy{mode=" + mode + ", maxErrors=" + maxErrors + "}";
    }

    /**
     * The error recovery mode.
     */
    public enum Mode {
        /**
         * Fail immediately on any error.
         */
        STRICT,

        /**
         * Skip malformed content and continue processing.
         */
        LENIENT,

        /**
         * Collect errors up to a limit, then fail.
         */
        CONTINUE_ON_ERROR
    }

    /**
     * Collector for tracking errors during parsing or validation.
     * Respects the error recovery strategy's limits and mode.
     */
    public static final class ErrorCollector {
        private final ErrorRecoveryStrategy strategy;
        private final List<EdiException> errors;
        private boolean limitReached;

        private ErrorCollector(ErrorRecoveryStrategy strategy) {
            this.strategy = strategy;
            this.errors = new ArrayList<>();
            this.limitReached = false;
        }

        /**
         * Records an error. Returns true if processing should continue,
         * false if processing should stop.
         * 
         * @param error the error to record
         * @return true if processing should continue
         */
        public boolean recordError(EdiException error) {
            Objects.requireNonNull(error, "error must not be null");
            
            if (strategy.isStrict()) {
                errors.add(error);
                limitReached = true;
                return false; // Stop immediately
            }
            
            if (strategy.isLenient()) {
                errors.add(error);
                return true; // Always continue
            }
            
            // Continue-on-error mode
            errors.add(error);
            if (errors.size() >= strategy.getMaxErrors()) {
                limitReached = true;
                return false; // Stop after reaching limit
            }
            return true; // Continue collecting
        }

        /**
         * Records a parse exception. Returns true if processing should continue.
         */
        public boolean recordParseError(String message, String errorCode, int lineNumber, 
                int charPosition, String rawSnippet, String segmentTag) {
            ParseException error = ParseException.builder(message)
                    .errorCode(errorCode)
                    .lineNumber(lineNumber)
                    .charPosition(charPosition)
                    .rawSnippet(rawSnippet)
                    .segmentTag(segmentTag)
                    .build();
            return recordError(error);
        }

        /**
         * Returns true if the error limit has been reached.
         */
        public boolean isLimitReached() {
            return limitReached;
        }

        /**
         * Returns true if any errors have been recorded.
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        /**
         * Returns the number of errors recorded.
         */
        public int getErrorCount() {
            return errors.size();
        }

        /**
         * Returns an unmodifiable list of recorded errors.
         */
        public List<EdiException> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        /**
         * Returns the first error, or null if no errors.
         */
        public EdiException getFirstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }

        /**
         * Throws the first error if any errors were recorded and the strategy
         * is not lenient mode.
         * 
         * @throws EdiException if errors were recorded and strategy is not lenient
         */
        public void throwIfErrors() throws EdiException {
            if (!errors.isEmpty() && !strategy.isLenient()) {
                throw errors.get(0);
            }
        }

        /**
         * Returns true if processing should continue based on current state.
         */
        public boolean shouldContinue() {
            if (strategy.isStrict() && hasErrors()) {
                return false;
            }
            if (strategy.isContinueOnError() && limitReached) {
                return false;
            }
            return true;
        }

        /**
         * Clears all recorded errors.
         */
        public void clear() {
            errors.clear();
            limitReached = false;
        }
    }
}
