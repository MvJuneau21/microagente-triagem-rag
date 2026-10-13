package com.nexi.triagem.modelo;

import java.util.List;

/** O que o microagente devolve: a triagem, as fontes que a embasaram e avisos dos guardrails. */
public record TriagemResultado(
        String ticketId,
        TriagemLlm triagem,
        List<Fonte> fontesRecuperadas,
        boolean filtroModuloRelaxado,
        List<String> avisos) {
}
