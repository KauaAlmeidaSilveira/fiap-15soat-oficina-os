# language: pt
Funcionalidade: Ciclo de vida da ordem de serviço
  A OS atravessa os três serviços, coordenada pela Saga orquestrada no OS Service.

  Cenário: OS percorre diagnóstico, orçamento, pagamento e reparo até a entrega
    Dado um cliente com veículo cadastrado
    E uma peça com saldo 10 e preço 50.00
    Quando a recepção abre uma OS com 2 unidades da peça
    Então a OS fica "RECEBIDA" e a Saga "AGUARDANDO_DIAGNOSTICO"
    Quando a oficina inicia e conclui o diagnóstico
    Então a OS fica "AGUARDANDO_APROVACAO" e a Saga "AGUARDANDO_DECISAO"
    Quando o cliente aprova pelo link recebido por e-mail
    Então a OS fica "AGUARDANDO_PAGAMENTO" e a Saga "AGUARDANDO_PAGAMENTO"
    E o saldo da peça é 8
    E o cliente recebe o e-mail com o QR de pagamento
    Quando o Mercado Pago notifica o pagamento
    Então a OS fica "EM_EXECUCAO" e a Saga "EM_REPARO"
    Quando a oficina inicia e finaliza o reparo
    Então a OS fica "FINALIZADA" e a Saga "CONCLUIDA"
    Quando a oficina registra a entrega
    Então a OS fica "ENTREGUE" e a Saga "CONCLUIDA"
    E o histórico da OS tem 7 mudanças de status
