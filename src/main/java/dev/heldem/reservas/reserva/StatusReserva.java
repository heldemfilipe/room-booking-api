package dev.heldem.reservas.reserva;

/** DELETE /reservas/{id} cancela (muda o status) em vez de apagar a linha: o histórico importa para a concorrência. */
public enum StatusReserva {
    ATIVA,
    CANCELADA
}
