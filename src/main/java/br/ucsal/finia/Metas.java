package br.ucsal.finia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Tela Metas (RF05). */
public class Metas {

    static void rotas(Web web) {
        web.get("/api/metas", p -> listar(p.usuarioId()));
        web.post("/api/metas", p -> criar(p.usuarioId(), p.texto("nome"), p.decimal("valorAlvo"), p.data("prazo"),
                p.decimal("taxaRendimento")));
        web.post("/api/metas/{id}/guardar", p -> guardar(p.usuarioId(), p.id(), p.decimal("valor")));
        web.delete("/api/metas/{id}", p -> {
            if (Banco.executar("DELETE FROM meta WHERE id = ? AND usuario_id = ?", p.id(), p.usuarioId()) == 0)
                throw new Web.Erro(404, "Meta não encontrada.");
            return null;
        });
    }

    /** taxaRendimento: rendimento esperado em % ao mês, digitado pelo usuário (pode ser null). */
    static Map<String, Object> criar(long usuarioId, String nome, BigDecimal valorAlvo, LocalDate prazo,
                                     BigDecimal taxaRendimento) {
        if (nome == null) throw new Web.Erro(400, "Dê um nome para a meta.");
        if (valorAlvo == null || valorAlvo.signum() <= 0) throw new Web.Erro(400, "O valor da meta deve ser maior que zero.");
        if (prazo == null || !prazo.isAfter(LocalDate.now())) throw new Web.Erro(400, "O prazo precisa ser uma data futura.");
        if (taxaRendimento != null && (taxaRendimento.signum() < 0 || taxaRendimento.compareTo(BigDecimal.TEN) > 0))
            throw new Web.Erro(400, "O rendimento deve ficar entre 0% e 10% ao mês.");

        long id = Banco.inserir("""
                INSERT INTO meta (usuario_id, nome, valor_alvo, valor_guardado, prazo, taxa_rendimento)
                VALUES (?, ?, ?, 0, ?, ?)""", usuarioId, nome, valorAlvo, prazo, taxaRendimento);
        return buscar(usuarioId, id);
    }

    /** Soma um valor ao que já foi guardado. */
    static Map<String, Object> guardar(long usuarioId, long id, BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) throw new Web.Erro(400, "Informe um valor maior que zero.");
        if (Banco.executar("UPDATE meta SET valor_guardado = valor_guardado + ? WHERE id = ? AND usuario_id = ?",
                valor, id, usuarioId) == 0) throw new Web.Erro(404, "Meta não encontrada.");
        return buscar(usuarioId, id);
    }

    static List<Map<String, Object>> listar(long usuarioId) {
        return Banco.linhas("SELECT * FROM meta WHERE usuario_id = ? ORDER BY prazo", usuarioId)
                .stream().map(Metas::paraTela).toList();
    }

    private static Map<String, Object> buscar(long usuarioId, long id) {
        return paraTela(Banco.linha("SELECT * FROM meta WHERE usuario_id = ? AND id = ?", usuarioId, id));
    }

    /** A linha do banco mais os cálculos que a tela mostra. */
    private static Map<String, Object> paraTela(Map<String, Object> m) {
        BigDecimal alvo = (BigDecimal) m.get("valor_alvo"), guardado = (BigDecimal) m.get("valor_guardado");
        BigDecimal taxa = (BigDecimal) m.get("taxa_rendimento");
        LocalDate prazo = (LocalDate) m.get("prazo"), hoje = LocalDate.now();
        BigDecimal falta = alvo.subtract(guardado).max(BigDecimal.ZERO);
        return Web.obj("id", m.get("id"), "nome", m.get("nome"), "valorAlvo", alvo, "valorGuardado", guardado,
                "prazo", prazo, "falta", falta, "percentual", percentual(alvo, guardado), "concluida", falta.signum() == 0,
                "mesesRestantes", mesesRestantes(prazo, hoje), "valorPorMes", valorPorMes(alvo, guardado, prazo, taxa, hoje),
                "taxaRendimento", taxa);
    }

    /** Percentual guardado, de 0 a 100. */
    static int percentual(BigDecimal alvo, BigDecimal guardado) {
        return Math.min(guardado.multiply(BigDecimal.valueOf(100)).divide(alvo, 0, RoundingMode.DOWN).intValue(), 100);
    }

    /** Meses até o prazo (no mínimo 1). */
    static long mesesRestantes(LocalDate prazo, LocalDate hoje) {
        return Math.max(ChronoUnit.MONTHS.between(hoje.withDayOfMonth(1), prazo.withDayOfMonth(1)), 1);
    }

    /**
     * Quanto guardar por mês para chegar no valor até o prazo.
     * Sem rendimento: o que falta dividido pelos meses.
     * Com rendimento i ao mês por n meses (o guardado e os depósitos também rendem):
     * parcela = (alvo - guardado x (1+i)^n) x i / ((1+i)^n - 1)
     */
    static BigDecimal valorPorMes(BigDecimal alvo, BigDecimal guardado, LocalDate prazo, BigDecimal taxa, LocalDate hoje) {
        long n = mesesRestantes(prazo, hoje);
        BigDecimal falta = alvo.subtract(guardado).max(BigDecimal.ZERO);
        if (falta.signum() == 0) return BigDecimal.ZERO;
        if (taxa == null || taxa.signum() == 0) return falta.divide(BigDecimal.valueOf(n), 2, RoundingMode.UP);
        double i = taxa.doubleValue() / 100, fator = Math.pow(1 + i, n);
        double parcela = (alvo.doubleValue() - guardado.doubleValue() * fator) * i / (fator - 1);
        return BigDecimal.valueOf(Math.max(parcela, 0)).setScale(2, RoundingMode.UP);
    }
}
