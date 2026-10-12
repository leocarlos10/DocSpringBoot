package com.app.to_do.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.app.to_do.dto.CreateTaskRequest;
import com.app.to_do.dto.TaskResponse;
import com.app.to_do.model.Task;
import com.app.to_do.model.User;
import com.app.to_do.repository.TaskRepository;
import com.app.to_do.security.CurrentUserService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Transactional 
@Service
@RequiredArgsConstructor 
public class TaskService {
    private final TaskRepository taskRepository;
    private final CurrentUserService currentUserService;


    public TaskResponse create(CreateTaskRequest request) {
        User currentUser = currentUserService.getRequiredUser();
        Task task = new Task(request.title(), request.description());
        task.setUsuario_id(currentUser);
        task = taskRepository.save(task);
        return  mapToResponse(task);

    }

    /**
     * Obtiene todas las tareas almacenadas en la base de datos y las convierte a DTO.
     * 
     * Uso de Stream:
     * - taskRepository.findAll() devuelve List<Task> (entidades JPA).
     * - .stream() convierte la lista en un Stream<Task> para procesar elementos secuencialmente.
     * - .map(this::mapToResponse) transforma cada Task en TaskResponse usando method reference.
     * - .collect(Collectors.toList()) recoge los resultados en una nueva List<TaskResponse>.
     * 
     * @return lista de tareas en formato DTO
     */
    
    public List<TaskResponse> findAll() {
        User currentUser = currentUserService.getRequiredUser();
        List<Task> tasks = currentUserService.isAdmin(currentUser)
                ? taskRepository.findAll()
                : taskRepository.findByUsuarioId(currentUser.getId(),
                        org.springframework.data.domain.Pageable.unpaged()).getContent();
        return tasks.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }


    public TaskResponse findById(Long id) {
        User currentUser = currentUserService.getRequiredUser();
        Task task = (currentUserService.isAdmin(currentUser)
                ? taskRepository.findById(id)
                : taskRepository.findByIdAndUsuarioId(id, currentUser.getId()))
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada con ID: " + id));
        return mapToResponse(task);
    }

    public TaskResponse update(Long id, CreateTaskRequest request) {
        Task task = findTaskForCurrentUser(id);
        
        task.setTitle(request.title());
        task.setDescription(request.description());
        
        Task updatedTask = taskRepository.save(task);
        return mapToResponse(updatedTask);
    }

    public void delete(Long id) {
        taskRepository.delete(findTaskForCurrentUser(id));
    }

    private Task findTaskForCurrentUser(Long id) {
        User currentUser = currentUserService.getRequiredUser();
        return (currentUserService.isAdmin(currentUser)
                ? taskRepository.findById(id)
                : taskRepository.findByIdAndUsuarioId(id, currentUser.getId()))
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada con ID: " + id));
    }


    // Helper para mapear Entidad a DTO
    private TaskResponse mapToResponse(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(), task.isCompleted());
    }
}
