package dev.heldem.reservas.comum;

/** O usuário está autenticado, mas a operação não é dele (cancelar a reserva de outra pessoa): vira 403. */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
