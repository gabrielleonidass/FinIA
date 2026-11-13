package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Enums.OrigemTransacao;
import br.ucsal.finia.model.Enums.StatusTransacao;
import br.ucsal.finia.model.Enums.TipoTransacao;
import br.ucsal.finia.model.Transacao;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Acesso à tabela transacao. */
public class TransacaoRepository {

    /** Pagamentos de fatura/transferências entre contas próprias não contam como receita ou despesa. */
    private static final String SEM_TRANSF = " and (categoria_id is null or categoria_id not in " +
            "(select id from categoria where nome = 'Transferências'))";

    private static final String SELECT = "select t.*, c.apelido conta_apelido, cat.nome cat_nome, cat.cor cat_cor " +
            "from transacao t join conta_financeira c on c.id = t.conta_id " +
            "left join categoria cat on cat.id = t.categoria_id ";

    private static Transacao mapear(ResultSet rs) throws SQLException {
        return new Transacao(rs.getLong("id"), rs.getLong("usuario_id"), rs.getLong("conta_id"),
                rs.getString("conta_apelido"), Jdbc.data(rs, "data"), rs.getString("descricao"),
                rs.getBigDecimal("valor"), TipoTransacao.valueOf(rs.getString("tipo")),
                Jdbc.longOuNulo(rs, "categoria_id"), rs.getString("cat_nome"), rs.getString("cat_cor"),
                rs.getDouble("confianca"), StatusTransacao.valueOf(rs.getString("status")),
                OrigemTransacao.valueOf(rs.getString("origem")));
    }

    public Optional<Transacao> buscar(long id, long usuarioId) {
        return Jdbc.buscar(SELECT + "where t.id = ? and t.usuario_id = ?", TransacaoRepository::mapear, id, usuarioId);
    }

    /** Lista com filtros opcionais (null = sem filtro). */
    public List<Transacao> listar(long usuarioId, LocalDate inicio, LocalDate fim, Long categoriaId,
                                  StatusTransacao status, Long contaId, String busca) {
        StringBuilder sql = new StringBuilder(SELECT + "where t.usuario_id = ?");
        List<Object> p = new ArrayList<>(List.of(usuarioId));
        if (inicio != null) { sql.append(" and t.data >= ?"); p.add(inicio); }
        if (fim != null) { sql.append(" and t.data <= ?"); p.add(fim); }
        if (categoriaId != null) { sql.append(" and t.categoria_id = ?"); p.add(categoriaId); }
        if (status != null) { sql.append(" and t.status = ?"); p.add(status); }
        if (contaId != null) { sql.append(" and t.conta_id = ?"); p.add(contaId); }
        if (busca != null && !busca.isBlank()) { sql.append(" and lower(t.descricao) like ?"); p.add("%" + busca.toLowerCase() + "%"); }
        sql.append(" order by t.data desc, t.id desc");
        return Jdbc.listar(sql.toString(), TransacaoRepository::mapear, p.toArray());
    }

    /** RN03: mesma data, valor e descrição = duplicada. */
    public boolean existeDuplicada(long usuarioId, LocalDate data, BigDecimal valor, String descricao) {
        return Jdbc.numero("select count(*) from transacao where usuario_id=? and data=? and valor=? and lower(descricao)=lower(?)",
                usuarioId, data, valor, descricao) > 0;
    }

    public long inserir(Transacao t) {
        return Jdbc.inserir("insert into transacao (usuario_id,conta_id,data,descricao,valor,tipo,categoria_id,confianca," +
                        "status,origem,criada_em) values (?,?,?,?,?,?,?,?,?,?,?)",
                t.usuarioId(), t.contaId(), t.data(), t.descricao(), t.valor(), t.tipo(), t.categoriaId(),
                t.confianca(), t.status(), t.origem(), LocalDateTime.now());
    }

    public void atualizarCategoria(long id, long categoriaId, double confianca, StatusTransacao status) {
        Jdbc.executar("update transacao set categoria_id=?, confianca=?, status=? where id=?", categoriaId, confianca, status, id);
    }

    public long contarPorStatus(long usuarioId, StatusTransacao status) {
        return Jdbc.numero("select count(*) from transacao where usuario_id=? and status=?", usuarioId, status);
    }

    public BigDecimal somaPorTipo(long usuarioId, TipoTransacao tipo, LocalDate ini, LocalDate fim) {
        return Jdbc.decimal("select coalesce(sum(valor),0) from transacao where usuario_id=? and tipo=? and data between ? and ?" + SEM_TRANSF,
                usuarioId, tipo, ini, fim);
    }

    public BigDecimal somaDespesasCategoria(long usuarioId, long categoriaId, LocalDate ini, LocalDate fim) {
        return Jdbc.decimal("select coalesce(sum(valor),0) from transacao where usuario_id=? and categoria_id=? " +
                "and tipo='DESPESA' and data between ? and ?", usuarioId, categoriaId, ini, fim);
    }

    /** Média histórica das despesas da categoria, excluindo uma transação (RF08). */
    public BigDecimal mediaDespesasCategoria(long usuarioId, long categoriaId, long ignorarId) {
        return Jdbc.decimal("select coalesce(avg(valor),0) from transacao where usuario_id=? and categoria_id=? " +
                "and tipo='DESPESA' and id <> ?", usuarioId, categoriaId, ignorarId);
    }

    public long contarDespesasCategoria(long usuarioId, long categoriaId, long ignorarId) {
        return Jdbc.numero("select count(*) from transacao where usuario_id=? and categoria_id=? and tipo='DESPESA' and id <> ?",
                usuarioId, categoriaId, ignorarId);
    }

    /** Total de despesas por categoria no período: nome -> [cor, total]. */
    public Map<String, Object[]> despesasPorCategoria(long usuarioId, LocalDate ini, LocalDate fim) {
        Map<String, Object[]> m = new LinkedHashMap<>();
        Jdbc.listar("select cat.nome, cat.cor, sum(t.valor) total from transacao t join categoria cat on cat.id=t.categoria_id " +
                        "where t.usuario_id=? and t.tipo='DESPESA' and t.data between ? and ? and cat.nome <> 'Transferências' group by cat.nome, cat.cor order by total desc",
                rs -> m.put(rs.getString(1), new Object[]{rs.getString(2), rs.getBigDecimal(3)}), usuarioId, ini, fim);
        return m;
    }

    /** Histórico de categorias atribuídas a descrições parecidas (sinal do histórico do usuário). */
    public List<Long> categoriasDeDescricoesSemelhantes(long usuarioId, String padrao) {
        return Jdbc.listar("select categoria_id from transacao where usuario_id=? and status='RECLASSIFICADA' " +
                "and upper(descricao) like ?", rs -> rs.getLong(1), usuarioId, "%" + padrao.toUpperCase() + "%");
    }
}
