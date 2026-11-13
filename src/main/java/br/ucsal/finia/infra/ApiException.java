package br.ucsal.finia.infra;

/** Erro de negócio ou de requisição, convertido em resposta HTTP com mensagem. */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static ApiException requisicaoInvalida(String msg) {
        return new ApiException(400, msg);
    }

    public static ApiException naoAutorizado(String msg) {
        return new ApiException(401, msg);
    }

    public static ApiException proibido(String msg) {
        return new ApiException(403, msg);
    }

    public static ApiException naoEncontrado(String msg) {
        return new ApiException(404, msg);
    }

    public static ApiException conflito(String msg) {
        return new ApiException(409, msg);
    }
}
