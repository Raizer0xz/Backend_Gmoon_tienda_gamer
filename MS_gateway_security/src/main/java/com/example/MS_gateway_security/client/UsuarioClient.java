package com.example.MS_gateway_security.client;

import com.example.MS_gateway_security.dto.UsuarioPerfilResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "MS-USUARIOS", path = "/api/v1/usuarios")
public interface UsuarioClient {

    @GetMapping("/{id}")
    UsuarioPerfilResponse buscarPorId(@PathVariable("id") Long id);
}
