package br.ucsal.finia.service;

import br.ucsal.finia.model.ConfiguracaoAlerta;
import br.ucsal.finia.model.ContaFinanceira;
import br.ucsal.finia.model.Enums.CanalAlerta;
import br.ucsal.finia.model.Enums.TipoAlerta;
import br.ucsal.finia.model.Enums.TipoConta;
import br.ucsal.finia.model.Transacao;
import br.ucsal.finia.repository.AlertaRepository;
import br.ucsal.finia.repository.ContaRepository;
import br.ucsal.finia.repository.TransacaoRepository;
import br.ucsal.finia.util.Texto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Regras de emissão de alertas (RF08 / RN07). */
public class AlertaService {

    /** Gasto atípico: valor maior que a média + 200% (ou seja, > 3x a média). */
    public static final BigDecimal FATOR_ATIPICO = BigDecimal.valueOf(3);
    /** Mínimo de transações anteriores na categoria para haver média confiável. */
    public static final int HISTORICO_MINIMO = 3;
    public static final int DIAS_ANTECEDENCIA_FATURA = 3;

    private final AlertaRepository alertas;
    private final TransacaoRepository transacoes;
    private final ContaRepository contas;

    public AlertaService(AlertaRepository alertas, TransacaoRepository transacoes, ContaRepository contas) {
        this.alertas = alertas;
        this.transacoes = transacoes;
        this.contas = contas;
    }

    public void criarConfiguracoesPadrao(long usuarioId) {
        for (TipoAlerta t : TipoAlerta.values()) alertas.salvarConfiguracao(usuarioId, t, true, CanalAlerta.AMBOS);
    }

    /** Emite o alerta apenas se o tipo estiver ativo para o usuário e não houver duplicata no mesmo dia. */
    public boolean emitir(long usuarioId, TipoAlerta tipo, String mensagem, LocalDateTime quando) {
        boolean ativo = alertas.buscarConfiguracao(usuarioId, tipo).map(ConfiguracaoAlerta::ativo).orElse(true);
        if (!ativo) return false;
        if (alertas.existeRecente(usuarioId, tipo, mensagem, quando.minusDays(1))) return false;
        alertas.inserir(usuarioId, tipo, mensagem, quando);
        return true;
    }

    /**
     * Verifica se a despesa excede em 200% a média histórica (valor > 3x a média).
     * Refinamento (pendência 4 da documentação): quando o estabelecimento já tem
     * pelo menos 3 ocorrências, a base é a média do próprio estabelecimento
     * (evita alertar aluguel ou combustível só por serem maiores que a média
     * da categoria); caso contrário, usa a média histórica da categoria.
     */
    public boolean verificarGastoAtipico(Transacao t) {
        if (!t.despesa() || t.categoriaId() == null) return false;
        String padrao = Texto.padraoEstabelecimento(t.descricao());
        List<Transacao> historico = transacoes.listar(t.usuarioId(), null, null, t.categoriaId(), null, null, null).stream()
                .filter(x -> x.despesa() && !x.id().equals(t.id())).toList();
        List<Transacao> mesmoLocal = historico.stream()
                .filter(x -> Texto.padraoEstabelecimento(x.descricao()).equals(padrao)).toList();
        List<Transacao> base = mesmoLocal.size() >= HISTORICO_MINIMO ? mesmoLocal : historico;
        if (base.size() < HISTORICO_MINIMO) return false;
        BigDecimal media = base.stream().map(Transacao::valor).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(base.size()), 2, RoundingMode.HALF_UP);
        if (media.signum() <= 0 || t.valor().compareTo(media.multiply(FATOR_ATIPICO)) <= 0) return false;
        long acima = t.valor().subtract(media).multiply(BigDecimal.valueOf(100)).divide(media, 0, RoundingMode.HALF_UP).longValue();
        String msg = String.format("Gasto atípico em %s: %s em \"%s\" está %d%% acima da sua média (%s).",
                t.categoriaNome(), Texto.moeda(t.valor()), t.descricao(), acima, Texto.moeda(media));
        return emitir(t.usuarioId(), TipoAlerta.GASTO_ATIPICO, msg, t.data().atTime(LocalDateTime.now().toLocalTime()));
    }

    /** Emite alerta de orçamento no máximo uma vez por tipo, categoria e mês. */
    public boolean emitirUmaVezNoMes(long usuarioId, TipoAlerta tipo, String categoria, String mensagem, YearMonth mes) {
        if (alertas.existeNoPeriodo(usuarioId, tipo, "%orçamento de " + categoria + "%", mes.atDay(1).atStartOfDay())) return false;
        return emitir(usuarioId, tipo, mensagem, LocalDateTime.now());
    }

    /** Alerta de vencimento de fatura com 3 dias corridos de antecedência. */
    public void verificarFaturas(long usuarioId) {
        LocalDate hoje = LocalDate.now();
        for (ContaFinanceira c : contas.listar(usuarioId)) {
            if (!c.ativa() || c.tipo() != TipoConta.CARTAO_CREDITO || c.diaVencimento() == null) continue;
            LocalDate venc = hoje.withDayOfMonth(Math.min(c.diaVencimento(), hoje.lengthOfMonth()));
            if (venc.isBefore(hoje)) venc = venc.plusMonths(1);
            long dias = ChronoUnit.DAYS.between(hoje, venc);
            if (dias <= DIAS_ANTECEDENCIA_FATURA) {
                String msg = String.format("A fatura do cartão %s vence em %s (%d dia%s).", c.apelido(),
                        venc.format(DateTimeFormatter.ofPattern("dd/MM")), dias, dias == 1 ? "" : "s");
                emitir(usuarioId, TipoAlerta.VENCIMENTO_FATURA, msg, LocalDateTime.now());
            }
        }
    }
}
