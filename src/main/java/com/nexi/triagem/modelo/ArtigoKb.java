package com.nexi.triagem.modelo;

/** Artigo da base de conhecimento (procedimentos e políticas oficiais). */
public record ArtigoKb(
        String id,
        String titulo,
        String conteudo,
        String modulo,
        boolean vigente,
        String atualizadoEm) {
}
