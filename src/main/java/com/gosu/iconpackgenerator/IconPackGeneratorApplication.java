package com.gosu.iconpackgenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties
public class IconPackGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(IconPackGeneratorApplication.class, args);
	}

}
