package com.tradacoms.parser.validation;

import com.tradacoms.parser.schema.MessageSchema;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration for EDI validation.
 */
public final class ValidationConfig {

    private final boolean failFast;
    private final int maxIssues;
    private final boolean validateEnvelope;
    private final boolean validateSchema;
    private final Map<String, MessageSchema> schemas;
    private final Map<String, Object> partnerRules;
    private final String filename;

    private ValidationConfig(Builder builder) {
        this.failFast = builder.failFast;
        this.maxIssues = builder.maxIssues;
        this.validateEnvelope = builder.validateEnvelope;
        this.validateSchema = builder.validateSchema;
        this.schemas = Map.copyOf(builder.schemas);
        this.partnerRules = Map.copyOf(builder.partnerRules);
        this.filename = builder.filename;
    }

    /**
     * Returns a default configuration.
     */
    public static ValidationConfig defaults() {
        return builder().build();
    }

    /**
     * Returns a builder for creating ValidationConfig instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public boolean isFailFast() {
        return failFast;
    }

    public int getMaxIssues() {
        return maxIssues;
    }

    public boolean isValidateEnvelope() {
        return validateEnvelope;
    }

    public boolean isValidateSchema() {
        return validateSchema;
    }

    public Map<String, MessageSchema> getSchemas() {
        return schemas;
    }

    public Optional<MessageSchema> getSchema(String messageType) {
        return Optional.ofNullable(schemas.get(messageType));
    }

    public Map<String, Object> getPartnerRules() {
        return partnerRules;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationConfig that = (ValidationConfig) o;
        return failFast == that.failFast &&
                maxIssues == that.maxIssues &&
                validateEnvelope == that.validateEnvelope &&
                validateSchema == that.validateSchema &&
                Objects.equals(schemas, that.schemas) &&
                Objects.equals(partnerRules, that.partnerRules) &&
                Objects.equals(filename, that.filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failFast, maxIssues, validateEnvelope, validateSchema, schemas, partnerRules, filename);
    }

    @Override
    public String toString() {
        return "ValidationConfig{" +
                "failFast=" + failFast +
                ", maxIssues=" + maxIssues +
                ", validateEnvelope=" + validateEnvelope +
                ", validateSchema=" + validateSchema +
                ", schemaCount=" + schemas.size() +
                '}';
    }

    public static final class Builder {
        private boolean failFast = false;
        private int maxIssues = Integer.MAX_VALUE;
        private boolean validateEnvelope = true;
        private boolean validateSchema = true;
        private Map<String, MessageSchema> schemas = new HashMap<>();
        private Map<String, Object> partnerRules = new HashMap<>();
        private String filename;

        private Builder() {}

        /**
         * Sets fail-fast mode. When true, validation stops at the first error.
         */
        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        /**
         * Sets the maximum number of issues to collect before stopping.
         */
        public Builder maxIssues(int maxIssues) {
            this.maxIssues = maxIssues;
            return this;
        }

        /**
         * Sets whether to validate envelope integrity (STX/END, message counts).
         */
        public Builder validateEnvelope(boolean validateEnvelope) {
            this.validateEnvelope = validateEnvelope;
            return this;
        }

        /**
         * Sets whether to validate messages against schema.
         */
        public Builder validateSchema(boolean validateSchema) {
            this.validateSchema = validateSchema;
            return this;
        }

        /**
         * Adds a message schema for validation.
         */
        public Builder addSchema(MessageSchema schema) {
            this.schemas.put(schema.getId(), schema);
            return this;
        }

        /**
         * Sets all message schemas for validation.
         */
        public Builder schemas(Map<String, MessageSchema> schemas) {
            this.schemas = new HashMap<>(schemas);
            return this;
        }

        /**
         * Sets partner-specific rules.
         */
        public Builder partnerRules(Map<String, Object> partnerRules) {
            this.partnerRules = new HashMap<>(partnerRules);
            return this;
        }

        /**
         * Sets the filename for error reporting.
         */
        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public ValidationConfig build() {
            return new ValidationConfig(this);
        }
    }
}
