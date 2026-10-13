package com.nexi.triagem.modelo;

import java.util.List;

/**
 * Formato estruturado que o LLM deve devolver. O Spring AI gera um JSON Schema a partir
 * deste record (BeanOutputConverter) e converte a resposta de volta para ele.
 */
public record TriagemLlm(
        Categoria categoria,
        Severidade severidade,
        String equipe,
        String justificativa,
        String respostaSugerida,
        List<String> fontesUsadas,
        boolean precisaRevisaoHumana) {
}
