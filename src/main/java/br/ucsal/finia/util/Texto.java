package br.ucsal.finia.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.util.Locale;

/** Funções auxiliares de texto e formatação. */
public final class Texto {

    /** Prefixos de adquirentes e conectivos que não identificam o estabelecimento. */
    private static final java.util.Set<String> IGNORAR = java.util.Set.of("PAG", "MP", "DO", "DA", "DE", "DOS", "DAS", "E");

    private static final Locale BR = Locale.forLanguageTag("pt-BR");

    private Texto() {
    }

    /** Maiúsculas, sem acentos e apenas letras, dígitos e espaços. */
    public static String normalizar(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toUpperCase().replaceAll("[^A-Z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Padrão do estabelecimento: as duas primeiras palavras significativas
     * da descrição (sem números), ex.: "UBER *TRIP 8821" -> "UBER TRIP".
     */
    public static String padraoEstabelecimento(String descricao) {
        String[] palavras = normalizar(descricao).split(" ");
        StringBuilder sb = new StringBuilder();
        int usadas = 0;
        for (String p : palavras) {
            if (p.length() < 2 || p.matches(".*\\d.*") || IGNORAR.contains(p)) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(p);
            if (++usadas == 2) break;
        }
        return sb.length() == 0 ? normalizar(descricao) : sb.toString();
    }

    public static String moeda(BigDecimal v) {
        return NumberFormat.getCurrencyInstance(BR).format(v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP))
                .replace(' ', ' ');
    }
}
