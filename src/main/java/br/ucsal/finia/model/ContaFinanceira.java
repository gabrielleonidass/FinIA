package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.TipoConta;

import java.math.BigDecimal;
import java.util.Map;

/** Conta corrente, poupança ou cartão de crédito (RF10). */
public record ContaFinanceira(Long id, long usuarioId, String apelido, String instituicao, TipoConta tipo,
                              BigDecimal saldoInicial, Integer diaVencimento, boolean ativa,
                              boolean openFinance) {

    public Map<String, Object> paraJson(BigDecimal saldoAtual, long qtdTransacoes) {
        return Json.obj("id", id, "apelido", apelido, "instituicao", instituicao, "tipo", tipo,
                "tipoRotulo", tipo.rotulo, "saldoInicial", saldoInicial, "saldoAtual", saldoAtual,
                "diaVencimento", diaVencimento, "ativa", ativa, "openFinance", openFinance,
                "qtdTransacoes", qtdTransacoes);
    }
}
