# Project
Spring Boot backend (Java 25, Maven) proof of concept project that acts as a microservice (intermediary) between elasticsearch + kibana container and public API interface that acts as a source of logs that will be shown in UI.

## Project Structure
- Backend: repository root

## Base Backend Layout
- Base package: dev.stacktrace
- Controllers: src/main/java/dev/stacktrace/controller/, services: src/main/java/dev/stacktrace/service/, mappers: src/main/java/dev/stacktrace/repository/ (XML in  src/main/resources/mybatis/mapper/)
- Database: src/main/resources/database/
- Authentication implementation: src/main/java/dev/stacktrace/auth/
- Error handling: src/main/java/dev/stacktrace/error/

## Commands
- Build: mvn compile
- Test: mvn test
- Clean & build: mvn clean install -DskipTests
- Run: mvn spring-boot:run (only when asked, it blocks)
- Frontend dev: npm run dev (in app/, only when asked)
- Frontend check: npm run check
- Frontend build: npm run build

## Boundaries
- Do not edit dependencies in pom.xml or package.json without asking
- Never read or print secrets, .env files, or production config
- Do not commit or push unless asked

## Architecture
- Layers: controller -> service -> mapper (MyBatis)
- Controllers stay thin and never touch mappers directly
- Never expose entities (if there are any) in API responses, map to DTOs

## Java code style
- IntelliJ default Java style: 4-space indent, 8-space continuation, max line length 120
- Wildcard imports (including static ones) once 5 or more members are used from the same package or class
- Lombok: @Slf4j for logging

### Formatting and layout
- Import order: IntelliJ default
- Class annotations one per line, stereotype first (`@RestController`, `@RequestMapping`, `@RequiredArgsConstructor`, `@Validated`)
- No blank line between the class opening brace and the first field; one blank line after the field block and between methods
- Dependencies are `private final` fields injected via `@RequiredArgsConstructor`; no hand-written DI constructors
- Blank line before the final `return` when the method has preceding statements; blank lines separate logical steps (fetch / transform / return); one-statement methods have no blank lines
- Wrapped method parameters align with the first parameter; other continuation lines use an 8-space indent
- Fluent builder and stream chains: one call per line, leading `.`, 8-space continuation
- Multi-line exception construction: argument on its own line (8-space continuation), closing `);` on its own line aligned with `throw`
- Guard clauses with early return instead of nested `if`/`else`
- `var` for local variables when the type is obvious from the right-hand side
- Switch expressions with arrow labels, `instanceof` pattern matching, text blocks for long string literals
- No magic numbers or strings; use named constants or enums
- Avoid comments unless they explain something non-obvious

### Naming
- Methods: `getX`, `getXForY`, `insertX`, `updateX`, `deleteX`, `setXFlag` (e.g. `setPostHidden`); keep the same name across controller -> service -> mapper
- IDs: `id` prefix + entity (`idUser`, `idSection`), column `id_user`, `id_section`
- DTOs: `Insert*Request`, `Update*Request`, `Get*Request`, `Get*Response`; read models `*Summary` / `*Details`
- REST paths: lowercase kebab-case resource nouns without a leading slash; the HTTP method expresses the action (`@RequestMapping("post")`, `@PostMapping`, `@PutMapping("hidden")`), never verbs like `insert-post`
- Constants: `UPPER_SNAKE_CASE` in a `final` utility class with a private constructor, used via static import

### Types and return values
- Wrapper types (`Integer`, `Long`, `Boolean`) in DTOs, method parameters and return types, not primitives
- `java.time` types for dates; `LocalDateTime` request fields get `@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")`
- Collections are returned as `List<T>`, never `null`
- `Optional<T>` for "may be absent" results of helper/parsing methods; never for fields or parameters
- Generic helpers take `Class<T> clazz` and return `T`
- Services return DTOs/Response objects; mappers return DTOs, or `void` for writes

### Controller style
- Controllers always return `ResponseEntity<T>` built with `new ResponseEntity<>(body, HttpStatus.X)`; `ResponseEntity<Void>` when there is no body
- Pick the most precise `HttpStatus` for the outcome. Common defaults: GET/PUT -> `OK`, POST create -> `CREATED`, DELETE -> `NO_CONTENT`, queued/async work -> `ACCEPTED`; use others (`BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `FORBIDDEN`, `UNPROCESSABLE_ENTITY`, ...) where they fit
- Prefer binding request DTOs with `@ModelAttribute` and single IDs as plain `Integer` parameters; use `@RequestBody`, `@PathVariable`, `@RequestParam` etc. where they fit better (e.g. JSON bodies)
- Body: call one service method, blank line, return the `ResponseEntity`; for reads, `var content = service.getX();`
- No try/catch, no business logic

### Service style
- Every method that writes to the database (write-only or read+write) is `@Transactional`; pure reads are not
- Write methods wrap the mapper call and translate failures into a domain exception (see Error handling)
- Simple reads return the mapper result directly; multi-step reads aggregate into a response object

### Method structure
- Private helpers go after public methods
- A private method used by only one method stays in the class that uses it
- A private method used by several methods or classes is extracted into its own util/service class, or moved into the most fitting existing one, rather than kept as a shared private helper
- Methods whose body exceeds 100 lines are split into private methods or util/service methods; keep the method as is only when splitting would make the code worse

### DTOs and enums
- DTOs are not records: `@Data`, `@AllArgsConstructor`, `@NoArgsConstructor`
- Use `@Builder` only when it makes construction clearly more readable (many or optional fields); prefer constructors/setters otherwise
- Enums carrying values: `@Getter`, `private final` field, explicit constructor

## Error handling
- Throw domain exceptions extending a common base exception, never bare RuntimeException
- Handle them centrally in a @RestControllerAdvice
- Never swallow exceptions; log once at the boundary
- When translating a general/unexpected exception, keep the original as the cause; for expected failures the message in the error map is enough
- Each failure has an `ErrorCode` enum constant with a 5-digit string code, grouped with header comments; use only code ranges defined in `error.properties`
- Code, message and HTTP status live in `error.properties` as `<code>.code`, `<code>.message`, `<code>.status`
- Build exceptions only via `errorResponseBuilder.build(errorProperties.getError(ErrorCode.X))`; the advice returns that error map with its status
- Standard Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Size`) on request DTOs for single-field checks; annotate the controller parameter with `@Valid` so they are applied
- Custom validation for checks that need an `ErrorCode` or span several fields: a constraint annotation (`@ValidX`) plus a `@Component` `ConstraintValidator` with a private `check(Supplier<Boolean>, ErrorCode)` helper that throws `ValidationException`; controllers using it are annotated `@Validated`
- Add a specific error code instead of reusing a generic/placeholder one; never use `@SneakyThrows`
- Log with parameterized messages, quoted values, ending with a period: `log.info("Sent moderation request: '{}' to '{}'.", request, topic);`

## MyBatis
- All dynamic values (method parameters, request data, anything computed at runtime) must use #{} binding to prevent SQL injection
- Static constants may be hardcoded in the SQL (status = 'ACTIVE', LIMIT 100)
- Never use ${}; if a dynamic identifier is needed (sort column), map it from a fixed allowlist in code
- Mapper interfaces: `@Mapper`, a single DTO or a single ID as parameter, one blank line between methods
- XML: uppercase SQL keywords, 4-space indent, one selected column per line and aligned
- Alias tables (`FROM section AS s`) and alias snake_case columns to camelCase (`s.id_section AS idSection`)
- `resultType`/`parameterType` use type aliases; inserts use `useGeneratedKeys="true"`

## Database
- PostgreSQL only
- Table names singular snake_case (user, order_item)
- Always quote the user table as "user" in SQL, migrations, and MyBatis mappers, since it is a reserved word
- Do not add fields like is_admin to the user table unless asked
- Never edit existing migrations; add a new one

## SQL verification
- Verify SQL, migrations, and mapper queries only against a temporary podman PostgreSQL container
- Create it with --rm, a unique name, throwaway credentials, no volumes, and a port bound to 127.0.0.1 only
- Use the same PostgreSQL major version as the project
- Apply migrations in order to a fresh container, never to a reused one
- Stop the container when done so --rm removes it
- Never connect to an existing container or to any database not created in the current task

## Testing
- JUnit 5 + Mockito for unit tests
- Run mvn test before declaring a task done (for backend changes); run npm run check for frontend changes
- Avoid writing unit tests unless prompted