package br.com.fiap.oficina.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    static final String ESQUEMA = "bearer-jwt";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Oficina Mecânica - OS Service")
                        .description("""
                                Ordens de serviço, clientes, veículos e funcionários. Orquestra a Saga da OS: publica comandos
                                para Billing e Execução por SQS e aplica as compensações.

                                Autenticação: Bearer JWT de sessão, emitido pela Lambda de login no API Gateway
                                (POST /auth/funcionario, POST /auth/cliente). As permissões vêm da claim roles.
                                Rotas /internal/** aceitam só o token interno da Lambda de login.
                                """)
                        .version("4.0.0"))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
    }
}
