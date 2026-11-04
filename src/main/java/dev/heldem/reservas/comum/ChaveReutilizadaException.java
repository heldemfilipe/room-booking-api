package dev.heldem.reservas.comum;

/** A mesma Idempotency-Key foi usada com uma requisição diferente da primeira: erro do cliente, vira 422. */
public class ChaveReutilizadaException extends RuntimeException {

    public ChaveReutilizadaException() {
        super("Esta Idempotency-Key já foi usada com outra requisição (sala ou horário diferentes). "
                + "Use uma chave nova para uma reserva nova.");
    }
}
