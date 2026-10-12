# Spring Security desde cero: explicación e implementación en la API To-Do

Esta guía está escrita para una persona que conoce Java y Spring Boot básico,
pero que nunca ha trabajado con Spring Security. Tiene dos partes:

1. **Conceptos:** qué problema resuelve Spring Security y cómo se conectan sus
   componentes.
2. **Proyecto:** explicación de la implementación JWT que existe actualmente
   en esta API, clase por clase, método por método y anotación por anotación.

La implementación usa Spring Boot `4.1.1`, Spring Security administrado por
Spring Boot, JJWT `0.12.3`, usuarios almacenados en MySQL y una API
**stateless**: cada petición protegida lleva su propio token.

---

# Parte I. Cómo funciona Spring Security

## 1. El problema que resuelve

Una API necesita responder dos preguntas:

- **Autenticación:** ¿quién está haciendo la petición?
- **Autorización:** ¿qué puede hacer ese usuario?

No son lo mismo. Por ejemplo:

```text
Usuario envía email y contraseña
              |
              v
Autenticación: el servidor comprueba quién es
              |
              v
Se identifica como usuario USER
              |
              v
Autorización: se decide si puede leer esa tarea concreta
```

Un usuario puede estar correctamente autenticado y aun así no tener permiso
para acceder a una ruta o a los datos de otro usuario.

## 2. El flujo de una petición protegida

Spring Security se ejecuta antes de que la petición llegue al controller:

```text
Cliente
  |
  | GET /api/tasks
  | Authorization: Bearer <token>
  v
Filtros de Spring Security
  |
  +-- extraen y validan el token
  +-- crean una Authentication
  +-- la guardan en SecurityContext
  v
SecurityFilterChain
  |
  +-- ¿la ruta es pública?
  +-- ¿hay usuario autenticado?
  +-- ¿tiene el rol necesario?
  v
Controller -> Service -> Repository
```

Si la petición no cumple una regla, el controller nunca se ejecuta.

## 3. Componentes principales

### 3.1 `SecurityFilterChain`

Es la cadena de filtros y reglas HTTP de la aplicación. En ella se configura:

- rutas públicas;
- rutas que requieren login;
- roles permitidos;
- sesiones;
- CSRF;
- filtros personalizados.

La configuración moderna se declara como un bean:

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated());
    return http.build();
}
```

`HttpSecurity` es el objeto de configuración que Spring entrega al método.
`build()` transforma esa configuración en una cadena ejecutable.

### 3.2 Filtros

Un filtro recibe la petición antes del controller, puede inspeccionarla y
decidir si continúa. Una cadena tiene varios filtros: manejo de sesión,
autenticación, autorización, excepciones, etc.

Para JWT se agrega un filtro propio antes de
`UsernamePasswordAuthenticationFilter`. Su responsabilidad es leer:

```http
Authorization: Bearer eyJ...
```

validar el token y crear la identidad de la petición.

### 3.3 `Authentication`

Representa el resultado de una autenticación. Puede contener:

- `principal`: quién es el usuario;
- `credentials`: credenciales, que normalmente se limpian después del login;
- `authorities`: permisos o roles;
- estado autenticado.

En esta aplicación el principal es un `UserDetails` y sus authorities son
`ROLE_USER` o `ROLE_ADMIN`.

### 3.4 `SecurityContext`

Es el contexto de seguridad asociado a la petición actual. Spring guarda allí
la `Authentication`. Se puede consultar mediante:

```java
SecurityContextHolder.getContext().getAuthentication();
```

El contexto no debe confundirse con la base de datos. Contiene la identidad
resuelta para esta petición, mientras que la base contiene los datos
persistentes del usuario.

### 3.5 `SecurityContextHolder`

Es la puerta de acceso estática al `SecurityContext`. Spring lo limpia al
terminar la petición. Si una clase necesita saber quién está autenticado, puede
leerlo desde aquí, aunque conviene encapsularlo en un servicio propio.

### 3.6 `UserDetails`

Spring Security usa `UserDetails` como representación de seguridad del usuario.
Incluye:

- nombre de usuario;
- contraseña almacenada;
- authorities;
- si está habilitado, expirado o bloqueado.

No obliga a que la entidad JPA tenga esa misma forma. Por eso el proyecto
convierte la entidad `User` en un `UserDetails`.

### 3.7 `UserDetailsService`

Su único propósito es cargar un usuario a partir de su identificador. Aunque el
método se llama `loadUserByUsername`, el identificador no tiene que ser un
nombre: en esta API es el email.

El flujo de login es:

```text
email recibido
  -> UserDetailsService
  -> UserRepository.findByEmail(email)
  -> UserDetails
```

### 3.8 `PasswordEncoder`

Las contraseñas no se cifran para luego descifrarlas: se convierten en hashes.
BCrypt genera un hash que incluye salt y un coste configurable.

```java
String hash = passwordEncoder.encode(password);
boolean coincide = passwordEncoder.matches(password, hash);
```

Nunca se debe guardar ni comparar una contraseña como texto plano.

### 3.9 `AuthenticationManager`

Coordina el login. Recibe un objeto de credenciales, busca el usuario mediante
`UserDetailsService` y delega la comparación de contraseña al encoder.

```java
Authentication credentials =
    new UsernamePasswordAuthenticationToken(email, password);

Authentication result = authenticationManager.authenticate(credentials);
```

Si las credenciales no son válidas, lanza una excepción de autenticación. Si
son válidas, devuelve una `Authentication` autenticada.

### 3.10 Roles y authorities

Spring Security trabaja técnicamente con authorities. Un rol es una convención:

```text
rol lógico:       ADMIN
authority real:   ROLE_ADMIN
```

Por eso:

```java
.hasRole("ADMIN")
```

busca internamente `ROLE_ADMIN`. No mezcles `ADMIN`, `ROLE_ADMIN` y nombres
distintos sin una regla clara.

### 3.11 Autorización por URL y por método

La autorización por URL se configura en `authorizeHttpRequests`:

```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

La autorización por método se habilita con `@EnableMethodSecurity` y permite
usar, por ejemplo, `@PreAuthorize("hasRole('ADMIN')")` en un método. La
implementación actual habilita esta capacidad, aunque sus reglas principales
están en la cadena HTTP y en `TaskService`.

### 3.12 Sesiones stateless

Con sesiones tradicionales el servidor guarda una sesión y el cliente envía
una cookie. Con JWT stateless:

1. el cliente hace login;
2. el servidor devuelve un token;
3. el cliente lo envía en cada petición;
4. el servidor valida el token en cada petición;
5. no se guarda una sesión de login.

La política se configura con:

```java
SessionCreationPolicy.STATELESS
```

### 3.13 CSRF

CSRF protege principalmente aplicaciones de navegador que autentican mediante
cookies enviadas automáticamente. Esta API usa un JWT explícito en la cabecera
`Authorization`, por lo que la configuración actual deshabilita CSRF.

Si en el futuro se autentica con cookies, no se debe copiar esta decisión sin
revisar la protección CSRF.

### 3.14 JWT

Un JWT tiene tres partes:

```text
header.payload.signature
```

El payload está codificado, no cifrado. Puede contener `sub`, `uid`, roles,
fecha de emisión y expiración. La firma permite detectar modificaciones.

El token no debe incluir:

- contraseñas;
- hashes;
- secretos;
- información privada innecesaria.

Un JWT válido no significa que tenga acceso a todos los datos. El servicio de
negocio todavía debe comprobar la propiedad de una tarea.

### 3.15 Códigos HTTP

| Código | Significado en seguridad |
|---|---|
| `401` | No hay autenticación válida: falta token, es inválido o expiró |
| `403` | El usuario está autenticado, pero no tiene el rol requerido |
| `404` | En este proyecto, una tarea no disponible para ese usuario no se revela |

El `404` de propiedad no es una decisión automática de Spring Security; lo
implementa el servicio consultando por id de tarea e id de propietario.

---

# Parte II. Implementación de este proyecto

## 4. Mapa de clases

```text
config/SecurityConfig
  configura Spring Security

security/CustomUserDetailsService
  convierte User de JPA en UserDetails

security/JwtService
  crea y valida JWT

security/JwtAuthenticationFilter
  lee JWT en cada petición

security/CurrentUserService
  obtiene el usuario actual desde SecurityContext

service/AuthService
  implementa registro y login

controller/AuthController
  expone POST /api/auth/register y POST /api/auth/login

service/TaskService
  aplica la propiedad de las tareas
```

## 5. `SecurityConfig`

Archivo: `src/main/java/com/app/to_do/config/SecurityConfig.java`.

### Anotaciones de la clase

#### `@Configuration`

Indica que la clase contiene configuración de Spring. Spring la registra como
fuente de beans al iniciar la aplicación.

#### `@EnableMethodSecurity`

Activa la autorización en métodos mediante anotaciones como `@PreAuthorize`.
Aunque actualmente la autorización principal está en `SecurityFilterChain`, se
deja habilitada para poder proteger casos de uso concretos más adelante.

#### `@RequiredArgsConstructor`

Es de Lombok, no de Spring. Genera un constructor para los campos `final`.
Spring usa ese constructor para inyectar `JwtAuthenticationFilter`.

### Campo

```java
private final JwtAuthenticationFilter jwtAuthenticationFilter;
```

Es el filtro propio que se insertará en la cadena.

### Método `securityFilterChain`

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception
```

#### `@Bean`

Le dice a Spring que el objeto devuelto debe administrarse como bean. Así
Spring Security usa esta cadena automáticamente.

#### `csrf(csrf -> csrf.disable())`

Desactiva CSRF porque la API usa `Authorization: Bearer ...` y no una cookie de
sesión. Esta decisión debe cambiar si cambia la forma de autenticación.

#### `sessionManagement`

```java
.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

Indica que Spring no debe crear ni usar sesiones HTTP para recordar al usuario.

#### `authorizeHttpRequests`

Define las reglas de autorización según la ruta.

```java
.requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**")
.permitAll()
```

Permite registro, login y documentación sin token.

```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
```

Restringe las rutas administrativas al authority `ROLE_ADMIN`.

```java
.anyRequest().authenticated()
```

Toda ruta no mencionada antes requiere un usuario autenticado.

#### `addFilterBefore`

```java
.addFilterBefore(
    jwtAuthenticationFilter,
    UsernamePasswordAuthenticationFilter.class)
```

Coloca el filtro JWT antes del filtro estándar de usuario/contraseña. Así el
`SecurityContext` queda preparado antes de evaluar las reglas de autorización.

### Método `authenticationManager`

```java
@Bean
AuthenticationManager authenticationManager(
        AuthenticationConfiguration configuration) throws Exception
```

Expone el `AuthenticationManager` como bean para que `AuthService` pueda usarlo
durante el login. `AuthenticationConfiguration` conoce los componentes de
autenticación registrados por Spring.

### Método `passwordEncoder`

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Expone el encoder BCrypt que se usa al registrar usuarios y al comparar
contraseñas durante el login.

## 6. `CustomUserDetailsService`

Archivo: `security/CustomUserDetailsService.java`.

### Anotaciones

#### `@Service`

Registra la clase como un bean de servicio. Además, por implementar
`UserDetailsService`, Spring Security puede usarla para cargar usuarios.

#### `@RequiredArgsConstructor`

Genera el constructor para `UserRepository`, permitiendo inyección por
constructor.

### Método `loadUserByUsername`

```java
@Override
public UserDetails loadUserByUsername(String email)
```

`@Override` indica que implementa el método exigido por `UserDetailsService`.
Aunque el nombre diga `username`, recibe el email porque así se identifica al
usuario en esta aplicación.

El método:

1. llama a `userRepository.findByEmail(email)`;
2. convierte la entidad `User` al `UserDetails` de Spring;
3. coloca el hash almacenado como password;
4. marca como deshabilitado al usuario cuyo `enabled` es falso;
5. transforma `USER` en `ROLE_USER` y `ADMIN` en `ROLE_ADMIN`;
6. lanza `UsernameNotFoundException` si no existe.

```java
User.withUsername(user.getEmail())
    .password(user.getPasswordHash())
    .disabled(!user.isEnabled())
    .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
    .build()
```

`User` aquí es `org.springframework.security.core.userdetails.User`, no la
entidad JPA `com.app.to_do.model.User`.

## 7. `JwtService`

Archivo: `security/JwtService.java`.

### Anotación `@Service`

Registra la clase para que Spring pueda inyectarla en `AuthService` y en el
filtro JWT.

### Constructor

```java
public JwtService(
    @Value("${app.jwt.secret}") String secret,
    @Value("${app.jwt.expiration-seconds:900}") long expirationSeconds)
```

`@Value` inyecta propiedades de configuración:

- `${app.jwt.secret}` es obligatorio;
- `${app.jwt.expiration-seconds:900}` usa `900` segundos si no se configura.

El constructor rechaza secretos de menos de 32 caracteres y crea una
`SecretKey` HMAC con `Keys.hmacShaKeyFor`.

En `application.properties`:

```properties
app.jwt.secret=${JWT_SECRET:dev-only-secret-change-this-value-1234567890}
app.jwt.expiration-seconds=900
```

En producción se debe definir `JWT_SECRET` fuera del código.

### Método `generateToken`

```java
public String generateToken(
        UserDetails userDetails, Long userId, String role)
```

Construye y firma el token:

- `subject(...)`: guarda el email;
- `claim("uid", ...)`: guarda el id;
- `claim("roles", ...)`: guarda el rol;
- `issuedAt(...)`: fecha de emisión;
- `expiration(...)`: fecha de vencimiento;
- `signWith(...)`: firma con la clave secreta;
- `compact()`: produce la cadena JWT.

El claim `roles` documenta el rol dentro del token, pero el filtro vuelve a
cargar el usuario desde la base y utiliza sus authorities actuales.

### Método `extractUsername`

```java
public String extractUsername(String token)
```

Parsea el token y obtiene su `subject`, que en este proyecto es el email.

### Método `isTokenValid`

Comprueba que:

1. el token pueda validarse con la firma;
2. su subject coincida con el usuario cargado;
3. no haya expirado.

Las excepciones de parsing se propagan al filtro, que responde `401`.

### Método `getExpirationSeconds`

Devuelve la duración configurada para que `AuthService` pueda incluirla en la
respuesta de login.

### Método privado `parseClaims`

```java
Jwts.parser()
    .verifyWith(signingKey)
    .build()
    .parseSignedClaims(token)
    .getPayload()
```

Configura JJWT para verificar la firma, parsea el token firmado y devuelve sus
claims. Es privado para que solo `JwtService` conozca los detalles de JJWT.

## 8. `JwtAuthenticationFilter`

Archivo: `security/JwtAuthenticationFilter.java`.

### Anotaciones

#### `@Component`

Registra el filtro como bean para que pueda inyectarse en `SecurityConfig`.

#### `@RequiredArgsConstructor`

Genera el constructor de `JwtService` y `CustomUserDetailsService`.

#### `@Override`

Indica que `doFilterInternal` implementa el método protegido de
`OncePerRequestFilter`.

### Por qué extiende `OncePerRequestFilter`

`OncePerRequestFilter` es una clase de Spring que facilita ejecutar la lógica
una vez por petición HTTP. Evita tener que implementar manualmente el contrato
completo de un filtro Servlet.

### Método `doFilterInternal`

#### 1. Leer la cabecera

```java
String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
```

Obtiene el header estándar `Authorization`.

#### 2. Permitir peticiones sin Bearer

```java
if (authorization == null || !authorization.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;
}
```

El filtro no inventa una identidad. Deja continuar la petición. Si la ruta era
protegida, la cadena producirá el `401`; si era pública, continuará normalmente.

#### 3. Extraer el token

```java
String token = authorization.substring(7);
```

Elimina `Bearer `, que tiene siete caracteres.

#### 4. Obtener el email

`jwtService.extractUsername(token)` verifica y parsea el token. Si la firma o el
formato son inválidos, JJWT lanza `JwtException`.

#### 5. Evitar reemplazar una autenticación existente

```java
if (SecurityContextHolder.getContext().getAuthentication() == null)
```

Solo se crea una autenticación si todavía no existe una para la petición.

#### 6. Cargar y validar el usuario

El filtro carga `UserDetails` desde la base, comprueba que esté habilitado y
llama a `isTokenValid`.

#### 7. Crear `Authentication`

```java
new UsernamePasswordAuthenticationToken(
    userDetails, null, userDetails.getAuthorities())
```

El primer argumento es el principal, el segundo serían credentials pero aquí no
se conservan, y el tercero contiene los roles.

`setDetails` agrega detalles técnicos de la petición, como información del
request.

#### 8. Guardar el contexto y continuar

```java
SecurityContextHolder.getContext().setAuthentication(authentication);
filterChain.doFilter(request, response);
```

Desde ese punto, los componentes posteriores consideran autenticado al usuario.

#### 9. Manejar errores

El filtro captura `JwtException`, `UsernameNotFoundException` e
`IllegalArgumentException`, limpia el contexto y responde `401`. No registra ni
devuelve el token.

## 9. `CurrentUserService`

Archivo: `security/CurrentUserService.java`.

### Anotaciones

`@Service` registra el servicio. `@RequiredArgsConstructor` genera el constructor
para `UserRepository`.

### Método `getRequiredUser`

Obtiene la `Authentication` del `SecurityContextHolder`, comprueba que exista y
esté autenticada, y vuelve a buscar la entidad JPA `User` por email.

La diferencia es importante:

```text
UserDetails       -> modelo de Spring Security
com.app...User    -> entidad persistente de la aplicación
```

El método devuelve la entidad que necesita `TaskService` para asignar
`usuario_id` y filtrar consultas.

### Método `isAdmin`

Comprueba si el rol persistido es exactamente `ADMIN`. Está centralizado aquí
para no repetir comparaciones de strings en los servicios.

## 10. `AuthService`

Archivo: `service/AuthService.java`.

### Anotaciones

#### `@Service`

Registra la lógica de registro y login como bean de negocio.

#### `@RequiredArgsConstructor`

Inyecta repository, encoder, authentication manager, servicio de detalles y
servicio JWT.

#### `@Transactional`

En `register`, abre una transacción para guardar el usuario. Si ocurre un error,
la operación puede revertirse de forma consistente.

### Método `register`

1. normaliza el email;
2. verifica duplicados;
3. crea una entidad `User`;
4. convierte la contraseña con BCrypt;
5. asigna el rol `USER` en servidor;
6. habilita el usuario;
7. guarda la entidad;
8. devuelve `UserResponse`, nunca el hash.

El cliente no puede enviar un rol porque `RegisterRequest` no tiene ese campo.

### Método `login`

1. normaliza el email;
2. crea `UsernamePasswordAuthenticationToken` con email y contraseña;
3. llama a `AuthenticationManager`;
4. vuelve a cargar la entidad para obtener id y rol;
5. genera el JWT;
6. devuelve token, tipo y duración.

### ¿En qué momento se valida el email y la contraseña?

No es necesario buscar primero manualmente el usuario y después autenticarlo.
La llamada siguiente inicia todo el proceso de autenticación:

```java
authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(email, request.password()));
```

Internamente ocurre este flujo:

```text
AuthenticationManager
        |
        +-- llama a CustomUserDetailsService.loadUserByUsername(email)
        |       |
        |       +-- UserRepository.findByEmail(email)
        |       +-- obtiene el passwordHash almacenado
        |       +-- convierte User en UserDetails
        |
        +-- usa PasswordEncoder para comparar:
                contraseña recibida
                         contra
                passwordHash almacenado
```

La comparación real la realiza BCrypt mediante el `PasswordEncoder`, no el
controller ni `AuthService` directamente. Si el email no existe, la contraseña
no coincide o el usuario está deshabilitado, `authenticate(...)` lanza una
excepción y la ejecución se detiene. En esos casos no se genera ningún JWT.

Por eso el código no debe hacer una comparación manual como esta:

```java
// Incorrecto: el valor almacenado es un hash BCrypt, no la contraseña original.
if (user.getPasswordHash().equals(request.password())) {
    // ...
}
```

Tampoco conviene buscar primero el usuario para después autenticarlo. Además de
repetir una consulta, se podría terminar revelando si un email existe mediante
mensajes diferentes para “usuario inexistente” y “contraseña incorrecta”. Lo
correcto es que ambos casos se presenten como credenciales inválidas.

### ¿Por qué se busca otra vez el usuario después de autenticar?

Después de esta línea:

```java
authenticationManager.authenticate(...);
```

la aplicación ya sabe que las credenciales son válidas. Sin embargo, en la
implementación actual se ejecuta después:

```java
User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new IllegalStateException(
                "El usuario autenticado ya no existe"));

UserDetails userDetails = userDetailsService.loadUserByUsername(email);
```

La segunda búsqueda existe porque el `AuthenticationManager` valida las
credenciales, pero `AuthService` necesita datos de la entidad JPA `User` para
generar el token:

```java
user.getId()
user.getRole()
```

También se vuelve a cargar `UserDetails` porque `JwtService.generateToken`
recibe ese tipo de Spring Security. En otras palabras, el flujo actual es:

```text
authenticate(...)
    +-- busca User internamente
    +-- obtiene passwordHash
    +-- compara la contraseña
    +-- devuelve Authentication autenticada

userRepository.findByEmail(email)
    +-- vuelve a obtener User para id y role

userDetailsService.loadUserByUsername(email)
    +-- vuelve a obtener UserDetails

generateToken(...)
```

La implementación es correcta y explícita para aprender el flujo, pero realiza
consultas repetidas. Una mejora futura sería crear una implementación propia de
`UserDetails` que incluya también `id` y `role`. Entonces se podrían obtener
esos datos desde el principal de la `Authentication` devuelta por:

```java
Authentication authentication =
        authenticationManager.authenticate(...);

CustomUserDetails principal =
        (CustomUserDetails) authentication.getPrincipal();
```

Y el token se generaría usando `principal.getId()` y `principal.getRole()`, sin
volver a cargar el usuario dos veces. Esa optimización no cambia el concepto
fundamental: `AuthenticationManager` sigue siendo el componente responsable de
validar las credenciales.

El servicio no compara passwords manualmente. Esa responsabilidad pertenece al
`AuthenticationManager` y al `PasswordEncoder`.

### Métodos privados

`normalizeEmail` elimina espacios y convierte a minúsculas usando `Locale.ROOT`.
`toResponse` transforma la entidad en un DTO seguro sin contraseña.

## 11. `AuthController`

Archivo: `controller/AuthController.java`.

### Anotaciones de Spring

#### `@RestController`

Combina `@Controller` y `@ResponseBody`. Los valores de los métodos se
serializan como JSON.

#### `@RequestMapping("/api/auth")`

Define el prefijo común de los endpoints.

#### `@PostMapping`

Mapea una petición HTTP POST a un método.

#### `@RequestBody`

Convierte el JSON recibido en un DTO Java.

#### `@Valid`

Activa las validaciones declaradas en el DTO.

#### `@RequiredArgsConstructor`

Inyecta `AuthService` mediante constructor.

### Anotaciones de documentación

#### `@Tag`

Agrupa endpoints bajo “Authentication” en Swagger UI.

#### `@Operation`

Describe cada operación en OpenAPI.

#### `@SecurityRequirements`

Indica que estos endpoints no requieren un esquema de seguridad en Swagger.
Esto documenta la API; la autorización real continúa definida en
`SecurityFilterChain`.

### Endpoint `register`

`POST /api/auth/register` recibe `RegisterRequest`, llama a `AuthService` y
devuelve `201 Created` con `UserResponse`.

### Endpoint `login`

`POST /api/auth/login` recibe `LoginRequest`, autentica y devuelve
`LoginResponse` con `200 OK`.

## 12. DTOs de autenticación

### `RegisterRequest`

Contiene email, password y fullName. No contiene role.

- `@Email`: valida formato básico de email.
- `@NotBlank`: rechaza null, vacío o solo espacios.
- `@Size`: limita longitud y exige mínimo de 8 caracteres para password.

Estas anotaciones son de Jakarta Validation y se ejecutan porque el controller
usa `@Valid`.

### `LoginRequest`

Contiene email y password. Usa `@Email` y `@NotBlank`.

### `LoginResponse`

Es un `record` inmutable con `token`, `tokenType` y `expiresIn`.

### `UserResponse`

Expone id, email, nombre y rol. Deliberadamente no expone `passwordHash`.

## 13. `TaskService` y `TaskRepository`

La seguridad no termina en validar el JWT. También hay que limitar los datos.

### `TaskService`

`@Service` registra el servicio, `@Transactional` mantiene las operaciones de
persistencia dentro de una transacción y `@RequiredArgsConstructor` inyecta
dependencias.

#### Crear

`create` obtiene el usuario actual mediante `CurrentUserService`, asigna
`task.setUsuario_id(currentUser)` y guarda la tarea. El cliente no decide el
propietario.

#### Listar

`findAll` carga al usuario actual:

- ADMIN: `taskRepository.findAll()`;
- USER: `findByUsuarioId(currentUser.getId(), ...)`.

#### Consultar, actualizar y eliminar

Usan `findTaskForCurrentUser`. Un administrador busca por id global; un usuario
normal busca por id de tarea **e id de propietario**. Si no existe coincidencia,
se lanza el error de tarea no encontrada y la API responde `404`.

### `TaskRepository`

Extiende `JpaRepository<Task, Long>`, por lo que hereda operaciones CRUD.

Las consultas explícitas usan `@Query` y `@Param`:

```java
@Query("select t from Task t where t.usuario_id.id = :usuarioId")
Page<Task> findByUsuarioId(@Param("usuarioId") Long usuarioId, Pageable pageable);
```

`@Query` declara JPQL y `@Param` conecta el parámetro Java con el nombre usado
en la consulta. La consulta de una tarea concreta también filtra por
`t.usuario_id.id`, que es la parte que evita que un usuario vea tareas ajenas.

`@EntityGraph(attributePaths = {"usuario_id"})` solicita cargar la relación
necesaria junto con la tarea y ayuda a evitar consultas N+1.

## 14. Cómo probar el flujo completo

### Registrar

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "nuevo@example.com",
  "password": "una-clave-segura",
  "fullName": "Usuario Nuevo"
}
```

### Iniciar sesión

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "nuevo@example.com",
  "password": "una-clave-segura"
}
```

### Usar el token

```http
GET /api/tasks
Authorization: Bearer eyJ...
```

En Swagger UI:

1. ejecuta login;
2. copia el valor de `token`;
3. pulsa **Authorize**;
4. pega el token;
5. prueba `/api/tasks`.

## 15. Lista de comprobación

- [ ] El registro nunca acepta el rol desde el cliente.
- [ ] Las contraseñas se guardan con BCrypt.
- [ ] El login usa `AuthenticationManager`.
- [ ] El JWT tiene firma y expiración.
- [ ] El secreto proviene de `JWT_SECRET`.
- [ ] El filtro llena `SecurityContext`.
- [ ] Las rutas de autenticación y Swagger son públicas.
- [ ] Las tareas requieren autenticación.
- [ ] Las tareas nuevas pertenecen al usuario del token.
- [ ] USER solo consulta sus propias tareas.
- [ ] ADMIN puede consultar todas.
- [ ] Las tareas ajenas responden `404`.
- [ ] Nunca se devuelve `passwordHash`.
- [ ] El hash temporal de los datos de prueba fue reemplazado por BCrypt.

## 16. Advertencia sobre los datos de prueba

La migración inicial del proyecto contiene valores de contraseña temporales.
`hash_temporal` no es un hash BCrypt válido. Antes de probar login con esos
usuarios, reemplaza esos valores por hashes generados con
`PasswordEncoder`, crea una nueva migración Flyway o registra usuarios nuevos.

No edites una migración ya aplicada en una base compartida: Flyway detectará un
cambio de checksum. Usa una nueva migración o repara/recrea la base local de
forma consciente.
