package com.example.MS_pedidos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoCatalogoResponse(
        Long id,
        String nombre,
        BigDecimal precio,
        Integer stock,
        Integer descuento,
        Boolean activo,
        CategoriaResponse categoria
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoriaResponse(Boolean activo) {
    }
}
