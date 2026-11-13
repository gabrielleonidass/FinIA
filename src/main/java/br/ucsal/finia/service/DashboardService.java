package br.ucsal.finia.service;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.ContaFinanceira;
import br.ucsal.finia.model.Enums.StatusTransacao;
import br.ucsal.finia.model.Enums.TipoTransacao;
import br.ucsal.finia.repository.AlertaRepository;
import br.ucsal.finia.repository.ContaRepository;
import br.ucsal.finia.repository.TransacaoRepository;
import br.ucsal.finia.util.Periodo;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Visão geral financeira consolidada (RF07 / UC07). */
public class DashboardService {

    private final ContaRepository contas;
    private final TransacaoRepository transacoes;
    private final AlertaRepository alertaRepo;
    private final AlertaService alertas;
    private final MetaService metas;

    public DashboardService(ContaRepository contas, TransacaoRepository transacoes, AlertaRepository alertaRepo,
                            AlertaService alertas, MetaService metas) {
        this.contas = contas;
        this.transacoes = transacoes;
        this.alertaRepo = alertaRepo;
        this.alertas = alertas;
        this.metas = metas;
    }

    public Map<String, Object> resumo(long usuarioId, Periodo p) {
        alertas.verificarFaturas(usuarioId);

        // saldo consolidado independente da instituição + detalhamento por conta
        BigDecimal saldoTotal = BigDecimal.ZERO;
        List<Map<String, Object>> porConta = new ArrayList<>();
        for (ContaFinanceira c : contas.listar(usuarioId)) {
            BigDecimal saldo = contas.saldoAtual(c);
            saldoTotal = saldoTotal.add(saldo);
            porConta.add(Json.obj("apelido", c.apelido(), "instituicao", c.instituicao(), "tipo", c.tipo().rotulo,
                    "saldo", saldo, "ativa", c.ativa()));
        }

        BigDecimal receitas = transacoes.somaPorTipo(usuarioId, TipoTransacao.RECEITA, p.inicio(), p.fim());
        BigDecimal despesas = transacoes.somaPorTipo(usuarioId, TipoTransacao.DESPESA, p.inicio(), p.fim());

        // evolução mensal: no mínimo 6 meses para o gráfico
        YearMonth fim = YearMonth.from(p.fim());
        YearMonth ini = YearMonth.from(p.inicio());
        if (ini.until(fim, java.time.temporal.ChronoUnit.MONTHS) < 5) ini = fim.minusMonths(5);
        List<Map<String, Object>> evolucao = new ArrayList<>();
        for (YearMonth ym = ini; !ym.isAfter(fim); ym = ym.plusMonths(1)) {
            evolucao.add(Json.obj("mes", ym.toString(),
                    "receitas", transacoes.somaPorTipo(usuarioId, TipoTransacao.RECEITA, ym.atDay(1), ym.atEndOfMonth()),
                    "despesas", transacoes.somaPorTipo(usuarioId, TipoTransacao.DESPESA, ym.atDay(1), ym.atEndOfMonth())));
        }

        List<Map<String, Object>> categorias = new ArrayList<>();
        transacoes.despesasPorCategoria(usuarioId, p.inicio(), p.fim())
                .forEach((nome, v) -> categorias.add(Json.obj("categoria", nome, "cor", v[0], "total", v[1])));

        Map<String, Object> metasInfo = metas.listar(usuarioId);

        return Json.obj("periodo", Json.obj("inicio", p.inicio(), "fim", p.fim(), "rotulo", p.rotulo()),
                "saldoTotal", saldoTotal, "contas", porConta, "receitas", receitas, "despesas", despesas,
                "resultado", receitas.subtract(despesas), "evolucao", evolucao, "categorias", categorias,
                "metas", metasInfo.get("metas"),
                "pendentesRevisao", transacoes.contarPorStatus(usuarioId, StatusTransacao.INCERTA),
                "alertas", alertaRepo.listar(usuarioId, 4),
                "ultimasTransacoes", transacoes.listar(usuarioId, null, null, null, null, null, null).stream().limit(6).toList());
    }
}
