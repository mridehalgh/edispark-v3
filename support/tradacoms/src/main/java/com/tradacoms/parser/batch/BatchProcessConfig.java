package com.tradacoms.parser.batch;

import com.tradacoms.parser.ParserConfig;
import com.tradacoms.parser.validation.ValidationConfig;

import java.util.Objects;

/**
 * Configuration for batch processing operations.
 * Combines parser and validation configurations with batch-specific settings.
 */
public final class BatchProcessConfig {

    private final ParserConfig parserConfig;
    private final ValidationConfig validationConfig;
    private final int threadPoolSize;
    private final boolean deterministicOutputOrder;
    private final boolean continueOnFileError;

    private BatchProcessConfig(Builder builder) {
        this.parserConfig = builder.parserConfig;
        this.validationConfig = builder.validationConfig;
        this.threadPoolSize = builder.threadPoolSize;
        this.deterministicOutputOrder = builder.deterministicOutputOrder;
        this.continueOnFileError = builder.continueOnFileError;
    }

    /**
     * Returns a default configuration.
     */
    public static BatchProcessConfig defaults() {
        return builder().build();
    }

    /**
     * Returns a builder for creating BatchProcessConfig instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the parser configuration.
     */
    public ParserConfig getParserConfig() {
        return parserConfig;
    }

    /**
     * Returns the validation configuration.
     */
    public ValidationConfig getValidationConfig() {
        return validationConfig;
    }

    /**
     * Returns the thread pool size for concurrent processing.
     * A value of 1 means single-threaded processing.
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * Returns whether output order should match input order regardless of processing concurrency.
     */
    public boolean isDeterministicOutputOrder() {
        return deterministicOutputOrder;
    }

    /**
     * Returns whether processing should continue when a file error occurs.
     */
    public boolean isContinueOnFileError() {
        return continueOnFileError;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchProcessConfig that = (BatchProcessConfig) o;
        return threadPoolSize == that.threadPoolSize &&
                deterministicOutputOrder == that.deterministicOutputOrder &&
                continueOnFileError == that.continueOnFileError &&
                Objects.equals(parserConfig, that.parserConfig) &&
                Objects.equals(validationConfig, that.validationConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parserConfig, validationConfig, threadPoolSize, 
                deterministicOutputOrder, continueOnFileError);
    }

    @Override
    public String toString() {
        return "BatchProcessConfig{" +
                "threadPoolSize=" + threadPoolSize +
                ", deterministicOutputOrder=" + deterministicOutputOrder +
                ", continueOnFileError=" + continueOnFileError +
                '}';
    }

    public static final class Builder {
        private ParserConfig parserConfig = ParserConfig.defaults();
        private ValidationConfig validationConfig = ValidationConfig.defaults();
        private int threadPoolSize = 1;
        private boolean deterministicOutputOrder = true;
        private boolean continueOnFileError = false;

        private Builder() {}

        /**
         * Sets the parser configuration.
         */
        public Builder parserConfig(ParserConfig parserConfig) {
            this.parserConfig = Objects.requireNonNull(parserConfig, "parserConfig must not be null");
            return this;
        }

        /**
         * Sets the validation configuration.
         */
        public Builder validationConfig(ValidationConfig validationConfig) {
            this.validationConfig = Objects.requireNonNull(validationConfig, "validationConfig must not be null");
            return this;
        }

        /**
         * Sets the thread pool size for concurrent processing.
         * Must be at least 1.
         */
        public Builder threadPoolSize(int threadPoolSize) {
            if (threadPoolSize < 1) {
                throw new IllegalArgumentException("threadPoolSize must be at least 1");
            }
            this.threadPoolSize = threadPoolSize;
            return this;
        }

        /**
         * Sets whether output order should match input order.
         * When true, results are returned in the same order as inputs regardless of processing order.
         */
        public Builder deterministicOutputOrder(boolean deterministicOutputOrder) {
            this.deterministicOutputOrder = deterministicOutputOrder;
            return this;
        }

        /**
         * Sets whether processing should continue when a file error occurs.
         * When true, errors are recorded but processing continues with remaining files.
         */
        public Builder continueOnFileError(boolean continueOnFileError) {
            this.continueOnFileError = continueOnFileError;
            return this;
        }

        public BatchProcessConfig build() {
            return new BatchProcessConfig(this);
        }
    }
}
