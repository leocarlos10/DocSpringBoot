package com.app.to_do.exception;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * Manejador global de errores de la API.
 *
 * <p>Función: intercepta las excepciones lanzadas desde cualquier controlador (o los servicios
 * que estos invocan) <strong>antes</strong> de que lleguen al cliente, y las traduce en
 * respuestas HTTP con códigos correctos y un cuerpo útil, en lugar de un error 500 genérico
 * con stacktrace.</p>
 *
 * <h2>Cómo funciona el flujo</h2>
 * <ol>
 *   <li>Cuando un endpoint lanza una excepción, Spring la intercepta antes de devolverla al cliente.</li>
 *   <li>Spring busca en esta clase un método anotado con {@code @ExceptionHandler(<TipoExcepción>)}
 *       que coincida con la excepción lanzada.</li>
 *   <li>Ese método construye la respuesta de error, que se envía al cliente en formato
 *       {@code application/problem+json} (RFC 7807).</li>
 * </ol>
 *
 * <h2>Handlers registrados</h2>
 * <table border="1">
 *   <caption>Respuestas según la excepción capturada</caption>
 *   <tr><th>Handler</th><th>Captura</th><th>HTTP</th></tr>
 *   <tr>
 *     <td>{@link #handleRuntimeException(RuntimeException)}</td>
 *     <td>Cualquier {@code RuntimeException}, ej. "Tarea no encontrada con ID: 5" lanzada por
 *         {@code TaskService}</td>
 *     <td>{@code 404 Not Found} + mensaje en {@code detail}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #handleValidationExceptions(MethodArgumentNotValidException)}</td>
 *     <td>{@code MethodArgumentNotValidException}, que lanza Spring cuando {@code @Valid} falla
 *         en un {@code @RequestBody}</td>
 *     <td>{@code 400 Bad Request} + lista {@code errors} con campo/mensaje
 *         (ej. "title: el titulo es requerido")</td>
 *   </tr>
 * </table>
 *
 * <h2>Detalles clave</h2>
 * <ul>
 *   <li>{@code @RestControllerAdvice} aplica a <strong>todos</strong> los controladores:
 *       no hay que repetir {@code @ExceptionHandler} en cada endpoint.</li>
 *   <li>{@link org.springframework.http.ProblemDetail} es el formato estándar RFC 7807:
 *       devuelve {@code status}, {@code title}, {@code detail}, etc.</li>
 *   <li>{@code RuntimeException} es una superclase muy amplia: cualquier excepción no controlada
 *       (incluidos errores de BD) caería aquí y devolvería 404. Lo ideal, como evolución, es usar
 *       excepciones personalizadas (ej. {@code TaskNotFoundException}) con su propio handler.</li>
 * </ul>
 *
 * <p>Sin esta clase, cada error devolvería un 500 genérico; con ella, los clientes reciben
 * códigos HTTP correctos (404, 400) y mensajes útiles en un formato consistente.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
    
    // Captura nuestros errores cuando no se encuentra una tarea
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntimeException(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Captura los errores de Bean Validation (ej. cuando el título viene vacío)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Errores de validación en la petición");
        
        // Extrae qué campos fallaron exactamente
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        
        problemDetail.setProperty("errors", errors);
        return problemDetail;
    }
    
}
