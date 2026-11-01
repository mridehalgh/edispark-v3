package com.edispark.partnerships.application;

import com.edispark.partnerships.domain.model.TradingAgreement;

public interface AgreementSnapshotPublisher { void publish(TradingAgreement agreement); }
