package br.ucsal.finia;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A "IA" do FinIA: escolhe a categoria pela descrição e diz o quanto confia nela (RF03 e RF04).
 *
 *  1. Regra do usuário: se ele aceitou criar uma regra para esse estabelecimento, usa a regra (98%).
 *  2. Palavras-chave: "UBER" -> Transporte, "IFOOD" -> Alimentação...
 *     Se várias combinam, vence a que aparece primeiro; empatando, a mais longa ("MERCADO LIVRE" > "MERCADO").
 *     90% se só uma categoria combinou; 60% se combinou com categorias diferentes.
 *  3. Nada combinou: "Outros" ou "Outras receitas" (30%).
 *
 * Abaixo do limite mínimo (70% por padrão) a transação fica marcada para revisão.
 * O limite pode ser mudado sem mexer no código: variável de ambiente FINIA_LIMIAR_CONFIANCA.
 */
public class Categorizador {

    static final int LIMIAR_CONFIANCA = Integer.parseInt(System.getenv().getOrDefault("FINIA_LIMIAR_CONFIANCA", "70"));

    private static final Map<String, List<String>> PALAVRAS = new LinkedHashMap<>();

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

    /** Resposta da IA: a categoria (linha da tabela categoria), o motivo e a confiança (0 a 100). */
    record Sugestao(Map<String, Object> categoria, String motivo, int confianca) {
        boolean revisar() {
            return confianca < LIMIAR_CONFIANCA;
        }
    }

    static Sugestao sugerir(long usuarioId, String descricao, String tipo) {
        // 1) Regra criada pelo usuário
        String padrao = padraoDoEstabelecimento(descricao);
        Map<String, Object> daRegra = Banco.linha("""
                SELECT c.* FROM regra_categoria r JOIN categoria c ON c.id = r.categoria_id
                WHERE r.usuario_id = ? AND r.padrao = ? AND r.aceita = TRUE AND c.tipo = ?""", usuarioId, padrao, tipo);
        if (daRegra != null) return new Sugestao(daRegra, "Regra criada por você para \"" + padrao + "\".", 98);

        // 2) Palavras-chave
        String texto = " " + normalizar(descricao) + " ";
        String melhorCategoria = null, melhorPalavra = "";
        int melhorPosicao = Integer.MAX_VALUE;
        Set<String> encontradas = new HashSet<>();
        for (var item : PALAVRAS.entrySet()) {
            for (String palavra : item.getValue()) {
                int posicao = texto.indexOf(" " + palavra + " ");
                if (posicao < 0 || categoria(item.getKey(), tipo) == null) continue;
                encontradas.add(item.getKey());
                if (posicao < melhorPosicao || (posicao == melhorPosicao && palavra.length() > melhorPalavra.length())) {
                    melhorCategoria = item.getKey();
                    melhorPalavra = palavra;
                    melhorPosicao = posicao;
                }
            }
        }
        if (melhorCategoria != null) {
            String motivo = "A palavra \"" + melhorPalavra + "\" indica " + melhorCategoria + ".";
            if (encontradas.size() > 1)
                return new Sugestao(categoria(melhorCategoria, tipo), motivo + " Mas a descrição também combina com outras categorias.", 60);
            return new Sugestao(categoria(melhorCategoria, tipo), motivo, 90);
        }

        // 3) Nada reconhecido
        String nome = tipo.equals("RECEITA") ? "Outras receitas" : "Outros";
        return new Sugestao(categoria(nome, tipo), "Nenhuma palavra conhecida. Confira a categoria.", 30);
    }

    private static Map<String, Object> categoria(String nome, String tipo) {
        return Banco.linha("SELECT * FROM categoria WHERE nome = ? AND tipo = ?", nome, tipo);
    }

    /**
     * Identifica o estabelecimento mesmo que a descrição mude:
     * "UBER *TRIP 123" e "Uber *trip 456" viram "UBER TRIP" (tira acentos, pontuação e números).
     */
    static String padraoDoEstabelecimento(String descricao) {
        String normal = normalizar(descricao);
        String semNumeros = Arrays.stream(normal.split(" "))
                .filter(parte -> !parte.matches(".*\\d.*"))
                .collect(Collectors.joining(" "));
        return semNumeros.isBlank() ? normal : semNumeros;
    }

    /** "iFood*Pedido" -> "IFOOD PEDIDO" (maiúsculas, sem acento e sem pontuação). */
    private static String normalizar(String s) {
        String semAcento = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toUpperCase().replaceAll("[^A-Z0-9]+", " ").trim();
    }
}
