package br.ucsal.finia.service;

import br.ucsal.finia.infra.ApiException;
import br.ucsal.finia.model.Meta;
import br.ucsal.finia.repository.MetaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Regras das metas de economia. */
public class MetaService {

    private final MetaRepository metas;

    public MetaService(MetaRepository metas) {
        this.metas = metas;
    }

    public Meta criar(long usuarioId, String nome, BigDecimal valorAlvo, LocalDate prazo) {
        if (nome == null || nome.isBlank()) throw ApiException.requisicaoInvalida("Dê um nome para a meta.");
        if (valorAlvo == null || valorAlvo.signum() <= 0)
            throw ApiException.requisicaoInvalida("O valor da meta deve ser maior que zero.");
        if (prazo == null || !prazo.isAfter(LocalDate.now()))
            throw ApiException.requisicaoInvalida("O prazo precisa ser uma data futura.");

        long id = metas.inserir(usuarioId, nome.trim(), valorAlvo, BigDecimal.ZERO, prazo);
        return metas.buscar(usuarioId, id).orElseThrow();
    }

    public List<Meta> listar(long usuarioId) {
        return metas.listar(usuarioId);
    }

    /** Soma um valor ao que já foi guardado na meta. */
    public Meta guardar(long usuarioId, long metaId, BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) throw ApiException.requisicaoInvalida("Informe um valor maior que zero.");
        Meta m = metas.buscar(usuarioId, metaId).orElseThrow(() -> ApiException.naoEncontrado("Meta não encontrada."));
        metas.atualizarValorGuardado(metaId, m.valorGuardado().add(valor));
        return metas.buscar(usuarioId, metaId).orElseThrow();
    }

    public void excluir(long usuarioId, long metaId) {
        if (metas.excluir(usuarioId, metaId) == 0) throw ApiException.naoEncontrado("Meta não encontrada.");
    }
}
