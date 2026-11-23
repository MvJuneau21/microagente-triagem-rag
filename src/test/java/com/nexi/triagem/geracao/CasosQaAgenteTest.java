package com.nexi.triagem.geracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import com.nexi.triagem.TriagemProperties;
import com.nexi.triagem.modelo.Categoria;
import com.nexi.triagem.modelo.Severidade;
import com.nexi.triagem.modelo.TicketEntrada;
import com.nexi.triagem.modelo.TriagemLlm;
import com.nexi.triagem.modelo.TriagemResultado;
import com.nexi.triagem.recuperacao.ContextoRecuperado;
import com.nexi.triagem.recuperacao.RecuperacaoService;

/**
 * Casos de teste gerados pela skill casos-de-teste (Desktop/entrega-skill-casos-de-teste/aplicacao-microagente-rag).
 * LLM simulado: os guardrails são testados com respostas montadas à mão.
 */
class CasosQaAgenteTest {

    private final List<String> equipes = List.of("squad-identidade", "squad-pagamentos", "squad-engajamento",
            "squad-dados", "squad-plataforma", "suporte-n1");
    private final Set<String> recuperados = Set.of("KB-001", "TCK-1012");

    @Test
    void ct08_contextoVazioNaoChamaOLlmEVaiParaRevisao() {
        RecuperacaoService recuperacao = mock(RecuperacaoService.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chat = mock(ChatClient.class);
        when(builder.build()).thenReturn(chat);
        // módulo informado, nada encontrado nem com o filtro relaxado (caso do SAP)
        when(recuperacao.recuperar(any())).thenReturn(new ContextoRecuperado("q", List.of(), List.of(), true));
        TriagemProperties props = new TriagemProperties(false,
                new TriagemProperties.Recuperacao(4, 2, 0.70, 1), equipes);
        TriagemAgente agente = new TriagemAgente(recuperacao, mock(PromptTriagem.class), builder, props);

        TriagemResultado r = agente.triar(ticket("enterprise"));

        verifyNoInteractions(chat);
        assertThat(r.fontesRecuperadas()).isEmpty();
        assertThat(r.triagem().precisaRevisaoHumana()).isTrue();
        assertThat(r.triagem().equipe()).isEqualTo("suporte-n1");
        assertThat(r.triagem().fontesUsadas()).isEmpty();
        // dúvida D4: com o filtro relaxado e nada encontrado, o campo vem true
        assertThat(r.filtroModuloRelaxado()).isTrue();
    }

    @Test
    void ct11_fonteInventadaERemovidaMantendoAValida() {
        List<String> avisos = new ArrayList<>();
        TriagemLlm r = TriagemAgente.aplicarGuardrails(
                llm(Severidade.MEDIA, "squad-identidade", List.of("KB-001", "TCK-9999")), ticket("pro"), recuperados, equipes, avisos);

        assertThat(r.fontesUsadas()).containsExactly("KB-001");
        assertThat(avisos).anyMatch(a -> a.contains("TCK-9999"));
        assertThat(r.precisaRevisaoHumana()).isTrue();
    }

    @Test
    void ct12_semNenhumaFonteValidaVaiParaRevisao() {
        List<String> avisos = new ArrayList<>();
        TriagemLlm r = TriagemAgente.aplicarGuardrails(
                llm(Severidade.MEDIA, "squad-identidade", List.of()), ticket("pro"), recuperados, equipes, avisos);

        assertThat(r.fontesUsadas()).isEmpty();
        assertThat(r.precisaRevisaoHumana()).isTrue();
    }

    @Test
    void ct13_equipeInexistenteViraSuporteN1ComAviso() {
        List<String> avisos = new ArrayList<>();
        TriagemLlm r = TriagemAgente.aplicarGuardrails(
                llm(Severidade.MEDIA, "squad-financeiro", List.of("KB-001")), ticket("pro"), recuperados, equipes, avisos);

        assertThat(r.equipe()).isEqualTo("suporte-n1");
        assertThat(avisos).anyMatch(a -> a.contains("squad-financeiro"));
        assertThat(r.precisaRevisaoHumana()).isTrue();
    }

    @Test
    void ct14_enterpriseComSeveridadeAltaVaiParaRevisao() {
        TriagemLlm r = TriagemAgente.aplicarGuardrails(
                llm(Severidade.ALTA, "squad-identidade", List.of("KB-001")), ticket("enterprise"), recuperados, equipes, new ArrayList<>());

        assertThat(r.precisaRevisaoHumana()).isTrue();
    }

    @Test
    void ct15_enterpriseComSeveridadeMediaNaoEForcado() {
        List<String> avisos = new ArrayList<>();
        TriagemLlm r = TriagemAgente.aplicarGuardrails(
                llm(Severidade.MEDIA, "squad-identidade", List.of("KB-001")), ticket("enterprise"), recuperados, equipes, avisos);

        assertThat(r.precisaRevisaoHumana()).isFalse();
        assertThat(avisos).isEmpty();
    }

    @Test
    void ct16_planoProComSeveridadeCriticaNaoEForcadoPelaRegraDeSla() {
        TriagemLlm r = TriagemAgente.aplicarGuardrails(
                llm(Severidade.CRITICA, "squad-pagamentos", List.of("KB-001")), ticket("pro"), recuperados, equipes, new ArrayList<>());

        assertThat(r.precisaRevisaoHumana()).isFalse();
    }

    private static TicketEntrada ticket(String plano) {
        return new TicketEntrada("TCK-3000", "Título", "Descrição", "autenticacao", "web", plano);
    }

    private static TriagemLlm llm(Severidade sev, String equipe, List<String> fontes) {
        return new TriagemLlm(Categoria.BUG, sev, equipe, "j", "resposta", fontes, false);
    }
}
