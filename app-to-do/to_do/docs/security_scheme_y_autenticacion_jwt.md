# @SecurityScheme: Documentando la Autenticacion JWT en Swagger UI

Este documento explica para que sirve la anotacion `@SecurityScheme` que esta en la clase `ToDoApplication`, como funciona en Swagger UI y por que **no protege nada por si sola**.

---

## 1. Que es @SecurityScheme

Es una anotacion de **springdoc OpenAPI** (`io.swagger.v3.oas.annotations.security.SecurityScheme`). Su unica funcion es **declarar en el spec OpenAPI** (el JSON que alimenta a Swagger UI) un esquema de autenticacion, para que Swagger muestre un boton **"Authorize"** y puedas probar los endpoints protegidos pegando tu token.

```java
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
```

## 2. Que significa cada campo

| Campo | Valor | Que hace |
|---|---|---|
| `name` | `"bearerAuth"` | Identificador unico del esquema. Se usa para referenciarlo con `@SecurityRequirement(name = "bearerAuth")` en un endpoint o controlador. |
| `type` | `SecuritySchemeType.HTTP` | Indica que el esquema usa cabeceras HTTP (estandar para Bearer Token). |
| `scheme` | `"bearer"` | Esquema de autenticacion HTTP `Bearer`, es decir la cabecera `Authorization: Bearer <token>`. |
| `bearerFormat` | `"JWT"` | **Solo informativo/metadata.** Documenta que el token es un JWT. No valida nada. |

## 3. Como se ve en la practica

1. Al abrir Swagger UI aparece el boton **"Authorize"** (normalmente en la esquina superior derecha).
2. Al hacer clic pegas tu token JWT.
3. Swagger agrega automaticamente la cabecera `Authorization: Bearer <token>` a **todas** las peticiones que hagas desde la UI.
4. En el spec `/v3/api-docs` se genera la seccion `components.securitySchemes.bearerAuth`.

## 4. ADVERTENCIA: esto NO protege nada

La anotacion es **pura documentacion**. No configura ninguna seguridad en runtime.

En este proyecto (verificado en `pom.xml`), la dependencia `spring-boot-starter-security` esta **comentada**, lo que significa que:

- Cualquier peticion llega al backend sin validar token.
- Cualquiera puede crear, listar o manipular tareas sin autenticarse.

El `@SecurityScheme` solo agrega el boton "Authorize" a Swagger UI y documenta el esquema JWT. **No bloquea el acceso.**

## 5. Que se necesita para que sea real

Para que la autenticacion sea efectiva hay que:

1. **Activar Spring Security**: descomentar `spring-boot-starter-security` en `pom.xml`.
2. **Crear un `SecurityFilterChain`**: configurar que rutas son publicas (ej. `/api/auth/**`) y cuales exigen token (ej. `/api/tasks/**`).
3. **Agregar un filtro JWT** (ej. `JwtAuthenticationFilter extends OncePerRequestFilter`) que:
   - Lea la cabecera `Authorization: Bearer <token>`.
   - Valide la firma y vigencia del JWT.
   - Cargue el usuario y lo registre en el `SecurityContext`.
4. **Marcar los endpoints protegidos** con `@SecurityRequirement(name = "bearerAuth")` para que en Swagger aparezcan con un candado y exijan el boton Authorize.

---

## Diagrama del flujo completo (Feo pero claro)

```
Cliente
  |
  | 1. POST /api/auth/login  (email + password)   <- publico
  v
Backend: genera y firma un JWT (ej. con jjwt / Nimbus)
  |
  | 2. Devuelve token -> Cliente lo guarda
  |
  | 3. GET /api/tasks  con cabecera:
  |    Authorization: Bearer eyJhbGciOi...
  v
JwtAuthenticationFilter (Spring Security)
  |  - valida firma/expiración
  |  - si es valido -> SecurityContext.setAuthentication(usuario)   -> PASA
  |  - si no      -> 401 Unauthorized                                -> BLOQUEADO
  v
TaskController (con @SecurityRequirement("bearerAuth"))
```

## 6. Resumen

- `@SecurityScheme` = declara el esquema JWT en OpenAPI y habilita el boton Authorize de Swagger.
- Solo funciona como documentacion; **no protege endpoints**.
- Para proteger de verdad: Spring Security + filtro de validacion de JWT + `@SecurityRequirement` en los endpoints.