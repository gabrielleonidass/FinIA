package br.ucsal.finia.model;

import br.ucsal.finia.model.Enums.StatusMeta;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Meta financeira (RF05). taxaRendimentoMensal em % (opcional). */
public record Meta(Long id, long usuarioId, String nome, BigDecimal valorAlvo, BigDecimal valorAcumulado,
                   LocalDate dataLimite, BigDecimal taxaRendimentoMensal, StatusMeta status) {
}
