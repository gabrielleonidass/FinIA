package br.ucsal.finia.util;

import br.ucsal.finia.infra.ApiException;

import java.time.LocalDate;
import java.time.YearMonth;

/** Período de consulta (RF07: mês atual, últimos 3 meses, últimos 12 meses ou customizado). */
public record Periodo(LocalDate inicio, LocalDate fim, String rotulo) {

    public static Periodo de(String tipo, String inicio, String fim) {
        LocalDate hoje = LocalDate.now();
        YearMonth mes = YearMonth.from(hoje);
        return switch (tipo == null ? "mes" : tipo) {
            case "3m" -> new Periodo(mes.minusMonths(2).atDay(1), mes.atEndOfMonth(), "Últimos 3 meses");
            case "12m" -> new Periodo(mes.minusMonths(11).atDay(1), mes.atEndOfMonth(), "Últimos 12 meses");
            case "custom" -> {
                try {
                    LocalDate i = LocalDate.parse(inicio), f = LocalDate.parse(fim);
                    if (f.isBefore(i)) throw ApiException.requisicaoInvalida("A data final deve ser posterior à inicial.");
                    yield new Periodo(i, f, "Período customizado");
                } catch (RuntimeException e) {
                    if (e instanceof ApiException a) throw a;
                    throw ApiException.requisicaoInvalida("Informe datas válidas para o período customizado.");
                }
            }
            default -> new Periodo(mes.atDay(1), mes.atEndOfMonth(), "Mês atual");
        };
    }

    /** Período imediatamente anterior, de mesma duração em meses (comparativo do RF11). */
    public Periodo anterior() {
        YearMonth ini = YearMonth.from(inicio), f = YearMonth.from(fim);
        long meses = ini.until(f, java.time.temporal.ChronoUnit.MONTHS) + 1;
        return new Periodo(ini.minusMonths(meses).atDay(1), f.minusMonths(meses).atEndOfMonth(), "Período anterior");
    }
}
