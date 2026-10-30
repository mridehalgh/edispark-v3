package com.tradacoms.parser;

import com.tradacoms.parser.model.Message;

import java.util.Objects;
import java.util.function.Function;

/**
 * Sealed interface defining strategies for splitting a batch into multiple outputs.
 * Each strategy determines how messages are grouped for output.
 * 
 * Requirements: 5.1, 5.2, 5.3, 5.6
 */
public sealed interface SplitStrategy permits 
        SplitStrategy.ByMessage,
        SplitStrategy.ByMessageType,
        SplitStrategy.ByRoutingKey,
        SplitStrategy.Custom {

    /**
     * Returns a grouping key for the given message.
     * Messages with the same key will be written to the same output.
     *
     * @param message the message to get a key for
     * @param index the message index in the batch
     * @return the grouping key
     */
    String getGroupKey(Message message, int index);

    /**
     * Strategy that produces one output file per message.
     * Each message gets its own unique key based on its index.
     */
    record ByMessage() implements SplitStrategy {
        @Override
        public String getGroupKey(Message message, int index) {
            return String.valueOf(index);
        }
    }

    /**
     * Strategy that groups messages by their message type.
     * All messages of the same type (e.g., ORDERS, INVOIC) go to the same output.
     */
    record ByMessageType() implements SplitStrategy {
        @Override
        public String getGroupKey(Message message, int index) {
            return message.getMessageType();
        }
    }

    /**
     * Strategy that groups messages by a routing key value.
     * The routing key is extracted from the message's routing keys map.
     *
     * @param keyName the name of the routing key to use for grouping
     */
    record ByRoutingKey(String keyName) implements SplitStrategy {
        public ByRoutingKey {
            Objects.requireNonNull(keyName, "keyName must not be null");
        }

        @Override
        public String getGroupKey(Message message, int index) {
            String value = message.getRoutingKeys().get(keyName);
            return value != null ? value : "unknown";
        }
    }

    /**
     * Strategy that uses a custom function to determine grouping.
     * Allows arbitrary predicate-based routing strategies.
     *
     * @param grouper function that returns a grouping key for each message
     */
    record Custom(Function<Message, String> grouper) implements SplitStrategy {
        public Custom {
            Objects.requireNonNull(grouper, "grouper must not be null");
        }

        @Override
        public String getGroupKey(Message message, int index) {
            return grouper.apply(message);
        }
    }
}
