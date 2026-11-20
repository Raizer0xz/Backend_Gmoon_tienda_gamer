package com.example.MS_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "jwt.secret=test-only-gmoon-jwt-secret-not-for-production-use")
class MsGatewayApplicationTests {

	@Autowired
	private RouteLocator routeLocator;

	@Test
	void contextLoads() {
	}

	@Test
	void loadsFrontendRoutesAndRewritesProductAndCategoryPaths() {
		List<Route> routes = routeLocator.getRoutes().collectList().block();
		assertThat(routes).isNotNull();
		assertThat(routes).extracting(Route::getId)
				.contains("ms-productos-v1", "ms-categorias-v1", "ms-usuarios", "ms-boletas-v1");

		assertRouteRewrites(routes, "ms-productos-v1",
				"/api/v1/productos/12", "/api/v2/productos/12");
		assertRouteRewrites(routes, "ms-categorias-v1",
				"/api/v1/categorias/12", "/api/v2/categorias/12");
	}

	private void assertRouteRewrites(
			List<Route> routes,
			String routeId,
			String requestedPath,
			String expectedPath) {
		Route route = routes.stream()
				.filter(candidate -> candidate.getId().equals(routeId))
				.findFirst()
				.orElseThrow();
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get(requestedPath).build());
		AtomicReference<String> forwardedPath = new AtomicReference<>();

		runFilters(route.getFilters(), 0, exchange, forwardedPath).block();

		assertThat(forwardedPath.get()).isEqualTo(expectedPath);
	}

	private Mono<Void> runFilters(
			List<GatewayFilter> filters,
			int index,
			ServerWebExchange exchange,
			AtomicReference<String> forwardedPath) {
		if (index == filters.size()) {
			forwardedPath.set(exchange.getRequest().getURI().getPath());
			return Mono.empty();
		}
		return filters.get(index).filter(exchange,
				next -> runFilters(filters, index + 1, next, forwardedPath));
	}
}
