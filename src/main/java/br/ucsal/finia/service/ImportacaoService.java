package br.ucsal.finia.service;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.infra.Json;
import br.ucsal.finia.model.Enums.OrigemTransacao;
import br.ucsal.finia.model.Enums.StatusTransacao;
import br.ucsal.finia.model.Enums.TipoTransacao;
import br.ucsal.finia.model.Transacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importação manual de extratos CSV e OFX (RF02 / UC02).
 * O arquivo é validado por inteiro antes do processamento: se houver qualquer
 * linha fora do padrão, nada é importado e o usuário recebe a linha e o motivo.
 */
public class ImportacaoService {

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private static final Pattern BLOCO_OFX = Pattern.compile("<STMTTRN>(.*?)</STMTTRN>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    record Linha(int numero, LocalDate data, String descricao, BigDecimal valor) {
    }

    private final TransacaoService transacoes;

    public ImportacaoService(TransacaoService transacoes) {
        this.transacoes = transacoes;
    }

    public Map<String, Object> importar(long usuarioId, long contaId, String nomeArquivo, String conteudo) {
        if (conteudo == null || conteudo.isBlank()) throw ApiException.requisicaoInvalida("O arquivo está vazio.");
        String nome = nomeArquivo == null ? "" : nomeArquivo.toLowerCase();
        List<String> erros = new ArrayList<>();
        List<Linha> linhas;
        OrigemTransacao origem;
        if (nome.endsWith(".ofx") || conteudo.contains("<OFX>") || conteudo.contains("<STMTTRN>")) {
            origem = OrigemTransacao.OFX;
            linhas = lerOfx(conteudo, erros);
        } else if (nome.endsWith(".csv") || nome.isEmpty()) {
            origem = OrigemTransacao.CSV;
            linhas = lerCsv(conteudo, erros);
        } else {
            throw ApiException.requisicaoInvalida("Formato não suportado. Envie um arquivo .csv ou .ofx.");
        }
        if (!erros.isEmpty()) {
            return Json.obj("sucesso", false, "importadas", 0, "duplicadas", 0, "incertas", 0, "erros", erros);
        }
        int importadas = 0, duplicadas = 0, incertas = 0;
        for (Linha l : linhas) {
            TipoTransacao tipo = l.valor().signum() < 0 ? TipoTransacao.DESPESA : TipoTransacao.RECEITA;
            Transacao t = transacoes.registrar(usuarioId, contaId, l.data(), l.descricao(), l.valor(), tipo, origem);
            if (t == null) duplicadas++;
            else {
                importadas++;
                if (t.status() == StatusTransacao.INCERTA) incertas++;
            }
        }
        return Json.obj("sucesso", true, "importadas", importadas, "duplicadas", duplicadas, "incertas", incertas, "erros", erros);
    }

    /** CSV esperado: cabeçalho data;descricao;valor (separador ; ou ,). Valor negativo = despesa. */
    List<Linha> lerCsv(String conteudo, List<String> erros) {
        List<Linha> linhas = new ArrayList<>();
        String[] texto = conteudo.replace("﻿", "").split("\\r?\\n");
        String cab = texto[0].trim().toLowerCase();
        char sep = cab.contains(";") ? ';' : ',';
        String[] colunas = cab.split(String.valueOf(sep));
        if (colunas.length < 3 || !colunas[0].trim().equals("data") || !colunas[1].trim().startsWith("descri")
                || !colunas[2].trim().equals("valor")) {
            erros.add("Linha 1: cabeçalho inválido. Esperado: data;descricao;valor");
            return linhas;
        }
        for (int i = 1; i < texto.length; i++) {
            int n = i + 1;
            String bruta = texto[i].trim();
            if (bruta.isEmpty()) continue;
            String[] c = bruta.split(String.valueOf(sep), -1);
            if (c.length != 3) {
                erros.add("Linha " + n + ": esperado 3 colunas, encontrado " + c.length + ".");
                continue;
            }
            LocalDate data = lerData(c[0].trim());
            if (data == null) {
                erros.add("Linha " + n + ": data inválida \"" + c[0].trim() + "\" (use dd/mm/aaaa).");
                continue;
            }
            String desc = c[1].trim().replace("\"", "");
            if (desc.isEmpty()) {
                erros.add("Linha " + n + ": descrição vazia.");
                continue;
            }
            BigDecimal valor = lerValor(c[2].trim(), sep);
            if (valor == null || valor.signum() == 0) {
                erros.add("Linha " + n + ": valor inválido \"" + c[2].trim() + "\".");
                continue;
            }
            linhas.add(new Linha(n, data, desc, valor));
        }
        if (linhas.isEmpty() && erros.isEmpty()) erros.add("O arquivo não contém transações.");
        return linhas;
    }

    List<Linha> lerOfx(String conteudo, List<String> erros) {
        List<Linha> linhas = new ArrayList<>();
        Matcher m = BLOCO_OFX.matcher(conteudo);
        int i = 0;
        while (m.find()) {
            i++;
            String bloco = m.group(1);
            String dt = tag(bloco, "DTPOSTED"), amt = tag(bloco, "TRNAMT");
            String desc = tag(bloco, "MEMO");
            if (desc == null || desc.isBlank()) desc = tag(bloco, "NAME");
            LocalDate data = null;
            try {
                if (dt != null && dt.length() >= 8) data = LocalDate.parse(dt.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
            } catch (DateTimeParseException ignored) {
            }
            BigDecimal valor = null;
            try {
                if (amt != null) valor = new BigDecimal(amt.replace(",", "."));
            } catch (NumberFormatException ignored) {
            }
            if (data == null) erros.add("Transação " + i + " (OFX): DTPOSTED inválido.");
            else if (valor == null || valor.signum() == 0) erros.add("Transação " + i + " (OFX): TRNAMT inválido.");
            else if (desc == null || desc.isBlank()) erros.add("Transação " + i + " (OFX): sem descrição (MEMO/NAME).");
            else linhas.add(new Linha(i, data, desc.trim(), valor));
        }
        if (i == 0) erros.add("Arquivo OFX sem transações (<STMTTRN>) ou corrompido.");
        return linhas;
    }

    private static String tag(String bloco, String nome) {
        Matcher m = Pattern.compile("<" + nome + ">([^<\\r\\n]*)", Pattern.CASE_INSENSITIVE).matcher(bloco);
        return m.find() ? m.group(1).trim() : null;
    }

    private static LocalDate lerData(String s) {
        try {
            return s.contains("/") ? LocalDate.parse(s, BR) : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal lerValor(String s, char sep) {
        try {
            String v = s.replace("R$", "").replace(" ", "").replace("\"", "");
            if (sep == ';' && v.contains(",")) v = v.replace(".", "").replace(",", ".");
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
