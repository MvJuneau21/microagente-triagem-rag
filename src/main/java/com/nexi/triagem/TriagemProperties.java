package com.nexi.triagem;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "triagem")
public record TriagemProperties(
        boolean indexarAoIniciar,
        Recuperacao recuperacao,
        List<String> equipesValidas) {

    public record Recuperacao(
            int topkTickets,
            int topkArtigos,
            double similaridadeMinima,
            int minimoResultadosModulo) {
    }
}
