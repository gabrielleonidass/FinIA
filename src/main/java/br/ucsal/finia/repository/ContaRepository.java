package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.ContaFinanceira;
import br.ucsal.finia.model.Enums.TipoConta;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Acesso à tabela conta_financeira. */
public class ContaRepository {

    private static ContaFinanceira mapear(ResultSet rs) throws SQLException {
        return new ContaFinanceira(rs.getLong("id"), rs.getLong("usuario_id"), rs.getString("apelido"),
                rs.getString("instituicao"), TipoConta.valueOf(rs.getString("tipo")), rs.getBigDecimal("saldo_inicial"),
                Jdbc.intOuNulo(rs, "dia_vencimento"), rs.getBoolean("ativa"), rs.getBoolean("open_finance"));
    }

    public List<ContaFinanceira> listar(long usuarioId) {
        return Jdbc.listar("select * from conta_financeira where usuario_id = ? order by ativa desc, apelido",
                ContaRepository::mapear, usuarioId);
    }

    public Optional<ContaFinanceira> buscar(long id, long usuarioId) {
        return Jdbc.buscar("select * from conta_financeira where id = ? and usuario_id = ?",
                ContaRepository::mapear, id, usuarioId);
    }

    public long inserir(ContaFinanceira c) {
        return Jdbc.inserir("insert into conta_financeira (usuario_id,apelido,instituicao,tipo,saldo_inicial," +
                        "dia_vencimento,ativa,open_finance,criada_em) values (?,?,?,?,?,?,?,?,?)",
                c.usuarioId(), c.apelido(), c.instituicao(), c.tipo(), c.saldoInicial(), c.diaVencimento(),
                c.ativa(), c.openFinance(), LocalDateTime.now());
    }

    public void atualizar(ContaFinanceira c) {
        Jdbc.executar("update conta_financeira set apelido=?, instituicao=?, tipo=?, saldo_inicial=?, " +
                        "dia_vencimento=? where id=? and usuario_id=?",
                c.apelido(), c.instituicao(), c.tipo(), c.saldoInicial(), c.diaVencimento(), c.id(), c.usuarioId());
    }

    public void definirAtiva(long id, long usuarioId, boolean ativa) {
        Jdbc.executar("update conta_financeira set ativa=? where id=? and usuario_id=?", ativa, id, usuarioId);
    }

    /** Saldo atual = saldo inicial + receitas - despesas. */
    public BigDecimal saldoAtual(ContaFinanceira c) {
        BigDecimal receitas = Jdbc.decimal("select coalesce(sum(valor),0) from transacao where conta_id=? and tipo='RECEITA'", c.id());
        BigDecimal despesas = Jdbc.decimal("select coalesce(sum(valor),0) from transacao where conta_id=? and tipo='DESPESA'", c.id());
        return c.saldoInicial().add(receitas).subtract(despesas);
    }

    public long contarTransacoes(long contaId) {
        return Jdbc.numero("select count(*) from transacao where conta_id = ?", contaId);
    }
}
