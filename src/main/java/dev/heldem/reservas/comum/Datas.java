package dev.heldem.reservas.comum;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class Datas {

    private Datas() {
    }

    /** O instante atual do relógio, em microssegundos: é a precisão do timestamptz do PostgreSQL. */
    public static Instant agora(Clock clock) {
        return paraOBanco(Instant.now(clock));
    }

    /**
     * Trunca para a precisão do banco. Sem isso, a resposta do POST (com a data como veio, em nanossegundos) traria mais
     * dígitos do que a leitura seguinte devolve do banco.
     */
    public static Instant paraOBanco(Instant instante) {
        return instante.truncatedTo(ChronoUnit.MICROS);
    }
}
