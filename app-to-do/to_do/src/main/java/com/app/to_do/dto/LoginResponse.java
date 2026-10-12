package com.app.to_do.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn) {
}
