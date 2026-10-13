package com.nexi.triagem.recuperacao;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.document.Document;

import com.nexi.triagem.indexacao.IndexacaoService;

public record ContextoRecuperado(
        String query,
        List<Document> tickets,
        List<Document> artigos,
        boolean filtroModuloRelaxado) {

    public boolean vazio() {
        return tickets.isEmpty() && artigos.isEmpty();
    }

    /** IDs de origem (TCK-..., KB-...) de tudo que foi recuperado; usado para validar as citações do LLM. */
    public Set<String> idsDeOrigem() {
        return Stream.concat(tickets.stream(), artigos.stream())
                .map(d -> String.valueOf(d.getMetadata().get(IndexacaoService.ORIGEM_ID)))
                .collect(Collectors.toSet());
    }
}
