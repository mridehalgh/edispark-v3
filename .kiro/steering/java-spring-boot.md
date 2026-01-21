---
inclusion: always
---
# Java 25 & Spring Boot Standards

## Java Version

- Use Java 25 features where appropriate (records, pattern matching, virtual threads, etc.)
- Prefer records for DTOs and value objects
- Use sealed classes for restricted type hierarchies

## Lombok

Lombok is mandatory for reducing boilerplate. Add to every module:

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>
```

- Use `@Getter`, `@ToString`, `@RequiredArgsConstructor` as standard
- Use `@Builder` for complex object construction
- Use `@Value` for immutable classes when records are not suitable
- NEVER write getters, setters, toString, equals, or hashCode manually
- See `coding-standards.md` for detailed Lombok patterns

## Spring Boot Best Practices

### Project Structure

- Follow hexagonal/ports-and-adapters architecture
- Separate domain logic from infrastructure concerns
- Use constructor injection (not field injection)

### Configuration

- Externalize configuration using `application.yml` or `application.properties`
- Use `@ConfigurationProperties` for type-safe configuration binding
- Profile-specific configs: `application-{profile}.yml`

### REST APIs

- Use `@RestController` with clear resource naming
- Return appropriate HTTP status codes
- Use `ResponseEntity` for explicit control over responses
- Validate inputs with `@Valid` and Bean Validation annotations

### Exception Handling

- Use `@ControllerAdvice` for global exception handling
- Return consistent error response structures
- Don't expose internal details in error messages

### Data Access

- Use Spring Data JPA repositories for standard CRUD
- Write custom queries with `@Query` when needed
- Use transactions appropriately with `@Transactional`

## Maven

### Build Configuration

- Use Maven wrapper (`mvnw`) for consistent builds
- Define dependency versions in `<dependencyManagement>`
- Use Spring Boot parent POM or BOM for version management

### Common Commands

```bash
./mvnw clean install        # Build and install
./mvnw test                 # Run tests
./mvnw spring-boot:run      # Run application
./mvnw verify               # Full build with integration tests
```

### Testing Dependencies

- JUnit 5 for unit tests
- Mockito for mocking
- Spring Boot Test for integration tests
- AssertJ for fluent assertions
