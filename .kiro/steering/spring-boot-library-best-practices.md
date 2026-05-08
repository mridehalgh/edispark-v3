# Spring Boot Library Best Practices 🚀

## Auto-Configuration 🔧
- Use `@ConditionalOnProperty` for optional features
- Provide `META-INF/spring.factories` for auto-configuration
- Create separate configuration classes for different concerns

## Dependency Management 📦
- Mark Spring dependencies as `provided` scope when possible
- Use minimal required dependencies
- Avoid version conflicts with host applications

## Interface Design 🎯
- Create interfaces for key components (e.g., `KeyProvider`)
- Use constructor injection with `@RequiredArgsConstructor` (Lombok)
- Declare dependencies as `private final` fields
- Avoid `@Autowired` annotation
- Design for testability with mockable interfaces

## Configuration Properties 📝
- Provide sensible defaults in `application-*.properties`
- Use clear property naming conventions
- Support both development and production configurations

## Error Handling ⚠️
- Create custom exceptions with meaningful error responses
- Use `@RestControllerAdvice` for global exception handling
- Provide clear error messages for configuration issues

## Testing 🧪
- Separate concerns into testable units
- Use static utility classes for pure functions
- Mock external dependencies (KMS, HTTP requests)
- Provide comprehensive unit tests