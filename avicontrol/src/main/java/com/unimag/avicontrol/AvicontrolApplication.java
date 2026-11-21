package com.unimag.avicontrol;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableScheduling
public class AvicontrolApplication {

	public static void main(String[] args) {
		SpringApplication.run(AvicontrolApplication.class, args);
	}

}
