package br.com.fiap.oficina.core.domain.entity;

import br.com.fiap.oficina.core.domain.enums.TipoProduto;

import java.math.BigDecimal;

/** Dados do produto que a OS copia para o item; o cadastro é do serviço de Execução. */
public record Produto(Long id, String nome, TipoProduto tipo, BigDecimal precoUnitario) {}
