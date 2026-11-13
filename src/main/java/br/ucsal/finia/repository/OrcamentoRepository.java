package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Orcamento;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Acesso à tabela orcamento (RF09). */
public class OrcamentoRepository {

    private static final String SELECT = "select o.*, c.nome cat_nome, c.cor cat_cor from orcamento o join categoria c on c.id=o.categoria_id ";

    private static Orcamento mapear(ResultSet rs) throws SQLException {
        return new Orcamento(rs.getLong("id"), rs.getLong("usuario_id"), rs.getLong("categoria_id"), rs.getString("cat_nome"),
                rs.getString("cat_cor"), rs.getBigDecimal("limite"), rs.getString("mes_referencia"));
    }

    public List<Orcamento> listar(long usuarioId, String mes) {
        return Jdbc.listar(SELECT + "where o.usuario_id=? and o.mes_referencia=? order by c.nome", OrcamentoRepository::mapear, usuarioId, mes);
    }

    public Optional<Orcamento> buscarPorCategoria(long usuarioId, long categoriaId, String mes) {
        return Jdbc.buscar(SELECT + "where o.usuario_id=? and o.categoria_id=? and o.mes_referencia=?",
                OrcamentoRepository::mapear, usuarioId, categoriaId, mes);
    }

    public void salvar(long usuarioId, long categoriaId, String mes, BigDecimal limite) {
        if (buscarPorCategoria(usuarioId, categoriaId, mes).isPresent()) {
            Jdbc.executar("update orcamento set limite=? where usuario_id=? and categoria_id=? and mes_referencia=?",
                    limite, usuarioId, categoriaId, mes);
        } else {
            Jdbc.inserir("insert into orcamento (usuario_id,categoria_id,limite,mes_referencia) values (?,?,?,?)",
                    usuarioId, categoriaId, limite, mes);
        }
    }

    public int remover(long id, long usuarioId) {
        return Jdbc.executar("delete from orcamento where id=? and usuario_id=?", id, usuarioId);
    }
}
