package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.OrigemTransacao;
import br.ucsal.finia.model.Enums.StatusTransacao;
import br.ucsal.finia.model.Enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Transação financeira (RF02-RF04). Valor sempre positivo; o sentido vem de {@code tipo}. */
public record Transacao(Long id, long usuarioId, long contaId, String contaApelido, LocalDate data,
                        String descricao, BigDecimal valor, TipoTransacao tipo, Long categoriaId,
                        String categoriaNome, String categoriaCor, double confianca,
                        StatusTransacao status, OrigemTransacao origem) implements Json.JsonConvertivel {

    public boolean despesa() {
        return tipo == TipoTransacao.DESPESA;
    }

    @Override
    public Map<String, Object> paraJson() {
        return Json.obj("id", id, "contaId", contaId, "conta", contaApelido, "data", data,
                "descricao", descricao, "valor", valor, "tipo", tipo, "categoriaId", categoriaId,
                "categoria", categoriaNome, "cor", categoriaCor,
                "confianca", Math.round(confianca * 100), "status", status, "origem", origem);
    }
}
