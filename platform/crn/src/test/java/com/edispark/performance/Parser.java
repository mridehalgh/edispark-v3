package com.edispark.performance;

import com.github.ksuid.KsuidGenerator;
import com.edispark.Crn;
import com.edispark.identifiers.region.Region;
import com.edispark.identifiers.resource.Resource;
import com.edispark.identifiers.resource.ResourceId;
import com.edispark.identifiers.service.Service;
import com.edispark.identifiers.tenant.TenantId;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Parser {

  public static void main(final String[] args) {
    List<String> sampleCrns = generateSampleCrns();

    IntStream.iterate(100, operand -> operand * 10)
        .limit(5)
        .forEach(count -> {
          final long start = System.nanoTime();
          IntStream.range(0, count).forEach(i -> {
            Crn.fromString(sampleCrns.get(count));
          });
          final long duration = TimeUnit.MILLISECONDS.convert(System.nanoTime() - start,
              TimeUnit.NANOSECONDS);
          System.out.println(
              String.format("%,d in %,d ms. rate = %,d/ms", count, duration, count / duration));
        });
  }

  private static List<String> generateSampleCrns() {
    final KsuidGenerator generator = new KsuidGenerator(new SecureRandom());
    IntStream.range(0, 100).forEach(i -> generator.newKsuid()); // prime the random

    Service service = Service.fromString("order");
    Region region = Region.fromString("eu");
    TenantId tenantId = TenantId.newTenantId();

    List<String> exampleCrns = new ArrayList<>();

    IntStream.iterate(100, operand -> operand * 10)
        .limit(5)
        .forEach(count -> {
          final long start = System.nanoTime();
          IntStream.range(0, count).forEach(i -> {
            exampleCrns.add(new Crn(service, region, tenantId,
                new Resource(null, new ResourceId(generator.newKsuid().toString()))).asString());
          });
          final long duration = TimeUnit.MILLISECONDS.convert(System.nanoTime() - start,
              TimeUnit.NANOSECONDS);
          System.out.println(
              String.format("Generated %,d in %,d ms. rate = %,d/ms", count, duration,
                  count / duration));
        });

    return exampleCrns;
  }
}
