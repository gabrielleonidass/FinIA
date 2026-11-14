package br.ucsal.finia.controller;

import br.ucsal.finia.infra.ApiException;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Lê o parâmetro ?mes=AAAA-MM (se vier vazio, usa o mês atual). */
final class Meses {

    private Meses() {
    }

    static YearMonth ler(String valor) {
        if (valor == null || valor.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(valor);
        } catch (DateTimeParseException e) {
            throw ApiException.requisicaoInvalida("Mês inválido (use AAAA-MM).");
        }
    }
}
