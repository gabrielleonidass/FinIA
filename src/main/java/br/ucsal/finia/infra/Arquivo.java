package br.ucsal.finia.infra;

/** Resposta binária para download (ex.: relatório XLSX). */
public record Arquivo(byte[] conteudo, String contentType, String nomeArquivo) {
}
