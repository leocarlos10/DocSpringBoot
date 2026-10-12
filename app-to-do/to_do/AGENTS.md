# AGENTS.md

## Propósito
Guía específica y verificada para este repositorio de ToDo con Spring Boot. Solo incluye hechos que se pueden verificar directamente desde el código.

## Stack tecnológico (verificado)
- Spring Boot 4.1.1 (parent), Java 25 (`java.version: 25`)
- Web MVC (`spring-boot-starter-webmvc`)
- JPA + MySQL (`spring-boot-starter-data-jpa`, `mysql-connector-j`)
- Validación (`spring-boot-starter-validation`)
- Flyway (`spring-boot-starter-flyway`, `flyway-mysql`) - Los scripts de migración deben ir en `src/main/resources/db/migration/` (actualmente vacío)
- Lombok (opcional)
- OpenAPI UI: `springdoc-openapi-starter-webmvc-ui` 3.1.1
- Dependencias dev/test: `spring-boot-starter-*test`, `spring-boot-devtools`, `spring-boot-docker-compose`
- Build: Maven + `spring-boot-maven-plugin`

## Estructura del proyecto (verificado)
- Punto de entrada: `src/main/java/com/app/to_do/ToDoApplication.java`
- Paquetes: `model` (`Task.java`), `dto` (`CreateTaskRequest.java`, `TaskResponse.java`), `repository` (`TaskRepository.java`), `service` (`TaskService.java`), `controller` (directorio vacío)
- Recursos: `src/main/resources/application.properties` (mínimo), `src/main/resources/db/migration/` (vacío), `static/`, `templates/`
- Tests: `src/test/java/com/app/to_do/ToDoApplicationTests.java`
- Docker compose: `compose.yaml` define MySQL `mysql:latest` (DB `mydatabase`, usuario `myuser`, contraseñas configuradas). Spring Boot Docker Compose está habilitado en runtime; se espera usar compose para la base de datos localmente.
- Build: `target/` existe con clases compiladas (artefactos de build).

## Comandos (exactos, verificados)
Usar el Maven wrapper en la raíz del repositorio:
- Compilar y empaquetar: `./mvnw clean package`
- Solo compilar: `./mvnw compile`
- Ejecutar tests: `./mvnw test`
- Ejecutar una sola clase de test: `./mvnw test -Dtest=ToDoApplicationTests`
- Ejecutar un solo método de test: `./mvnw test -Dtest=ToDoApplicationTests#contextLoads`
- Ejecutar la aplicación (desarrollo): `./mvnw spring-boot:run` (requiere MySQL; `spring-boot-docker-compose` puede levantar compose, o proveer DB por separado)
- Saltar tests: `./mvnw -DskipTests package` / `./mvnw -DskipTests compile`
- Lint/typecheck: No hay comandos explícitos de lint/typecheck en pom.xml ni en configuración. No se detecta checkstyle/eslint/spotless/ktlint. Usar `./mvnw test` como verificación principal.
- Formateo: No hay configuración de formateo en el repo.

## Límites del módulo
Módulo Maven único `com.app:to_do` (artifact `to_do`, versión `0.0.1-SNAPSHOT`). Paquete base `com.app.to_do.*`. Sigue arquitectura por capas estándar (model/dto/repository/service/controller).

## Particularidades importantes (verificadas)
- Java 25. Asegurar que el JDK local coincida con esta versión.
- Spring Boot 4.1.1 - Tener en cuenta cambios potenciales respecto a 3.x al agregar dependencias.
- Flyway habilitado pero `db/migration/` vacío. Si se requieren migraciones de esquema, agregar SQL versionados ahí (ej. `V1__init.sql`). No asumir que existan migraciones.
- No hay clases `@Controller` en código fuente (directorio `controller/` vacío), mientras que existen service/repo/model/dto. La implementación está incompleta. Al agregar controladores, preservar estructura de paquetes y convenciones existentes.
- Dependencia `spring-boot-starter-security` comentada en pom.xml - la app no tiene seguridad configurada actualmente.
- `application.properties` es mínimo; la configuración de BD puede venir desde `compose.yaml` vía Spring Boot Docker Compose integration o desde variables de entorno. No agregar secretos al repositorio.
- El test por defecto (`@SpringBootTest` - `contextLoads`) es de integración: requiere contexto de BD. Con Flyway + JPA puede necesitar una BD real/test. Tener esto en cuenta.
- `target/` existe en disco. `.gitignore` incluye `target/` (estándar). Tratar como artefactos de build, no editar clases compiladas.
- No hay README ni archivos de instrucciones AGENTS/CLAUDE/Cursor/Copilot/OpenCode adicionales. Este `AGENTS.md` es la guía principal para agentes.

## Flujo de trabajo recomendado
1. Leer `pom.xml` y las clases fuente existentes antes de hacer cambios.
2. Para nuevos endpoints REST, crear un controlador en `com.app.to_do.controller` usando los DTOs record existentes y la capa service; seguir convenciones de Spring MVC + validación.
3. Preferir editar archivos existentes. Crear nuevos archivos solo cuando sea estrictamente necesario. Imitar estilo existente (anotaciones Lombok, layout de paquetes, comentarios en español en algunos archivos; mantener código consistente).
4. Ejecutar `./mvnw test` después de cambios. Si los tests requieren servicios externos, asegurar que compose/DB esté disponible o ajustar según corresponda.
5. Mantener cambios mínimos y alineados con buenas prácticas de Spring Boot. No commitear secretos.