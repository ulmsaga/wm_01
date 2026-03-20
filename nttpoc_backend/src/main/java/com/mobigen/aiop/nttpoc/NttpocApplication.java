package com.mobigen.aiop.nttpoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NttpocApplication {

	public static void main(String[] args) {
		SpringApplication.run(NttpocApplication.class, args);
	}

}
