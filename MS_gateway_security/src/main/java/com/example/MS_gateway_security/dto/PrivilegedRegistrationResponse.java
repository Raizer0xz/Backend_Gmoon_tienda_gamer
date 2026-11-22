package com.example.MS_gateway_security.dto;

public record PrivilegedRegistrationResponse(Long usuarioId, String email, String rol, String mensaje) {
}
