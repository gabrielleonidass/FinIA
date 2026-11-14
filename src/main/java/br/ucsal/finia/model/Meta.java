package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Meta de economia: quanto a pessoa quer juntar e até quando. */
public record Meta(Long id, Long usuarioId, String nome, BigDecimal valorAlvo, BigDecimal valorGuardado,
                   LocalDate prazo) implements Json.JsonConvertivel {

    /** Quanto ainda falta guardar (nunca negativo). */
    public BigDecimal falta() {
        return valorAlvo.subtract(valorGuardado).max(BigDecimal.ZERO);
    }

    /** Percentual concluído, de 0 a 100. */
    public int percentual() {
        if (valorAlvo.signum() == 0) return 100;
        int p = valorGuardado.multiply(BigDecimal.valueOf(100)).divide(valorAlvo, 0, RoundingMode.DOWN).intValue();
        return Math.min(p, 100);
    }

    /** Meses que faltam até o prazo (mínimo 1). */
    public long mesesRestantes(LocalDate hoje) {
        long meses = ChronoUnit.MONTHS.between(hoje.withDayOfMonth(1), prazo.withDayOfMonth(1));
        return Math.max(meses, 1);
    }

    /** Quanto guardar por mês para chegar no valor até o prazo. */
    public BigDecimal valorPorMes(LocalDate hoje) {
        return falta().divide(BigDecimal.valueOf(mesesRestantes(hoje)), 2, RoundingMode.UP);
    }

    public boolean concluida() {
        return falta().signum() == 0;
    }

    @Override
    public Map<String, Object> paraJson() {
        LocalDate hoje = LocalDate.now();
        return Json.obj("id", id, "nome", nome, "valorAlvo", valorAlvo, "valorGuardado", valorGuardado,
                "prazo", prazo, "falta", falta(), "percentual", percentual(), "concluida", concluida(),
                "mesesRestantes", mesesRestantes(hoje), "valorPorMes", valorPorMes(hoje));
    }
}
