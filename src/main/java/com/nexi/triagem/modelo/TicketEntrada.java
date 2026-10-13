package com.nexi.triagem.modelo;

/**
 * Ticket novo que chega para triagem.
 *
 * @param modulo módulo escolhido pelo cliente no formulário (pode estar errado ou vazio)
 * @param plano  plano do cliente (free, pro, enterprise), vem do cadastro e define o SLA
 */
public record TicketEntrada(
        String id,
        String titulo,
        String descricao,
        String modulo,
        String plataforma,
        String plano) {
}
