package com.app.to_do.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Task
 * Modelo para registrar las tareas
 *
 * <h2>Anotaciones de relación de JPA</h2>
 * <p>La relación de esta entidad está definida en el campo {@code owner}:</p>
 *
 * <h3>{@code @ManyToOne(fetch = FetchType.LAZY)}</h3>
 * <ul>
 *   <li>Define que <strong>muchas</strong> tareas pertenecen a <strong>un</strong> usuario.
 *       Es el <strong>lado propietario</strong> de la relación, por tener el {@code @JoinColumn}.</li>
 *   <li>{@code fetch = FetchType.LAZY}: el {@code User} <strong>no se carga al leer la tarea</strong>,
 *       solo se consulta en la BD cuando realmente accedes a {@code owner}. Es lo recomendado.</li>
 *   <li>Con {@code open-in-view=false} configurado en {@code application.properties}, debes resolver
 *       la relación dentro de la transacción o fallará con {@code LazyInitializationException}.</li>
 *   <li>El por defecto para {@code @ManyToOne} es {@code EAGER} (cargaría al usuario en cada consulta,
 *       costoso); aquí se fuerza {@code LAZY} para evitarlo.</li>
 * </ul>
 *
 * <h3>{@code @JoinColumn(name = "usuario_id", nullable = false)}</h3>
 * <ul>
 *   <li>Mapea la <strong>columna de la llave foránea</strong> en la tabla {@code tasks}.</li>
 *   <li>{@code name = "usuario_id"}: nombre físico de la columna en la BD (BIGINT con FK hacia
 *       {@code users.id}). Sin esto, JPA usaría el nombre por defecto {@code owner_id}.</li>
 *   <li>{@code nullable = false}: cada tarea <strong>debe</strong> tener un {@code owner};
 *       un INSERT sin usuario lanzaría un error de constraint. Si fuera opcional, se omitiría
 *       (o {@code nullable = true}).</li>
 * </ul>
 *
 * <h3>Reflejo en la base de datos</h3>
 * <p>Con {@code ddl-auto=validate}, Flyway debe crear el esquema:</p>
 * <pre>
 * tasks
 * ├── id          BIGINT PK AUTO_INCREMENT
 * ├── title       VARCHAR(120) NOT NULL
 * ├── ...
 * └── usuario_id  BIGINT NOT NULL  -&gt; FK REFERENCES users(id)
 * </pre>
 *
 * <h3>Relación inversa (opcional)</h3>
 * <p>Del lado de {@code User} sería {@code @OneToMany(mappedBy = "owner") List<Task> tasks;},
 * donde {@code mappedBy} apunta al campo de la entidad propietaria para <strong>no</strong>
 * duplicar la FK. Hoy la relación es solo de {@code Task} hacia {@code User}, unidireccional.</p>
 */

@Entity
@Table(name = "tasks")
@Getter 
@Setter 
@NoArgsConstructor // Lombok genera el constructor vacío que exige JPA 
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean completed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario_id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

   
    public Task(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
