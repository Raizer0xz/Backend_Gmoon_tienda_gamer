package com.example.MS_pedidos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CrearDetallePedidoRequest(
        @NotBlank @Size(max = 255) String id,
        @NotBlank @Size(max = 150) String nombre,
        @NotNull @Min(1) Integer cantidad,
        @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal precioUnitario
) {
}
