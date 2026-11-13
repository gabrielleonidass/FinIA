package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.RegraCategorizacao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Regras permanentes de categorização por usuário (RF04). */
public class RegraRepository {

    private static RegraCategorizacao mapear(ResultSet rs) throws SQLException {
        return new RegraCategorizacao(rs.getLong("id"), rs.getLong("usuario_id"), rs.getString("padrao"),
                rs.getLong("categoria_id"), rs.getString("cat_nome"));
    }

    private static final String SELECT = "select r.*, c.nome cat_nome from regra_categorizacao r join categoria c on c.id=r.categoria_id ";

    public List<RegraCategorizacao> listar(long usuarioId) {
        return Jdbc.listar(SELECT + "where r.usuario_id=? order by r.padrao", RegraRepository::mapear, usuarioId);
    }

    public Optional<RegraCategorizacao> buscarPorPadrao(long usuarioId, String padrao) {
        return Jdbc.buscar(SELECT + "where r.usuario_id=? and r.padrao=?", RegraRepository::mapear, usuarioId, padrao);
    }

    public void salvar(long usuarioId, String padrao, long categoriaId) {
        if (buscarPorPadrao(usuarioId, padrao).isPresent()) {
            Jdbc.executar("update regra_categorizacao set categoria_id=? where usuario_id=? and padrao=?", categoriaId, usuarioId, padrao);
        } else {
            Jdbc.inserir("insert into regra_categorizacao (usuario_id,padrao,categoria_id,criada_em) values (?,?,?,?)",
                    usuarioId, padrao, categoriaId, LocalDateTime.now());
        }
    }

    public int remover(long id, long usuarioId) {
        return Jdbc.executar("delete from regra_categorizacao where id=? and usuario_id=?", id, usuarioId);
    }
}
