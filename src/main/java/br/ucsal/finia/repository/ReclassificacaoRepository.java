package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Auditoria das reclassificações manuais (RF04 / RN05). */
public class ReclassificacaoRepository {

    public void inserir(long transacaoId, long usuarioId, Long categoriaAnterior, long categoriaNova, String padrao) {
        Jdbc.inserir("insert into reclassificacao (transacao_id,usuario_id,categoria_anterior_id,categoria_nova_id,padrao,data_hora) " +
                "values (?,?,?,?,?,?)", transacaoId, usuarioId, categoriaAnterior, categoriaNova, padrao, LocalDateTime.now());
    }

    /** Categorias das últimas N correções para o mesmo padrão, da mais recente para a mais antiga. */
    public List<Long> ultimasCategoriasDoPadrao(long usuarioId, String padrao, int limite) {
        return Jdbc.listar("select categoria_nova_id from reclassificacao where usuario_id=? and padrao=? " +
                "order by data_hora desc, id desc limit " + limite, rs -> rs.getLong(1), usuarioId, padrao);
    }

    public List<Map<String, Object>> historico(long usuarioId, int limite) {
        return Jdbc.listar("select r.data_hora, r.padrao, ca.nome anterior, cn.nome nova from reclassificacao r " +
                        "left join categoria ca on ca.id=r.categoria_anterior_id join categoria cn on cn.id=r.categoria_nova_id " +
                        "where r.usuario_id=? order by r.data_hora desc, r.id desc limit " + limite,
                rs -> Map.of("dataHora", Jdbc.dataHora(rs, "data_hora").withNano(0).toString(),
                        "padrao", rs.getString("padrao"),
                        "anterior", rs.getString("anterior") == null ? "-" : rs.getString("anterior"),
                        "nova", rs.getString("nova")), usuarioId);
    }
}
