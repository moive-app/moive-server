package com.moive.MoiveBE;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-for-jwt-testing-123456789"
})
class MoiveBEApplicationTests {

	@Test
	void contextLoads() {
	}

}
