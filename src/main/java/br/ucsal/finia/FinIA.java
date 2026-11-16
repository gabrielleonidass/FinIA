package br.ucsal.finia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/** É aqui que o sistema começa: prepara o banco, cria os dados de exemplo e liga o servidor. */
public class FinIA {

    public static void main(String[] args) throws Exception {
        Banco.criarTabelas();
        criarCategorias();
        criarUsuarioDemo();

        Web web = new Web();
        Usuarios.rotas(web);
        Transacoes.rotas(web);
        Metas.rotas(web);

        int porta = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        web.iniciar(porta);
        System.out.println("FinIA rodando em http://localhost:" + porta);
        System.out.println("Login de teste: demo@finia.com / Demo@1234");
    }

    static void criarCategorias() {
        String[][] categorias = {
                {"Alimentação", "DESPESA", "#E4572E"}, {"Transporte", "DESPESA", "#F3A712"},
                {"Moradia", "DESPESA", "#4C6EF5"}, {"Saúde", "DESPESA", "#12B886"},
                {"Lazer", "DESPESA", "#BE4BDB"}, {"Educação", "DESPESA", "#15AABF"},
                {"Compras", "DESPESA", "#F06595"}, {"Outros", "DESPESA", "#868E96"},
                {"Salário", "RECEITA", "#2B8A3E"}, {"Renda extra", "RECEITA", "#5C940D"},
                {"Outras receitas", "RECEITA", "#74B816"}};
        for (String[] c : categorias) {
            if (Banco.linha("SELECT id FROM categoria WHERE nome = ?", c[0]) == null)
                Banco.inserir("INSERT INTO categoria (nome, tipo, cor) VALUES (?, ?, ?)", c[0], c[1], c[2]);
        }
    }

    /** Cria a usuária de teste com uns 2 meses de transações e 3 metas (só na primeira vez que roda). */
    static void criarUsuarioDemo() {
        if (Banco.linha("SELECT id FROM usuario WHERE email = 'demo@finia.com'") != null) return;
        long id = (Long) Usuarios.cadastrar("Ana Beatriz Souza", "demo@finia.com", "52998224725", "Demo@1234").get("id");

        // Formato: "meses atrás;dia;descrição;valor;tipo". A categoria quem escolhe é a IA.
        String[] transacoes = {
                "1;5;Salário empresa;4500.00;RECEITA", "1;6;Aluguel apartamento;1200.00;DESPESA",
                "1;8;Supermercado Assaí;420.30;DESPESA", "1;10;Conta de luz Coelba;172.80;DESPESA",
                "1;12;Uber;31.20;DESPESA", "1;15;Netflix;55.90;DESPESA", "1;18;iFood pedido;74.50;DESPESA",
                "1;22;Posto Shell gasolina;180.00;DESPESA", "1;25;Farmácia Pague Menos;63.40;DESPESA",
                "1;27;Cinema shopping;56.00;DESPESA",
                "0;1;Aluguel apartamento;1200.00;DESPESA", "0;3;Supermercado Assaí;386.75;DESPESA",
                "0;5;Salário empresa;4500.00;RECEITA", "0;6;Uber;24.90;DESPESA", "0;7;iFood pedido;62.90;DESPESA",
                "0;8;Conta de luz Coelba;180.35;DESPESA", "0;9;Curso Udemy Java;34.90;DESPESA",
                "0;10;Freela site para cliente;800.00;RECEITA", "0;11;Farmácia Pague Menos;48.70;DESPESA",
                "0;12;Netflix;55.90;DESPESA", "0;13;Padaria Pão Quente;18.50;DESPESA",
                "0;14;Amazon fone de ouvido;129.90;DESPESA", "0;14;Presente aniversário;90.00;DESPESA"};
        for (String linha : transacoes) {
            String[] p = linha.split(";");
            YearMonth mes = YearMonth.now().minusMonths(Integer.parseInt(p[0]));
            LocalDate data = mes.atDay(Math.min(Integer.parseInt(p[1]), mes.lengthOfMonth()));
            if (data.isAfter(LocalDate.now())) data = LocalDate.now();
            Transacoes.registrar(id, p[2], new BigDecimal(p[3]), p[4], data, null);
        }

        LocalDate mesQueVem = LocalDate.now().withDayOfMonth(1);
        String meta = "INSERT INTO meta (usuario_id, nome, valor_alvo, valor_guardado, prazo) VALUES (?, ?, ?, ?, ?)";
        Banco.inserir(meta, id, "Reserva de emergência", 10000, 3200, mesQueVem.plusMonths(8));
        Banco.inserir(meta, id, "Viagem para Porto Seguro", 3000, 900, mesQueVem.plusMonths(5));
        Banco.inserir(meta, id, "Notebook novo", 4500, 4500, mesQueVem.plusMonths(2));
    }
}
