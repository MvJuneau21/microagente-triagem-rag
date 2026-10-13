package com.nexi.triagem.modelo;

/** Ticket já resolvido, exportado do helpdesk. É a principal fonte de conhecimento do agente. */
public record TicketResolvido(
        String id,
        String titulo,
        String descricao,
        String resolucao,
        String modulo,
        String plataforma,
        String categoria,
        String severidade,
        String equipe,
        String status,
        String resolvidoEm) {
}
