package com.edispark.transformations.application;

public final class RetainedSourceChanged extends IllegalArgumentException {
    public RetainedSourceChanged() { super("The document source changed. Run readiness again before testing a mapping."); }
}
