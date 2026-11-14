package br.ucsal.finia.service;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.TipoTransacao;
import br.ucsal.finia.repository.MetaRepository;
import br.ucsal.finia.repository.TransacaoRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

/** Monta o resumo da tela Início: receitas, despesas, saldo, gráfico, últimas transações e metas. */
public class InicioService {

    private final TransacaoRepository transacoes;
    private final MetaRepository metas;

    public InicioService(TransacaoRepository transacoes, MetaRepository metas) {
        this.transacoes = transacoes;
        this.metas = metas;
    }

    public Map<String, Object> resumo(long usuarioId, YearMonth mes) {
        BigDecimal receitas = transacoes.somarDoMes(usuarioId, mes, TipoTransacao.RECEITA);
        BigDecimal despesas = transacoes.somarDoMes(usuarioId, mes, TipoTransacao.DESPESA);
        return Json.obj(
                "mes", mes.toString(),
                "receitas", receitas,
                "despesas", despesas,
                "saldo", receitas.subtract(despesas),
                "gastosPorCategoria", transacoes.despesasPorCategoria(usuarioId, mes),
                "ultimas", transacoes.ultimas(usuarioId, 5),
                "metas", metas.listar(usuarioId));
    }
}
