package com.inspien.assignment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InspienAssignmentApplication {

	public static void main(String[] args) {
		SpringApplication.run(InspienAssignmentApplication.class, args);
	}

}
