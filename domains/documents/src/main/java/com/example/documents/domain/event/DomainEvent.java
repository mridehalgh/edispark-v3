package com.example.documents.domain.event;

import java.time.Instant;

/**
 * Marker interface for domain events in the Documents bounded context.
 * 
 * <p>All domain events must include a timestamp indicating when the event occurred.</p>
 */
public interface DomainEvent {
    
    /**
     * Returns the timestamp when this event occurred.
     */
    Instant occurredAt();
}
