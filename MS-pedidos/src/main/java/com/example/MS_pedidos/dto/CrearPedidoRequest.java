package com.example.MS_pedidos.dto;

import com.example.MS_pedidos.model.TipoComprobante;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CrearPedidoRequest(
        @NotBlank @Size(max = 150) String clienteNombre,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull TipoComprobante tipoComprobante,
        @NotEmpty @Valid List<CrearDetallePedidoRequest> items
) {
}
