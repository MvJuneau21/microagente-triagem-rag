package com.nexi.triagem.geracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import com.nexi.triagem.modelo.TicketEntrada;
import com.nexi.triagem.recuperacao.ContextoRecuperado;

class PromptTriagemTest {

    private final PromptTriagem prompt = new PromptTriagem("Equipes: {equipes}", List.of("squad-a", "suporte-n1"));

    private final TicketEntrada ticket = new TicketEntrada("T-1", "Pix pendente", "Paguei há 3h.",
            "app-mobile", "android", "enterprise");

    private final Document ticketAntigo = Document.builder().text("Pix não confirma. Paguei há 2h.")
            .metadata(Map.of("origem_id", "TCK-1115", "tipo", "ticket_resolvido", "modulo", "pagamentos",
                    "status", "resolvido", "resolucao", "Reprocessar webhooks.", "equipe", "squad-pagamentos"))
            .score(0.83).build();

    private final Document artigo = Document.builder().text("Prazos. Pix: até 10 minutos.")
            .metadata(Map.of("origem_id", "KB-004", "tipo", "artigo_kb", "atualizado_em", "2026-08-31"))
            .score(0.71).build();

    @Test
    void systemRecebeAListaDeEquipes() {
        assertThat(prompt.system()).isEqualTo("Equipes: squad-a, suporte-n1");
    }

    @Test
    void userJuntaEntradaEContextoComResolucaoEIds() {
        String user = prompt.user(ticket, new ContextoRecuperado("q", List.of(ticketAntigo), List.of(artigo), true));

        assertThat(user)
                .contains("<ticket>", "plano do cliente: enterprise", "título: Pix pendente", "</ticket>")
                .contains("<contexto>", "[KB-004]", "Pix: até 10 minutos", "[TCK-1115]",
                        "Resolução: Reprocessar webhooks.", "status=resolvido", "</contexto>")
                .contains("O módulo informado pode estar errado");
        // artigos (procedimento oficial) aparecem antes dos tickets
        assertThat(user.indexOf("[KB-004]")).isLessThan(user.indexOf("[TCK-1115]"));
    }

    @Test
    void avisaQuandoNaoHaContexto() {
        String user = prompt.user(ticket, new ContextoRecuperado("q", List.of(), List.of(), false));

        assertThat(user).contains("Nenhum artigo relevante", "Nenhum ticket parecido")
                .doesNotContain("pode estar errado");
    }
}
