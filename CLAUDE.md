# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**FAF Moderator Client** is a Spring Boot 4.0.6 + JavaFX 21 desktop application for moderating the [FAForever](https://faforever.com) gaming community platform. It communicates with the FAF API via JSON:API protocol, authenticates via OAuth2 (Hydra), and supports three environments: production, test, and localhost.

## Build Commands

The JavaFX platform is auto-detected for all OSes (Windows, Linux, Mac/Mac-aarch64) via `gradle.properties` default. Override only if detection fails:

```bash
./gradlew build                           # Auto-detects platform
./gradlew test                            # Run all tests
./gradlew test --tests "com.faforever.moderatorclient.SomeTest"  # Single test
./gradlew shadowJar                       # Build fat JAR for distribution
./gradlew -PjavafxPlatform=win build      # Override platform explicitly if needed
```

The Shadow plugin produces a platform-classified ZIP archive for distribution.

## Running the Application

The environment (production, test, localhost) is selected at runtime via the login screen dropdown — there are no Spring profile files. All three environments are defined in a single `src/main/resources/application.yml` under `faforever.environments` keys (`faforever.com`, `test.faforever.com`, `localhost:8010`).

The last-used environment is persisted by `LocalPreferences`.

## Architecture

### Entry Point & Startup

`Launcher.java` → `FafModeratorClientApplication` (extends `javafx.application.Application`):
1. `init()`: Starts Spring ApplicationContext, wires all beans
2. `start()`: Loads `mainWindow.fxml` via `UiService`, applies CSS theme from `LocalPreferences`
3. A background timer thread monitors API request rate (displayed in window title alongside uptime and open report counts)

Spring is configured with `allow-circular-references: true` due to interdependencies between controllers and services.

### Layer Structure

```
UI Layer       — JavaFX Controllers (FXML-backed, Spring-managed beans)
     ↓
Domain Services — api/domain/ services (UserService, BanService, etc.)
     ↓
API Layer      — FafApiCommunicationService (single entry point for all HTTP calls)
```

### UI Layer (`ui/` and `ui/main_window/`)

- `UiService.loadFxml(path)` loads FXML using Spring's `ApplicationContext` as the `FXMLLoader` controller factory — all controllers are Spring beans with full DI
- `MainController` manages the main `TabPane` and enforces permission-based tab visibility
- The root view is `mainWindow.fxml`; each feature tab has its own FXML in `src/main/resources/ui/`
- Custom `TableCell` renderers live in `ui/data_cells/`
- Domain objects for JavaFX bindings are `*FX` classes in `ui/domain/` (e.g., `PlayerFX`, `BanInfoFX`, `ModerationReportFX`)

### API Layer (`api/`)

`FafApiCommunicationService` is the single HTTP client:
- Uses Spring `RestTemplate` with `JdkClientHttpRequestFactory` (Java built-in HTTP client)
- JSON:API protocol via the `jasminb` library
- OAuth2 bearer token injected via interceptor; auto-refreshes on expiry (`TokenExpiredEvent`)
- Rate-limited to 90 req/min via a custom sliding-window implementation (`checkRateLimit()` in `FafApiCommunicationService`)
- HMAC header signing for sensitive operations (key in `application.yml`)
- Query building via Elide `ElideNavigator` (see `ElideNavigatorTest` for examples)
- Root URI set via `DefaultUriBuilderFactory`; `RestTemplate` wired manually (Spring Boot 4.0 removed `RestTemplateBuilder` and `JacksonAutoConfiguration`)

### DTO Mapping (`mapstruct/`)

MapStruct mappers convert between JSON:API domain objects (from `faf-java-commons`) and JavaFX `*FX` domain objects. A shared `CycleAvoidingMappingContext` parameter prevents infinite loops from circular references. The component model is `jsr330` (Spring-compatible).

### Configuration

- `ApplicationProperties` (`@ConfigurationProperties`) exposes typed access to `application.yml`
- `LocalPreferences` persists user settings (theme, last-used tabs, etc.) to `client-prefs.json` in the working directory
- Three FAF environments (`faforever.com`, `test.faforever.com`, `localhost:8010`) are defined in `application.yml` — no Spring profile files exist; environment selection is UI-driven
- `JsonApiConfig` explicitly defines the `ObjectMapper` bean (with `JavaTimeModule`, `Jdk8Module`, `NON_NULL` serialization, and `FAIL_ON_UNKNOWN_PROPERTIES` disabled) — Spring Boot 4.0 no longer auto-configures Jackson

## Key Files

| File | Purpose |
|------|---------|
| `FafModeratorClientApplication.java` | JavaFX + Spring lifecycle |
| `ui/MainController.java` | Tab orchestration, permission enforcement |
| `api/FafApiCommunicationService.java` | All API communication |
| `ui/UiService.java` | FXML loading, Spring-as-FXMLLoader-factory |
| `config/ApplicationProperties.java` | Typed config properties |
| `src/main/resources/application.yml` | Environment profiles & endpoints |

## Testing

The test suite is minimal — a Spring context load test and `ElideNavigatorTest`. There is no mock layer for the API; integration-style tests would require a running FAF API instance.

## Dependency Notes

- `faf-java-commons` (`com.faforever.commons:api` and `com.faforever.commons:data`) is pulled from JitPack using commit-based version strings (e.g. `20260407-92612a0`)
- Lombok requires annotation processing enabled in the IDE
- MapStruct annotation processor must also be enabled (both declared in `annotationProcessor` config)
