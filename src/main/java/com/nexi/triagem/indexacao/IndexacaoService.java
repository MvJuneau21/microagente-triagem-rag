package com.nexi.triagem.indexacao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexi.triagem.modelo.ArtigoKb;
import com.nexi.triagem.modelo.TicketResolvido;

/**
 * ETAPA 1 - INDEXAÇÃO.
 *
 * Converte tickets resolvidos e artigos da base de conhecimento em {@link Document}s e grava no
 * pgvector. Cada linha da tabela vector_store fica assim:
 *
 * <pre>
 *  id        uuid    -> derivado do ID de origem (TCK-1012, KB-001), para reindexar sem duplicar
 *  content   text    -> o texto que vira embedding
 *  metadata  jsonb   -> campos usados nos filtros da busca e no prompt
 *  embedding vector  -> calculado pelo EmbeddingModel (gemini-embedding-001) dentro do vectorStore.add()
 * </pre>
 */
@Service
public class IndexacaoService {

    // Nomes das chaves de metadata, compartilhados com a etapa de recuperação
    public static final String TIPO = "tipo";
    public static final String TIPO_TICKET = "ticket_resolvido";
    public static final String TIPO_ARTIGO = "artigo_kb";
    public static final String ORIGEM_ID = "origem_id";
    public static final String TITULO = "titulo";
    public static final String MODULO = "modulo";
    public static final String STATUS = "status";
    public static final String VIGENTE = "vigente";

    private static final Logger log = LoggerFactory.getLogger(IndexacaoService.class);

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public IndexacaoService(VectorStore vectorStore, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    public int indexarTudo() {
        List<Document> documentos = new ArrayList<>();
        ler("dados/tickets-resolvidos.json", new TypeReference<List<TicketResolvido>>() { })
                .forEach(t -> documentos.add(paraDocumento(t)));
        ler("dados/artigos-kb.json", new TypeReference<List<ArtigoKb>>() { })
                .forEach(a -> documentos.add(paraDocumento(a)));

        // add() chama o EmbeddingModel para cada content e faz upsert (id, content, metadata, embedding).
        // Como o id é determinístico, reindexar atualiza a linha em vez de criar duplicata.
        vectorStore.add(documentos);
        log.info("Indexados {} documentos no pgvector", documentos.size());
        return documentos.size();
    }

    /**
     * Ticket resolvido -> Document.
     *
     * O content contém só o PROBLEMA (título + descrição), porque a query de busca também é a
     * descrição de um problema: comparamos "problema com problema". A resolução vai no metadata e
     * é entregue ao LLM na etapa de aumento, sem "diluir" o embedding.
     */
    static Document paraDocumento(TicketResolvido t) {
        String content = t.titulo() + ". " + t.descricao();
        Map<String, Object> metadata = Map.ofEntries(
                Map.entry(TIPO, TIPO_TICKET),          // separa tickets de artigos na busca
                Map.entry(ORIGEM_ID, t.id()),          // permite citar a fonte e validar citações
                Map.entry(TITULO, t.titulo()),
                Map.entry(MODULO, t.modulo()),         // filtro: restringe ao módulo do problema
                Map.entry(STATUS, t.status()),         // filtro: só soluções que funcionaram
                Map.entry("plataforma", t.plataforma()), // contexto para o LLM (não é filtro, ver README)
                Map.entry("categoria", t.categoria()),   // exemplos de classificação para o LLM
                Map.entry("severidade", t.severidade()),
                Map.entry("equipe", t.equipe()),
                Map.entry("resolvido_em", t.resolvidoEm()), // o LLM pode preferir soluções recentes
                Map.entry("resolucao", t.resolucao()));
        return new Document(idDeterministico(t.id()), content, metadata);
    }

    /**
     * Artigo da KB -> Document. Um documento por artigo: os artigos são curtos (< 200 tokens) e
     * quebrá-los separaria passos do procedimento das condições em que se aplicam.
     */
    static Document paraDocumento(ArtigoKb a) {
        String content = a.titulo() + ". " + a.conteudo();
        Map<String, Object> metadata = Map.of(
                TIPO, TIPO_ARTIGO,
                ORIGEM_ID, a.id(),
                TITULO, a.titulo(),
                MODULO, a.modulo(),         // filtro: mesmo critério de módulo dos tickets
                VIGENTE, a.vigente(),       // filtro: artigos antigos ficam indexados mas não são usados
                "atualizado_em", a.atualizadoEm());
        return new Document(idDeterministico(a.id()), content, metadata);
    }

    static String idDeterministico(String origemId) {
        return UUID.nameUUIDFromBytes(origemId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private <T> List<T> ler(String caminho, TypeReference<List<T>> tipo) {
        try (InputStream in = new ClassPathResource(caminho).getInputStream()) {
            return objectMapper.readValue(in, tipo);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + caminho, e);
        }
    }
}
