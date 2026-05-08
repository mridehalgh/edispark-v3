# Project Standards and Guidelines 📋

## Code Quality Standards 🎯

### Java Development
- Follow Google Java Style Guide with 2-space indentation
- Use Java 21 with preview features enabled
- Apply SOLID principles and favor composition over inheritance
- Keep methods small and focused (4-10 lines ideally)
- Use meaningful names for classes, methods, and variables
- Implement proper exception handling without exposing sensitive information

### Lombok Integration
- Use `@RequiredArgsConstructor` for dependency injection
- Declare dependencies as `private final` fields
- Add Lombok as annotation processor in Maven compiler plugin
- Avoid `@Autowired` annotation in favor of constructor injection

### Multi-Module Architecture
- Keep modules focused on single responsibilities
- Use `${project.version}` for inter-module dependencies
- Define dependency versions in parent POM `<dependencyManagement>`
- Avoid circular dependencies between modules

## Testing Requirements 🧪

### Test Coverage Standards
- Maintain minimum 80% code coverage across all modules
- Write unit tests for all business logic functions
- Include integration tests for API endpoints and AWS services
- Use Spock Framework for Groovy-based testing
- Use JUnit 5 for Java unit tests

### Test Organization
- Use descriptive test names explaining scenario and expected result
- Follow Arrange-Act-Assert pattern for clear test structure
- Test units of behavior, not individual classes
- Mock external dependencies (AWS services, HTTP requests)
- Include test data generation for EDI message processing

### Test Execution
- Run `mvn test` before committing changes
- Ensure all tests pass in CI/CD pipeline
- Update tests when changing method signatures
- Verify mock objects match new method signatures

## Documentation Standards 📝

### Code Documentation
- Use Javadoc for public APIs with `@param`, `@return`, `@throws`
- Document complex EDI parsing logic and business rules
- Include examples for API usage and configuration
- Maintain architectural decision records (ADRs)

### Project Documentation
- Update README.md for significant changes
- Document AWS infrastructure setup and deployment
- Include troubleshooting guides for common issues
- Maintain changelog for version releases

## Security Practices 🔒

### AWS Security
- Use IAM roles with least privilege principle
- Encrypt sensitive data at rest and in transit
- Store secrets in AWS Systems Manager Parameter Store
- Implement proper VPC security groups and NACLs

### Application Security
- Validate and sanitize all EDI input data
- Use parameterized queries for database operations
- Never hardcode credentials, API keys, or secrets
- Implement rate limiting for API endpoints
- Log security events for monitoring and auditing

### Data Protection
- Substitute PII with placeholders: `[company_name]`, `[address]`, `[contact_info]`
- Implement data masking for non-production environments
- Follow data retention policies for EDI messages
- Ensure GDPR/CCPA compliance for customer data

## Performance Guidelines ⚡

### EDI Processing Optimization
- Implement streaming for large EDI files
- Use compression (GZIP) for S3 storage as documented in parser/README.md
- Optimize Lambda memory allocation based on file sizes
- Implement proper caching strategies for schema validation

### AWS Resource Optimization
- Monitor Lambda execution times and memory usage
- Use S3 lifecycle policies for cost optimization
- Implement CloudWatch alarms for performance monitoring
- Consider DynamoDB vs S3 storage costs per analysis

## Build and Deployment 🚀

### Maven Configuration
- Use parent POM for dependency management
- Configure plugins in `<pluginManagement>` section
- Enable Java 21 preview features consistently
- Include Lombok annotation processor configuration

### AWS CDK Infrastructure
- Use TypeScript for CDK constructs
- Implement proper stack separation (infra vs application)
- Use CDK context for environment-specific configuration
- Include proper IAM policies and resource tagging

### CI/CD Pipeline
- Run full test suite on every commit
- Build and deploy infrastructure changes separately
- Use blue-green deployment for Lambda functions
- Implement proper rollback procedures