package com.example.MS_pedidos.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum TipoComprobante {
    BOLETA,
    FACTURA;

    @JsonCreator
    public static TipoComprobante from(String value) {
        return TipoComprobante.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
