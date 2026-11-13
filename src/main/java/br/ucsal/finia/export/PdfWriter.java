package br.ucsal.finia.export;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerador mínimo de PDF (A4) sem bibliotecas externas: texto em Helvetica
 * (WinAnsi, com acentuação), retângulos preenchidos e linhas.
 * Coordenadas em pontos, com origem no canto superior esquerdo.
 */
public class PdfWriter {

    public static final float LARGURA = 595f, ALTURA = 842f;
    private static final Charset WIN = Charset.forName("windows-1252");

    private final List<StringBuilder> paginas = new ArrayList<>();
    private StringBuilder atual;

    public PdfWriter() {
        novaPagina();
    }

    public void novaPagina() {
        atual = new StringBuilder();
        paginas.add(atual);
    }

    public void texto(float x, float y, float tamanho, boolean negrito, String texto, String corHex) {
        float[] c = cor(corHex);
        atual.append(String.format(java.util.Locale.US, "BT %.3f %.3f %.3f rg /%s %.1f Tf %.2f %.2f Td (%s) Tj ET\n",
                c[0], c[1], c[2], negrito ? "F2" : "F1", tamanho, x, ALTURA - y, escapar(texto)));
    }

    /** Texto alinhado à direita em xDireita (largura estimada pelas métricas médias da Helvetica). */
    public void textoDireita(float xDireita, float y, float tamanho, boolean negrito, String texto, String corHex) {
        texto(xDireita - larguraTexto(texto, tamanho, negrito), y, tamanho, negrito, texto, corHex);
    }

    public static float larguraTexto(String t, float tamanho, boolean negrito) {
        float soma = 0;
        for (char ch : t.toCharArray()) {
            if ("il.,:;|!'I".indexOf(ch) >= 0) soma += 0.28f;
            else if ("mwMW".indexOf(ch) >= 0) soma += 0.85f;
            else if (Character.isUpperCase(ch) || Character.isDigit(ch)) soma += 0.62f;
            else if (ch == ' ') soma += 0.28f;
            else soma += 0.52f;
        }
        return soma * tamanho * (negrito ? 1.05f : 1f);
    }

    public void retangulo(float x, float y, float w, float h, String corHex) {
        float[] c = cor(corHex);
        atual.append(String.format(java.util.Locale.US, "%.3f %.3f %.3f rg %.2f %.2f %.2f %.2f re f\n",
                c[0], c[1], c[2], x, ALTURA - y - h, w, h));
    }

    public void linha(float x1, float y1, float x2, float y2, float espessura, String corHex) {
        float[] c = cor(corHex);
        atual.append(String.format(java.util.Locale.US, "%.3f %.3f %.3f RG %.2f w %.2f %.2f m %.2f %.2f l S\n",
                c[0], c[1], c[2], espessura, x1, ALTURA - y1, x2, ALTURA - y2));
    }

    public byte[] gerar() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        int n = paginas.size();
        // objetos: 1 catálogo, 2 páginas, 3 fonte normal, 4 fonte negrito, depois (página, conteúdo) por página
        escrever(out, "%PDF-1.4\n%âãÏÓ\n");
        objeto(out, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(5 + i * 2).append(" 0 R ");
        objeto(out, offsets, "<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>");
        objeto(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objeto(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int i = 0; i < n; i++) {
            objeto(out, offsets, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents "
                    + (6 + i * 2) + " 0 R >>");
            byte[] conteudo = paginas.get(i).toString().getBytes(WIN);
            offsets.add(out.size());
            escrever(out, offsets.size() + " 0 obj\n<< /Length " + conteudo.length + " >>\nstream\n");
            out.writeBytes(conteudo);
            escrever(out, "\nendstream\nendobj\n");
        }
        int xref = out.size();
        StringBuilder sb = new StringBuilder("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
        for (int off : offsets) sb.append(String.format("%010d 00000 n \n", off));
        sb.append("trailer\n<< /Size ").append(offsets.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        escrever(out, sb.toString());
        return out.toByteArray();
    }

    private static void objeto(ByteArrayOutputStream out, List<Integer> offsets, String corpo) {
        offsets.add(out.size());
        escrever(out, offsets.size() + " 0 obj\n" + corpo + "\nendobj\n");
    }

    private static void escrever(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static float[] cor(String hex) {
        String h = hex == null ? "#000000" : hex.replace("#", "");
        return new float[]{Integer.parseInt(h.substring(0, 2), 16) / 255f,
                Integer.parseInt(h.substring(2, 4), 16) / 255f, Integer.parseInt(h.substring(4, 6), 16) / 255f};
    }
}
