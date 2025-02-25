package com.archipelago;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.archipelago.mapper")
public class ArchipelagoApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArchipelagoApplication.class, args);
	}

}
