package com.edispark.edifact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EdifactParserWriterTest {
    @Test
    void writesAndParsesAnOrdersD96aInterchange() {
        var message = new EdifactMessage("1", "ORDERS", "D96A",
                List.of("UNH+1+ORDERS:D:96A:UN", "BGM+220+PO-1001+9", "UNT+3+1"));
        String interchange = new EdifactWriter().interchange("SENDER", "RECEIVER", "CTRL1", List.of(message));

        var parsed = new EdifactParser().parse(interchange);

        assertThat(parsed).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo("ORDERS");
            assertThat(item.directory()).isEqualTo("D96A");
            assertThat(item.segments()).containsExactlyElementsOf(message.segments());
        });
    }
}
