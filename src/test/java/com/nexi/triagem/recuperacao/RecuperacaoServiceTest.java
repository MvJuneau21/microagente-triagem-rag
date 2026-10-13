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

class RecuperacaoServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final TriagemProperties props = new TriagemProperties(false,
            new TriagemProperties.Recuperacao(4, 2, 0.6, 2), List.of("suporte-n1"));
    private final RecuperacaoService service = new RecuperacaoService(vectorStore, props);

    private final TicketEntrada ticket = new TicketEntrada("T-1", " Pix pendente ", "Paguei e não confirmou.",
            "pagamentos", "android", "pro");

    @Test
    void queryEhORelatoDoCliente() {
        assertThat(RecuperacaoService.montarQuery(ticket)).isEqualTo("Pix pendente. Paguei e não confirmou.");
    }

    @Test
    void filtrosDeTicketsEArtigos() {
        assertThat(RecuperacaoService.filtroTickets("pagamentos").toString())
                .contains("tipo", "ticket_resolvido", "status", "resolvido", "workaround", "modulo", "pagamentos");
        assertThat(RecuperacaoService.filtroTickets(null).toString()).doesNotContain("modulo");
        assertThat(RecuperacaoService.filtroArtigos("pagamentos").toString())
                .contains("artigo_kb", "vigente", "true", "pagamentos");
    }

    @Test
    void usaTopKEThresholdConfiguradosEFiltroDeModulo() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc("A"), doc("B")));

        ContextoRecuperado ctx = service.recuperar(ticket);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, times(2)).similaritySearch(captor.capture());
        SearchRequest tickets = captor.getAllValues().get(0);
        SearchRequest artigos = captor.getAllValues().get(1);
        assertThat(tickets.getTopK()).isEqualTo(4);
        assertThat(artigos.getTopK()).isEqualTo(2);
        assertThat(tickets.getSimilarityThreshold()).isEqualTo(0.6);
        assertThat(tickets.getFilterExpression().toString()).contains("pagamentos");
        assertThat(ctx.filtroModuloRelaxado()).isFalse();
    }

    @Test
    void relaxaFiltroDeModuloQuandoVemPoucoResultado() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(doc("A")))                       // tickets com módulo: só 1 (mínimo é 2)
                .thenReturn(List.of())                               // artigos com módulo
                .thenReturn(List.of(doc("A"), doc("B"), doc("C")))   // tickets sem módulo
                .thenReturn(List.of(doc("KB")));                     // artigos sem módulo

        ContextoRecuperado ctx = service.recuperar(ticket);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, times(4)).similaritySearch(captor.capture());
        assertThat(captor.getAllValues().get(2).getFilterExpression().toString()).doesNotContain("modulo");
        assertThat(ctx.filtroModuloRelaxado()).isTrue();
        assertThat(ctx.tickets()).hasSize(3);
        assertThat(ctx.idsDeOrigem()).containsExactlyInAnyOrder("A", "B", "C", "KB");
    }

    private static Document doc(String origemId) {
        return Document.builder().text("x").metadata(Map.of("origem_id", origemId)).score(0.8).build();
    }
}
