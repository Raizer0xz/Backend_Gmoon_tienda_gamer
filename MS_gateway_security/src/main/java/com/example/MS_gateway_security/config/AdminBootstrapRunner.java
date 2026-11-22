package com.example.MS_gateway_security.config;

import com.example.MS_gateway_security.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "admin.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AuthService authService;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        Long usuarioId = Long.valueOf(required("ADMIN_BOOTSTRAP_USER_ID"));
        String email = required("ADMIN_BOOTSTRAP_EMAIL");
        String password = required("ADMIN_BOOTSTRAP_PASSWORD");
        authService.bootstrapAdmin(usuarioId, email, password);
    }

    private String required(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " es obligatorio al activar el bootstrap ADMIN.");
        }
        return value;
    }
}
