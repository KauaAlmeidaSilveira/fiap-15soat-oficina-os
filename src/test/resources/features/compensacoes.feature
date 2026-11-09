# language: pt
Funcionalidade: Compensações da Saga
  Cada falha desfaz o que já aconteceu, em ordem inversa, e deixa a OS em um estado final conhecido.

  Contexto:
    Dado um cliente com veículo cadastrado

  Cenário: Orçamento recusado encerra em REPROVADA sem mexer no estoque (F1)
    Dado uma peça com saldo 10 e preço 50.00
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico
    E o cliente recusa pelo link recebido por e-mail
    Então a OS fica "REPROVADA" e a Saga "REPROVADA"
    E o saldo da peça é 10

  Cenário: Falha ao gerar orçamento volta ao diagnóstico e a oficina conclui de novo (F2)
    Dado uma peça com saldo 10 e preço 50.00
    E a falha "GERAR_ORCAMENTO" está ligada no Billing
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico sem esperar o orçamento
    Então a OS fica "EM_DIAGNOSTICO" e a Saga "EM_DIAGNOSTICO"
    Quando a falha "GERAR_ORCAMENTO" é desligada no Billing
    E a oficina conclui de novo o diagnóstico
    Então a OS fica "AGUARDANDO_APROVACAO" e a Saga "AGUARDANDO_DECISAO"

  Cenário: Cliente sem resposta no prazo cancela a OS sem mexer no estoque (F3)
    Dado uma peça com saldo 10 e preço 50.00
    E o prazo de decisão do orçamento é de 5 segundos
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico
    Então a OS fica "CANCELADA" e a Saga "COMPENSADA"
    E o saldo da peça é 10
    E o link de aprovação não vale mais

  Cenário: Estoque insuficiente na aprovação cancela sem baixar nada (F4)
    Dado uma peça com saldo 1 e preço 50.00
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico
    E o cliente aprova pelo link recebido por e-mail
    Então a OS fica "CANCELADA" e a Saga "COMPENSADA"
    E o saldo da peça é 1
    E o cliente recebe o aviso de cancelamento

  Cenário: Falha ao gerar a cobrança devolve o estoque e cancela (F5)
    Dado uma peça com saldo 10 e preço 50.00
    E a falha "GERAR_COBRANCA" está ligada no Billing
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico
    E o cliente aprova pelo link recebido por e-mail
    Então a OS fica "CANCELADA" e a Saga "COMPENSADA"
    E o saldo da peça é 10

  Cenário: QR expirado devolve o estoque e cancela (F6)
    Dado uma peça com saldo 10 e preço 50.00
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico
    E o cliente aprova pelo link recebido por e-mail
    Então a OS fica "AGUARDANDO_PAGAMENTO" e a Saga "AGUARDANDO_PAGAMENTO"
    Quando o Mercado Pago notifica que o QR expirou
    Então a OS fica "CANCELADA" e a Saga "COMPENSADA"
    E o saldo da peça é 10

  Cenário: Falha ao enviar para execução reembolsa, devolve o estoque e cancela (F7)
    Dado uma peça com saldo 10 e preço 50.00
    E a falha "ENFILEIRAR_REPARO" está ligada na Execução
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico
    E o cliente aprova pelo link recebido por e-mail
    E o Mercado Pago notifica o pagamento
    Então a OS fica "CANCELADA" e a Saga "COMPENSADA"
    E o saldo da peça é 10
    E o pagamento foi reembolsado no Mercado Pago

  Cenário: Pagamento depois do cancelamento é reembolsado e a OS continua cancelada (F8)
    Dado uma peça com saldo 10 e preço 50.00
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico
    E o cliente aprova pelo link recebido por e-mail
    Então a OS fica "AGUARDANDO_PAGAMENTO" e a Saga "AGUARDANDO_PAGAMENTO"
    Quando o Mercado Pago notifica que o QR expirou
    Então a OS fica "CANCELADA" e a Saga "COMPENSADA"
    Quando o Mercado Pago notifica um pagamento tardio
    Então o pagamento foi reembolsado no Mercado Pago
    E a OS fica "CANCELADA" e a Saga "COMPENSADA"
    E o saldo da peça é 10

  Cenário: Reembolso que falha exige intervenção e pode ser reprocessado (F9)
    Dado uma peça com saldo 10 e preço 50.00
    E a falha "ENFILEIRAR_REPARO" está ligada na Execução
    E o Mercado Pago recusa reembolsos
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico
    E o cliente aprova pelo link recebido por e-mail
    E o Mercado Pago notifica o pagamento
    Então a OS fica "AGUARDANDO_PAGAMENTO" e a Saga "COMPENSACAO_FALHOU"
    E o saldo da peça é 8
    Quando o Mercado Pago volta a aceitar reembolsos
    E o administrador reprocessa a Saga
    Então a OS fica "CANCELADA" e a Saga "COMPENSADA"
    E o saldo da peça é 10

  Cenário: Cancelamento com reparo em andamento reembolsa e devolve todas as peças (F10)
    Dado uma peça com saldo 10 e preço 50.00
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico
    E o cliente aprova pelo link recebido por e-mail
    E o Mercado Pago notifica o pagamento
    E a oficina inicia o reparo
    E a oficina cancela a OS por "Cliente desistiu"
    Então a OS fica "CANCELADA" e a Saga "COMPENSADA"
    E o saldo da peça é 10
    E o pagamento foi reembolsado no Mercado Pago
    E o cliente recebe o aviso de cancelamento

  Cenário: A Saga continua depois de reiniciar o orquestrador
    Dado uma peça com saldo 10 e preço 50.00
    Quando a recepção abre uma OS com 2 unidades da peça
    E a oficina inicia e conclui o diagnóstico
    E o OS Service é reiniciado
    E o cliente aprova pelo link recebido por e-mail
    Então a OS fica "AGUARDANDO_PAGAMENTO" e a Saga "AGUARDANDO_PAGAMENTO"
    E o saldo da peça é 8
