package br.ucsal.finia.model;

import br.ucsal.finia.infra.Json;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Uma entrada ou saída de dinheiro.
 * categoriaAutomatica = true quando a categoria foi escolhida pelo categorizador (IA).
 */
public record Transacao(Long id, Long usuarioId, String descricao, BigDecimal valor, TipoTransacao tipo,
                        LocalDate data, Categoria categoria, boolean categoriaAutomatica)
        implements Json.JsonConvertivel {

    @Override
    public Map<String, Object> paraJson() {
        return Json.obj("id", id, "descricao", descricao, "valor", valor, "tipo", tipo, "data", data,
                "categoria", categoria, "categoriaAutomatica", categoriaAutomatica);
    }
}
