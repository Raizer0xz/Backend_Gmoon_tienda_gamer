package com.example.MS_gateway_security.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioPerfilResponse(Long id, String email, String rol) {
}
