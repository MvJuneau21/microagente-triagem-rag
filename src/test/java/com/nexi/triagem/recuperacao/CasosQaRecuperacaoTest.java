package com.nexi.triagem.recuperacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import com.nexi.triagem.TriagemProperties;
import com.nexi.triagem.modelo.TicketEntrada;

/**
 * Casos de teste gerados pela skill casos-de-teste (Desktop/entrega-skill-casos-de-teste/aplicacao-microagente-rag).
 * Usa a configuração real do application.yml: topK 4/2, threshold 0.70, mínimo por módulo 1.
 */
class CasosQaRecuperacaoTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final TriagemProperties props = new TriagemProperties(false,
            new TriagemProperties.Recuperacao(4, 2, 0.70, 1), List.of("suporte-n1"));
    private final RecuperacaoService service = new RecuperacaoService(vectorStore, props);

    @Test
    void ct05_umTicketNoModuloNaoRelaxaOFiltro() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("TCK-1012")))   // tickets no módulo: 1 (= mínimo)
                .thenReturn(List.of(doc("KB-001")));    // artigos no módulo

        ContextoRecuperado ctx = service.recuperar(ticket("autenticacao"));

        verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
        assertThat(ctx.filtroModuloRelaxado()).isFalse();
    }

    @Test
    void ct06_zeroTicketsNoModuloRelaxaOFiltro() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of())                   // tickets no módulo: 0
                .thenReturn(List.of())                   // artigos no módulo
                .thenReturn(List.of(doc("TCK-1115")))    // tickets sem módulo
                .thenReturn(List.of(doc("KB-004")));     // artigos sem módulo

        ContextoRecuperado ctx = service.recuperar(ticket("app-mobile"));

        assertThat(ctx.filtroModuloRelaxado()).isTrue();
        assertThat(ctx.idsDeOrigem()).containsExactlyInAnyOrder("TCK-1115", "KB-004");
    }

    @Test
    void ct07_filtroRelaxadoMantemStatusEVigente() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.recuperar(ticket("relatorios"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, times(4)).similaritySearch(captor.capture());
        String ticketsSemModulo = captor.getAllValues().get(2).getFilterExpression().toString();
        String artigosSemModulo = captor.getAllValues().get(3).getFilterExpression().toString();
        assertThat(ticketsSemModulo).doesNotContain("modulo").contains("resolvido", "workaround");
        assertThat(artigosSemModulo).doesNotContain("modulo").contains("vigente", "true");
    }

    @Test
    void ct09_ct10_buscasUsamThreshold070ETopK4e2() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("X")));

        service.recuperar(ticket("autenticacao"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, times(2)).similaritySearch(captor.capture());
        assertThat(captor.getAllValues().get(0).getTopK()).isEqualTo(4);
        assertThat(captor.getAllValues().get(1).getTopK()).isEqualTo(2);
        assertThat(captor.getAllValues()).allMatch(r -> r.getSimilarityThreshold() == 0.70);
    }

    @Test
    void duvidaD5_moduloVazioBuscaSemFiltroENaoMarcaRelaxado() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ContextoRecuperado ctx = service.recuperar(ticket("  "));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, times(2)).similaritySearch(captor.capture());
        assertThat(captor.getAllValues()).allMatch(r -> !r.getFilterExpression().toString().contains("modulo"));
        assertThat(ctx.filtroModuloRelaxado()).isFalse();
    }

    private static TicketEntrada ticket(String modulo) {
        return new TicketEntrada("TCK-3000", "Título", "Descrição", modulo, "android", "pro");
    }

    private static Document doc(String origemId) {
        return Document.builder().text("x").metadata(Map.of("origem_id", origemId)).score(0.8).build();
    }
}
