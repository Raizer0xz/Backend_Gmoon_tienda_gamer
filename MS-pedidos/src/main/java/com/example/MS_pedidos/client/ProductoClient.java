package com.example.MS_pedidos.client;

import com.example.MS_pedidos.dto.ProductoCatalogoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "MS-PRODUCTOS", path = "/api/v2/productos")
public interface ProductoClient {

    @GetMapping("/{id}")
    ProductoCatalogoResponse obtenerPorId(@PathVariable("id") Long id);
}
