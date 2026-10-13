package com.nexi.triagem.recuperacao;

import static com.nexi.triagem.indexacao.IndexacaoService.MODULO;
import static com.nexi.triagem.indexacao.IndexacaoService.STATUS;
import static com.nexi.triagem.indexacao.IndexacaoService.TIPO;
import static com.nexi.triagem.indexacao.IndexacaoService.TIPO_ARTIGO;
import static com.nexi.triagem.indexacao.IndexacaoService.TIPO_TICKET;
import static com.nexi.triagem.indexacao.IndexacaoService.VIGENTE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.nexi.triagem.TriagemProperties;
import com.nexi.triagem.modelo.TicketEntrada;

/**
 * ETAPA 2 - RECUPERAÇÃO.
 *
 * Faz duas buscas por similaridade separadas (tickets e artigos), cada uma com seu topK e seus
 * filtros de metadata. Separar evita que os tickets, que são mais numerosos, ocupem todas as
 * vagas e deixem os procedimentos oficiais da KB de fora.
 */
@Service
public class RecuperacaoService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoService.class);

    private final VectorStore vectorStore;
    private final TriagemProperties.Recuperacao config;

    public RecuperacaoService(VectorStore vectorStore, TriagemProperties properties) {
        this.vectorStore = vectorStore;
        this.config = properties.recuperacao();
    }

    public ContextoRecuperado recuperar(TicketEntrada ticket) {
        String query = montarQuery(ticket);
        String modulo = StringUtils.hasText(ticket.modulo()) ? ticket.modulo() : null;

        List<Document> tickets = buscar(query, config.topkTickets(), filtroTickets(modulo));
        List<Document> artigos = buscar(query, config.topkArtigos(), filtroArtigos(modulo));

        // O módulo vem do formulário e o cliente erra com frequência (ex.: escolhe "app-mobile" para
        // um problema de pagamento). Se o filtro por módulo trouxer pouca coisa, busca de novo sem ele.
        boolean relaxado = false;
        if (modulo != null && tickets.size() < config.minimoResultadosModulo()) {
            log.debug("Só {} ticket(s) no módulo '{}', refazendo a busca sem o filtro de módulo", tickets.size(), modulo);
            tickets = buscar(query, config.topkTickets(), filtroTickets(null));
            artigos = buscar(query, config.topkArtigos(), filtroArtigos(null));
            relaxado = true;
        }

        return new ContextoRecuperado(query, tickets, artigos, relaxado);
    }

    /**
     * Query em linguagem natural: o próprio relato do cliente (título + descrição), no mesmo
     * formato do content indexado. Não adicionamos plataforma/plano/prefixos fixos, porque esse
     * texto entraria igual em todas as queries e só aproximaria documentos sem relação.
     */
    static String montarQuery(TicketEntrada ticket) {
        String titulo = ticket.titulo() == null ? "" : ticket.titulo().strip();
        String descricao = ticket.descricao() == null ? "" : ticket.descricao().strip();
        return (titulo + ". " + descricao).strip();
    }

    /** tipo == 'ticket_resolvido' AND status IN ('resolvido','workaround') [AND modulo == :modulo] */
    static Filter.Expression filtroTickets(String modulo) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op base = b.and(
                b.eq(TIPO, TIPO_TICKET),
                // "nao_reproduzido" e similares não têm solução: só ensinariam o LLM a chutar
                b.in(STATUS, "resolvido", "workaround"));
        return modulo == null ? base.build() : b.and(base, b.eq(MODULO, modulo)).build();
    }

    /** tipo == 'artigo_kb' AND vigente == true [AND modulo == :modulo] */
    static Filter.Expression filtroArtigos(String modulo) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op base = b.and(
                b.eq(TIPO, TIPO_ARTIGO),
                // artigos desatualizados continuam indexados (histórico), mas nunca entram no prompt
                b.eq(VIGENTE, true));
        return modulo == null ? base.build() : b.and(base, b.eq(MODULO, modulo)).build();
    }

    private List<Document> buscar(String query, int topK, Filter.Expression filtro) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(config.similaridadeMinima())
                .filterExpression(filtro)
                .build();
        List<Document> resultado = vectorStore.similaritySearch(request);
        log.debug("Busca topK={} filtro={} -> {}", topK, filtro, resultado.stream()
                .map(d -> d.getMetadata().get("origem_id") + String.format("(%.2f)", d.getScore()))
                .toList());
        return resultado;
    }
}
