package com.nexi.triagem.geracao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.nexi.triagem.TriagemProperties;
import com.nexi.triagem.indexacao.IndexacaoService;
import com.nexi.triagem.modelo.Categoria;
import com.nexi.triagem.modelo.Fonte;
import com.nexi.triagem.modelo.Severidade;
import com.nexi.triagem.modelo.TicketEntrada;
import com.nexi.triagem.modelo.TriagemLlm;
import com.nexi.triagem.modelo.TriagemResultado;
import com.nexi.triagem.recuperacao.ContextoRecuperado;
import com.nexi.triagem.recuperacao.RecuperacaoService;

/**
 * O microagente: recupera (etapa 2) -> aumenta o prompt (etapa 3a) -> gera com o LLM (etapa 3b)
 * -> aplica guardrails em código -> devolve a resposta com as fontes.
 */
@Service
public class TriagemAgente {

    private static final Logger log = LoggerFactory.getLogger(TriagemAgente.class);

    private final RecuperacaoService recuperacao;
    private final PromptTriagem prompt;
    private final ChatClient chatClient;
    private final List<String> equipesValidas;

    public TriagemAgente(RecuperacaoService recuperacao, PromptTriagem prompt,
                         ChatClient.Builder chatClientBuilder, TriagemProperties properties) {
        this.recuperacao = recuperacao;
        this.prompt = prompt;
        this.chatClient = chatClientBuilder.build();
        this.equipesValidas = properties.equipesValidas();
    }

    public TriagemResultado triar(TicketEntrada ticket) {
        ContextoRecuperado contexto = recuperacao.recuperar(ticket);
        List<Fonte> fontes = fontes(contexto);

        // Sem contexto não há o que "aumentar": chamar o LLM só produziria uma resposta genérica
        // (ou inventada). Economiza a chamada e manda direto para um humano.
        if (contexto.vazio()) {
            return new TriagemResultado(ticket.id(), semContexto(), fontes, contexto.filtroModuloRelaxado(),
                    List.of("Nenhum documento acima da similaridade mínima; LLM não foi chamado."));
        }

        String system = prompt.system();
        String user = prompt.user(ticket, contexto);
        log.debug("Prompt enviado ao LLM:\n--- SYSTEM ---\n{}\n--- USER ---\n{}", system, user);

        // ETAPA 3b - GERAÇÃO. entity() acrescenta ao prompt o JSON Schema de TriagemLlm e converte a resposta.
        TriagemLlm resposta = chatClient.prompt()
                .system(system)
                .user(user)
                .call()
                .entity(TriagemLlm.class);

        List<String> avisos = new ArrayList<>();
        if (contexto.filtroModuloRelaxado()) {
            avisos.add("Poucos casos no módulo '" + ticket.modulo() + "'; busca refeita em todos os módulos.");
        }
        TriagemLlm validada = aplicarGuardrails(resposta, ticket, contexto.idsDeOrigem(), equipesValidas, avisos);
        return new TriagemResultado(ticket.id(), validada, fontes, contexto.filtroModuloRelaxado(), List.copyOf(avisos));
    }

    /**
     * O prompt pede, mas não garante. Tudo que é regra dura é conferido aqui, em código.
     */
    static TriagemLlm aplicarGuardrails(TriagemLlm r, TicketEntrada ticket, Set<String> idsRecuperados,
                                        List<String> equipesValidas, List<String> avisos) {
        boolean revisao = r.precisaRevisaoHumana();

        // 1. Citação de fonte que não estava no contexto = alucinação.
        List<String> citadas = r.fontesUsadas() == null ? List.of() : r.fontesUsadas();
        List<String> validas = citadas.stream().filter(idsRecuperados::contains).toList();
        if (validas.size() < citadas.size()) {
            avisos.add("LLM citou fontes fora do contexto (removidas): "
                    + citadas.stream().filter(id -> !idsRecuperados.contains(id)).toList());
            revisao = true;
        }
        if (validas.isEmpty()) {
            avisos.add("Resposta não se apoiou em nenhuma fonte recuperada.");
            revisao = true;
        }

        // 2. Equipe tem que existir, senão o ticket vai para uma fila que ninguém olha.
        String equipe = r.equipe();
        if (!equipesValidas.contains(equipe)) {
            avisos.add("Equipe inválida '" + equipe + "' substituída por suporte-n1.");
            equipe = "suporte-n1";
            revisao = true;
        }

        // 3. SLA enterprise: não depende do LLM ter lido a regra.
        boolean grave = r.severidade() == Severidade.ALTA || r.severidade() == Severidade.CRITICA;
        if ("enterprise".equalsIgnoreCase(ticket.plano()) && grave) {
            revisao = true;
        }

        return new TriagemLlm(r.categoria(), r.severidade(), equipe, r.justificativa(), r.respostaSugerida(),
                validas, revisao);
    }

    private static TriagemLlm semContexto() {
        return new TriagemLlm(Categoria.DUVIDA, Severidade.MEDIA, "suporte-n1",
                "Nenhum caso parecido ou artigo encontrado na base.",
                "Olá! Recebemos sua solicitação e um especialista vai analisá-la. "
                        + "Se puder, envie prints do erro e os passos que levaram a ele.",
                List.of(), true);
    }

    private static List<Fonte> fontes(ContextoRecuperado contexto) {
        List<Fonte> fontes = new ArrayList<>();
        contexto.artigos().forEach(d -> fontes.add(fonte(d)));
        contexto.tickets().forEach(d -> fontes.add(fonte(d)));
        return fontes;
    }

    private static Fonte fonte(Document d) {
        var m = d.getMetadata();
        return new Fonte(String.valueOf(m.get(IndexacaoService.ORIGEM_ID)), String.valueOf(m.get(IndexacaoService.TIPO)),
                String.valueOf(m.get(IndexacaoService.TITULO)), d.getScore() == null ? 0 : d.getScore());
    }
}
