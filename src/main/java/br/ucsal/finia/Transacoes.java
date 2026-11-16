package br.ucsal.finia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Telas Transações (RF03, RF04) e Início (RF07). */
public class Transacoes {

    static void rotas(Web web) {
        web.get("/api/inicio", Transacoes::resumoDoPeriodo);
        web.get("/api/categorias", p -> Banco.linhas("SELECT * FROM categoria ORDER BY tipo, nome"));
        web.get("/api/transacoes", p -> {
            YearMonth mes = mes(p.texto("mes"));
            return listar(p.usuarioId(), mes.atDay(1), mes.atEndOfMonth());
        });
        web.get("/api/transacoes/sugestao", p -> {
            if (p.texto("descricao") == null) return Web.obj("categoria", null, "motivo", "");
            Categorizador.Sugestao s = Categorizador.sugerir(p.usuarioId(), p.texto("descricao"),
                    p.texto("tipo") == null ? "DESPESA" : p.texto("tipo"));
            return Web.obj("categoria", s.categoria(), "motivo", s.motivo(), "confianca", s.confianca(),
                    "revisar", s.revisar(), "limiar", Categorizador.LIMIAR_CONFIANCA);
        });
        web.post("/api/transacoes", p -> comPerguntaDeRegra(p.usuarioId(), registrar(p.usuarioId(),
                p.texto("descricao"), p.decimal("valor"), p.texto("tipo"), p.data("data"), p.numero("categoriaId"))));
        web.put("/api/transacoes/{id}/categoria", p -> comPerguntaDeRegra(p.usuarioId(),
                trocarCategoria(p.usuarioId(), p.id(), p.numero("categoriaId"))));
        web.delete("/api/transacoes/{id}", p -> {
            if (Banco.executar("DELETE FROM transacao WHERE id = ? AND usuario_id = ?", p.id(), p.usuarioId()) == 0)
                throw new Web.Erro(404, "Transação não encontrada.");
            return null;
        });

        // Regras de categoria (RF04)
        web.get("/api/regras", p -> Banco.linhas("""
                SELECT r.id, r.padrao, c.nome AS categoria, c.cor FROM regra_categoria r
                JOIN categoria c ON c.id = r.categoria_id WHERE r.usuario_id = ? AND r.aceita = TRUE ORDER BY r.padrao""",
                p.usuarioId()));
        web.post("/api/regras", p -> {
            responderRegra(p.usuarioId(), p.texto("padrao"), p.numero("categoriaId"), p.simOuNao("aceitar"));
            return null;
        });
        web.delete("/api/regras/{id}", p -> {
            Banco.executar("DELETE FROM regra_categoria WHERE id = ? AND usuario_id = ?", p.id(), p.usuarioId());
            return null;
        });
    }

    // ================= transações =================

    private static final String SELECT = """
            SELECT t.*, c.nome AS cat_nome, c.tipo AS cat_tipo, c.cor AS cat_cor
            FROM transacao t JOIN categoria c ON c.id = t.categoria_id""";

    /** Cadastra uma transação. Sem categoria escolhida, a IA escolhe. */
    static Map<String, Object> registrar(long usuarioId, String descricao, BigDecimal valor, String tipo,
                                         LocalDate data, Long categoriaId) {
        if (descricao == null) throw new Web.Erro(400, "Informe a descrição.");
        if (valor == null || valor.signum() <= 0) throw new Web.Erro(400, "O valor deve ser maior que zero.");
        if (!"RECEITA".equals(tipo) && !"DESPESA".equals(tipo)) throw new Web.Erro(400, "Escolha se é receita ou despesa.");
        if (data == null) throw new Web.Erro(400, "Informe a data.");

        Integer confianca = null;
        if (categoriaId == null) {
            Categorizador.Sugestao s = Categorizador.sugerir(usuarioId, descricao, tipo);
            categoriaId = (Long) s.categoria().get("id");
            confianca = s.confianca();
        } else {
            conferirCategoria(categoriaId, tipo);
        }
        long id = Banco.inserir("""
                INSERT INTO transacao (usuario_id, descricao, valor, tipo, data, categoria_id, categoria_automatica, confianca)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                usuarioId, descricao, valor, tipo, data, categoriaId, confianca != null, confianca);
        return buscar(usuarioId, id);
    }

    /** O usuário corrige a categoria: deixa de ser automática e perde a confiança da IA. */
    static Map<String, Object> trocarCategoria(long usuarioId, long id, Long categoriaId) {
        Map<String, Object> t = buscar(usuarioId, id);
        if (t == null) throw new Web.Erro(404, "Transação não encontrada.");
        conferirCategoria(categoriaId, (String) t.get("tipo"));
        Banco.executar("""
                UPDATE transacao SET categoria_id = ?, categoria_automatica = FALSE, confianca = NULL
                WHERE id = ? AND usuario_id = ?""", categoriaId, id, usuarioId);
        return buscar(usuarioId, id);
    }

    static List<Map<String, Object>> listar(long usuarioId, LocalDate de, LocalDate ate) {
        return Banco.linhas(SELECT + " WHERE t.usuario_id = ? AND t.data BETWEEN ? AND ? ORDER BY t.data DESC, t.id DESC",
                usuarioId, de, ate).stream().map(Transacoes::paraTela).toList();
    }

    private static Map<String, Object> buscar(long usuarioId, long id) {
        Map<String, Object> linha = Banco.linha(SELECT + " WHERE t.usuario_id = ? AND t.id = ?", usuarioId, id);
        return linha == null ? null : paraTela(linha);
    }

    /** Arruma a linha do banco no formato que a tela usa. */
    private static Map<String, Object> paraTela(Map<String, Object> t) {
        boolean automatica = (Boolean) t.get("categoria_automatica");
        Integer confianca = (Integer) t.get("confianca");
        return Web.obj("id", t.get("id"), "descricao", t.get("descricao"), "valor", t.get("valor"),
                "tipo", t.get("tipo"), "data", t.get("data"),
                "categoria", Web.obj("id", t.get("categoria_id"), "nome", t.get("cat_nome"),
                        "tipo", t.get("cat_tipo"), "cor", t.get("cat_cor")),
                "categoriaAutomatica", automatica, "confianca", confianca,
                "revisar", automatica && confianca != null && confianca < Categorizador.LIMIAR_CONFIANCA);
    }

    private static void conferirCategoria(Long categoriaId, String tipo) {
        Map<String, Object> c = categoriaId == null ? null : Banco.linha("SELECT * FROM categoria WHERE id = ?", categoriaId);
        if (c == null) throw new Web.Erro(400, "Categoria inválida.");
        if (!c.get("tipo").equals(tipo)) throw new Web.Erro(400, "Essa categoria não serve para esse tipo de transação.");
    }

    // ================= RF04: regra depois de 3 correções seguidas =================

    /** Devolve a transação e, se for a 3ª correção seguida do mesmo estabelecimento, a pergunta "criar regra?". */
    private static Map<String, Object> comPerguntaDeRegra(long usuarioId, Map<String, Object> t) {
        t.put("sugestaoRegra", sugestaoDeRegra(usuarioId, t));
        return t;
    }

    /** A pergunta só aparece se as 3 últimas transações do estabelecimento foram corrigidas para a mesma categoria. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> sugestaoDeRegra(long usuarioId, Map<String, Object> t) {
        if ((Boolean) t.get("categoriaAutomatica")) return null;
        String padrao = Categorizador.padraoDoEstabelecimento((String) t.get("descricao"));
        if (Banco.linha("SELECT id FROM regra_categoria WHERE usuario_id = ? AND padrao = ?", usuarioId, padrao) != null)
            return null; // já respondeu (sim ou não)

        Map<String, Object> categoria = (Map<String, Object>) t.get("categoria");
        List<Map<String, Object>> ultimas3 = Banco.linhas(
                "SELECT descricao, categoria_id, categoria_automatica FROM transacao WHERE usuario_id = ? AND tipo = ? "
                        + "ORDER BY data DESC, id DESC", usuarioId, t.get("tipo")).stream()
                .filter(x -> Categorizador.padraoDoEstabelecimento((String) x.get("descricao")).equals(padrao))
                .limit(3).toList();
        boolean tresCorrecoesIguais = ultimas3.size() == 3 && ultimas3.stream().allMatch(x ->
                !(Boolean) x.get("categoria_automatica") && x.get("categoria_id").equals(categoria.get("id")));
        return tresCorrecoesIguais ? Web.obj("padrao", padrao, "categoria", categoria, "correcoes", 3) : null;
    }

    /** Aceitar cria a regra; recusar fica guardado para não perguntar de novo. */
    static void responderRegra(long usuarioId, String padrao, Long categoriaId, boolean aceitar) {
        if (padrao == null || categoriaId == null) throw new Web.Erro(400, "Informe o estabelecimento e a categoria.");
        padrao = Categorizador.padraoDoEstabelecimento(padrao);
        Banco.executar("DELETE FROM regra_categoria WHERE usuario_id = ? AND padrao = ?", usuarioId, padrao);
        Banco.inserir("INSERT INTO regra_categoria (usuario_id, padrao, categoria_id, aceita) VALUES (?, ?, ?, ?)",
                usuarioId, padrao, categoriaId, aceitar);
    }

    // ================= RF07: resumo da tela Início =================

    /** Mês (padrão), últimos 3 meses, últimos 12 meses ou período personalizado. */
    private static Map<String, Object> resumoDoPeriodo(Web.Pedido p) {
        YearMonth atual = YearMonth.now();
        String periodo = p.texto("periodo") == null ? "mes" : p.texto("periodo");
        switch (periodo) {
            case "3m":
                return resumo(p.usuarioId(), atual.minusMonths(2).atDay(1), atual.atEndOfMonth(), "3m");
            case "12m":
                return resumo(p.usuarioId(), atual.minusMonths(11).atDay(1), atual.atEndOfMonth(), "12m");
            case "personalizado":
                LocalDate de = p.data("de"), ate = p.data("ate");
                if (de == null || ate == null) throw new Web.Erro(400, "Informe as datas do período.");
                if (ate.isBefore(de)) throw new Web.Erro(400, "A data final deve ser depois da inicial.");
                return resumo(p.usuarioId(), de, ate, "personalizado");
            default:
                YearMonth mes = mes(p.texto("mes"));
                return resumo(p.usuarioId(), mes.atDay(1), mes.atEndOfMonth(), mes.toString());
        }
    }

    /** Receitas, despesas, saldo, gastos por categoria, últimas 5 transações e metas. */
    static Map<String, Object> resumo(long usuarioId, LocalDate de, LocalDate ate, String rotulo) {
        String soma = "SELECT SUM(valor) AS total FROM transacao WHERE usuario_id = ? AND tipo = ? AND data BETWEEN ? AND ?";
        BigDecimal receitas = Banco.somar(soma, usuarioId, "RECEITA", de, ate);
        BigDecimal despesas = Banco.somar(soma, usuarioId, "DESPESA", de, ate);
        List<Map<String, Object>> gastos = Banco.linhas("""
                SELECT c.nome AS categoria, c.cor, SUM(t.valor) AS total
                FROM transacao t JOIN categoria c ON c.id = t.categoria_id
                WHERE t.usuario_id = ? AND t.tipo = 'DESPESA' AND t.data BETWEEN ? AND ?
                GROUP BY c.nome, c.cor ORDER BY total DESC""", usuarioId, de, ate);
        List<Map<String, Object>> ultimas = Banco.linhas(SELECT + " WHERE t.usuario_id = ? ORDER BY t.data DESC, t.id DESC LIMIT 5",
                usuarioId).stream().map(Transacoes::paraTela).toList();
        return Web.obj("mes", rotulo, "de", de, "ate", ate, "receitas", receitas, "despesas", despesas,
                "saldo", receitas.subtract(despesas), "gastosPorCategoria", gastos, "ultimas", ultimas,
                "metas", Metas.listar(usuarioId));
    }

    /** "2026-11" -> novembro de 2026 (vazio = mês atual). */
    private static YearMonth mes(String texto) {
        try {
            return texto == null ? YearMonth.now() : YearMonth.parse(texto);
        } catch (RuntimeException e) {
            throw new Web.Erro(400, "Mês inválido (use AAAA-MM).");
        }
    }
}
