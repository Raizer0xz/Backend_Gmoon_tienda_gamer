package com.example.MS_gateway_security.service;

import com.example.MS_gateway_security.dto.*;
import com.example.MS_gateway_security.client.UsuarioClient;
import com.example.MS_gateway_security.model.Credencial;
import com.example.MS_gateway_security.model.RefreshToken;
import com.example.MS_gateway_security.repository.CredencialRepository;
import com.example.MS_gateway_security.repository.RefreshTokenRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CredencialRepository credencialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioClient usuarioClient;

    @org.springframework.beans.factory.annotation.Value("${refresh-token.expiration:604800000}")
    private long refreshTokenExpirationMs = 604800000L;

    // POST /auth/registrar
    @Transactional
    public AuthenticationResult registrar(RegisterRequest request) {
        return registrar(request.getUsuarioId(), request.getEmail(), request.getPassword(), "CLIENTE");
    }

    @Transactional
    public PrivilegedRegistrationResponse registrarClienteAdministrativo(RegisterRequest request) {
        Credencial credencial = crearCredencial(
                request.getUsuarioId(), request.getEmail(), request.getPassword(), "CLIENTE");
        return new PrivilegedRegistrationResponse(
                credencial.getUsuarioId(),
                credencial.getEmail(),
                credencial.getRol(),
                "Cuenta de cliente creada");
    }

    @Transactional
    public PrivilegedRegistrationResponse registrarPrivilegiado(PrivilegedRegisterRequest request) {
        String rol = normalizeRole(request.getRol());
        if (!"VENDEDOR".equals(rol) && !"ADMIN".equals(rol)) {
            throw new IllegalArgumentException("El rol debe ser VENDEDOR o ADMIN");
        }
        Credencial credencial = crearCredencial(
                request.getUsuarioId(), request.getEmail(), request.getPassword(), rol);
        return new PrivilegedRegistrationResponse(
                credencial.getUsuarioId(),
                credencial.getEmail(),
                credencial.getRol(),
                "Cuenta privilegiada creada");
    }

    @Transactional
    public void bootstrapAdmin(Long usuarioId, String email, String password) {
        if (credencialRepository.existsByRolAndActivoTrue("ADMIN")) {
            throw new IllegalStateException("Ya existe una cuenta ADMIN activa; se cancela el bootstrap.");
        }
        crearCredencial(usuarioId, email, password, "ADMIN");
    }

    private AuthenticationResult registrar(Long usuarioId, String email, String password, String rol) {
        Credencial credencial = crearCredencial(usuarioId, email, password, rol);
        return crearSesion(credencial, "Usuario registrado exitosamente");
    }

    private Credencial crearCredencial(Long usuarioId, String email, String password, String rol) {
        String emailNormalizado = normalizeEmail(email);
        if (credencialRepository.existsByEmail(emailNormalizado))
            throw new RuntimeException("Ya existe una cuenta con ese email");
        if (credencialRepository.existsByUsuarioId(usuarioId))
            throw new RuntimeException("Ese usuario ya tiene credenciales");

        validarPerfil(usuarioId, emailNormalizado, rol);

        Credencial credencial = Credencial.builder()
                .usuarioId(usuarioId)
                .email(emailNormalizado)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .activo(true)
                .build();

        return credencialRepository.save(credencial);
    }

    // POST /auth/login
    @Transactional
    public AuthenticationResult login(LoginRequest request) {
        Credencial credencial = credencialRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new RuntimeException("Email o contrasena incorrectos"));

        if (!credencial.getActivo())
            throw new RuntimeException("Cuenta desactivada");

        // BCrypt compara texto plano vs hash almacenado
        if (!passwordEncoder.matches(request.getPassword(), credencial.getPasswordHash()))
            throw new RuntimeException("Email o contrasena incorrectos");

        return crearSesion(credencial, "Login exitoso");
    }

    @Transactional
    public AuthenticationResult renovarSesion(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Sesión inválida o expirada");
        }

        RefreshToken session = refreshTokenRepository.findByTokenHashForUpdate(hashToken(refreshToken))
                .orElseThrow(() -> new IllegalArgumentException("Sesión inválida o expirada"));
        if (session.isRevoked() || !session.getExpiresAt().isAfter(Instant.now())) {
            throw new IllegalArgumentException("Sesión inválida o expirada");
        }

        Credencial credencial = credencialRepository.findByUsuarioId(session.getUsuarioId())
                .filter(Credencial::getActivo)
                .orElseThrow(() -> new IllegalArgumentException("La cuenta está desactivada"));

        session.setRevoked(true);
        refreshTokenRepository.save(session);
        return crearSesion(credencial, "Sesión renovada");
    }

    @Transactional
    public void cerrarSesion(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(hashToken(refreshToken))
                .ifPresent(session -> {
                    session.setRevoked(true);
                    refreshTokenRepository.save(session);
                });
    }

    private AuthenticationResult crearSesion(Credencial credencial, String mensaje) {
        String accessToken = jwtService.generarToken(
                credencial.getEmail(), credencial.getUsuarioId(), credencial.getRol());
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        refreshTokenRepository.save(RefreshToken.builder()
                .usuarioId(credencial.getUsuarioId())
                .tokenHash(hashToken(refreshToken))
                .expiresAt(Instant.now().plus(Duration.ofMillis(refreshTokenExpirationMs)))
                .build());

        AuthResponse response = AuthResponse.builder()
                .token(accessToken)
                .email(credencial.getEmail())
                .rol(credencial.getRol())
                .usuarioId(credencial.getUsuarioId())
                .mensaje(mensaje)
                .build();
        return new AuthenticationResult(response, refreshToken);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }

    // POST /auth/validar  (lo llaman los demas microservicios)
    public ValidarTokenResponse validarToken(String token) {
        try {
            if (!jwtService.esTokenValido(token))
                return ValidarTokenResponse.builder().valido(false).mensaje("Token expirado o invalido").build();

            return ValidarTokenResponse.builder()
                    .valido(true)
                    .usuarioId(jwtService.extraerUsuarioId(token))
                    .email(jwtService.extraerEmail(token))
                    .rol(jwtService.extraerRol(token))
                    .mensaje("Token valido").build();
        } catch (Exception e) {
            return ValidarTokenResponse.builder().valido(false).mensaje("Token invalido: " + e.getMessage()).build();
        }
    }

    private void validarPerfil(Long usuarioId, String email, String rol) {
        UsuarioPerfilResponse perfil;
        try {
            perfil = usuarioClient.buscarPorId(usuarioId);
        } catch (FeignException.NotFound e) {
            throw new IllegalArgumentException("No existe un perfil para el usuario: " + usuarioId);
        }

        if (perfil == null || !Objects.equals(perfil.id(), usuarioId)
                || perfil.email() == null || perfil.rol() == null) {
            throw new IllegalStateException("MS-usuarios devolvió un perfil incompleto");
        }
        if (!Objects.equals(normalizeEmail(perfil.email()), email)) {
            throw new IllegalArgumentException("El correo no coincide con el perfil del usuario");
        }
        if (!normalizeRole(perfil.rol()).equals(rol)) {
            throw new IllegalArgumentException("El rol no coincide con el perfil del usuario");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }
}
