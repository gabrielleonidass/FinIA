package br.ucsal.finia.service;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.StatusMeta;
import br.ucsal.finia.model.Enums.TipoAlerta;
import br.ucsal.finia.model.Enums.TipoTransacao;
import br.ucsal.finia.model.Meta;
import br.ucsal.finia.repository.MetaRepository;
import br.ucsal.finia.repository.TransacaoRepository;
import br.ucsal.finia.util.Texto;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cadastro, simulação e acompanhamento de metas financeiras (RF05 / UC05). */
public class MetaService {

    private static final MathContext MC = MathContext.DECIMAL64;

    private final MetaRepository metas;
    private final TransacaoRepository transacoes;
    private final AlertaService alertas;

    public MetaService(MetaRepository metas, TransacaoRepository transacoes, AlertaService alertas) {
        this.metas = metas;
        this.transacoes = transacoes;
        this.alertas = alertas;
    }

    // ------------------------------------------------------------- cálculos

    /**
     * Aporte mensal necessário. Sem rendimento: (alvo - acumulado) / meses.
     * Com taxa i ao mês: PMT = (alvo - acumulado*(1+i)^n) * i / ((1+i)^n - 1).
     */
    public static BigDecimal aporteMensal(BigDecimal alvo, BigDecimal acumulado, int meses, BigDecimal taxaPercentual) {
        BigDecimal faltante = alvo.subtract(acumulado);
        if (faltante.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        if (meses <= 0) return faltante.setScale(2, RoundingMode.HALF_UP);
        if (taxaPercentual == null || taxaPercentual.signum() == 0) {
            return faltante.divide(BigDecimal.valueOf(meses), 2, RoundingMode.HALF_UP);
        }
        BigDecimal i = taxaPercentual.divide(BigDecimal.valueOf(100), MC);
        BigDecimal fator = BigDecimal.ONE.add(i).pow(meses, MC);
        BigDecimal necessario = alvo.subtract(acumulado.multiply(fator, MC));
        if (necessario.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        return necessario.multiply(i, MC).divide(fator.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }

    /** Meses necessários para atingir o alvo com um aporte mensal fixo (null se nunca atingir). */
    public static Integer mesesParaAtingir(BigDecimal alvo, BigDecimal acumulado, BigDecimal aporte, BigDecimal taxaPercentual) {
        if (acumulado.compareTo(alvo) >= 0) return 0;
        BigDecimal i = taxaPercentual == null ? BigDecimal.ZERO : taxaPercentual.divide(BigDecimal.valueOf(100), MC);
        if (aporte.signum() <= 0 && i.signum() <= 0) return null;
        BigDecimal saldo = acumulado;
        for (int m = 1; m <= 600; m++) {
            saldo = saldo.multiply(BigDecimal.ONE.add(i), MC).add(aporte, MC);
            if (saldo.compareTo(alvo) >= 0) return m;
        }
        return null;
    }

    public static int mesesRestantes(LocalDate dataLimite) {
        long m = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(dataLimite));
        return (int) Math.max(1, m);
    }

    /** Capacidade de poupança: média de (receitas - despesas) dos 3 últimos meses completos. */
    public BigDecimal capacidadeMensal(long usuarioId) {
        YearMonth atual = YearMonth.now();
        BigDecimal soma = BigDecimal.ZERO;
        for (int k = 1; k <= 3; k++) {
            YearMonth ym = atual.minusMonths(k);
            BigDecimal r = transacoes.somaPorTipo(usuarioId, TipoTransacao.RECEITA, ym.atDay(1), ym.atEndOfMonth());
            BigDecimal d = transacoes.somaPorTipo(usuarioId, TipoTransacao.DESPESA, ym.atDay(1), ym.atEndOfMonth());
            soma = soma.add(r.subtract(d));
        }
        return soma.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------- consulta

    /**
     * Lista as metas recalculando a projeção (RF05): a capacidade de poupança é
     * distribuída pelas metas em andamento por ordem de prazo; a meta cujo aporte
     * necessário não cabe na capacidade restante passa a EM_RISCO.
     */
    public Map<String, Object> listar(long usuarioId) {
        BigDecimal capacidade = capacidadeMensal(usuarioId);
        BigDecimal restante = capacidade;
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Meta m : metas.listar(usuarioId)) {
            int meses = mesesRestantes(m.dataLimite());
            BigDecimal aporte = aporteMensal(m.valorAlvo(), m.valorAcumulado(), meses, m.taxaRendimentoMensal());
            StatusMeta status = m.status();
            if (m.valorAcumulado().compareTo(m.valorAlvo()) >= 0 && status != StatusMeta.CANCELADA) {
                status = StatusMeta.CONCLUIDA;
            } else if (status == StatusMeta.ATIVA || status == StatusMeta.EM_RISCO) {
                boolean cabe = aporte.compareTo(restante) <= 0;
                status = cabe ? StatusMeta.ATIVA : StatusMeta.EM_RISCO;
                restante = restante.subtract(aporte).max(BigDecimal.ZERO);
                if (status == StatusMeta.EM_RISCO && m.status() != StatusMeta.EM_RISCO) {
                    alertas.emitir(usuarioId, TipoAlerta.META_EM_RISCO, String.format(
                            "A meta \"%s\" está em risco: são necessários %s/mês e sua capacidade disponível é menor.",
                            m.nome(), Texto.moeda(aporte)), LocalDateTime.now());
                }
            }
            if (status != m.status()) {
                metas.atualizar(new Meta(m.id(), m.usuarioId(), m.nome(), m.valorAlvo(), m.valorAcumulado(),
                        m.dataLimite(), m.taxaRendimentoMensal(), status));
            }
            lista.add(paraJson(m, status, meses, aporte));
        }
        return Json.obj("capacidadeMensal", capacidade, "metas", lista);
    }

    private static Map<String, Object> paraJson(Meta m, StatusMeta status, int meses, BigDecimal aporte) {
        int pct = m.valorAlvo().signum() == 0 ? 0 : m.valorAcumulado().multiply(BigDecimal.valueOf(100))
                .divide(m.valorAlvo(), 0, RoundingMode.DOWN).min(BigDecimal.valueOf(100)).intValue();
        return Json.obj("id", m.id(), "nome", m.nome(), "valorAlvo", m.valorAlvo(), "valorAcumulado", m.valorAcumulado(),
                "dataLimite", m.dataLimite(), "taxaRendimentoMensal", m.taxaRendimentoMensal(), "status", status,
                "mesesRestantes", meses, "aporteMensal", aporte, "percentual", pct);
    }

    // ------------------------------------------------------------- comandos

    public long criar(long usuarioId, String nome, BigDecimal alvo, BigDecimal acumulado, LocalDate dataLimite, BigDecimal taxa) {
        validar(nome, alvo, acumulado, dataLimite, taxa);
        return metas.inserir(new Meta(null, usuarioId, nome.trim(), alvo, acumulado == null ? BigDecimal.ZERO : acumulado,
                dataLimite, taxa, StatusMeta.ATIVA));
    }

    public void editar(long usuarioId, long id, String nome, BigDecimal alvo, BigDecimal acumulado, LocalDate dataLimite, BigDecimal taxa) {
        Meta m = buscar(usuarioId, id);
        validar(nome, alvo, acumulado, dataLimite, taxa);
        metas.atualizar(new Meta(m.id(), usuarioId, nome.trim(), alvo, acumulado == null ? m.valorAcumulado() : acumulado,
                dataLimite, taxa, m.status() == StatusMeta.CONCLUIDA ? StatusMeta.ATIVA : m.status()));
    }

    public void aportar(long usuarioId, long id, BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) throw ApiException.requisicaoInvalida("Informe um valor de aporte positivo.");
        Meta m = buscar(usuarioId, id);
        if (m.status() == StatusMeta.CANCELADA) throw ApiException.requisicaoInvalida("Meta cancelada não recebe aportes.");
        BigDecimal novo = m.valorAcumulado().add(valor);
        StatusMeta s = novo.compareTo(m.valorAlvo()) >= 0 ? StatusMeta.CONCLUIDA : m.status();
        metas.atualizar(new Meta(m.id(), usuarioId, m.nome(), m.valorAlvo(), novo, m.dataLimite(), m.taxaRendimentoMensal(), s));
    }

    /** Pausar, retomar ou cancelar (somente o criador - garantido pelo filtro de usuário). */
    public void alterarStatus(long usuarioId, long id, StatusMeta novo) {
        Meta m = buscar(usuarioId, id);
        if (m.status() == StatusMeta.CANCELADA) throw ApiException.requisicaoInvalida("A meta já foi cancelada.");
        if (novo != StatusMeta.PAUSADA && novo != StatusMeta.ATIVA && novo != StatusMeta.CANCELADA)
            throw ApiException.requisicaoInvalida("Transição de status inválida.");
        metas.atualizar(new Meta(m.id(), usuarioId, m.nome(), m.valorAlvo(), m.valorAcumulado(), m.dataLimite(),
                m.taxaRendimentoMensal(), novo));
    }

    public Meta buscar(long usuarioId, long id) {
        return metas.buscar(id, usuarioId).orElseThrow(() -> ApiException.naoEncontrado("Meta não encontrada."));
    }

    private static void validar(String nome, BigDecimal alvo, BigDecimal acumulado, LocalDate data, BigDecimal taxa) {
        if (nome == null || nome.isBlank()) throw ApiException.requisicaoInvalida("Informe o nome da meta.");
        if (alvo == null || alvo.signum() <= 0) throw ApiException.requisicaoInvalida("O valor-alvo deve ser maior que zero.");
        if (acumulado != null && acumulado.signum() < 0) throw ApiException.requisicaoInvalida("O valor acumulado não pode ser negativo.");
        if (data == null || !data.isAfter(LocalDate.now())) throw ApiException.requisicaoInvalida("O prazo deve ser uma data futura.");
        if (taxa != null && (taxa.signum() < 0 || taxa.compareTo(BigDecimal.TEN) > 0))
            throw ApiException.requisicaoInvalida("A taxa de rendimento mensal deve estar entre 0% e 10%.");
    }
}
