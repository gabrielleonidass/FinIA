// ============================================================
// FinIA - gera um arquivo .xlsx (Excel) de verdade no navegador, sem bibliotecas.
// Um .xlsx é um ZIP com alguns arquivos XML; aqui o ZIP é montado sem compressão.
// Uso: baixarXlsx('relatorio.xlsx', 'Relatório', [['Texto', 123.45], ...])
// ============================================================

(function () {
  const enc = new TextEncoder();

  const TABELA_CRC = (() => {
    const t = new Uint32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
      t[n] = c >>> 0;
    }
    return t;
  })();

  function crc32(bytes) {
    let c = 0xFFFFFFFF;
    for (let i = 0; i < bytes.length; i++) c = TABELA_CRC[(c ^ bytes[i]) & 0xFF] ^ (c >>> 8);
    return (c ^ 0xFFFFFFFF) >>> 0;
  }

  /** Monta um ZIP (sem compressão) a partir de { caminho: texto }. */
  function zip(arquivos) {
    const partes = [], central = [];
    let posicao = 0;
    for (const [nome, conteudo] of Object.entries(arquivos)) {
      const nomeB = enc.encode(nome), dados = enc.encode(conteudo), crc = crc32(dados);
      const local = new DataView(new ArrayBuffer(30));
      local.setUint32(0, 0x04034b50, true); local.setUint16(4, 20, true); local.setUint16(6, 0x0800, true);
      local.setUint32(14, crc, true); local.setUint32(18, dados.length, true); local.setUint32(22, dados.length, true);
      local.setUint16(26, nomeB.length, true);
      partes.push(new Uint8Array(local.buffer), nomeB, dados);

      const c = new DataView(new ArrayBuffer(46));
      c.setUint32(0, 0x02014b50, true); c.setUint16(4, 20, true); c.setUint16(6, 20, true); c.setUint16(8, 0x0800, true);
      c.setUint32(16, crc, true); c.setUint32(20, dados.length, true); c.setUint32(24, dados.length, true);
      c.setUint16(28, nomeB.length, true); c.setUint32(42, posicao, true);
      central.push(new Uint8Array(c.buffer), nomeB);
      posicao += 30 + nomeB.length + dados.length;
    }
    const tamanhoCentral = central.reduce((s, p) => s + p.length, 0);
    const fim = new DataView(new ArrayBuffer(22));
    fim.setUint32(0, 0x06054b50, true);
    fim.setUint16(8, Object.keys(arquivos).length, true); fim.setUint16(10, Object.keys(arquivos).length, true);
    fim.setUint32(12, tamanhoCentral, true); fim.setUint32(16, posicao, true);
    return new Blob([...partes, ...central, new Uint8Array(fim.buffer)],
      { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
  }

  const xml = t => String(t).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  const coluna = i => String.fromCharCode(65 + i);

  function planilha(linhas) {
    const corpo = linhas.map((linha, r) => `<row r="${r + 1}">` + linha.map((v, c) => {
      const ref = coluna(c) + (r + 1);
      if (typeof v === 'number' && isFinite(v)) return `<c r="${ref}" s="1"><v>${v}</v></c>`;
      if (v === '' || v == null) return '';
      return `<c r="${ref}" t="inlineStr"><is><t>${xml(v)}</t></is></c>`;
    }).join('') + '</row>').join('');
    return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols><col min="1" max="1" width="32" customWidth="1"/><col min="2" max="4" width="18" customWidth="1"/></cols><sheetData>${corpo}</sheetData></worksheet>`;
  }

  window.baixarXlsx = function (nomeArquivo, nomeAba, linhas) {
    const arquivos = {
      '[Content_Types].xml': `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>`,
      '_rels/.rels': `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`,
      'xl/workbook.xml': `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${xml(nomeAba).slice(0, 31)}" sheetId="1" r:id="rId1"/></sheets></workbook>`,
      'xl/_rels/workbook.xml.rels': `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>`,
      'xl/styles.xml': `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="&quot;R$&quot; #,##0.00"/></numFmts><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="2"><xf/><xf numFmtId="164" applyNumberFormat="1"/></cellXfs></styleSheet>`,
      'xl/worksheets/sheet1.xml': planilha(linhas),
    };
    const link = document.createElement('a');
    link.href = URL.createObjectURL(zip(arquivos));
    link.download = nomeArquivo;
    link.click();
  };
})();
