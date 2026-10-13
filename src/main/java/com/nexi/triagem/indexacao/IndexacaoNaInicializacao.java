package com.nexi.triagem.indexacao;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "triagem.indexar-ao-iniciar", havingValue = "true")
class IndexacaoNaInicializacao implements ApplicationRunner {

    private final IndexacaoService indexacaoService;

    IndexacaoNaInicializacao(IndexacaoService indexacaoService) {
        this.indexacaoService = indexacaoService;
    }

    @Override
    public void run(ApplicationArguments args) {
        indexacaoService.indexarTudo();
    }
}
