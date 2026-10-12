# Comprendiendo FetchType.LAZY, el Problema N+1 y la Paginación

Este documento sirve como referencia para entender conceptos críticos de rendimiento en Spring Data JPA: las estrategias de carga (Fetch), cómo evitar saturar la base de datos (N+1) y cómo devolver datos eficientemente en partes (Paginación).

---

## PARTE 1: Fetch LAZY y el Problema N+1

Cuando definimos relaciones entre entidades (como un `User` que tiene muchas `Task`, o una `Task` que pertenece a un `User`), necesitamos decirle a la base de datos **cuándo** debe traer los datos relacionados. A esto se le conoce como **Fetch Strategy** (Estrategia de carga). Existen dos tipos: **EAGER** (Ansioso) y **LAZY** (Perezoso).

### 1. El modo EAGER (Ansioso)
Es el comportamiento por defecto cuando usas anotaciones como `@ManyToOne` sin especificar nada más.

*   **¿Cómo funciona?** Si buscas la Tarea ID 1, Hibernate va a la base de datos por la tarea, y de inmediato hace un JOIN o una consulta extra para traer todos los datos del usuario (su email, nombre, etc.), *por si acaso* los necesitas.
*   **El problema:** Si tu endpoint solo quería listar títulos de tareas, gastaste memoria y tiempo trayendo datos del usuario que nunca usaste. 

### 2. El modo LAZY (Perezoso) - 🟢 Recomendado
Para activarlo, lo indicamos explícitamente:
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "owner_id")
private User owner;
```
*   **¿Cómo funciona?** Al buscar la Tarea ID 1, trae solo los datos de la tarea. Del usuario (dueño), solo pone un "fantasma" en la memoria. **Solo va a la base de datos por el usuario si lo pides explícitamente en el código** (ej. `task.getOwner().getFullName()`).
*   **La ventaja:** Ahorras memoria. Las consultas son ligeras.

### 3. El peligroso "Problema N+1"
El modo LAZY es genial, pero si no tienes cuidado, genera este problema:
```java
List<Task> tareas = repository.findAll(); // 1 consulta para traer (ej) 10 tareas

for (Task tarea : tareas) {
    // Como es LAZY, Hibernate hace una consulta extra a la BD por CADA iteración
    System.out.println(tarea.getOwner().getEmail()); 
}
```
Hiciste **1** consulta original, más **10** (N) consultas individuales. Total = **11 (N+1) consultas**. Si tienes mil tareas, harás mil y una consultas, destruyendo el rendimiento.

### 4. La Solución: `@EntityGraph`
Le decimos a Hibernate: *"Sé que es LAZY, pero **solo para esta consulta**, tráeme al dueño de una vez en un JOIN limpio"*.
```java
public interface TaskRepository extends JpaRepository<Task, Long> {
    @EntityGraph(attributePaths = {"owner"})
    Page<Task> findAll(Pageable pageable);
}
```

---

## PARTE 2: Paginación en Spring Data JPA

Imagina que tienes 10,000 tareas en tu base de datos. Si haces un `findAll()` normal, Spring intentará cargar las 10,000 tareas en la memoria RAM y enviarlas en un JSON gigantesco. El servidor colapsará. 

La **paginación** evita esto pidiendo a la base de datos "porciones" de información (ej. "Dame solo 10 tareas, empezando desde la página 0").

Spring Data JPA maneja esto con dos interfaces clave:

### 1. La Petición: `Pageable`
Es el objeto que indica qué porción quieres:
*   **page:** El número de página (empieza desde 0).
*   **size:** Cuántos elementos por página.
*   **sort:** (Opcional) El orden (ej. por fecha).

### 2. La Respuesta: `Page<T>`
En lugar de devolver un `List<T>`, Spring te devuelve un `Page<T>`. Este objeto contiene los datos solicitados y **metadatos** útiles para el frontend:
*   `totalElements`: Cuántos registros hay en total.
*   `totalPages`: Cuántas páginas salen en total.
*   `isFirst()` / `isLast()`: ¿Estás en la primera/última página?

### 3. Implementación en Código

**En el Controller:**
Usamos `@RequestParam` para recibir la página y el tamaño desde la URL (con valores por defecto por si el usuario no los envía).
```java
@GetMapping
public ResponseEntity<Page<TaskResponse>> findAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
) {
    return ResponseEntity.ok(service.findAll(page, size));
}
```
Esto permite usar URLs como: `GET /api/tasks?page=0&size=5`

**En el Service:**
Transformamos los parámetros en un `Pageable`, hacemos la consulta (usando el método con `@EntityGraph` para evitar el N+1) y luego mapeamos el resultado.
```java
public Page<TaskResponse> findAll(int page, int size) {
    // 1. Crear el objeto Pageable
    Pageable pageable = PageRequest.of(page, size);
    
    // 2. Buscar en BD (aplica LIMIT y OFFSET en SQL automáticamente)
    Page<Task> taskPage = repository.findAll(pageable);
    
    // 3. Convertir Page<Task> a Page<TaskResponse> usando .map()
    return taskPage.map(this::mapToResponse);
}
```