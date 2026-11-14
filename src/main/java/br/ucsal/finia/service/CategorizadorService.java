package br.ucsal.finia.service;

import br.ucsal.finia.model.Categoria;
import br.ucsal.finia.model.TipoTransacao;
import br.ucsal.finia.repository.CategoriaRepository;
import br.ucsal.finia.repository.TransacaoRepository;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "IA" do FinIA: sugere a categoria de uma transação pela descrição.
 *
 * Funciona em 2 passos:
 *  1. Aprendizado: se o usuário já corrigiu a categoria de uma transação com a
 *     mesma descrição, usa a escolha dele.
 *  2. Palavras-chave: procura palavras conhecidas na descrição
 *     (ex.: "UBER" -> Transporte, "IFOOD" -> Alimentação). Se várias combinam,
 *     vence a que aparece primeiro na descrição; empatando, a mais longa
 *     ("MERCADO LIVRE" ganha de "MERCADO").
 *  Se nada combinar, usa "Outros" (despesa) ou "Outras receitas" (receita).
 */
public class CategorizadorService {

    public static final String OUTROS = "Outros";
    public static final String OUTRAS_RECEITAS = "Outras receitas";

    /** Categoria -> palavras-chave. */
    static final Map<String, List<String>> PALAVRAS = new LinkedHashMap<>();

    static {
        PALAVRAS.put("Alimentação", List.of("IFOOD", "RAPPI", "MERCADO", "SUPERMERCADO", "PADARIA", "RESTAURANTE",
                "LANCHONETE", "PIZZA", "PIZZARIA", "ACAI", "ATACADAO", "ASSAI", "CARREFOUR", "HAMBURGUERIA",
                "BURGER", "MCDONALDS", "CAFE", "FEIRA", "ACOUGUE"));
        PALAVRAS.put("Transporte", List.of("UBER", "99", "99POP", "POSTO", "GASOLINA", "COMBUSTIVEL", "SHELL",
                "IPIRANGA", "ESTACIONAMENTO", "METRO", "ONIBUS", "PEDAGIO", "OFICINA"));
        PALAVRAS.put("Moradia", List.of("ALUGUEL", "CONDOMINIO", "LUZ", "ENERGIA", "COELBA", "AGUA", "EMBASA",
                "INTERNET", "GAS", "IPTU"));
        PALAVRAS.put("Saúde", List.of("FARMACIA", "DROGARIA", "PAGUE MENOS", "DROGASIL", "CONSULTA", "EXAME",
                "PLANO DE SAUDE", "HOSPITAL", "DENTISTA", "ACADEMIA"));
        PALAVRAS.put("Lazer", List.of("NETFLIX", "SPOTIFY", "PRIME VIDEO", "DISNEY", "CINEMA", "SHOW", "INGRESSO",
                "BAR", "VIAGEM", "HOTEL", "PASSAGEM", "JOGO", "STEAM"));
        PALAVRAS.put("Educação", List.of("CURSO", "UDEMY", "ALURA", "FACULDADE", "MENSALIDADE", "LIVRO",
                "LIVRARIA", "ESCOLA", "MATERIAL ESCOLAR"));
        PALAVRAS.put("Compras", List.of("AMAZON", "MERCADO LIVRE", "SHOPEE", "SHEIN", "MAGALU", "MAGAZINE",
                "RENNER", "RIACHUELO", "LOJA", "ROUPA"));
        PALAVRAS.put("Salário", List.of("SALARIO", "FOLHA", "PAGAMENTO EMPRESA", "DECIMO TERCEIRO", "FERIAS"));
        PALAVRAS.put("Renda extra", List.of("FREELA", "FREELANCE", "VENDA", "BICO", "PIX RECEBIDO", "RENDIMENTO"));
    }

    /** Resultado da sugestão: a categoria e uma explicação para mostrar na tela. */
    public record Sugestao(Categoria categoria, String motivo) {
    }

    private final CategoriaRepository categorias;
    private final TransacaoRepository transacoes;

    public CategorizadorService(CategoriaRepository categorias, TransacaoRepository transacoes) {
        this.categorias = categorias;
        this.transacoes = transacoes;
    }

    public Sugestao sugerir(long usuarioId, String descricao, TipoTransacao tipo) {
        // 1) Aprendizado com as correções do próprio usuário
        Optional<Categoria> aprendida = transacoes.categoriaEscolhidaAntes(usuarioId, descricao.trim())
                .flatMap(categorias::buscarPorId)
                .filter(c -> c.tipo() == tipo);
        if (aprendida.isPresent()) {
            return new Sugestao(aprendida.get(), "Você já classificou \"" + descricao.trim() + "\" assim antes.");
        }

        // 2) Palavras-chave
        String texto = " " + normalizar(descricao) + " ";
        String melhorCategoria = null;
        String melhorPalavra = "";
        int melhorPosicao = Integer.MAX_VALUE;
        for (Map.Entry<String, List<String>> e : PALAVRAS.entrySet()) {
            Optional<Categoria> cat = categorias.buscarPorNome(e.getKey());
            if (cat.isEmpty() || cat.get().tipo() != tipo) continue;
            for (String palavra : e.getValue()) {
                int posicao = texto.indexOf(" " + palavra + " ");
                if (posicao < 0) continue;
                boolean antes = posicao < melhorPosicao;
                boolean empateMaisLonga = posicao == melhorPosicao && palavra.length() > melhorPalavra.length();
                if (antes || empateMaisLonga) {
                    melhorCategoria = e.getKey();
                    melhorPalavra = palavra;
                    melhorPosicao = posicao;
                }
            }
        }
        if (melhorCategoria != null) {
            return new Sugestao(categorias.buscarPorNome(melhorCategoria).orElseThrow(),
                    "A palavra \"" + melhorPalavra + "\" indica " + melhorCategoria + ".");
        }

        // 3) Nada reconhecido
        String padrao = tipo == TipoTransacao.RECEITA ? OUTRAS_RECEITAS : OUTROS;
        return new Sugestao(categorias.buscarPorNome(padrao).orElseThrow(),
                "Nenhuma palavra conhecida. Você pode trocar a categoria depois.");
    }

    /** Deixa o texto em maiúsculas, sem acentos e sem pontuação: "iFood*Pedido" -> "IFOOD PEDIDO". */
    static String normalizar(String s) {
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toUpperCase().replaceAll("[^A-Z0-9]+", " ").trim();
    }
}
