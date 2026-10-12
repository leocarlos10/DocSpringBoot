package com.app.to_do.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 *  ¿Qué son los records en Java?
    Un record es un tipo especial de clase en Java diseñado específicamente para ser un portador de datos inmutable 
    (que no cambia una vez creado).
    Cuando defines un record, le dices a Java qué datos quieres que contenga, 
    y el lenguaje genera automáticamente todo el código repetitivo (boilerplate) que normalmente tendrías que escribir a mano.
 * CreateTaskRequest
    DTO para la creacion de una tarea, haciendo uso de los records
 * 
  */

public record CreateTaskRequest(
        @NotBlank(message = "el titulo es requerido")
        @Size(max = 120, message = "El titiulo no puede exceder los 120 caracteres")
        String title,
        @Size (max = 1000, message = "La descripcion no puede exceder los 1000 caracteres")
        String description
) {

}
