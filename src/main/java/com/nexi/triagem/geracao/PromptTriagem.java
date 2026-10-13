package com.nexi.triagem.geracao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.nexi.triagem.TriagemProperties;
import com.nexi.triagem.indexacao.IndexacaoService;
import com.nexi.triagem.modelo.TicketEntrada;
import com.nexi.triagem.recuperacao.ContextoRecuperado;

/**
 * ETAPA 3a - AUMENTO.
 *
 * Monta as duas mensagens enviadas ao LLM:
 * <ul>
 *   <li>system: papel do agente + regras de uso do contexto + regras de negócio (fixas)</li>
 *   <li>user: o ticket novo (entrada) + os documentos recuperados (contexto)</li>
 * </ul>
 * Fica separado do ChatClient para poder ser testado sem chamar o modelo.
 */
@Component
public class PromptTriagem {

    private final String systemTemplate;
    private final List<String> equipesValidas;

    @Autowired
    public PromptTriagem(TriagemProperties properties) {
        this(carregar("prompts/triagem-system.md"), properties.equipesValidas());
    }

    PromptTriagem(String systemTemplate, List<String> equipesValidas) {
        this.systemTemplate = systemTemplate;
        this.equipesValidas = equipesValidas;
    }

    public String system() {
        return systemTemplate.replace("{equipes}", String.join(", ", equipesValidas));
    }

    public String user(TicketEntrada ticket, ContextoRecuperado contexto) {
        StringBuilder sb = new StringBuilder();

        sb.append("<ticket>\n");
        sb.append("id: ").append(ticket.id()).append('\n');
        sb.append("plano do cliente: ").append(valorOuNaoInformado(ticket.plano())).append('\n');
        sb.append("plataforma: ").append(valorOuNaoInformado(ticket.plataforma())).append('\n');
        sb.append("módulo informado pelo cliente: ").append(valorOuNaoInformado(ticket.modulo())).append('\n');
        sb.append("título: ").append(ticket.titulo()).append('\n');
        sb.append("descrição: ").append(ticket.descricao()).append('\n');
        sb.append("</ticket>\n\n");

        sb.append("<contexto>\n");
        if (contexto.filtroModuloRelaxado()) {
            sb.append("(Aviso: havia poucos casos no módulo informado pelo cliente; o contexto inclui outros módulos. "
                    + "O módulo informado pode estar errado.)\n\n");
        }
        sb.append("## Artigos da base de conhecimento (procedimentos oficiais)\n");
        if (contexto.artigos().isEmpty()) {
            sb.append("Nenhum artigo relevante encontrado.\n");
        }
        contexto.artigos().forEach(d -> sb.append(formatarArtigo(d)));

        sb.append("\n## Tickets parecidos já resolvidos\n");
        if (contexto.tickets().isEmpty()) {
            sb.append("Nenhum ticket parecido encontrado.\n");
        }
        contexto.tickets().forEach(d -> sb.append(formatarTicket(d)));
        sb.append("</contexto>\n\n");

        sb.append("Faça a triagem do ticket acima seguindo as regras.");
        return sb.toString();
    }

    private static String formatarArtigo(Document d) {
        Map<String, Object> m = d.getMetadata();
        return """
                [%s] (atualizado em %s, similaridade %.2f)
                %s
                """.formatted(m.get(IndexacaoService.ORIGEM_ID), m.get("atualizado_em"), d.getScore(), d.getText());
    }

    private static String formatarTicket(Document d) {
        Map<String, Object> m = d.getMetadata();
        return """
                [%s] (similaridade %.2f) módulo=%s, plataforma=%s, categoria=%s, severidade=%s, equipe=%s, status=%s, resolvido em %s
                Problema: %s
                Resolução: %s
                """.formatted(
                m.get(IndexacaoService.ORIGEM_ID), d.getScore(), m.get(IndexacaoService.MODULO), m.get("plataforma"),
                m.get("categoria"), m.get("severidade"), m.get("equipe"), m.get(IndexacaoService.STATUS),
                m.get("resolvido_em"), d.getText(), m.get("resolucao"));
    }

    private static String valorOuNaoInformado(String valor) {
        return StringUtils.hasText(valor) ? valor : "não informado";
    }

    private static String carregar(String caminho) {
        try {
            return new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + caminho, e);
        }
    }
}
