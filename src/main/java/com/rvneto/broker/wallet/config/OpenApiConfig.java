package com.rvneto.broker.wallet.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Broker Wallet API")
                        .version("1.0.0")
                        .description("Financial custody service for the My Broker B3 ecosystem. " +
                                "Manages account balances, asset positions and reacts to order lifecycle events " +
                                "(PENDING → FILLED → REJECTED) consumed from Kafka to block, settle or refund funds.")
                        .contact(new Contact()
                                .name("Roberto de Vargas Neto")
                                .url("https://www.linkedin.com/in/roberto-de-vargas/")));
    }
}