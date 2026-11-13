package br.ucsal.finia.model;

import java.math.BigDecimal;

/** Limite mensal de gasto por categoria (RF09). mesReferencia = AAAA-MM. */
public record Orcamento(Long id, long usuarioId, long categoriaId, String categoriaNome, String categoriaCor,
                        BigDecimal limite, String mesReferencia) {
}
