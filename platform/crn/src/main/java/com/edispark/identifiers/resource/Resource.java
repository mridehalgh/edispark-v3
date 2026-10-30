package com.edispark.identifiers.resource;

import java.util.Arrays;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Getter
@Builder
public class Resource {

  public static final String SEPARATOR = "/";
  ResourceType resourceType;
  ResourceId resourceId;

  public Resource(ResourceType resourceType, ResourceId resourceId) {
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }

  public static Resource fromString(String token) {
    if (StringUtils.isBlank(token) || token.startsWith(SEPARATOR) || token.endsWith(SEPARATOR)) {
      throw new IllegalArgumentException("resource must include a non-empty ID");
    }
    String[] strings = StringUtils.split(token, SEPARATOR);

    if (resourceStringDoesNotContainType(strings)) {
      ResourceId id = ResourceId.fromString(token);
      return new Resource(null, id);
    }

    String[] resourceIdArray = Arrays.copyOfRange(strings, 0, strings.length - 1);
    String resourceType = String.join(SEPARATOR, resourceIdArray);
    ResourceType type = ResourceType.fromString(resourceType);
    ResourceId id = ResourceId.fromString(strings[strings.length - 1]);

    return new Resource(type, id);
  }

  private static boolean resourceStringDoesNotContainType(String[] strings) {
    return strings.length == 1;
  }

  public String asString() {
    return this.toString();
  }

  public String toString() {
    Optional<ResourceType> hasResourceType = Optional.ofNullable(this.getResourceType());

    if (hasResourceType.isPresent()) {
      StringBuilder stringBuilder = new StringBuilder(this.getResourceType().asString());
      stringBuilder.append("/");
      stringBuilder.append(this.getResourceId().asString());
      return stringBuilder.toString();
    }

    return resourceId.asString();
  }
}
