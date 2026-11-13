package br.ucsal.finia.service;

import br.ucsal.finia.model.Categoria;
import br.ucsal.finia.model.Enums.StatusTransacao;
import br.ucsal.finia.model.Enums.TipoTransacao;
import br.ucsal.finia.model.RegraCategorizacao;
import br.ucsal.finia.repository.CategoriaRepository;
import br.ucsal.finia.repository.ReclassificacaoRepository;
import br.ucsal.finia.repository.RegraRepository;
import br.ucsal.finia.util.Texto;

import java.util.*;

/**
 * Classificador de transações (RF03 / UC03).
 *
 * Ordem de decisão:
 * 1. Regra permanente do usuário (RF04) -> confiança 99%;
 * 2. Histórico de correções do próprio usuário para o mesmo padrão -> 92% (sinal de maior peso);
 * 3. Modelo de palavras-chave ponderadas por categoria, com confiança
 *    proporcional à força e à exclusividade do sinal encontrado;
 * 4. Sem sinal -> "Outros" com baixa confiança.
 * Abaixo do limiar de 70% a transação é marcada como INCERTA (RN04).
 */
public class CategorizadorService {

    public static final double LIMIAR_CONFIANCA = 0.70;

    public record Resultado(Categoria categoria, double confianca, StatusTransacao status) {
    }

    /** Vocabulário do modelo: categoria -> (palavra-chave -> peso). */
    private static final Map<String, Map<String, Integer>> MODELO = new LinkedHashMap<>();

    static {
        modelo("Alimentação", "IFOOD:3,RESTAURANTE:3,PADARIA:3,SUPERMERCADO:3,MERCADO:2,LANCHONETE:3,PIZZARIA:3," +
                "ATACADAO:3,ASSAI:3,CARREFOUR:2,BURGER:3,MCDONALDS:3,ACOUGUE:3,HORTIFRUTI:3,RAPPI:2,UBER EATS:3," +
                "CAFE:1,PAO DE ACUCAR:3,GBARBOSA:3,HIPER IDEAL:3,BAR:1,SORVETERIA:3,ACAI:3");
        modelo("Transporte", "UBER:3,99APP:3,99 POP:3,POSTO:3,COMBUSTIVEL:3,GASOLINA:3,SHELL:2,IPIRANGA:2," +
                "ESTACIONAMENTO:3,METRO:3,ONIBUS:3,PEDAGIO:3,CABIFY:3,TAXI:3,SALVADOR CARD:3,INDRIVE:3");
        modelo("Moradia", "ALUGUEL:3,CONDOMINIO:3,COELBA:3,NEOENERGIA:3,ENERGIA:2,EMBASA:3,AGUA:1,GAS:1," +
                "INTERNET:2,CLARO:1,VIVO:1,TIM:1,IPTU:3,LEROY MERLIN:2");
        modelo("Saúde", "FARMACIA:3,DROGARIA:3,DROGASIL:3,PAGUE MENOS:3,HOSPITAL:3,CLINICA:3,LABORATORIO:3," +
                "UNIMED:3,HAPVIDA:3,PLANO DE SAUDE:3,DENTISTA:3,ACADEMIA:2,SMARTFIT:2,RAIA:3");
        modelo("Lazer", "CINEMA:3,CINEMARK:3,INGRESSO:3,SYMPLA:3,SHOW:2,TEATRO:3,PARQUE:2,VIAGEM:2,HOTEL:3," +
                "AIRBNB:3,STEAM:3,PLAYSTATION:3,BAR:2,BOLICHE:3,CLUBE:2");
        modelo("Assinaturas", "NETFLIX:3,SPOTIFY:3,AMAZON PRIME:3,PRIME VIDEO:3,DISNEY:3,HBO:3,YOUTUBE PREMIUM:3," +
                "DEEZER:3,GLOBOPLAY:3,ICLOUD:3,GOOGLE ONE:3,CHATGPT:3,ASSINATURA:2");
        modelo("Educação", "ESCOLA:3,FACULDADE:3,UCSAL:3,CURSO:3,UDEMY:3,ALURA:3,LIVRARIA:3,MENSALIDADE:2," +
                "MATERIAL ESCOLAR:3,COURSERA:3");
        modelo("Transferências", "PAGAMENTO FATURA:3,PAGTO FATURA:3,TRANSF ENTRE CONTAS:3");
        modelo("Compras", "AMAZON:2,MERCADO LIVRE:3,MERCADOLIVRE:3,SHOPEE:3,MAGALU:3,MAGAZINE:3,AMERICANAS:3," +
                "RENNER:3,RIACHUELO:3,SHEIN:3,LOJA:1,ALIEXPRESS:3,KABUM:3,CENTAURO:3");
    }

    private static void modelo(String categoria, String pares) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String par : pares.split(",")) {
            String[] kv = par.split(":");
            m.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
        }
        MODELO.put(categoria, m);
    }

    private final CategoriaRepository categorias;
    private final RegraRepository regras;
    private final ReclassificacaoRepository reclassificacoes;

    public CategorizadorService(CategoriaRepository categorias, RegraRepository regras,
                                ReclassificacaoRepository reclassificacoes) {
        this.categorias = categorias;
        this.regras = regras;
        this.reclassificacoes = reclassificacoes;
    }

    public Resultado classificar(long usuarioId, String descricao, TipoTransacao tipo) {
        String texto = Texto.normalizar(descricao);
        String padrao = Texto.padraoEstabelecimento(descricao);

        // 1) regra permanente do usuário
        for (RegraCategorizacao r : regras.listar(usuarioId)) {
            if ((" " + texto + " ").contains(" " + r.padrao() + " ") || padrao.equals(r.padrao())) {
                return resultado(categorias.buscarPorId(r.categoriaId()).orElseThrow(), 0.99);
            }
        }

        // 2) histórico de correções do usuário (maior peso)
        List<Long> historico = reclassificacoes.ultimasCategoriasDoPadrao(usuarioId, padrao, 1);
        if (!historico.isEmpty()) {
            return resultado(categorias.buscarPorId(historico.get(0)).orElseThrow(), 0.92);
        }

        if (texto.contains("PAGAMENTO FATURA") || texto.contains("TRANSF ENTRE CONTAS")) {
            return resultado(categoria("Transferências"), 0.97);
        }

        if (tipo == TipoTransacao.RECEITA) {
            boolean salario = texto.contains("SALARIO") || texto.contains("PROVENTOS") || texto.contains("FOLHA");
            return resultado(categoria(salario ? "Salário" : "Outras receitas"), salario ? 0.97 : 0.80);
        }

        // 3) modelo de palavras-chave ponderadas
        Map<String, Integer> pontuacao = pontuar(texto);
        if (pontuacao.isEmpty()) return resultado(categoria("Outros"), 0.35);

        List<Map.Entry<String, Integer>> ordenado = new ArrayList<>(pontuacao.entrySet());
        ordenado.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        int melhor = ordenado.get(0).getValue();
        int segundo = ordenado.size() > 1 ? ordenado.get(1).getValue() : 0;
        double forca = Math.min(0.97, 0.45 + 0.17 * melhor);
        double exclusividade = (double) melhor / (melhor + segundo);
        double confianca = Math.round(forca * exclusividade * 100) / 100.0;
        return resultado(categoria(ordenado.get(0).getKey()), confianca);
    }

    /** Soma os pesos das palavras-chave encontradas; expressões mais longas têm prioridade. */
    static Map<String, Integer> pontuar(String texto) {
        record Chave(String categoria, String palavra, int peso) {
        }
        List<Chave> chaves = new ArrayList<>();
        MODELO.forEach((cat, mapa) -> mapa.forEach((p, peso) -> chaves.add(new Chave(cat, p, peso))));
        chaves.sort(Comparator.comparingInt((Chave c) -> c.palavra().length()).reversed());

        String alvo = " " + texto + " ";
        List<String> usadas = new ArrayList<>();
        Map<String, Integer> pontos = new HashMap<>();
        for (Chave c : chaves) {
            if (!alvo.contains(" " + c.palavra() + " ")) continue;
            boolean contida = usadas.stream().anyMatch(u -> u.length() > c.palavra().length()
                    && (" " + u + " ").contains(" " + c.palavra() + " "));
            if (contida) continue;
            usadas.add(c.palavra());
            pontos.merge(c.categoria(), c.peso(), Integer::sum);
        }
        return pontos;
    }

    private Categoria categoria(String nome) {
        return categorias.buscarPorNome(nome).orElseThrow(() -> new IllegalStateException("Categoria ausente: " + nome));
    }

    private static Resultado resultado(Categoria c, double confianca) {
        StatusTransacao status = confianca < LIMIAR_CONFIANCA ? StatusTransacao.INCERTA : StatusTransacao.CATEGORIZADA;
        return new Resultado(c, confianca, status);
    }
}
