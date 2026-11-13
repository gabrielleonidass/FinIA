package br.ucsal.finia.infra;

import br.ucsal.finia.model.ContaFinanceira;
import br.ucsal.finia.model.Enums.*;
import br.ucsal.finia.model.Meta;
import br.ucsal.finia.model.Transacao;
import br.ucsal.finia.model.Usuario;
import br.ucsal.finia.repository.*;
import br.ucsal.finia.service.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Random;

/**
 * Carga inicial: categorias padrão e um usuário de demonstração com seis meses
 * de movimentação fictícia, metas, orçamentos, regras e grupo familiar.
 * Executada somente quando o banco está vazio.
 */
public class DadosIniciais {

    public static final String EMAIL_DEMO = "demo@finia.com";
    public static final String SENHA_DEMO = "Demo@1234";

    private final UsuarioRepository usuarios;
    private final CategoriaRepository categorias;
    private final ContaRepository contas;
    private final TransacaoService transacoes;
    private final MetaRepository metas;
    private final OrcamentoService orcamentos;
    private final AlertaRepository alertaRepo;
    private final AlertaService alertas;
    private final RegraRepository regras;
    private final FamiliaRepository familia;
    private final UsuarioService usuarioService;
    private final Random rnd = new Random(2026);

    public DadosIniciais(UsuarioRepository usuarios, CategoriaRepository categorias, ContaRepository contas,
                         TransacaoService transacoes, MetaRepository metas, OrcamentoService orcamentos,
                         AlertaRepository alertaRepo, AlertaService alertas, RegraRepository regras,
                         FamiliaRepository familia, UsuarioService usuarioService) {
        this.usuarios = usuarios;
        this.categorias = categorias;
        this.contas = contas;
        this.transacoes = transacoes;
        this.metas = metas;
        this.orcamentos = orcamentos;
        this.alertaRepo = alertaRepo;
        this.alertas = alertas;
        this.regras = regras;
        this.familia = familia;
        this.usuarioService = usuarioService;
    }

    public void carregar() {
        if (categorias.contar() == 0) criarCategorias();
        if (usuarios.contar() == 0) criarUsuarioDemo();
    }

    private void criarCategorias() {
        categorias.inserir("Alimentação", "#F97316", false);
        categorias.inserir("Transporte", "#3B82F6", false);
        categorias.inserir("Moradia", "#8B5CF6", false);
        categorias.inserir("Saúde", "#EF4444", false);
        categorias.inserir("Lazer", "#EC4899", false);
        categorias.inserir("Assinaturas", "#14B8A6", false);
        categorias.inserir("Educação", "#EAB308", false);
        categorias.inserir("Compras", "#6366F1", false);
        categorias.inserir("Outros", "#94A3B8", false);
        categorias.inserir("Transferências", "#64748B", false);
        categorias.inserir("Salário", "#16A34A", true);
        categorias.inserir("Outras receitas", "#22C55E", true);
    }

    private long cat(String nome) {
        return categorias.buscarPorNome(nome).orElseThrow().id();
    }

    private void criarUsuarioDemo() {
        Usuario u = usuarioService.cadastrar("Ana Beatriz Souza", EMAIL_DEMO, "529.982.247-25", SENHA_DEMO);
        usuarios.atualizarStatus(u.id(), StatusUsuario.ATIVA);
        long id = u.id();

        long itau = contas.inserir(new ContaFinanceira(null, id, "Itaú Salário", "Itaú Unibanco", TipoConta.CONTA_CORRENTE,
                new BigDecimal("850.00"), null, true, true));
        long nubank = contas.inserir(new ContaFinanceira(null, id, "Nubank Roxinho", "Nubank", TipoConta.CARTAO_CREDITO,
                BigDecimal.ZERO, 15, true, true));
        long poupanca = contas.inserir(new ContaFinanceira(null, id, "Poupança Caixa", "Caixa Econômica Federal", TipoConta.POUPANCA,
                new BigDecimal("6800.00"), null, true, false));
        long antigo = contas.inserir(new ContaFinanceira(null, id, "Cartão Loja (antigo)", "Banco Pan", TipoConta.CARTAO_CREDITO,
                BigDecimal.ZERO, 8, true, false));

        // Durante a carga histórica os alertas ficam desligados para não poluir a caixa de entrada.
        for (TipoAlerta t : TipoAlerta.values()) alertaRepo.salvarConfiguracao(id, t, false, CanalAlerta.AMBOS);

        regras.salvar(id, "POSTO IPIRANGA", cat("Transporte"));

        YearMonth atual = YearMonth.now();
        for (int k = 5; k >= 1; k--) mesHistorico(id, atual.minusMonths(k), itau, nubank, poupanca, antigo, k);
        lancar(id, antigo, atual.minusMonths(4).atDay(20), "RIACHUELO LOJA 0332", "189.90");
        contas.definirAtiva(antigo, id, false);

        // correções antigas do usuário (aprendizado): marmita da vizinha = Alimentação
        for (Transacao t : transacoes.listar(id, null, null, null, null, null, "JOSEMARIA")) {
            transacoes.reclassificar(id, t.id(), cat("Alimentação"));
        }

        // reativa alertas e limpa os gerados na carga
        alertaRepo.marcarTodosLidos(id);
        alertaRepo.listar(id, 1000).forEach(a -> Jdbc.executar("delete from alerta where id = ?", a.id()));
        for (TipoAlerta t : TipoAlerta.values()) alertaRepo.salvarConfiguracao(id, t, true, CanalAlerta.AMBOS);
        alertaRepo.inserir(id, TipoAlerta.VENCIMENTO_FATURA, "A fatura do cartão Nubank Roxinho vence em 15/10 (3 dias).",
                atual.minusMonths(1).atDay(12).atTime(9, 0));
        alertaRepo.marcarTodosLidos(id);

        // orçamentos do mês corrente (RF09)
        orcamentos.definir(id, cat("Alimentação"), atual, new BigDecimal("800"));
        orcamentos.definir(id, cat("Transporte"), atual, new BigDecimal("400"));
        orcamentos.definir(id, cat("Lazer"), atual, new BigDecimal("500"));
        orcamentos.definir(id, cat("Moradia"), atual, new BigDecimal("3500"));
        orcamentos.definir(id, cat("Assinaturas"), atual, new BigDecimal("120"));
        orcamentos.definir(id, cat("Saúde"), atual, new BigDecimal("300"));
        orcamentos.definir(id, cat("Compras"), atual, new BigDecimal("400"));

        mesAtual(id, atual, itau, nubank);

        // metas (RF05)
        metas.inserir(new Meta(null, id, "Reserva de emergência", new BigDecimal("18000"), new BigDecimal("7400"),
                atual.plusMonths(11).atEndOfMonth(), new BigDecimal("0.80"), StatusMeta.ATIVA));
        metas.inserir(new Meta(null, id, "Viagem para a Chapada Diamantina", new BigDecimal("4500"), new BigDecimal("1350"),
                atual.plusMonths(5).atEndOfMonth(), null, StatusMeta.ATIVA));
        metas.inserir(new Meta(null, id, "Entrada do apartamento", new BigDecimal("60000"), new BigDecimal("12500"),
                atual.plusMonths(25).atEndOfMonth(), new BigDecimal("0.85"), StatusMeta.ATIVA));
        metas.inserir(new Meta(null, id, "Curso de inglês", new BigDecimal("2400"), new BigDecimal("600"),
                atual.plusMonths(8).atEndOfMonth(), null, StatusMeta.PAUSADA));
        metas.inserir(new Meta(null, id, "Notebook para a faculdade", new BigDecimal("4200"), new BigDecimal("4200"),
                atual.minusMonths(1).atEndOfMonth().plusMonths(2), null, StatusMeta.CONCLUIDA));

        // grupo familiar (RF12)
        long grupo = familia.criarGrupo("Família Souza", id);
        long carlos = familia.convidar(grupo, "Carlos Souza", "carlos.souza@email.com", PermissaoMembro.EDITAR);
        familia.aceitar(carlos, grupo);
        long marina = familia.convidar(grupo, "Marina Souza", "marina.souza@email.com", PermissaoMembro.VISUALIZAR);
        familia.aceitar(marina, grupo);
        familia.convidar(grupo, "Pedro Souza", "pedro.souza@email.com", PermissaoMembro.VISUALIZAR);
    }

    private void mesHistorico(long id, YearMonth m, long itau, long nubank, long poupanca, long antigo, int k) {
        lancarReceita(id, itau, m.atDay(5), "SALARIO ACME TECNOLOGIA LTDA", "6200.00");
        if (k % 2 == 0) lancarReceita(id, itau, m.atDay(18), "PIX RECEBIDO FREELA DESIGN", "850.00");
        lancarReceita(id, poupanca, m.atDay(28), "RENDIMENTO POUPANCA", v(38, 52));

        lancar(id, itau, m.atDay(10), "ALUGUEL APTO PITUBA", "1800.00");
        lancar(id, itau, m.atDay(10), "CONDOMINIO EDF MAR AZUL", "420.00");
        lancar(id, itau, m.atDay(12), "COELBA ENERGIA ELETRICA", v(178, 236));
        lancar(id, itau, m.atDay(14), "EMBASA AGUA E ESGOTO", v(78, 108));
        lancar(id, nubank, m.atDay(8), "INTERNET CLARO FIBRA", "119.90");

        for (int i = 0; i < 2 + rnd.nextInt(2); i++) lancar(id, nubank, dia(m), "SUPERMERCADO GBARBOSA", v(170, 310));
        for (int i = 0; i < 3 + rnd.nextInt(3); i++) lancar(id, nubank, dia(m), "IFOOD *RESTAURANTE " + pick("SABOR DA BAHIA", "SUSHI KAI", "BURGUER HOUSE", "CANTINA ITALIA"), v(34, 82));
        for (int i = 0; i < 2 + rnd.nextInt(2); i++) lancar(id, nubank, dia(m), "PADARIA PAO QUENTE", v(14, 36));
        for (int i = 0; i < 5 + rnd.nextInt(4); i++) lancar(id, nubank, dia(m), "UBER *TRIP " + (1000 + rnd.nextInt(8999)), v(11, 39));
        lancar(id, nubank, dia(m), "POSTO IPIRANGA AV PARALELA", v(150, 225));
        lancar(id, nubank, m.atDay(3), "NETFLIX.COM", "55.90");
        lancar(id, nubank, m.atDay(6), "SPOTIFY BRASIL", "21.90");
        lancar(id, nubank, m.atDay(2), "SMARTFIT ACADEMIA", "109.90");
        lancar(id, nubank, m.atDay(4), "CURSO ALURA ASSINATURA", "89.90");
        if (rnd.nextBoolean()) lancar(id, nubank, dia(m), "DROGASIL FARMACIA 0921", v(38, 125));
        lancar(id, nubank, dia(m), "CINEMARK SALVADOR SHOP", v(42, 72));
        lancar(id, nubank, dia(m), "BAR DO JUAREZ RIO VERMELHO", v(48, 90));
        lancar(id, nubank, dia(m), pick("MERCADO LIVRE *COMPRA", "SHOPEE BRASIL", "AMAZON MARKETPLACE", "RENNER SHOPPING BARRA"), v(70, 240));
        if (k >= 2) lancar(id, itau, dia(m), "PAG*JOSEMARIA SILVA", v(40, 55));
        if (k == 3) lancar(id, itau, dia(m), "TED 341 ROBERTO ALVES", "300.00");
        pagarFatura(id, m, itau, nubank);
    }

    /** Pagamento da fatura do mês m no dia 10 do mês seguinte (transferência entre contas próprias). */
    private void pagarFatura(long id, YearMonth m, long itau, long nubank) {
        BigDecimal fatura = transacoes.listar(id, m.atDay(1), m.atEndOfMonth(), null, null, nubank, null).stream()
                .filter(Transacao::despesa).map(Transacao::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate pagamento = m.plusMonths(1).atDay(10);
        if (pagamento.isAfter(LocalDate.now())) return;
        transacoes.registrar(id, itau, pagamento, "PAGAMENTO FATURA NUBANK", fatura, TipoTransacao.DESPESA, OrigemTransacao.OPEN_FINANCE);
        transacoes.registrar(id, nubank, pagamento, "PAGAMENTO FATURA RECEBIDO", fatura, TipoTransacao.RECEITA, OrigemTransacao.OPEN_FINANCE);
    }

    private void mesAtual(long id, YearMonth m, long itau, long nubank) {
        lancarReceita(id, itau, m.atDay(5), "SALARIO ACME TECNOLOGIA LTDA", "6200.00");
        lancar(id, itau, m.atDay(10), "ALUGUEL APTO PITUBA", "1800.00");
        lancar(id, itau, m.atDay(10), "CONDOMINIO EDF MAR AZUL", "420.00");
        lancar(id, itau, m.atDay(12), "COELBA ENERGIA ELETRICA", "214.37");
        lancar(id, nubank, m.atDay(8), "INTERNET CLARO FIBRA", "119.90");
        // Alimentação: total de R$ 850,00 para orçamento de R$ 800,00 (critério do RF09)
        lancar(id, nubank, m.atDay(3), "SUPERMERCADO GBARBOSA", "312.40");
        lancar(id, nubank, m.atDay(4), "IFOOD *RESTAURANTE SUSHI KAI", "68.90");
        lancar(id, nubank, m.atDay(6), "PADARIA PAO QUENTE", "24.50");
        lancar(id, nubank, m.atDay(9), "SUPERMERCADO GBARBOSA", "287.30");
        lancar(id, nubank, m.atDay(11), "IFOOD *RESTAURANTE SABOR DA BAHIA", "74.90");
        lancar(id, nubank, m.atDay(12), "PADARIA PAO QUENTE", "22.00");
        lancar(id, nubank, m.atDay(12), "HORTIFRUTI BOA SAFRA", "60.00");
        // Transporte: R$ 328,00 de R$ 400,00 (82% - em alerta)
        String[] uber = {"18.40", "26.90", "22.10", "31.50", "19.10"};
        for (int i = 0; i < uber.length; i++) lancar(id, nubank, m.atDay(2 + i * 2), "UBER *TRIP " + (4410 + i * 37), uber[i]);
        lancar(id, nubank, m.atDay(7), "POSTO IPIRANGA AV PARALELA", "210.00");
        lancar(id, nubank, m.atDay(3), "NETFLIX.COM", "55.90");
        lancar(id, nubank, m.atDay(6), "SPOTIFY BRASIL", "21.90");
        lancar(id, nubank, m.atDay(2), "SMARTFIT ACADEMIA", "109.90");
        lancar(id, nubank, m.atDay(4), "CURSO ALURA ASSINATURA", "89.90");
        lancar(id, nubank, m.atDay(8), "MERCADO LIVRE *COMPRA", "129.90");
        lancar(id, nubank, m.atDay(5), "CINEMARK SALVADOR SHOP", "64.00");
        lancar(id, itau, m.atDay(9), "PIX ENVIADO CARLOS EDUARDO", "150.00");
        lancar(id, nubank, m.atDay(11), "MP *LOJAVIRTUAL 7731", "67.40");
        // gasto atípico em Lazer (gera alerta real pelo serviço)
        lancar(id, nubank, m.atDay(12), "SYMPLA INGRESSO SHOW IVETE", "420.00");
        alertas.verificarFaturas(id);
    }

    private void lancar(long id, long conta, LocalDate data, String desc, String valor) {
        if (data.isAfter(LocalDate.now())) data = LocalDate.now();
        transacoes.registrar(id, conta, data, desc, new BigDecimal(valor), TipoTransacao.DESPESA, OrigemTransacao.OPEN_FINANCE);
    }

    private void lancarReceita(long id, long conta, LocalDate data, String desc, String valor) {
        if (data.isAfter(LocalDate.now())) data = LocalDate.now();
        transacoes.registrar(id, conta, data, desc, new BigDecimal(valor), TipoTransacao.RECEITA, OrigemTransacao.OPEN_FINANCE);
    }

    private LocalDate dia(YearMonth m) {
        return m.atDay(1 + rnd.nextInt(m.lengthOfMonth()));
    }

    private String v(int min, int max) {
        return BigDecimal.valueOf(min + rnd.nextDouble() * (max - min)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String pick(String... opcoes) {
        return opcoes[rnd.nextInt(opcoes.length)];
    }
}
