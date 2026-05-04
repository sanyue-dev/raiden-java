# Repository Guidelines

## Project Structure & Module Organization

This is a Maven-based Java Swing application for simulating MQTT charging stations. Source code lives under `src/main/java/com/raiden/`. The entry point is `com.raiden.Main`. Core packages are organized by responsibility: `domain/` contains charging station and port state models, `mqtt/` contains MQTT integration, protocol encoding/decoding, and application services, `ui/` contains Swing panels, table models, and theme classes, and `platform/` contains disposable lifecycle utilities. Runtime assets, currently bundled fonts, live in `src/main/resources/fonts/`. Build output is generated in `target/` and should not be edited or committed. There is currently no `src/test/java` tree; add tests there when introducing automated coverage.

## Build, Test, and Development Commands

- `mvn exec:java`: runs the Swing application using `com.raiden.Main`.
- `mvn package`: compiles and creates the shaded executable JAR in `target/`.
- `java -jar target/raiden-java-1.0.1.jar`: runs the packaged application after `mvn package`.
- `mvn test`: runs Maven tests when test classes are present.
- `mvn -Pnative package`: builds the JAR and invokes `jpackage` for a native app image. Requires a JDK with `jpackage`.

The project targets JDK 25, so verify `java -version` before building.

## Coding Style & Naming Conventions

Use Java with 2-space indentation, UTF-8 encoding, and the existing brace style. Keep classes small and package-private where possible unless they form an API boundary. Use `UpperCamelCase` for classes and enums, `lowerCamelCase` for methods and local variables, and existing `myFieldName` style for private instance fields. Prefer explicit failures over silent fallbacks; do not add mock success paths, hidden caps, or defensive degradation unless explicitly agreed.

## Testing Guidelines

Place tests under `src/test/java` mirroring the production package structure. Use descriptive names such as `RaidenProtocolCodecTest` and test methods that state behavior, for example `decodesStartChargingCommand`. Prioritize protocol parsing, domain state transitions, and MQTT service lifecycle behavior. Run `mvn test` before submitting changes.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commits, such as `feat: support cancelling in-progress MQTT connections`, `fix: resolve cancel race condition and EDT blocking on disconnect`, and `refactor: consolidate application/protocol/infrastructure into mqtt package`. Keep commits focused and use `feat:`, `fix:`, `refactor:`, or `docs:` as appropriate. Pull requests should include a short summary, testing performed, linked issues if applicable, and screenshots or screen recordings for visible UI changes.

## Agent skills

### Issue tracker

Issues and PRDs are tracked in GitHub Issues for `sanyue-dev/raiden-java`. See `docs/agents/issue-tracker.md`.

### Triage labels

Triage uses the default category and state label vocabulary. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repo with root `CONTEXT.md` as the domain glossary. See `docs/agents/domain.md`.
