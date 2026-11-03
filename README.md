# Oficina — OS Service

Microsserviço de ordens de serviço da Fase 4 do Tech Challenge: abertura, status, histórico, cliente, veículo, usuário e orquestração da Saga.

> Em construção. Nesta etapa o código é a base migrada do monólito da Fase 3 e ainda inclui produto e estoque, que saem na divisão dos serviços.

## Documentos

- [Checklist de entrega](CHECKLIST.md)
- [Plano das Seções 0 a 4](docs/plano-secoes-0-4.md): catálogo de eventos, estados da Saga e compensações
- [Requisitos da Fase 4](docs/requisitos-fase4.md)
- [Destino dos endpoints e entidades](docs/mapa-endpoints-entidades.md)

## Rodar os testes

Requer Java 21 e Maven 3.9+. As chaves JWT não são versionadas; gere em PKCS#8:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out src/main/resources/jwt.private.key
openssl rsa -in src/main/resources/jwt.private.key -pubout -out src/main/resources/jwt.public.key
mvn verify
```
