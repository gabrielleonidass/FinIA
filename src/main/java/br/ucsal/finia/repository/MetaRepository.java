package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Enums.StatusMeta;
import br.ucsal.finia.model.Meta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Acesso à tabela meta (RF05). */
public class MetaRepository {

    private static Meta mapear(ResultSet rs) throws SQLException {
        return new Meta(rs.getLong("id"), rs.getLong("usuario_id"), rs.getString("nome"), rs.getBigDecimal("valor_alvo"),
                rs.getBigDecimal("valor_acumulado"), Jdbc.data(rs, "data_limite"), rs.getBigDecimal("taxa_rendimento_mensal"),
                StatusMeta.valueOf(rs.getString("status")));
    }

    public List<Meta> listar(long usuarioId) {
        return Jdbc.listar("select * from meta where usuario_id=? order by case status when 'CANCELADA' then 2 " +
                "when 'CONCLUIDA' then 1 else 0 end, data_limite", MetaRepository::mapear, usuarioId);
    }

    public List<Meta> listarEmAndamento(long usuarioId) {
        return Jdbc.listar("select * from meta where usuario_id=? and status in ('ATIVA','EM_RISCO') order by data_limite",
                MetaRepository::mapear, usuarioId);
    }

    public Optional<Meta> buscar(long id, long usuarioId) {
        return Jdbc.buscar("select * from meta where id=? and usuario_id=?", MetaRepository::mapear, id, usuarioId);
    }

    public long inserir(Meta m) {
        return Jdbc.inserir("insert into meta (usuario_id,nome,valor_alvo,valor_acumulado,data_limite,taxa_rendimento_mensal," +
                        "status,criada_em) values (?,?,?,?,?,?,?,?)", m.usuarioId(), m.nome(), m.valorAlvo(), m.valorAcumulado(),
                m.dataLimite(), m.taxaRendimentoMensal(), m.status(), LocalDateTime.now());
    }

    public void atualizar(Meta m) {
        Jdbc.executar("update meta set nome=?, valor_alvo=?, valor_acumulado=?, data_limite=?, taxa_rendimento_mensal=?, status=? " +
                        "where id=? and usuario_id=?", m.nome(), m.valorAlvo(), m.valorAcumulado(), m.dataLimite(),
                m.taxaRendimentoMensal(), m.status(), m.id(), m.usuarioId());
    }
}
