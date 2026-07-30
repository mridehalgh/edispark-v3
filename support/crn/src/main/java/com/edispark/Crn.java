package com.edispark;

import com.edispark.identifiers.partition.Partition;
import com.edispark.identifiers.region.Region;
import com.edispark.identifiers.resource.Resource;
import com.edispark.identifiers.service.Service;
import com.edispark.identifiers.tenant.TenantId;
import com.edispark.rules.CrnRuleValidator;
import com.edispark.rules.LengthRestriction;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

/**
 * Canonical EdiSpark resource name.
 *
 * <p>Format: {@code crn:edispark:service:region:tenant-id:resource-type/resource-id}.
 * The region may be empty for global resources. The resource type is optional, but resource IDs
 * must always be present.</p>
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class Crn extends CrnRuleValidator {

  /**
   * The leading sequence.
   */
  private static final String PREFIX = "crn";

  /**
   * The separator.
   */
  private static final String SEP = ":";

  /** EdiSpark's fixed CRN partition. */
  public static final String FORMAT = "crn:edispark:service:region:tenant-id:resource-type/resource-id";
  private static final Partition DEFAULT_PARTITION = Partition.fromString("edispark");

  Partition partition;
  Service service;
  Region region;
  TenantId tenantId;
  Resource resource;


  public Crn(Partition partition, Service service, Region region, TenantId tenantId,
      Resource resource) {
    this.partition = requireEdiSparkPartition(partition);
    this.service = requireValue(service, "service");
    this.region = Objects.requireNonNull(region, "region must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenant ID must not be null");
    this.resource = requireResource(resource);
    checkRule(new LengthRestriction(this));
  }

  public Crn(Service service, Region region, TenantId tenantId, Resource resource) {
    this(DEFAULT_PARTITION, service, region, tenantId, resource);
  }

  public static Crn fromString(String crnString) {
    if (StringUtils.isEmpty(crnString)) {
      throw new MalformedCrnException();
    }

    String[] tokens = StringUtils.splitPreserveAllTokens(crnString, SEP);

    if (tokens.length != 6) {
      throw new MalformedCrnException();
    }

    if (doesNotStartWithCrn(tokens)) {
      throw new MalformedCrnException();
    }

    try {
      return new Crn(
          Partition.fromString(tokens[1]),
          Service.fromString(tokens[2]),
          Region.fromString(tokens[3]),
          TenantId.fromString(tokens[4]),
          Resource.fromString(tokens[5]));
    } catch (RuntimeException exception) {
      throw new MalformedCrnException(exception);
    }
  }

  private static boolean doesNotStartWithCrn(String[] tokens) {
    return !tokens[0].equals(PREFIX);
  }

  private static Partition requireEdiSparkPartition(Partition partition) {
    Partition resolvedPartition = Objects.requireNonNull(partition, "partition must not be null");
    if (!DEFAULT_PARTITION.equals(resolvedPartition)) {
      throw new IllegalArgumentException("partition must be 'edispark'");
    }
    return resolvedPartition;
  }

  private static Service requireValue(Service service, String name) {
    Service resolvedService = Objects.requireNonNull(service, name + " must not be null");
    if (resolvedService.asString().isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return resolvedService;
  }

  private static Resource requireResource(Resource resource) {
    Resource resolvedResource = Objects.requireNonNull(resource, "resource must not be null");
    if (resolvedResource.asString().isBlank()) {
      throw new IllegalArgumentException("resource must not be blank");
    }
    return resolvedResource;
  }

  public String asString() {
    return this.toString();
  }

  /**
   * {@value #FORMAT}
   */
  @Override
  public String toString() {
    StringBuilder crnStringBuilder = new StringBuilder(PREFIX);
    crnStringBuilder.append(SEP);
    crnStringBuilder.append(this.getPartition().asString());
    crnStringBuilder.append(SEP);
    crnStringBuilder.append(this.getService().asString());
    crnStringBuilder.append(SEP);
    crnStringBuilder.append(this.getRegion().asString());
    crnStringBuilder.append(SEP);
    crnStringBuilder.append(this.getTenantId().asString());
    crnStringBuilder.append(SEP);
    crnStringBuilder.append(this.getResource().asString());
    return crnStringBuilder.toString();
  }
}
