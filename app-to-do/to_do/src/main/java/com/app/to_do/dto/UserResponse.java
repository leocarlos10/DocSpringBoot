package com.app.to_do.dto;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String role) {
}
