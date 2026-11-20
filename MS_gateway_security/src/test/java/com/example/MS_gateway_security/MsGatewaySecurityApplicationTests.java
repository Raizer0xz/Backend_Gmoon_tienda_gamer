package com.example.MS_gateway_security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret=test-only-gmoon-jwt-secret-not-for-production-use")
class MsGatewaySecurityApplicationTests {

	@Test
	void contextLoads() {
	}

}
