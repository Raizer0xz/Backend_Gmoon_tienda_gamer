package com.example.MS_gateway_security.controller;

import com.example.MS_gateway_security.dto.*;
import com.example.MS_gateway_security.service.AuthService;
import feign.FeignException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * AuthController — Endpoints del MS-seguridad (puerto 8082)
 *
 * POST /auth/registrar  → crea credenciales para un usuario nuevo
 * POST /auth/login      → autentica y devuelve JWT
 * POST /auth/validar    → valida token (lo usan los demás microservicios)
 * GET  /auth/health     → verifica que el servicio está activo
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Autenticacion", description = "Endpoints de registro, login y validacion de tokens JWT")
public class AuthController {

    private final AuthService authService;

    @Value("${refresh-token.cookie-name:gmoon_refresh}")
    private String refreshCookieName;

    @Value("${refresh-token.expiration:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${refresh-token.cookie-secure:false}")
    private boolean refreshCookieSecure;

    @Value("${refresh-token.cookie-same-site:Strict}")
    private String refreshCookieSameSite;

    // -------------------------------------------------------------------------
    // POST /auth/registrar
    // -------------------------------------------------------------------------
    @Operation(
            summary = "Registrar credenciales",
            description = "Crea credenciales CLIENTE y una sesión solo si el ID, correo y rol coinciden con un perfil existente en MS-USUARIOS."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Credenciales creadas y JWT de acceso emitido",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos (validación fallida)",
                    content = @Content(schema = @Schema(example = "{\"error\": \"El email es obligatorio\"}"))),
            @ApiResponse(responseCode = "503", description = "No se pudo validar el perfil en MS-USUARIOS"),
            @ApiResponse(responseCode = "409", description = "Ya existe cuenta con ese email o usuarioId",
                    content = @Content(schema = @Schema(example = "{\"error\": \"Ya existe una cuenta con ese email\"}")))
    })
    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegisterRequest request) {
        log.info("Solicitud de registro para email: {}", request.getEmail());
        try {
            AuthenticationResult response = authService.registrar(request);
            log.info("Credenciales creadas para usuarioId: {}", request.getUsuarioId());
            return respuestaAutenticada(response, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (FeignException | IllegalStateException e) {
            log.error("No se pudo validar el perfil para usuarioId: {}", request.getUsuarioId(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "No se pudo validar el perfil en MS-usuarios"));
        } catch (RuntimeException e) {
            log.warn("Conflicto al registrar: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
            summary = "Registrar ADMIN o VENDEDOR",
            description = "Crea credenciales privilegiadas. Requiere un token JWT de ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Credenciales privilegiadas creadas",
                    content = @Content(schema = @Schema(implementation = PrivilegedRegistrationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN puede crear roles privilegiados"),
            @ApiResponse(responseCode = "503", description = "No se pudo validar el perfil en MS-USUARIOS"),
            @ApiResponse(responseCode = "409", description = "Ya existe cuenta con ese email o usuarioId")
    })
    @PostMapping("/registrar-privilegiado")
    public ResponseEntity<?> registrarPrivilegiado(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @Valid @RequestBody PrivilegedRegisterRequest request) {
        log.info("Solicitud de registro privilegiado para email: {}", request.getEmail());
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Se requiere un token Bearer válido"));
        }

        ValidarTokenResponse token = authService.validarToken(authHeader.substring(7));
        if (!token.isValido()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token inválido o expirado"));
        }
        if (!"ADMIN".equals(token.getRol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Solo ADMIN puede crear roles privilegiados"));
        }

        try {
            PrivilegedRegistrationResponse response = authService.registrarPrivilegiado(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (FeignException | IllegalStateException e) {
            log.error("No se pudo validar el perfil para usuarioId: {}", request.getUsuarioId(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "No se pudo validar el perfil en MS-usuarios"));
        } catch (RuntimeException e) {
            log.warn("Conflicto al registrar credenciales privilegiadas: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
            summary = "Crear cuenta de cliente desde administración",
            description = "Crea credenciales CLIENTE sin iniciar sesión como esa cuenta. Requiere token ADMIN.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta de cliente creada",
                    content = @Content(schema = @Schema(implementation = PrivilegedRegistrationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "Solo ADMIN puede crear cuentas desde administración")
    })
    @PostMapping("/registrar-cliente")
    public ResponseEntity<?> registrarClienteAdministrativo(
            @RequestHeader(name = "Authorization", required = false) String authHeader,
            @Valid @RequestBody RegisterRequest request) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Se requiere un token Bearer válido"));
        }

        ValidarTokenResponse token = authService.validarToken(authHeader.substring(7));
        if (!token.isValido()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token inválido o expirado"));
        }
        if (!"ADMIN".equals(token.getRol())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Solo ADMIN puede crear cuentas desde administración"));
        }

        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(authService.registrarClienteAdministrativo(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (FeignException | IllegalStateException e) {
            log.error("No se pudo validar el perfil para usuarioId: {}", request.getUsuarioId(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "No se pudo validar el perfil en MS-usuarios"));
        } catch (RuntimeException e) {
            log.warn("Conflicto al crear cuenta de cliente: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    // -------------------------------------------------------------------------
    // POST /auth/login
    // -------------------------------------------------------------------------
    @Operation(
            summary = "Iniciar sesion",
            description = "Autentica con email y password. Retorna un JWT Bearer de acceso de 15 minutos " +
                    "y una cookie HttpOnly para renovar la sesión durante 7 días."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login exitoso, JWT retornado",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(schema = @Schema(example = "{\"error\": \"El email es obligatorio\"}"))),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas o cuenta desactivada",
                    content = @Content(schema = @Schema(example = "{\"error\": \"Email o contrasena incorrectos\"}")))
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        log.info("Solicitud de login para email: {}", request.getEmail());
        try {
            AuthenticationResult response = authService.login(request);
            log.info("Login exitoso para email: {}", request.getEmail());
            return respuestaAutenticada(response, HttpStatus.OK);
        } catch (RuntimeException e) {
            log.warn("Login fallido para email: {} — {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
            summary = "Renovar sesión",
            description = "Rota el refresh token guardado en una cookie HttpOnly y devuelve un nuevo JWT de acceso."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión renovada",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token ausente, revocado o expirado")
    })
    @PostMapping("/refresh")
    public ResponseEntity<?> renovarSesion(
            @CookieValue(name = "${refresh-token.cookie-name:gmoon_refresh}", required = false) String refreshToken) {
        try {
            return respuestaAutenticada(authService.renovarSesion(refreshToken), HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, limpiarCookieRefresh().toString())
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Cerrar sesión",
            description = "Revoca el refresh token asociado a la cookie HttpOnly y la elimina del navegador.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    @PostMapping("/logout")
    public ResponseEntity<Void> cerrarSesion(
            @CookieValue(name = "${refresh-token.cookie-name:gmoon_refresh}", required = false) String refreshToken) {
        authService.cerrarSesion(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, limpiarCookieRefresh().toString())
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /auth/validar
    // -------------------------------------------------------------------------
    @Operation(
            summary = "Validar token JWT",
            description = "Verifica si un token JWT es válido y no ha expirado. " +
                    "Lo usan internamente MS-gateway y el resto de microservicios. " +
                    "Enviar el token en el header: Authorization: Bearer <token>"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token válido — retorna usuarioId, email y rol",
                    content = @Content(schema = @Schema(implementation = ValidarTokenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Header Authorization ausente o con formato incorrecto",
                    content = @Content(schema = @Schema(example = "{\"valido\": false, \"mensaje\": \"Header invalido. Formato esperado: Bearer <token>\"}"))),
            @ApiResponse(responseCode = "401", description = "Token expirado o inválido",
                    content = @Content(schema = @Schema(example = "{\"valido\": false, \"mensaje\": \"Token expirado o invalido\"}")))
    })
    @PostMapping("/validar")
    public ResponseEntity<ValidarTokenResponse> validarToken(
            @RequestHeader("Authorization") String authHeader) {

        log.info("Solicitud de validacion de token");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Header Authorization invalido o ausente");
            return ResponseEntity.badRequest().body(
                    ValidarTokenResponse.builder()
                            .valido(false)
                            .mensaje("Header invalido. Formato esperado: Bearer <token>")
                            .build());
        }

        String token = authHeader.substring(7);
        ValidarTokenResponse response = authService.validarToken(token);

        if (response.isValido()) {
            log.info("Token valido para usuarioId: {}", response.getUsuarioId());
            return ResponseEntity.ok(response);
        } else {
            log.warn("Token invalido: {}", response.getMensaje());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    // -------------------------------------------------------------------------
    // GET /auth/health
    // -------------------------------------------------------------------------
    @Operation(summary = "Health check", description = "Verifica que el microservicio de seguridad está activo.")
    @ApiResponse(responseCode = "200", description = "Servicio activo",
            content = @Content(schema = @Schema(example = "{\"servicio\": \"ms-seguridad\", \"estado\": \"activo\", \"puerto\": \"8082\"}")))
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "servicio", "ms-seguridad",
                "estado",   "activo",
                "puerto",   "8082"
        ));
    }

    private ResponseEntity<AuthResponse> respuestaAutenticada(
            AuthenticationResult result, HttpStatus status) {
        ResponseCookie cookie = ResponseCookie.from(refreshCookieName, result.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/auth")
                .maxAge(Duration.ofMillis(refreshTokenExpirationMs))
                .build();
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    private ResponseCookie limpiarCookieRefresh() {
        return ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path("/auth")
                .maxAge(Duration.ZERO)
                .build();
    }
}