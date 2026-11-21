package com.edispark.exchange.application;

import com.edispark.exchange.domain.EdiExchange;
import com.edispark.exchange.domain.ExchangeRepository;
import com.edispark.exchange.domain.ExchangeStatus;
import com.edispark.partnerships.domain.model.ExchangeDirection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class ExchangeEvidenceQuery {
    private static final int PAGE_SIZE = 32;
    private final ExchangeRepository exchanges;

    public ExchangeEvidenceQuery(ExchangeRepository exchanges) {
        this.exchanges = exchanges;
    }

    public Optional<EdiExchange> find(UUID id) {
        return exchanges.find(id);
    }

    public Page<EdiExchange> find(Search search, UUID after) {
        return matching(after, search::includes);
    }

    public Page<EdiExchange> forSourceDocument(UUID setId, UUID documentId, UUID after) {
        return matching(after, exchange -> {
            var source = exchange.sourceDocument();
            return source != null && source.documentSetId().equals(setId) && source.documentId().equals(documentId);
        });
    }

    public Page<DocumentException> documentExceptions(DateWindow updated, UUID partnerId, UUID after) {
        var page = matching(after, exchange -> exchange.sourceDocument() != null && updated.includes(exchange.updatedAt())
                && (partnerId == null || partnerId.equals(exchange.partnerId())) && exceptionKind(exchange.status()) != null);
        var exceptions = page.results().stream().map(exchange -> new DocumentException(exchange, exceptionKind(exchange.status()))).toList();
        return new Page<>(exceptions, page.next());
    }

    public Page<EdiExchange> forTransfer(UUID transferId, UUID after) {
        return matching(after, exchange -> transferId.equals(exchange.mftTransferId()));
    }

    private Page<EdiExchange> matching(UUID after, Predicate<EdiExchange> includes) {
        var page = exchanges.page(after, PAGE_SIZE);
        return new Page<>(page.exchanges().stream().filter(includes).toList(), page.next());
    }

    private static ExceptionKind exceptionKind(ExchangeStatus status) {
        return switch (status) {
            case NEEDS_REVIEW -> ExceptionKind.REVIEW_REQUIRED;
            case FAILED -> ExceptionKind.PROCESSING_FAILED;
            case AWAITING_RECEIPT -> ExceptionKind.WAITING_FOR_RECEIPT;
            default -> null;
        };
    }

    public record Search(DateWindow created, UUID partnerId, String messageReference, ExchangeStatus status,
                         ExchangeDirection direction) {
        boolean includes(EdiExchange exchange) {
            return created.includes(exchange.createdAt())
                    && (partnerId == null || partnerId.equals(exchange.partnerId()))
                    && (messageReference == null || messageReference.equals(exchange.sourceMessageReference()))
                    && (status == null || status == exchange.status())
                    && (direction == null || direction == exchange.direction());
        }
    }

    public record DateWindow(Instant from, Instant to) {
        public DateWindow {
            if (from == null || to == null || !from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
                throw new IllegalArgumentException("Search requires a date window of up to 31 days");
            }
        }

        boolean includes(Instant instant) {
            return !instant.isBefore(from) && instant.isBefore(to);
        }
    }

    public enum ExceptionKind { REVIEW_REQUIRED, PROCESSING_FAILED, WAITING_FOR_RECEIPT }
    public record DocumentException(EdiExchange exchange, ExceptionKind kind) { }
    public record Page<T>(List<T> results, UUID next) { }
}
