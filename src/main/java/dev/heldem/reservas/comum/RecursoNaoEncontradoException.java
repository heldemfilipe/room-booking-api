package dev.heldem.reservas.comum;

/** O recurso pedido (ou o referenciado no corpo) não existe: vira 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
