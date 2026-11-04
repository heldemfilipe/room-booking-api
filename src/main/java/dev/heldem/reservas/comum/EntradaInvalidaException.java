package dev.heldem.reservas.comum;

/** A entrada passou na validação de campos, mas não faz sentido junta (por exemplo, um período invertido): vira 400. */
public class EntradaInvalidaException extends RuntimeException {

    public EntradaInvalidaException(String mensagem) {
        super(mensagem);
    }
}
