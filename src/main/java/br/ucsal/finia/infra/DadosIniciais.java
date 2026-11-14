package br.ucsal.finia.infra;

import br.ucsal.finia.model.TipoTransacao;
import br.ucsal.finia.model.Usuario;
import br.ucsal.finia.repository.CategoriaRepository;
import br.ucsal.finia.repository.MetaRepository;
import br.ucsal.finia.repository.TransacaoRepository;
import br.ucsal.finia.repository.UsuarioRepository;
import br.ucsal.finia.service.TransacaoService;
import br.ucsal.finia.service.UsuarioService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static br.ucsal.finia.model.TipoTransacao.DESPESA;
import static br.ucsal.finia.model.TipoTransacao.RECEITA;

/**
 * Cria as categorias e um usuário de demonstração com transações e metas,
 * para o sistema já abrir com dados. Só roda na primeira vez.
 */
public class DadosIniciais {

    public static final String EMAIL_DEMO = "demo@finia.com";
    public static final String SENHA_DEMO = "Demo@1234";

    private final CategoriaRepository categorias;
    private final UsuarioRepository usuarios;
    private final TransacaoRepository transacaoRepo;
    private final MetaRepository metas;
    private final UsuarioService usuarioService;
    private final TransacaoService transacaoService;

    public DadosIniciais(CategoriaRepository categorias, UsuarioRepository usuarios, TransacaoRepository transacaoRepo,
                         MetaRepository metas, UsuarioService usuarioService, TransacaoService transacaoService) {
        this.categorias = categorias;
        this.usuarios = usuarios;
        this.transacaoRepo = transacaoRepo;
        this.metas = metas;
        this.usuarioService = usuarioService;
        this.transacaoService = transacaoService;
    }

    public void carregar() {
        criarCategorias();
        if (usuarios.buscarPorEmail(EMAIL_DEMO).isEmpty()) criarUsuarioDemo();
    }

    public void criarCategorias() {
        categorias.inserirSeNaoExistir("Alimentação", DESPESA, "#E4572E");
        categorias.inserirSeNaoExistir("Transporte", DESPESA, "#F3A712");
        categorias.inserirSeNaoExistir("Moradia", DESPESA, "#4C6EF5");
        categorias.inserirSeNaoExistir("Saúde", DESPESA, "#12B886");
        categorias.inserirSeNaoExistir("Lazer", DESPESA, "#BE4BDB");
        categorias.inserirSeNaoExistir("Educação", DESPESA, "#15AABF");
        categorias.inserirSeNaoExistir("Compras", DESPESA, "#F06595");
        categorias.inserirSeNaoExistir("Outros", DESPESA, "#868E96");
        categorias.inserirSeNaoExistir("Salário", RECEITA, "#2B8A3E");
        categorias.inserirSeNaoExistir("Renda extra", RECEITA, "#5C940D");
        categorias.inserirSeNaoExistir("Outras receitas", RECEITA, "#74B816");
    }

    private void criarUsuarioDemo() {
        Usuario u = usuarioService.cadastrar("Ana Beatriz Souza", EMAIL_DEMO, SENHA_DEMO);
        long id = u.id();

        // Mês passado
        YearMonth passado = YearMonth.now().minusMonths(1);
        add(id, passado, 5, "Salário empresa", "4500.00", RECEITA);
        add(id, passado, 6, "Aluguel apartamento", "1200.00", DESPESA);
        add(id, passado, 8, "Supermercado Assaí", "420.30", DESPESA);
        add(id, passado, 10, "Conta de luz Coelba", "172.80", DESPESA);
        add(id, passado, 12, "Uber", "31.20", DESPESA);
        add(id, passado, 15, "Netflix", "55.90", DESPESA);
        add(id, passado, 18, "iFood pedido", "74.50", DESPESA);
        add(id, passado, 22, "Posto Shell gasolina", "180.00", DESPESA);
        add(id, passado, 25, "Farmácia Pague Menos", "63.40", DESPESA);
        add(id, passado, 27, "Cinema shopping", "56.00", DESPESA);

        // Mês atual (só até hoje)
        YearMonth atual = YearMonth.now();
        add(id, atual, 1, "Aluguel apartamento", "1200.00", DESPESA);
        add(id, atual, 3, "Supermercado Assaí", "386.75", DESPESA);
        add(id, atual, 5, "Salário empresa", "4500.00", RECEITA);
        add(id, atual, 6, "Uber", "24.90", DESPESA);
        add(id, atual, 7, "iFood pedido", "62.90", DESPESA);
        add(id, atual, 8, "Conta de luz Coelba", "180.35", DESPESA);
        add(id, atual, 9, "Curso Udemy Java", "34.90", DESPESA);
        add(id, atual, 10, "Freela site para cliente", "800.00", RECEITA);
        add(id, atual, 11, "Farmácia Pague Menos", "48.70", DESPESA);
        add(id, atual, 12, "Netflix", "55.90", DESPESA);
        add(id, atual, 13, "Padaria Pão Quente", "18.50", DESPESA);
        add(id, atual, 14, "Amazon fone de ouvido", "129.90", DESPESA);
        add(id, atual, 14, "Presente aniversário", "90.00", DESPESA);

        LocalDate hoje = LocalDate.now();
        metas.inserir(id, "Reserva de emergência", new BigDecimal("10000.00"), new BigDecimal("3200.00"),
                hoje.plusMonths(8).withDayOfMonth(1));
        metas.inserir(id, "Viagem para Porto Seguro", new BigDecimal("3000.00"), new BigDecimal("900.00"),
                hoje.plusMonths(5).withDayOfMonth(1));
        metas.inserir(id, "Notebook novo", new BigDecimal("4500.00"), new BigDecimal("4500.00"),
                hoje.plusMonths(2).withDayOfMonth(1));
    }

    /** Cadastra uma transação pelo serviço normal (a categoria é escolhida pela IA). */
    private void add(long usuarioId, YearMonth mes, int dia, String descricao, String valor, TipoTransacao tipo) {
        LocalDate data = mes.atDay(Math.min(dia, mes.lengthOfMonth()));
        if (data.isAfter(LocalDate.now())) data = LocalDate.now();
        transacaoService.registrar(usuarioId, descricao, new BigDecimal(valor), tipo, data, null);
    }

    /** Usado pelos testes. */
    public long totalTransacoes(long usuarioId) {
        return transacaoRepo.contar(usuarioId);
    }
}
