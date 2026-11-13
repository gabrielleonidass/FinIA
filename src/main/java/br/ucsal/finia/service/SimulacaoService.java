package br.ucsal.finia.service;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Categoria;
import br.ucsal.finia.model.Meta;
import br.ucsal.finia.repository.CategoriaRepository;
import br.ucsal.finia.repository.MetaRepository;
import br.ucsal.finia.repository.TransacaoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simulação de cenários "E se" (RF06 / UC06 / RN06).
 * A simulação é somente leitura: nenhum dado real é alterado.
 */
public class SimulacaoService {

    private final MetaRepository metas;
    private final TransacaoRepository transacoes;
    private final CategoriaRepository categorias;
    private final MetaService metaService;

    public SimulacaoService(MetaRepository metas, TransacaoRepository transacoes, CategoriaRepository categorias,
                            MetaService metaService) {
        this.metas = metas;
        this.transacoes = transacoes;
        this.categorias = categorias;
        this.metaService = metaService;
    }

    /** Média mensal de despesa da categoria nos 3 últimos meses completos. */
    public BigDecimal mediaMensalCategoria(long usuarioId, long categoriaId) {
        YearMonth atual = YearMonth.now();
        LocalDate ini = atual.minusMonths(3).atDay(1), fim = atual.minusMonths(1).atEndOfMonth();
        return transacoes.somaDespesasCategoria(usuarioId, categoriaId, ini, fim).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
    }

    public Map<String, Object> simular(long usuarioId, long categoriaId, int percentualReducao, BigDecimal rendaExtra) {
        if (percentualReducao < 0 || percentualReducao > 100) throw ApiException.requisicaoInvalida("O percentual deve estar entre 0 e 100.");
        Categoria cat = categorias.buscarPorId(categoriaId).orElseThrow(() -> ApiException.requisicaoInvalida("Categoria inválida."));
        BigDecimal media = mediaMensalCategoria(usuarioId, categoriaId);
        BigDecimal economia = media.multiply(BigDecimal.valueOf(percentualReducao)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal extra = rendaExtra == null ? BigDecimal.ZERO : rendaExtra;
        BigDecimal capAtual = metaService.capacidadeMensal(usuarioId);
        BigDecimal capNova = capAtual.add(economia).add(extra);

        List<Meta> ativas = metas.listarEmAndamento(usuarioId);
        BigDecimal somaAportes = BigDecimal.ZERO;
        List<BigDecimal> aportes = new ArrayList<>();
        for (Meta m : ativas) {
            BigDecimal a = MetaService.aporteMensal(m.valorAlvo(), m.valorAcumulado(), MetaService.mesesRestantes(m.dataLimite()),
                    m.taxaRendimentoMensal());
            aportes.add(a);
            somaAportes = somaAportes.add(a);
        }

        List<Map<String, Object>> comparacao = new ArrayList<>();
        for (int k = 0; k < ativas.size(); k++) {
            Meta m = ativas.get(k);
            BigDecimal peso = somaAportes.signum() == 0 ? BigDecimal.ONE.divide(BigDecimal.valueOf(ativas.size()), 6, RoundingMode.HALF_UP)
                    : aportes.get(k).divide(somaAportes, 6, RoundingMode.HALF_UP);
            BigDecimal aporteAtual = capAtual.max(BigDecimal.ZERO).multiply(peso).setScale(2, RoundingMode.HALF_UP);
            BigDecimal aporteNovo = capNova.max(BigDecimal.ZERO).multiply(peso).setScale(2, RoundingMode.HALF_UP);
            Integer mAtual = MetaService.mesesParaAtingir(m.valorAlvo(), m.valorAcumulado(), aporteAtual, m.taxaRendimentoMensal());
            Integer mNovo = MetaService.mesesParaAtingir(m.valorAlvo(), m.valorAcumulado(), aporteNovo, m.taxaRendimentoMensal());
            comparacao.add(Json.obj("meta", m.nome(), "prazo", m.dataLimite(),
                    "aporteAtual", aporteAtual, "conclusaoAtual", mAtual == null ? null : YearMonth.now().plusMonths(mAtual).toString(),
                    "aporteSimulado", aporteNovo, "conclusaoSimulada", mNovo == null ? null : YearMonth.now().plusMonths(mNovo).toString(),
                    "mesesAntecipados", mAtual == null || mNovo == null ? null : mAtual - mNovo));
        }
        return Json.obj("categoria", cat.nome(), "percentual", percentualReducao, "mediaMensalCategoria", media,
                "economiaMensal", economia, "rendaExtra", extra, "capacidadeAtual", capAtual, "capacidadeSimulada", capNova,
                "metas", comparacao, "aviso", "Simulação hipotética: nenhum dado real foi alterado.");
    }
}
