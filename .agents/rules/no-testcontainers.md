# No TestContainers Rule 🚫

## Prohibition
- **NEVER** use TestContainers in any test code
- **NEVER** suggest TestContainers as a testing solution
- **NEVER** include TestContainers dependencies in POM files

## Alternative Approaches
- Use in-memory databases (H2, HSQLDB) for database testing
- Mock AWS services with Mockito or LocalStack
- Use embedded servers for integration testing
- Create test doubles and stubs for external dependencies

## Rationale
- **Slow execution damages developer velocity** - TestContainers significantly increase test runtime
- **Poor developer experience** - Long feedback loops discourage frequent testing
- **Resource intensive** - Heavy memory and CPU usage slows development machines
- **Complex setup** - Docker dependency adds unnecessary complexity to development environment
- **CI/CD bottleneck** - Slower pipeline execution reduces deployment frequency