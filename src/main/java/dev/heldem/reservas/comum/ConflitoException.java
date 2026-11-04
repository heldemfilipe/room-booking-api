package dev.heldem.reservas.comum;

/** A operação conflita com o estado atual (nome de sala repetido, sala com reservas): vira 409. */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
