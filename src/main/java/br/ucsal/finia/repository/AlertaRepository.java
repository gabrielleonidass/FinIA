package br.ucsal.finia.repository;

import br.ucsal.finia.infra.Jdbc;
import br.ucsal.finia.model.Alerta;
import br.ucsal.finia.model.ConfiguracaoAlerta;
import br.ucsal.finia.model.Enums.CanalAlerta;
import br.ucsal.finia.model.Enums.TipoAlerta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Acesso às tabelas alerta e configuracao_alerta (RF08). */
public class AlertaRepository {

    private static Alerta mapear(ResultSet rs) throws SQLException {
        return new Alerta(rs.getLong("id"), rs.getLong("usuario_id"), TipoAlerta.valueOf(rs.getString("tipo")),
                rs.getString("mensagem"), rs.getBoolean("lido"), Jdbc.dataHora(rs, "criado_em"));
    }

    private static ConfiguracaoAlerta mapearConfig(ResultSet rs) throws SQLException {
        return new ConfiguracaoAlerta(rs.getLong("id"), rs.getLong("usuario_id"), TipoAlerta.valueOf(rs.getString("tipo")),
                rs.getBoolean("ativo"), CanalAlerta.valueOf(rs.getString("canal")));
    }

    public List<Alerta> listar(long usuarioId, int limite) {
        return Jdbc.listar("select * from alerta where usuario_id=? order by criado_em desc, id desc limit " + limite,
                AlertaRepository::mapear, usuarioId);
    }

    public long contarNaoLidos(long usuarioId) {
        return Jdbc.numero("select count(*) from alerta where usuario_id=? and lido=false", usuarioId);
    }

    public boolean existeRecente(long usuarioId, TipoAlerta tipo, String mensagem, LocalDateTime desde) {
        return Jdbc.numero("select count(*) from alerta where usuario_id=? and tipo=? and mensagem=? and criado_em > ?",
                usuarioId, tipo, mensagem, desde) > 0;
    }

    public boolean existeNoPeriodo(long usuarioId, TipoAlerta tipo, String trechoLike, LocalDateTime desde) {
        return Jdbc.numero("select count(*) from alerta where usuario_id=? and tipo=? and lower(mensagem) like lower(?) and criado_em >= ?",
                usuarioId, tipo, trechoLike, desde) > 0;
    }

    public long inserir(long usuarioId, TipoAlerta tipo, String mensagem, LocalDateTime quando) {
        return Jdbc.inserir("insert into alerta (usuario_id,tipo,mensagem,lido,criado_em) values (?,?,?,false,?)",
                usuarioId, tipo, mensagem, quando);
    }

    public void marcarLido(long id, long usuarioId) {
        Jdbc.executar("update alerta set lido=true where id=? and usuario_id=?", id, usuarioId);
    }

    public void marcarTodosLidos(long usuarioId) {
        Jdbc.executar("update alerta set lido=true where usuario_id=?", usuarioId);
    }

    public List<ConfiguracaoAlerta> listarConfiguracoes(long usuarioId) {
        return Jdbc.listar("select * from configuracao_alerta where usuario_id=? order by id", AlertaRepository::mapearConfig, usuarioId);
    }

    public Optional<ConfiguracaoAlerta> buscarConfiguracao(long usuarioId, TipoAlerta tipo) {
        return Jdbc.buscar("select * from configuracao_alerta where usuario_id=? and tipo=?", AlertaRepository::mapearConfig, usuarioId, tipo);
    }

    public void salvarConfiguracao(long usuarioId, TipoAlerta tipo, boolean ativo, CanalAlerta canal) {
        if (buscarConfiguracao(usuarioId, tipo).isPresent()) {
            Jdbc.executar("update configuracao_alerta set ativo=?, canal=? where usuario_id=? and tipo=?", ativo, canal, usuarioId, tipo);
        } else {
            Jdbc.inserir("insert into configuracao_alerta (usuario_id,tipo,ativo,canal) values (?,?,?,?)", usuarioId, tipo, ativo, canal);
        }
    }
}
