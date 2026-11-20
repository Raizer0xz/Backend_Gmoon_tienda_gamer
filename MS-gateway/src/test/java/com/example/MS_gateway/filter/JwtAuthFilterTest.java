package com.example.MS_gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    private static final String SECRET = "test-only-gmoon-jwt-secret-not-for-production-use";

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter();
        ReflectionTestUtils.setField(filter, "secret", SECRET);
    }

    @Test
    void allowsPublicCatalogGetAndRemovesUntrustedIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v2/productos")
                        .header("X-Usuario-Id", "7")
                        .header("X-Usuario-Rol", "ADMIN")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> {
            forwarded.set(true);
            assertThat(request.getRequest().getHeaders().getFirst("X-Usuario-Id")).isNull();
            assertThat(request.getRequest().getHeaders().getFirst("X-Usuario-Rol")).isNull();
            return Mono.empty();
        }).block();

        assertThat(forwarded).isTrue();
    }

    @Test
    void allowsPublicVersionedProductAndCategoryReads() {
        MockServerWebExchange products = exchange(HttpMethod.GET, "/api/v1/productos/12", null);
        MockServerWebExchange categories = exchange(HttpMethod.GET, "/api/v1/categorias", null);

        filter.apply(new JwtAuthFilter.Config()).filter(products, request -> Mono.empty()).block();
        filter.apply(new JwtAuthFilter.Config()).filter(categories, request -> Mono.empty()).block();

        assertThat(products.getResponse().getStatusCode()).isNull();
        assertThat(categories.getResponse().getStatusCode()).isNull();
    }

    @Test
    void deniesUnauthenticatedProductWrite() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/api/v2/productos", null);

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deniesUnauthenticatedBoletaRead() {
        MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/v1/boletas", null);

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void allowsPublicCredentialRegistration() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/auth/registrar", null);

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void deniesUnauthenticatedPrivilegedRegistration() {
        MockServerWebExchange exchange = exchange(HttpMethod.POST, "/auth/registrar-privilegiado", null);

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deniesClientPrivilegedRegistration() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.POST, "/auth/registrar-privilegiado", token(7L, "CLIENTE"));

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void allowsAdminPrivilegedRegistration() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.POST, "/auth/registrar-privilegiado", token(7L, "ADMIN"));

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void deniesClientUserCreationEvenWithValidToken() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.POST, "/api/v1/usuarios", token(7L, "CLIENTE"));

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deniesVendorProductWrite() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.POST, "/api/v2/productos", token(7L, "VENDEDOR"));

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void allowsAdminProductWriteAndOverwritesIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v2/productos")
                        .header("Authorization", "Bearer " + token(7L, "ADMIN"))
                        .header("X-Usuario-Id", "999")
                        .header("X-Usuario-Email", "attacker@example.com")
                        .header("X-Usuario-Rol", "CLIENTE")
                        .build());

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> {
            assertThat(request.getRequest().getHeaders().getFirst("X-Usuario-Id")).isEqualTo("7");
            assertThat(request.getRequest().getHeaders().getFirst("X-Usuario-Email")).isEqualTo("user@example.com");
            assertThat(request.getRequest().getHeaders().getFirst("X-Usuario-Rol")).isEqualTo("ADMIN");
            return Mono.empty();
        }).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void clientCanOnlyReadOwnOrders() {
        MockServerWebExchange ownOrders = exchange(
                HttpMethod.GET, "/api/v1/pedidos/usuario/7", token(7L, "CLIENTE"));
        MockServerWebExchange anotherUsersOrders = exchange(
                HttpMethod.GET, "/api/v1/pedidos/usuario/8", token(7L, "CLIENTE"));

        filter.apply(new JwtAuthFilter.Config()).filter(ownOrders, request -> Mono.empty()).block();
        filter.apply(new JwtAuthFilter.Config()).filter(anotherUsersOrders, request -> Mono.empty()).block();

        assertThat(ownOrders.getResponse().getStatusCode()).isNull();
        assertThat(anotherUsersOrders.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void vendorCanReadBoletasButCannotCreateThem() {
        MockServerWebExchange list = exchange(
                HttpMethod.GET, "/api/v1/boletas", token(7L, "VENDEDOR"));
        MockServerWebExchange create = exchange(
                HttpMethod.POST, "/api/v1/boletas", token(7L, "VENDEDOR"));

        filter.apply(new JwtAuthFilter.Config()).filter(list, request -> Mono.empty()).block();
        filter.apply(new JwtAuthFilter.Config()).filter(create, request -> Mono.empty()).block();

        assertThat(list.getResponse().getStatusCode()).isNull();
        assertThat(create.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clientCanOnlyReadOwnBoletas() {
        MockServerWebExchange ownBoletas = exchange(
                HttpMethod.GET, "/api/v1/boletas/usuario/7", token(7L, "CLIENTE"));
        MockServerWebExchange anotherUsersBoletas = exchange(
                HttpMethod.GET, "/api/v1/boletas/usuario/8", token(7L, "CLIENTE"));

        filter.apply(new JwtAuthFilter.Config()).filter(ownBoletas, request -> Mono.empty()).block();
        filter.apply(new JwtAuthFilter.Config()).filter(anotherUsersBoletas, request -> Mono.empty()).block();

        assertThat(ownBoletas.getResponse().getStatusCode()).isNull();
        assertThat(anotherUsersBoletas.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsLegacyRoles() {
        MockServerWebExchange exchange = exchange(
                HttpMethod.GET, "/api/v1/pedidos", token(7L, "REPARTIDOR"));

        filter.apply(new JwtAuthFilter.Config()).filter(exchange, request -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private MockServerWebExchange exchange(HttpMethod method, String path, String token) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.method(method, path);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return MockServerWebExchange.from(request.build());
    }

    private String token(Long usuarioId, String rol) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("user@example.com")
                .claim("usuarioId", usuarioId)
                .claim("rol", rol)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .signWith(key)
                .compact();
    }
}
