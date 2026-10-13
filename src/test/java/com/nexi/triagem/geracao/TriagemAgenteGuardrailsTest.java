package com.nexi.triagem.geracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.nexi.triagem.modelo.Categoria;
import com.nexi.triagem.modelo.Severidade;
import com.nexi.triagem.modelo.TicketEntrada;
import com.nexi.triagem.modelo.TriagemLlm;

class TriagemAgenteGuardrailsTest {

    private final List<String> equipes = List.of("squad-pagamentos", "suporte-n1");
    private final Set<String> recuperados = Set.of("TCK-1115", "KB-004");
    private final TicketEntrada ticketPro = new TicketEntrada("T", "t", "d", "pagamentos", "web", "pro");

    @Test
    void respostaValidaPassaSemAvisos() {
        List<String> avisos = new ArrayList<>();
        TriagemLlm r = TriagemAgente.aplicarGuardrails(
                llm(Severidade.ALTA, "squad-pagamentos", List.of("TCK-1115", "KB-004")), ticketPro, recuperados, equipes, avisos);

        assertThat(avisos).isEmpty();
        assertThat(r.precisaRevisaoHumana()).isFalse();
        assertThat(r.fontesUsadas()).containsExactly("TCK-1115", "KB-004");
    }

    @Test
    void removeFonteInventadaEMandaParaRevisao() {
        List<String> avisos = new ArrayList<>();
        TriagemLlm r = TriagemAgente.aplicarGuardrails(
                llm(Severidade.MEDIA, "squad-pagamentos", List.of("TCK-1115", "TCK-9999")), ticketPro, recuperados, equipes, avisos);

        assertThat(r.fontesUsadas()).containsExactly("TCK-1115");
        assertThat(r.precisaRevisaoHumana()).isTrue();
        assertThat(avisos).anyMatch(a -> a.contains("TCK-9999"));
    }

    @Test
    void equipeInexistenteViraSuporteN1() {
        List<String> avisos = new ArrayList<>();
        TriagemLlm r = TriagemAgente.aplicarGuardrails(
                llm(Severidade.MEDIA, "time-financeiro", List.of("KB-004")), ticketPro, recuperados, equipes, avisos);

        assertThat(r.equipe()).isEqualTo("suporte-n1");
        assertThat(r.precisaRevisaoHumana()).isTrue();
    }

    @Test
    void enterpriseGraveSempreVaiParaHumanoMesmoSeOLlmEsquecer() {
        TicketEntrada enterprise = new TicketEntrada("T", "t", "d", "pagamentos", "web", "Enterprise");
        TriagemLlm r = TriagemAgente.aplicarGuardrails(
                llm(Severidade.CRITICA, "squad-pagamentos", List.of("KB-004")), enterprise, recuperados, equipes, new ArrayList<>());

        assertThat(r.precisaRevisaoHumana()).isTrue();
    }

    private static TriagemLlm llm(Severidade sev, String equipe, List<String> fontes) {
        return new TriagemLlm(Categoria.INCIDENTE, sev, equipe, "j", "resposta", fontes, false);
    }
}
