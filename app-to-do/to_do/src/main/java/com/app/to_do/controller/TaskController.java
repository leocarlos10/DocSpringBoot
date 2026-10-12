package com.app.to_do.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.app.to_do.dto.CreateTaskRequest;
import com.app.to_do.dto.TaskResponse;
import com.app.to_do.service.TaskService;

import java.util.List;

/**
 * Controlador REST para la gestión de tareas.
 * 
 * Define los endpoints bajo la ruta base {@code /api/tasks}.
 * 
 * Anotaciones importantes:
 * - {@link RestController}: Indica que es un controlador REST. Combina {@code @Controller} y 
 *   {@code @ResponseBody}, por lo que los valores devueltos se serializan a JSON automáticamente.
 * - {@link RequestMapping}: Define el prefijo común para todos los endpoints de este controlador.
 * - {@link Tag}: Anotación de SpringDoc OpenAPI para agrupar los endpoints en la documentación
 *   Swagger UI bajo el nombre "Tasks".
 */
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Gestión de tareas")
@RequiredArgsConstructor 
public class TaskController {


    private final TaskService service;


    /**
     * Crea una nueva tarea.
     * 
     * Flujo:
     * 1. Recibe un {@link CreateTaskRequest} en el cuerpo de la petición.
     * 2. {@link Valid} activa la validación de los campos definidos en el DTO.
     * 3. Delega la creación a {@link TaskService#create(CreateTaskRequest)}.
     * 4. Retorna {@link ResponseEntity} con el DTO resultante y código HTTP {@code 201 Created}.
     * 
     * @param request datos de la tarea a crear
     * @return respuesta con la tarea creada
     */
    @Operation(summary = "Crear una nueva tarea", description = "Registra una nueva tarea con título y descripción")
    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request)); 
    }

    /**
     * Lista todas las tareas existentes.
     * 
     * Flujo:
     * 1. Delega la consulta a {@link TaskService#findAll()}.
     * 2. Retorna la lista de tareas mapeadas a {@link TaskResponse}.
     * 
     * @return {@link ResponseEntity} con la lista de tareas y código HTTP {@code 200 OK}
     */
    @Operation(summary = "Listar todas las tareas", description = "Obtiene todas las tareas registradas")
    @GetMapping
    public ResponseEntity<List<TaskResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }


    @Operation(summary = "Ver el detalle de una tarea")
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @Operation(summary = "Actualizar una tarea")
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @Operation(summary = "Eliminar una tarea")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
