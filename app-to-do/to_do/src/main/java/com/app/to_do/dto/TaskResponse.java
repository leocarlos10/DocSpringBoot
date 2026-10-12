package com.app.to_do.dto;

/**
 * DTO para la respuesta de una tarea
 * TaskResponse
 * @param id
 * @param title
 * @param description
 * @param completed
  */
public record TaskResponse(
        Long id,
        String title,
        String description,
        Boolean completed
) {}
