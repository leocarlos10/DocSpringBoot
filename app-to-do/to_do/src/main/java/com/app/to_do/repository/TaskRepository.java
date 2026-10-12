package com.app.to_do.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.app.to_do.model.Task;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    
    // Lista tareas de un usuario específico (para el rol USER)
    @EntityGraph(attributePaths = {"usuario_id"}) // Previene el N+1 cargando al usuario en la misma query
    @Query("select t from Task t where t.usuario_id.id = :usuarioId")
    Page<Task> findByUsuarioId(@Param("usuarioId") Long usuarioId, Pageable pageable);

    @EntityGraph(attributePaths = {"usuario_id"})
    @Query("select t from Task t where t.id = :taskId and t.usuario_id.id = :usuarioId")
    Optional<Task> findByIdAndUsuarioId(
            @Param("taskId") Long taskId,
            @Param("usuarioId") Long usuarioId);

    // Sobrescribimos el findAll para aplicarle paginación y prevenir N+1 (para el rol ADMIN)
    @EntityGraph(attributePaths = {"usuario_id"})
    Page<Task> findAll(Pageable pageable);
    
}
