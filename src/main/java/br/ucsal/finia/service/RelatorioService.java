package br.ucsal.finia.service;

import br.ucsal.finia.export.PdfWriter;
import br.ucsal.finia.export.XlsxWriter;
import br.ucsal.finia.export.XlsxWriter.Negrito;
import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.TipoTransacao;
import br.ucsal.finia.model.Transacao;
import br.ucsal.finia.model.Usuario;
import br.ucsal.finia.repository.TransacaoRepository;
import br.ucsal.finia.util.Periodo;
import br.ucsal.finia.util.Texto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Geração e exportação de relatórios financeiros (RF11 / UC11):
 * mensal, trimestral ou anual, em PDF e XLSX.
 * Conteúdo mínimo: saldo do período, gastos por categoria, comparativo com o
 * período anterior e progresso das metas ativas.
 */
public class RelatorioService {

    private static final Locale BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TransacaoRepository transacoes;
    private final MetaService metas;

    public RelatorioService(TransacaoRepository transacoes, MetaService metas) {
        this.transacoes = transacoes;
        this.metas = metas;
    }

    /** Período do relatório a partir do tipo e do mês de referência (AAAA-MM). */
    public static Periodo periodo(String tipo, String referencia) {
        YearMonth ref;
        try {
            ref = referencia == null || referencia.isBlank() ? YearMonth.now() : YearMonth.parse(referencia);
        } catch (RuntimeException e) {
            throw ApiException.requisicaoInvalida("Mês de referência inválido (use AAAA-MM).");
        }
        return switch (tipo == null ? "mensal" : tipo) {
            case "trimestral" -> {
                int trimestre = (ref.getMonthValue() - 1) / 3;
                YearMonth ini = YearMonth.of(ref.getYear(), trimestre * 3 + 1);
                yield new Periodo(ini.atDay(1), ini.plusMonths(2).atEndOfMonth(), (trimestre + 1) + "º trimestre de " + ref.getYear());
            }
            case "anual" -> new Periodo(LocalDate.of(ref.getYear(), 1, 1), LocalDate.of(ref.getYear(), 12, 31), "Ano de " + ref.getYear());
            case "mensal" -> new Periodo(ref.atDay(1), ref.atEndOfMonth(), nomeMes(ref) + " de " + ref.getYear());
            default -> throw ApiException.requisicaoInvalida("Tipo de relatório inválido.");
        };
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> dados(long usuarioId, String tipo, String referencia) {
        Periodo p = periodo(tipo, referencia);
        Periodo ant = p.anterior();
        BigDecimal rec = transacoes.somaPorTipo(usuarioId, TipoTransacao.RECEITA, p.inicio(), p.fim());
        BigDecimal desp = transacoes.somaPorTipo(usuarioId, TipoTransacao.DESPESA, p.inicio(), p.fim());
        BigDecimal recAnt = transacoes.somaPorTipo(usuarioId, TipoTransacao.RECEITA, ant.inicio(), ant.fim());
        BigDecimal despAnt = transacoes.somaPorTipo(usuarioId, TipoTransacao.DESPESA, ant.inicio(), ant.fim());

        Map<String, Object[]> catAtual = transacoes.despesasPorCategoria(usuarioId, p.inicio(), p.fim());
        Map<String, Object[]> catAnt = transacoes.despesasPorCategoria(usuarioId, ant.inicio(), ant.fim());
        List<Map<String, Object>> categorias = new ArrayList<>();
        catAtual.forEach((nome, v) -> {
            BigDecimal atual = (BigDecimal) v[1];
            BigDecimal anterior = catAnt.containsKey(nome) ? (BigDecimal) catAnt.get(nome)[1] : BigDecimal.ZERO;
            categorias.add(Json.obj("categoria", nome, "cor", v[0], "total", atual, "anterior", anterior,
                    "variacao", variacao(atual, anterior),
                    "participacao", desp.signum() == 0 ? 0 : atual.multiply(BigDecimal.valueOf(100)).divide(desp, 1, RoundingMode.HALF_UP)));
        });

        List<Map<String, Object>> mensal = new ArrayList<>();
        for (YearMonth ym = YearMonth.from(p.inicio()); !ym.isAfter(YearMonth.from(p.fim())); ym = ym.plusMonths(1)) {
            BigDecimal r = transacoes.somaPorTipo(usuarioId, TipoTransacao.RECEITA, ym.atDay(1), ym.atEndOfMonth());
            BigDecimal d = transacoes.somaPorTipo(usuarioId, TipoTransacao.DESPESA, ym.atDay(1), ym.atEndOfMonth());
            mensal.add(Json.obj("mes", ym.toString(), "rotulo", nomeMes(ym) + "/" + ym.getYear(), "receitas", r, "despesas", d,
                    "saldo", r.subtract(d)));
        }

        List<Map<String, Object>> metasAtivas = ((List<Map<String, Object>>) metas.listar(usuarioId).get("metas")).stream()
                .filter(m -> !"CANCELADA".equals(String.valueOf(m.get("status")))).toList();

        return Json.obj("tipo", tipo, "periodo", Json.obj("inicio", p.inicio(), "fim", p.fim(), "rotulo", p.rotulo()),
                "periodoAnterior", Json.obj("inicio", ant.inicio(), "fim", ant.fim()),
                "receitas", rec, "despesas", desp, "saldo", rec.subtract(desp),
                "comparativo", Json.obj("receitasAnterior", recAnt, "despesasAnterior", despAnt,
                        "saldoAnterior", recAnt.subtract(despAnt),
                        "variacaoReceitas", variacao(rec, recAnt), "variacaoDespesas", variacao(desp, despAnt)),
                "categorias", categorias, "mensal", mensal, "metas", metasAtivas);
    }

    private static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior.signum() == 0) return null;
        return atual.subtract(anterior).multiply(BigDecimal.valueOf(100)).divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private static String nomeMes(YearMonth ym) {
        String n = ym.getMonth().getDisplayName(TextStyle.FULL, BR);
        return n.substring(0, 1).toUpperCase() + n.substring(1);
    }

    // ------------------------------------------------------------------ XLSX

    @SuppressWarnings("unchecked")
    public byte[] gerarXlsx(Usuario u, String tipo, String referencia) {
        Map<String, Object> d = dados(u.id(), tipo, referencia);
        Map<String, Object> per = (Map<String, Object>) d.get("periodo");
        Map<String, Object> comp = (Map<String, Object>) d.get("comparativo");
        XlsxWriter x = new XlsxWriter();

        List<List<Object>> resumo = x.novaAba("Resumo", 34, 18, 18, 14);
        resumo.add(List.of(new Negrito("FinIA - Relatório financeiro")));
        resumo.add(List.of("Titular", u.nome()));
        resumo.add(List.of("Período", per.get("rotulo") + " (" + fmt((LocalDate) per.get("inicio")) + " a " + fmt((LocalDate) per.get("fim")) + ")"));
        resumo.add(new ArrayList<>());
        resumo.add(List.of(new Negrito("Indicador"), new Negrito("Período"), new Negrito("Período anterior"), new Negrito("Variação %")));
        resumo.add(linha("Receitas", d.get("receitas"), comp.get("receitasAnterior"), comp.get("variacaoReceitas")));
        resumo.add(linha("Despesas", d.get("despesas"), comp.get("despesasAnterior"), comp.get("variacaoDespesas")));
        resumo.add(linha("Saldo do período", d.get("saldo"), comp.get("saldoAnterior"), null));

        List<List<Object>> cat = x.novaAba("Gastos por categoria", 24, 16, 18, 14, 16);
        cat.add(List.of(new Negrito("Categoria"), new Negrito("Total"), new Negrito("Período anterior"), new Negrito("Variação %"), new Negrito("Participação %")));
        for (Map<String, Object> c : (List<Map<String, Object>>) d.get("categorias")) {
            cat.add(linha(c.get("categoria"), c.get("total"), c.get("anterior"), c.get("variacao"), c.get("participacao")));
        }
        cat.add(List.of(new Negrito("Total"), new Negrito(d.get("despesas"))));

        List<List<Object>> mensal = x.novaAba("Mensal", 18, 16, 16, 16);
        mensal.add(List.of(new Negrito("Mês"), new Negrito("Receitas"), new Negrito("Despesas"), new Negrito("Saldo")));
        for (Map<String, Object> m : (List<Map<String, Object>>) d.get("mensal")) {
            mensal.add(List.of(m.get("rotulo"), m.get("receitas"), m.get("despesas"), m.get("saldo")));
        }
        mensal.add(List.of(new Negrito("Total"), new Negrito(d.get("receitas")), new Negrito(d.get("despesas")), new Negrito(d.get("saldo"))));

        List<List<Object>> mt = x.novaAba("Metas", 30, 16, 16, 12, 14, 16);
        mt.add(List.of(new Negrito("Meta"), new Negrito("Valor-alvo"), new Negrito("Acumulado"), new Negrito("Progresso %"),
                new Negrito("Prazo"), new Negrito("Aporte mensal")));
        for (Map<String, Object> m : (List<Map<String, Object>>) d.get("metas")) {
            mt.add(List.of(m.get("nome"), m.get("valorAlvo"), m.get("valorAcumulado"), m.get("percentual"),
                    fmt((LocalDate) m.get("dataLimite")), m.get("aporteMensal")));
        }

        List<List<Object>> tr = x.novaAba("Transações", 12, 40, 22, 18, 10, 14);
        tr.add(List.of(new Negrito("Data"), new Negrito("Descrição"), new Negrito("Conta"), new Negrito("Categoria"),
                new Negrito("Tipo"), new Negrito("Valor")));
        for (Transacao t : transacoes.listar(u.id(), (LocalDate) per.get("inicio"), (LocalDate) per.get("fim"), null, null, null, null)) {
            tr.add(List.of(fmt(t.data()), t.descricao(), t.contaApelido(), t.categoriaNome() == null ? "-" : t.categoriaNome(),
                    t.tipo() == TipoTransacao.RECEITA ? "Receita" : "Despesa", t.despesa() ? t.valor().negate() : t.valor()));
        }
        return x.gerar();
    }

    private static List<Object> linha(Object... valores) {
        List<Object> l = new ArrayList<>();
        for (Object v : valores) l.add(v == null ? "-" : v);
        return l;
    }

    // ------------------------------------------------------------------ PDF

    @SuppressWarnings("unchecked")
    public byte[] gerarPdf(Usuario u, String tipo, String referencia) {
        Map<String, Object> d = dados(u.id(), tipo, referencia);
        Map<String, Object> per = (Map<String, Object>) d.get("periodo");
        Map<String, Object> comp = (Map<String, Object>) d.get("comparativo");
        PdfWriter pdf = new PdfWriter();
        final String VERDE = "#0F766E", CINZA = "#64748B", ESCURO = "#0F172A", CLARO = "#F1F5F9";

        // cabeçalho
        pdf.retangulo(0, 0, PdfWriter.LARGURA, 78, VERDE);
        pdf.texto(40, 34, 20, true, "FinIA", "#FFFFFF");
        pdf.texto(40, 56, 10, false, "Relatório financeiro " + tipo + " - " + per.get("rotulo"), "#CCFBF1");
        pdf.textoDireita(555, 34, 9, false, u.nome(), "#FFFFFF");
        pdf.textoDireita(555, 50, 9, false, fmt((LocalDate) per.get("inicio")) + " a " + fmt((LocalDate) per.get("fim")), "#CCFBF1");

        // cartões de resumo
        float y = 100;
        String[][] cartoes = {
                {"Receitas", Texto.moeda((BigDecimal) d.get("receitas")), pct(comp.get("variacaoReceitas"))},
                {"Despesas", Texto.moeda((BigDecimal) d.get("despesas")), pct(comp.get("variacaoDespesas"))},
                {"Saldo do período", Texto.moeda((BigDecimal) d.get("saldo")), "anterior: " + Texto.moeda((BigDecimal) comp.get("saldoAnterior"))}};
        for (int i = 0; i < 3; i++) {
            float x = 40 + i * 175;
            pdf.retangulo(x, y, 165, 62, CLARO);
            pdf.texto(x + 12, y + 18, 9, false, cartoes[i][0], CINZA);
            pdf.texto(x + 12, y + 38, 15, true, cartoes[i][1], ESCURO);
            pdf.texto(x + 12, y + 53, 8, false, cartoes[i][2], CINZA);
        }

        // gastos por categoria
        y = 190;
        pdf.texto(40, y, 12, true, "Gastos por categoria", ESCURO);
        y += 18;
        y = cabecalhoTabela(pdf, y, new String[]{"Categoria", "Total", "Anterior", "Variação", "Part."}, new float[]{40, 300, 390, 470, 555});
        for (Map<String, Object> c : (List<Map<String, Object>>) d.get("categorias")) {
            pdf.retangulo(46, y - 8, 7, 7, (String) c.get("cor"));
            pdf.texto(60, y, 9, false, (String) c.get("categoria"), ESCURO);
            pdf.textoDireita(300, y, 9, false, Texto.moeda((BigDecimal) c.get("total")), ESCURO);
            pdf.textoDireita(390, y, 9, false, Texto.moeda((BigDecimal) c.get("anterior")), CINZA);
            pdf.textoDireita(470, y, 9, false, pctCurto(c.get("variacao")), CINZA);
            pdf.textoDireita(555, y, 9, false, c.get("participacao") + "%", CINZA);
            pdf.linha(40, y + 5, 555, y + 5, 0.4f, "#E2E8F0");
            y += 17;
        }

        // evolução mensal
        y += 16;
        pdf.texto(40, y, 12, true, "Totais por mês", ESCURO);
        y += 18;
        y = cabecalhoTabela(pdf, y, new String[]{"Mês", "Receitas", "Despesas", "Saldo"}, new float[]{40, 330, 445, 555});
        for (Map<String, Object> m : (List<Map<String, Object>>) d.get("mensal")) {
            if (y > 790) {
                pdf.novaPagina();
                y = 50;
            }
            pdf.texto(46, y, 9, false, (String) m.get("rotulo"), ESCURO);
            pdf.textoDireita(330, y, 9, false, Texto.moeda((BigDecimal) m.get("receitas")), ESCURO);
            pdf.textoDireita(445, y, 9, false, Texto.moeda((BigDecimal) m.get("despesas")), ESCURO);
            BigDecimal s = (BigDecimal) m.get("saldo");
            pdf.textoDireita(555, y, 9, true, Texto.moeda(s), s.signum() < 0 ? "#B91C1C" : VERDE);
            pdf.linha(40, y + 5, 555, y + 5, 0.4f, "#E2E8F0");
            y += 17;
        }
        pdf.texto(46, y, 9, true, "Total", ESCURO);
        pdf.textoDireita(330, y, 9, true, Texto.moeda((BigDecimal) d.get("receitas")), ESCURO);
        pdf.textoDireita(445, y, 9, true, Texto.moeda((BigDecimal) d.get("despesas")), ESCURO);
        pdf.textoDireita(555, y, 9, true, Texto.moeda((BigDecimal) d.get("saldo")), ESCURO);
        y += 30;

        // metas
        if (y > 700) {
            pdf.novaPagina();
            y = 50;
        }
        pdf.texto(40, y, 12, true, "Progresso das metas", ESCURO);
        y += 22;
        for (Map<String, Object> m : (List<Map<String, Object>>) d.get("metas")) {
            if (y > 800) {
                pdf.novaPagina();
                y = 50;
            }
            int pctMeta = (Integer) m.get("percentual");
            pdf.texto(40, y, 9, true, (String) m.get("nome"), ESCURO);
            pdf.textoDireita(555, y, 9, false, Texto.moeda((BigDecimal) m.get("valorAcumulado")) + " de "
                    + Texto.moeda((BigDecimal) m.get("valorAlvo")) + " (" + pctMeta + "%) - prazo "
                    + fmt((LocalDate) m.get("dataLimite")), CINZA);
            pdf.retangulo(40, y + 6, 515, 6, "#E2E8F0");
            pdf.retangulo(40, y + 6, 515 * pctMeta / 100f, 6, VERDE);
            y += 30;
        }
        pdf.texto(40, 825, 7, false, "Gerado pelo FinIA em " + fmt(LocalDate.now()) + ". Valores consolidados de todas as contas do titular.", CINZA);
        return pdf.gerar();
    }

    private static float cabecalhoTabela(PdfWriter pdf, float y, String[] titulos, float[] xs) {
        pdf.retangulo(40, y - 11, 515, 16, "#E2E8F0");
        pdf.texto(46, y, 8, true, titulos[0].toUpperCase(), "#334155");
        for (int i = 1; i < titulos.length; i++) pdf.textoDireita(xs[i], y, 8, true, titulos[i].toUpperCase(), "#334155");
        return y + 18;
    }

    private static String pct(Object v) {
        if (v == null) return "sem base anterior";
        BigDecimal b = (BigDecimal) v;
        return (b.signum() > 0 ? "+" : "") + b.toPlainString().replace('.', ',') + "% vs. anterior";
    }

    private static String pctCurto(Object v) {
        if (v == null) return "-";
        BigDecimal b = (BigDecimal) v;
        return (b.signum() > 0 ? "+" : "") + b.toPlainString().replace('.', ',') + "%";
    }

    private static String fmt(LocalDate d) {
        return d == null ? "-" : d.format(DATA);
    }
}
