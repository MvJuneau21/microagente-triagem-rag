package com.nexi.triagem.modelo;

/** Documento recuperado do vector store, devolvido na API para dar rastreabilidade à resposta. */
public record Fonte(String id, String tipo, String titulo, double similaridade) {
}
