package com.nexi.triagem.indexacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import com.nexi.triagem.modelo.ArtigoKb;
import com.nexi.triagem.modelo.TicketResolvido;

class IndexacaoServiceTest {

    private final TicketResolvido ticket = new TicketResolvido("TCK-1012", "SMS não chega",
            "O código nunca chega.", "Desbloqueio no provedor.", "autenticacao", "android",
            "BUG", "MEDIA", "squad-identidade", "resolvido", "2026-08-14");

    @Test
    void ticketEmbedaSoOProblemaEGuardaAResolucaoNoMetadata() {
        Document doc = IndexacaoService.paraDocumento(ticket);

        assertThat(doc.getText()).isEqualTo("SMS não chega. O código nunca chega.");
        assertThat(doc.getText()).doesNotContain("Desbloqueio");
        assertThat(doc.getMetadata())
                .containsEntry("tipo", "ticket_resolvido")
                .containsEntry("origem_id", "TCK-1012")
                .containsEntry("modulo", "autenticacao")
                .containsEntry("status", "resolvido")
                .containsEntry("resolucao", "Desbloqueio no provedor.");
    }

    @Test
    void idEhDeterministicoParaReindexarSemDuplicar() {
        assertThat(IndexacaoService.paraDocumento(ticket).getId())
                .isEqualTo(IndexacaoService.paraDocumento(ticket).getId())
                .isEqualTo(IndexacaoService.idDeterministico("TCK-1012"));
    }

    @Test
    void artigoGuardaVigenciaComoBooleano() {
        Document doc = IndexacaoService.paraDocumento(
                new ArtigoKb("KB-005", "Prazos antigos", "Pix em 24h.", "pagamentos", false, "2025-11-10"));

        assertThat(doc.getMetadata()).containsEntry("tipo", "artigo_kb").containsEntry("vigente", false);
    }
}
