package com.example.MS_gateway_security.dto;

public record AuthenticationResult(AuthResponse response, String refreshToken) {
}
