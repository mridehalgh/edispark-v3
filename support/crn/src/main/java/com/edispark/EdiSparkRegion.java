package com.edispark;

import com.edispark.identifiers.region.Region;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;

@Builder
@Value
@Setter(AccessLevel.NONE)
public final class EdiSparkRegion {

  public static final Region REGION;

  public static final String EDISPARK_REGION_ENV = "EDISPARK_REGION";

  static {
    REGION = getRegionFromEnv();
  }

  private static Region getRegionFromEnv() {
    return Region.fromString(Optional.ofNullable(System.getenv(EDISPARK_REGION_ENV)).orElse(
        EMPTY_REGION()));
  }

  private static String EMPTY_REGION() {
    return "";
  }

  public static Region getRegion() {
    return REGION;
  }
}
