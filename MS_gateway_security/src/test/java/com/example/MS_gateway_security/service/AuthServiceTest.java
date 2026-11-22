package com.example.MS_gateway_security.service;

import com.example.MS_gateway_security.client.UsuarioClient;
import com.example.MS_gateway_security.dto.AuthenticationResult;
import com.example.MS_gateway_security.dto.AuthResponse;
import com.example.MS_gateway_security.dto.LoginRequest;
import com.example.MS_gateway_security.dto.PrivilegedRegisterRequest;
import com.example.MS_gateway_security.dto.PrivilegedRegistrationResponse;
import com.example.MS_gateway_security.dto.RegisterRequest;
import com.example.MS_gateway_security.dto.UsuarioPerfilResponse;
import com.example.MS_gateway_security.dto.ValidarTokenResponse;
import com.example.MS_gateway_security.model.Credencial;
import com.example.MS_gateway_security.model.RefreshToken;
import com.example.MS_gateway_security.repository.CredencialRepository;
import com.example.MS_gateway_security.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CredencialRepository credencialRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioClient usuarioClient;

    @InjectMocks
    private AuthService authService;

    private Credencial credencial;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        credencial = Credencial.builder()
                .id(1L)
                .usuarioId(10L)
                .email("juan@mail.com")
                .passwordHash("$2a$10$hasheado")
                .rol("CLIENTE")
                .activo(true)
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setUsuarioId(10L);
        registerRequest.setEmail("juan@mail.com");
        registerRequest.setPassword("segura123");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("juan@mail.com");
        loginRequest.setPassword("segura123");
    }

    // -------------------------------------------------------------------------
    // registrar()
    // -------------------------------------------------------------------------
    @Test
    void registrar_deberiaCrearCredencialesYRetornarJwt() {
        when(credencialRepository.existsByEmail("juan@mail.com")).thenReturn(false);
        when(credencialRepository.existsByUsuarioId(10L)).thenReturn(false);
        when(usuarioClient.buscarPorId(10L))
                .thenReturn(new UsuarioPerfilResponse(10L, "juan@mail.com", "CLIENTE"));
        when(passwordEncoder.encode("segura123")).thenReturn("$2a$10$hasheado");
        when(credencialRepository.save(any(Credencial.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generarToken("juan@mail.com", 10L, "CLIENTE"))
                .thenReturn("eyJ.test.token");

        AuthenticationResult session = authService.registrar(registerRequest);
        AuthResponse response = session.response();

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("eyJ.test.token");
        assertThat(response.getEmail()).isEqualTo("juan@mail.com");
        assertThat(response.getRol()).isEqualTo("CLIENTE");
        assertThat(response.getUsuarioId()).isEqualTo(10L);
        assertThat(response.getMensaje()).isEqualTo("Usuario registrado exitosamente");

        verify(credencialRepository).existsByEmail("juan@mail.com");
        verify(credencialRepository).existsByUsuarioId(10L);
        verify(usuarioClient).buscarPorId(10L);
        verify(credencialRepository).save(any(Credencial.class));
        verify(refreshTokenRepository).save(argThat(token ->
                token.getUsuarioId().equals(10L)
                        && token.getTokenHash().length() == 64
                        && token.getExpiresAt().isAfter(Instant.now())));
        verify(jwtService).generarToken("juan@mail.com", 10L, "CLIENTE");
    }

    @Test
    void registrar_deberiaLanzarExcepcionCuandoEmailYaExiste() {
        when(credencialRepository.existsByEmail("juan@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Ya existe una cuenta con ese email");

        verify(credencialRepository).existsByEmail("juan@mail.com");
        verify(credencialRepository, never()).save(any());
    }

    @Test
    void registrar_deberiaLanzarExcepcionCuandoUsuarioYaTieneCredenciales() {
        when(credencialRepository.existsByEmail("juan@mail.com")).thenReturn(false);
        when(credencialRepository.existsByUsuarioId(10L)).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(registerRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Ese usuario ya tiene credenciales");

        verify(credencialRepository).existsByUsuarioId(10L);
        verify(credencialRepository, never()).save(any());
    }

    @Test
    void registrar_deberiaRechazarCorreoQueNoCoincideConElPerfil() {
        when(credencialRepository.existsByEmail("juan@mail.com")).thenReturn(false);
        when(credencialRepository.existsByUsuarioId(10L)).thenReturn(false);
        when(usuarioClient.buscarPorId(10L))
                .thenReturn(new UsuarioPerfilResponse(10L, "otro@mail.com", "CLIENTE"));

        assertThatThrownBy(() -> authService.registrar(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El correo no coincide con el perfil del usuario");

        verify(passwordEncoder, never()).encode(anyString());
        verify(credencialRepository, never()).save(any());
    }

    @Test
    void registrar_deberiaRechazarPerfilConRolPrivilegiado() {
        when(credencialRepository.existsByEmail("juan@mail.com")).thenReturn(false);
        when(credencialRepository.existsByUsuarioId(10L)).thenReturn(false);
        when(usuarioClient.buscarPorId(10L))
                .thenReturn(new UsuarioPerfilResponse(10L, "juan@mail.com", "VENDEDOR"));

        assertThatThrownBy(() -> authService.registrar(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El rol no coincide con el perfil del usuario");

        verify(passwordEncoder, never()).encode(anyString());
        verify(credencialRepository, never()).save(any());
    }

    @Test
    void registrarPrivilegiado_deberiaCrearCredencialesConRolSolicitado() {
        PrivilegedRegisterRequest request = new PrivilegedRegisterRequest();
        request.setUsuarioId(10L);
        request.setEmail("vendedor@mail.com");
        request.setPassword("segura123");
        request.setRol("VENDEDOR");
        when(credencialRepository.existsByEmail("vendedor@mail.com")).thenReturn(false);
        when(credencialRepository.existsByUsuarioId(10L)).thenReturn(false);
        when(usuarioClient.buscarPorId(10L))
                .thenReturn(new UsuarioPerfilResponse(10L, "vendedor@mail.com", "VENDEDOR"));
        when(passwordEncoder.encode("segura123")).thenReturn("$2a$10$hasheado");
        when(credencialRepository.save(any(Credencial.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PrivilegedRegistrationResponse response = authService.registrarPrivilegiado(request);

        assertThat(response.rol()).isEqualTo("VENDEDOR");
        verify(usuarioClient).buscarPorId(10L);
        verify(credencialRepository).save(argThat(saved -> "VENDEDOR".equals(saved.getRol())));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void registrarClienteAdministrativo_deberiaCrearCredencialSinCrearSesion() {
        when(credencialRepository.existsByEmail("juan@mail.com")).thenReturn(false);
        when(credencialRepository.existsByUsuarioId(10L)).thenReturn(false);
        when(usuarioClient.buscarPorId(10L))
                .thenReturn(new UsuarioPerfilResponse(10L, "juan@mail.com", "CLIENTE"));
        when(passwordEncoder.encode("segura123")).thenReturn("$2a$hash");
        when(credencialRepository.save(any(Credencial.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(authService.registrarClienteAdministrativo(registerRequest).rol())
                .isEqualTo("CLIENTE");

        verify(credencialRepository).save(argThat(saved -> "CLIENTE".equals(saved.getRol())));
        verify(jwtService, never()).generarToken(any(), any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void registrarPrivilegiado_deberiaRechazarRolCliente() {
        PrivilegedRegisterRequest request = new PrivilegedRegisterRequest();
        request.setRol("CLIENTE");

        assertThatThrownBy(() -> authService.registrarPrivilegiado(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El rol debe ser VENDEDOR o ADMIN");
        verify(credencialRepository, never()).save(any());
    }

    @Test
    void bootstrapAdmin_deberiaCrearAdminSoloCuandoNoHayOtroActivo() {
        when(credencialRepository.existsByRolAndActivoTrue("ADMIN")).thenReturn(false);
        when(credencialRepository.existsByEmail("admin@mail.com")).thenReturn(false);
        when(credencialRepository.existsByUsuarioId(10L)).thenReturn(false);
        when(usuarioClient.buscarPorId(10L))
                .thenReturn(new UsuarioPerfilResponse(10L, "admin@mail.com", "ADMIN"));
        when(passwordEncoder.encode("una-clave-segura")).thenReturn("$2a$hash");

        authService.bootstrapAdmin(10L, "admin@mail.com", "una-clave-segura");

        verify(credencialRepository).save(argThat(saved ->
                "ADMIN".equals(saved.getRol())
                        && "$2a$hash".equals(saved.getPasswordHash())));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void bootstrapAdmin_deberiaRechazarCuandoYaExisteAdminActivo() {
        when(credencialRepository.existsByRolAndActivoTrue("ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> authService.bootstrapAdmin(10L, "admin@mail.com", "una-clave-segura"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Ya existe una cuenta ADMIN activa; se cancela el bootstrap.");

        verify(usuarioClient, never()).buscarPorId(any());
        verify(credencialRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // login()
    // -------------------------------------------------------------------------
    @Test
    void login_deberiaAutenticarYRetornarJwt() {
        when(credencialRepository.findByEmail("juan@mail.com")).thenReturn(Optional.of(credencial));
        when(passwordEncoder.matches("segura123", "$2a$10$hasheado")).thenReturn(true);
        when(jwtService.generarToken("juan@mail.com", 10L, "CLIENTE"))
                .thenReturn("eyJ.test.token");

        AuthenticationResult session = authService.login(loginRequest);
        AuthResponse response = session.response();

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("eyJ.test.token");
        assertThat(response.getEmail()).isEqualTo("juan@mail.com");
        assertThat(response.getMensaje()).isEqualTo("Login exitoso");

        verify(credencialRepository).findByEmail("juan@mail.com");
        verify(passwordEncoder).matches("segura123", "$2a$10$hasheado");
        verify(jwtService).generarToken("juan@mail.com", 10L, "CLIENTE");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_deberiaLanzarExcepcionCuandoEmailNoExiste() {
        when(credencialRepository.findByEmail("noexiste@mail.com")).thenReturn(Optional.empty());

        loginRequest.setEmail("noexiste@mail.com");

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email o contrasena incorrectos");

        verify(credencialRepository).findByEmail("noexiste@mail.com");
        verify(jwtService, never()).generarToken(any(), any(), any());
    }

    @Test
    void login_deberiaLanzarExcepcionCuandoPasswordEsIncorrecta() {
        when(credencialRepository.findByEmail("juan@mail.com")).thenReturn(Optional.of(credencial));
        when(passwordEncoder.matches("wrongpass", "$2a$10$hasheado")).thenReturn(false);

        loginRequest.setPassword("wrongpass");

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Email o contrasena incorrectos");

        verify(passwordEncoder).matches("wrongpass", "$2a$10$hasheado");
        verify(jwtService, never()).generarToken(any(), any(), any());
    }

    @Test
    void login_deberiaLanzarExcepcionCuandoCuentaEstaDesactivada() {
        credencial.setActivo(false);
        when(credencialRepository.findByEmail("juan@mail.com")).thenReturn(Optional.of(credencial));

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Cuenta desactivada");

        verify(credencialRepository).findByEmail("juan@mail.com");
        verify(jwtService, never()).generarToken(any(), any(), any());
    }

    @Test
    void renovarSesion_deberiaRevocarTokenAnteriorYCrearOtro() throws Exception {
        String oldToken = "refresh-token-anterior";
        RefreshToken session = RefreshToken.builder()
                .id(4L)
                .usuarioId(10L)
                .tokenHash(hash(oldToken))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(refreshTokenRepository.findByTokenHashForUpdate(hash(oldToken)))
                .thenReturn(Optional.of(session));
        when(credencialRepository.findByUsuarioId(10L)).thenReturn(Optional.of(credencial));
        when(jwtService.generarToken("juan@mail.com", 10L, "CLIENTE"))
                .thenReturn("eyJ.nuevo.token");

        AuthenticationResult result = authService.renovarSesion(oldToken);

        assertThat(session.isRevoked()).isTrue();
        assertThat(result.response().getToken()).isEqualTo("eyJ.nuevo.token");
        assertThat(result.refreshToken()).isNotEqualTo(oldToken);
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void renovarSesion_deberiaRechazarTokenYaRevocado() throws Exception {
        String oldToken = "refresh-token-revocado";
        RefreshToken session = RefreshToken.builder()
                .usuarioId(10L)
                .tokenHash(hash(oldToken))
                .expiresAt(Instant.now().plusSeconds(600))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByTokenHashForUpdate(hash(oldToken)))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> authService.renovarSesion(oldToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sesión inválida o expirada");

        verify(credencialRepository, never()).findByUsuarioId(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void cerrarSesion_deberiaRevocarRefreshToken() throws Exception {
        String token = "refresh-token-logout";
        RefreshToken session = RefreshToken.builder()
                .usuarioId(10L)
                .tokenHash(hash(token))
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(refreshTokenRepository.findByTokenHashForUpdate(hash(token)))
                .thenReturn(Optional.of(session));

        authService.cerrarSesion(token);

        assertThat(session.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(session);
    }

    private String hash(String token) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    // -------------------------------------------------------------------------
    // validarToken()
    // -------------------------------------------------------------------------
    @Test
    void validarToken_deberiaRetornarValidoCuandoTokenEsValido() {
        when(jwtService.esTokenValido("token.valido")).thenReturn(true);
        when(jwtService.extraerUsuarioId("token.valido")).thenReturn(10L);
        when(jwtService.extraerEmail("token.valido")).thenReturn("juan@mail.com");
        when(jwtService.extraerRol("token.valido")).thenReturn("CLIENTE");

        ValidarTokenResponse response = authService.validarToken("token.valido");

        assertThat(response.isValido()).isTrue();
        assertThat(response.getUsuarioId()).isEqualTo(10L);
        assertThat(response.getEmail()).isEqualTo("juan@mail.com");
        assertThat(response.getRol()).isEqualTo("CLIENTE");
        assertThat(response.getMensaje()).isEqualTo("Token valido");

        verify(jwtService).esTokenValido("token.valido");
        verify(jwtService).extraerUsuarioId("token.valido");
        verify(jwtService).extraerEmail("token.valido");
        verify(jwtService).extraerRol("token.valido");
    }

    @Test
    void validarToken_deberiaRetornarInvalidoCuandoTokenEstaExpirado() {
        when(jwtService.esTokenValido("token.expirado")).thenReturn(false);

        ValidarTokenResponse response = authService.validarToken("token.expirado");

        assertThat(response.isValido()).isFalse();
        assertThat(response.getMensaje()).isEqualTo("Token expirado o invalido");

        verify(jwtService).esTokenValido("token.expirado");
        verify(jwtService, never()).extraerEmail(anyString());
    }

    @Test
    void validarToken_deberiaRetornarInvalidoCuandoJwtServiceLanzaExcepcion() {
        when(jwtService.esTokenValido("token.corrupto"))
                .thenThrow(new RuntimeException("firma invalida"));

        ValidarTokenResponse response = authService.validarToken("token.corrupto");

        assertThat(response.isValido()).isFalse();
        assertThat(response.getMensaje()).contains("Token invalido");
    }
}