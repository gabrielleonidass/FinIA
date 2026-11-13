package br.ucsal.finia.service;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.SituacaoOrcamento;
import br.ucsal.finia.model.Enums.TipoAlerta;
import br.ucsal.finia.model.Orcamento;
import br.ucsal.finia.repository.CategoriaRepository;
import br.ucsal.finia.repository.OrcamentoRepository;
import br.ucsal.finia.repository.TransacaoRepository;
import br.ucsal.finia.util.Texto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Orçamento mensal por categoria com acompanhamento (RF09 / RN08). */
public class OrcamentoService {

    public static final int PERCENTUAL_ALERTA = 80;

    private final OrcamentoRepository orcamentos;
    private final TransacaoRepository transacoes;
    private final CategoriaRepository categorias;
    private final AlertaService alertas;

    public OrcamentoService(OrcamentoRepository orcamentos, TransacaoRepository transacoes,
                            CategoriaRepository categorias, AlertaService alertas) {
        this.orcamentos = orcamentos;
        this.transacoes = transacoes;
        this.categorias = categorias;
        this.alertas = alertas;
    }

    /** Situação a partir do percentual consumido: >=80% alerta, >100% estourado. */
    public static SituacaoOrcamento situacao(BigDecimal consumido, BigDecimal limite) {
        if (limite.signum() <= 0) return SituacaoOrcamento.ESTOURADO;
        // compara sem arredondar: consumido*100 versus limite*percentual
        BigDecimal consumido100 = consumido.multiply(BigDecimal.valueOf(100));
        if (consumido100.compareTo(limite.multiply(BigDecimal.valueOf(100))) > 0) return SituacaoOrcamento.ESTOURADO;
        if (consumido100.compareTo(limite.multiply(BigDecimal.valueOf(PERCENTUAL_ALERTA))) >= 0) return SituacaoOrcamento.EM_ALERTA;
        return SituacaoOrcamento.DENTRO_DO_LIMITE;
    }

    public List<Map<String, Object>> acompanhamento(long usuarioId, YearMonth mes) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Orcamento o : orcamentos.listar(usuarioId, mes.toString())) {
            BigDecimal consumido = transacoes.somaDespesasCategoria(usuarioId, o.categoriaId(), mes.atDay(1), mes.atEndOfMonth());
            int pct = o.limite().signum() == 0 ? 100 :
                    consumido.multiply(BigDecimal.valueOf(100)).divide(o.limite(), 0, RoundingMode.HALF_UP).intValue();
            SituacaoOrcamento s = situacao(consumido, o.limite());
            lista.add(Json.obj("id", o.id(), "categoriaId", o.categoriaId(), "categoria", o.categoriaNome(),
                    "cor", o.categoriaCor(), "limite", o.limite(), "consumido", consumido,
                    "disponivel", o.limite().subtract(consumido), "percentual", pct, "situacao", s));
        }
        return lista;
    }

    public void definir(long usuarioId, long categoriaId, YearMonth mes, BigDecimal limite) {
        if (limite == null || limite.signum() <= 0) throw ApiException.requisicaoInvalida("O limite deve ser maior que zero.");
        categorias.buscarPorId(categoriaId).filter(c -> !c.receita())
                .orElseThrow(() -> ApiException.requisicaoInvalida("Categoria de despesa inválida."));
        orcamentos.salvar(usuarioId, categoriaId, mes.toString(), limite.setScale(2, RoundingMode.HALF_UP));
        verificar(usuarioId, categoriaId, mes);
    }

    /** Emite alerta quando o consumo atinge 80% ou ultrapassa 100%. */
    public void verificar(long usuarioId, long categoriaId, YearMonth mes) {
        orcamentos.buscarPorCategoria(usuarioId, categoriaId, mes.toString()).ifPresent(o -> {
            BigDecimal consumido = transacoes.somaDespesasCategoria(usuarioId, categoriaId, mes.atDay(1), mes.atEndOfMonth());
            SituacaoOrcamento s = situacao(consumido, o.limite());
            if (s == SituacaoOrcamento.ESTOURADO) {
                alertas.emitirUmaVezNoMes(usuarioId, TipoAlerta.ORCAMENTO_ESTOURADO, o.categoriaNome(), String.format(
                        "Orçamento de %s estourado: %s gastos de %s.", o.categoriaNome(),
                        Texto.moeda(consumido), Texto.moeda(o.limite())), mes);
            } else if (s == SituacaoOrcamento.EM_ALERTA) {
                alertas.emitirUmaVezNoMes(usuarioId, TipoAlerta.ORCAMENTO_EM_ALERTA, o.categoriaNome(), String.format(
                        "Você já usou %s de %s do orçamento de %s (%d%%).", Texto.moeda(consumido),
                        Texto.moeda(o.limite()), o.categoriaNome(),
                        consumido.multiply(BigDecimal.valueOf(100)).divide(o.limite(), 0, RoundingMode.HALF_UP).intValue()), mes);
            }
        });
    }
}
