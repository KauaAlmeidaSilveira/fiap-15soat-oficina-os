# Destino dos endpoints e entidades do monólito

Mapa dos 37 endpoints e das 10 entidades JPA de `fiap-15soat-oficina-api` (Fase 3) para os serviços da Fase 4. Os caminhos não mudam; as mudanças de comportamento estão na coluna **Observação**, com a decisão do `CHECKLIST.md`.

## Endpoints

| # | Método | Caminho | Serviço | Observação |
|---|---|---|---|---|
| 1 | POST | `/api/ordens-servico` | OS | Abre a OS e inicia a Saga |
| 2 | GET | `/api/ordens-servico` | OS | Inclui `CANCELADA` nas ocultas e `AGUARDANDO_PAGAMENTO` na prioridade |
| 3 | GET | `/api/ordens-servico/{id}` | OS | |
| 4 | GET | `/api/ordens-servico/numero/{numero}` | OS | |
| 5 | PATCH | `/api/ordens-servico/{id}/avancar-status` | OS | Só `FINALIZADA → ENTREGUE`; demais casos 422 (D21) |
| 6 | PATCH | `/api/ordens-servico/{id}/aprovar` | OS | Responde 202 e passa pela Saga (D11, D23) |
| 7 | POST | `/api/ordens-servico/{id}/itens` | OS | Consulta o produto na Execução; bloqueado depois do diagnóstico (D15) |
| 8 | DELETE | `/api/ordens-servico/{id}/itens/{itemId}` | OS | Bloqueado depois do diagnóstico (D15) |
| 9 | GET | `/api/ordens-servico/metricas/tempo-medio` | OS | `dataInicio` é o início real do reparo (D23) |
| 10 | GET | `/api/minhas-ordens-servico` | OS | |
| 11 | GET | `/api/minhas-ordens-servico/{id}` | OS | |
| 12 | POST | `/api/clientes` | OS | |
| 13 | GET | `/api/clientes` | OS | |
| 14 | GET | `/api/clientes/{id}` | OS | |
| 15 | GET | `/api/clientes/cpf-cnpj/{cpfCnpj}` | OS | |
| 16 | PUT | `/api/clientes/{id}` | OS | |
| 17 | PATCH | `/api/clientes/{id}/status` | OS | |
| 18 | DELETE | `/api/clientes/{id}` | OS | |
| 19 | POST | `/api/veiculos` | OS | |
| 20 | GET | `/api/veiculos` | OS | |
| 21 | GET | `/api/veiculos/{id}` | OS | |
| 22 | GET | `/api/veiculos/placa/{placa}` | OS | |
| 23 | PUT | `/api/veiculos/{id}` | OS | |
| 24 | DELETE | `/api/veiculos/{id}` | OS | |
| 25 | POST | `/api/veiculos/{veiculoId}/clientes/{clienteId}` | OS | |
| 26 | GET | `/api/veiculos/cliente/{clienteId}` | OS | |
| 27 | POST | `/api/auth/register` | OS | |
| 28 | POST | `/api/produtos` | Execução | |
| 29 | GET | `/api/produtos` | Execução | |
| 30 | GET | `/api/produtos/{id}` | Execução | |
| 31 | PUT | `/api/produtos/{id}` | Execução | |
| 32 | DELETE | `/api/produtos/{id}` | Execução | |
| 33 | POST | `/api/produtos/estoque/entrada` | Execução | |
| 34 | POST | `/api/produtos/estoque/saida` | Execução | |
| 35 | GET | `/api/produtos/{id}/estoque/movimentacoes` | Execução | |
| 36 | GET | `/api/produtos/estoque/movimentacoes` | Execução | |
| 37 | GET | `/aprovacao-os` | Billing | Decisão registrada no orçamento; idempotência por escrita condicional |

Totais: OS 27, Execução 9, Billing 1.

## Entidades JPA

| Entidade | Serviço | Observação |
|---|---|---|
| `OrdemServico` | OS | |
| `OsItem` | OS | `produto_id` deixa de ser FK; guarda cópia de nome, tipo e preço |
| `Cliente` | OS | |
| `Veiculo` | OS | |
| `ClienteVeiculo` | OS | |
| `User` | OS | |
| `Role` | OS | |
| `Produto` | Execução | |
| `SaldoEstoque` | Execução | |
| `MovimentacaoEstoque` | Execução | `ordem_servico_id` deixa de ser FK (feito) |
