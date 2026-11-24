package com.example.MS_usuarios.config;

import com.example.MS_usuarios.model.Usuario;
import com.example.MS_usuarios.service.ServiceUsuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private ServiceUsuario serviceUsuario;

    @Mock
    private Environment environment;

    @Test
    void run_creaPerfilConLosValoresDelEntorno() {
        when(environment.getProperty("ADMIN_BOOTSTRAP_NAME")).thenReturn("Admin Gmoon");
        when(environment.getProperty("ADMIN_BOOTSTRAP_EMAIL")).thenReturn("admin@mail.com");
        when(serviceUsuario.bootstrapAdmin("Admin Gmoon", "admin@mail.com"))
                .thenReturn(Usuario.builder().id(2L).email("admin@mail.com").rol("ADMIN").build());

        new AdminBootstrapRunner(serviceUsuario, environment)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(serviceUsuario).bootstrapAdmin("Admin Gmoon", "admin@mail.com");
    }

    @Test
    void run_fallaSiFaltaElNombreDelPerfil() {
        when(environment.getProperty("ADMIN_BOOTSTRAP_NAME")).thenReturn(" ");

        assertThatThrownBy(() -> new AdminBootstrapRunner(serviceUsuario, environment)
                .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_NAME");
    }
}
