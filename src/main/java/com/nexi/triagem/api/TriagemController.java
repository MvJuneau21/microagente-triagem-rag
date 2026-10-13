package com.nexi.triagem.api;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nexi.triagem.geracao.TriagemAgente;
import com.nexi.triagem.indexacao.IndexacaoService;
import com.nexi.triagem.modelo.TicketEntrada;
import com.nexi.triagem.modelo.TriagemResultado;

/**
 * Porta de entrada HTTP do microagente. Em produção o mesmo {@link TriagemAgente#triar} seria
 * chamado por um consumidor do evento "ticket.criado" do helpdesk.
 */
@RestController
class TriagemController {

    private final TriagemAgente agente;
    private final IndexacaoService indexacao;

    TriagemController(TriagemAgente agente, IndexacaoService indexacao) {
        this.agente = agente;
        this.indexacao = indexacao;
    }

    @PostMapping("/triagem")
    TriagemResultado triar(@RequestBody TicketEntrada ticket) {
        return agente.triar(ticket);
    }

    @PostMapping("/admin/reindexar")
    Map<String, Integer> reindexar() {
        return Map.of("documentosIndexados", indexacao.indexarTudo());
    }
}
