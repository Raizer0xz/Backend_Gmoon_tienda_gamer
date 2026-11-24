package com.example.MS_usuarios.config;

import com.example.MS_usuarios.model.Usuario;
import com.example.MS_usuarios.service.ServiceUsuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "admin.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private final ServiceUsuario serviceUsuario;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String nombre = required("ADMIN_BOOTSTRAP_NAME");
        String email = required("ADMIN_BOOTSTRAP_EMAIL");
        Usuario admin = serviceUsuario.bootstrapAdmin(nombre, email);
        log.info("Perfil ADMIN creado con id {} y correo {}", admin.getId(), admin.getEmail());
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " es obligatorio al activar el bootstrap ADMIN.");
        }
        return value;
    }
}
