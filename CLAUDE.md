# Tech Challenge Fase 4 — regras para o Claude Code

Vale para quem operar a Fase 4 (Kauã ou Júlio). Idioma: pt-BR em conversa, docs, commits e PRs.

## Montar a pasta de trabalho

Todos os repositórios lado a lado em `Challenge - Fase 4/`, com o monólito da Fase 3 só como referência:

```bash
mkdir "Challenge - Fase 4" && cd "Challenge - Fase 4"
for r in os billing execucao lambda-auth; do git clone https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-$r.git; done
for r in infra-k8s infra-db; do git clone https://github.com/JulioNCavalcanti/fiap-15soat-oficina-$r.git; done
mkdir legado && git clone https://github.com/KauaAlmeidaSilveira/fiap-15soat-oficina-api.git legado/fiap-15soat-oficina-api
ln -s fiap-15soat-oficina-os/CLAUDE.md CLAUDE.md   # o Claude lê estas regras abrindo a pasta de cima
```

Chaves e tokens não vão para o git: cada um gera os seus com `sh scripts/gerar-chaves-locais.sh` e
`python3 postman/gerar-tokens.py`, a partir deste repositório.

## Fonte de verdade: `CHECKLIST.md`

- Ao começar, ler a seção **Retomada** e, para AWS, **Antes de testar na AWS** (tem o roteiro do Júlio).
- Plano aprovado e diferenças: `docs/plano-secoes-0-4.md`. Requisitos do enunciado: `docs/requisitos-fase4.md`.
- **O checklist fica sempre atualizado.** Em todo fix, feat, push, PR, merge ou decisão, no mesmo passo:
  o item `[ ]`/`[x]`, a Retomada, a Dimensão (contagem total e por seção) e os parágrafos que ficaram velhos.
  Contar só marcadores de item (`- [x]`) e de célula de tabela (`| [x] |`), nunca o texto que cita o marcador.
- Mudança no checklist vai em branch nova a partir de `main`, com PR. Não empurrar commit em branch de PR já mergeado.

## Ações externas

Push, PR, merge, `terraform apply`, criação de recurso na AWS e troca de secrets: só com pedido explícito de
quem está operando. Merge de `infra-k8s`, `infra-db` e `lambda-auth` dispara `terraform apply`; a ordem
está no checklist (`lambda-auth` por último, depois do deploy dos serviços).

## Regra de negócio

Não inventar. As decisões D1 a D23 estão na Seção 0 do checklist; caso omisso vira pergunta, não código.

## Testes

- Serviço: `mvn verify` no repositório (gate de 80% do JaCoCo sobre o serviço inteiro).
- Sistema inteiro: `sh scripts/rodar-bdd.sh` (sobe tudo em Docker, roda os 12 cenários e derruba).
- **Toda mudança de endpoint ou de fluxo atualiza `postman/` e roda no Newman**, com o sistema no ar
  (`docker compose up -d --build --wait`):

  ```bash
  npx -y newman@6 run postman/oficina-fase4.collection.json -e postman/oficina-fase4.environment.local.json
  ```

  Resultado esperado hoje: 236 asserções, com 3 falhas só na pasta "Só na AWS: Login" (a Lambda não sobe
  no Docker), ou seja, 233/233 no local.
- Teste reproduz produção: não limpar fila, cache ou estado para passar; não alterar nem pular teste
  existente sem combinar.
