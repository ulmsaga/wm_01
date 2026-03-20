package com.mobigen.aiop.nttpoc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

import com.mobigen.aiop.nttpoc.module.auth.sse.StartupSessionCleanup;

@SpringBootTest
@ActiveProfiles("test")
class NttpocApplicationTests {

	@MockitoBean
	StartupSessionCleanup startupSessionCleanup;

	@Test
	void contextLoads() {
	}

}
