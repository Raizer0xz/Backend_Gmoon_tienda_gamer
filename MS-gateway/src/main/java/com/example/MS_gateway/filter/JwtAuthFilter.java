package com.example.MS_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JwtAuthFilter — filtro de Gateway que valida el token JWT en cada request.
 *
 * Si el token es válido, propaga el usuarioId, email y rol como headers
 * para que los MS internos puedan usarlos sin volver a validar.
 *
 * Uso en application.properties:
 *   spring.cloud.gateway.routes[N].filters[0]=JwtAuthFilter
 */
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    @Value("${jwt.secret}")
    private String secret;

    public JwtAuthFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            String path = exchange.getRequest().getURI().getPath();
            HttpMethod method = exchange.getRequest().getMethod();

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                if (isPublicRequest(method, path)) {
                    return chain.filter(withoutIdentityHeaders(exchange));
                }
                return unauthorized(exchange, "Token no proporcionado");
            }

            String token = authHeader.substring(7);

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(getKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String rol = claims.get("rol", String.class);
                Object usuarioIdClaim = claims.get("usuarioId");
                if (!(usuarioIdClaim instanceof Number usuarioIdNumber)) {
                    return forbidden(exchange, "El token no contiene un usuario válido");
                }
                Long usuarioId = usuarioIdNumber.longValue();
                if (!isAllowed(method, path, rol, usuarioId)) {
                    return forbidden(exchange, "No tienes permisos para realizar esta operación");
                }

                var modifiedRequest = exchange.getRequest().mutate()
                        .headers(headers -> {
                            headers.remove("X-Usuario-Id");
                            headers.remove("X-Usuario-Email");
                            headers.remove("X-Usuario-Rol");
                            headers.set("X-Usuario-Id", String.valueOf(usuarioId));
                            headers.set("X-Usuario-Email", claims.getSubject());
                            headers.set("X-Usuario-Rol", rol);
                        })
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());

            } catch (JwtException | IllegalArgumentException e) {
                return unauthorized(exchange, "Token inválido o expirado");
            }
        };
    }

    private boolean isPublicRequest(HttpMethod method, String path) {
        if (method == HttpMethod.OPTIONS) {
            return true;
        }

        String normalizedPath = normalizePath(path);
        if (method == HttpMethod.POST && (
                normalizedPath.equals("/auth/login")
                        || normalizedPath.equals("/auth/registrar")
                        || normalizedPath.equals("/auth/validar")
                        || normalizedPath.equals("/auth/refresh")
                        || normalizedPath.equals("/auth/logout"))) {
            return true;
        }
        if (method == HttpMethod.GET && normalizedPath.equals("/auth/health")) {
            return true;
        }

        if (method == HttpMethod.GET) {
            return isPath(path, "/api/v1/productos")
                    || isPath(path, "/api/v1/categorias")
                    || isPath(path, "/api/v2/productos")
                    || isPath(path, "/api/v2/categorias");
        }

        return method == HttpMethod.POST && normalizePath(path).equals("/api/v1/usuarios");
    }

    private boolean isAllowed(HttpMethod method, String path, String role, Long usuarioId) {
        if (!"ADMIN".equals(role) && !"VENDEDOR".equals(role) && !"CLIENTE".equals(role)) {
            return false;
        }

        if (isPublicRequest(method, path) && !isPath(path, "/api/v1/usuarios")) {
            return true;
        }

        if (isPath(path, "/auth/registrar-privilegiado")) {
            return method == HttpMethod.POST && "ADMIN".equals(role);
        }
        if (isPath(path, "/auth/registrar-cliente")) {
            return method == HttpMethod.POST && "ADMIN".equals(role);
        }

        if (isPath(path, "/api/v1/usuarios")) {
            return "ADMIN".equals(role);
        }

        if (isPath(path, "/api/v1/productos")
                || isPath(path, "/api/v1/categorias")
                || isPath(path, "/api/v2/productos")
                || isPath(path, "/api/v2/categorias")) {
            return method == HttpMethod.GET || "ADMIN".equals(role);
        }

        if (isPath(path, "/api/v1/boletas")) {
            if ("ADMIN".equals(role)) {
                return true;
            }
            if ("VENDEDOR".equals(role)) {
                return method == HttpMethod.GET;
            }
            if ("CLIENTE".equals(role)) {
                String usuarioPath = "/api/v1/boletas/usuario/" + usuarioId;
                return (method == HttpMethod.GET && normalizePath(path).equals(usuarioPath))
                        || method == HttpMethod.POST;
            }
        }

        if (isPath(path, "/api/v1/pedidos")) {
            if ("ADMIN".equals(role)) {
                return true;
            }
            if ("VENDEDOR".equals(role)) {
                return method == HttpMethod.GET;
            }
            if ("CLIENTE".equals(role)) {
                String usuarioPath = "/api/v1/pedidos/usuario/" + usuarioId;
                return (method == HttpMethod.GET && normalizePath(path).equals(usuarioPath))
                        || method == HttpMethod.POST;
            }
        }

        return false;
    }

    private boolean isPath(String path, String basePath) {
        String normalized = normalizePath(path);
        return normalized.equals(basePath) || normalized.startsWith(basePath + "/");
    }

    private String normalizePath(String path) {
        return path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
    }

    private ServerWebExchange withoutIdentityHeaders(ServerWebExchange exchange) {
        var request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-Usuario-Id");
                    headers.remove("X-Usuario-Email");
                    headers.remove("X-Usuario-Rol");
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String mensaje) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        var body = exchange.getResponse().bufferFactory()
                .wrap(("{\"error\": \"" + mensaje + "\"}").getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(body));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String mensaje) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
        var body = exchange.getResponse().bufferFactory()
                .wrap(("{\"error\": \"" + mensaje + "\"}").getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(body));
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static class Config {}
}