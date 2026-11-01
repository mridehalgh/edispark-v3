package com.edispark.edifact;

import java.util.List;

public record EdifactMessage(String reference, String type, String directory, List<String> segments) {
    public EdifactMessage { segments = List.copyOf(segments); }
}
