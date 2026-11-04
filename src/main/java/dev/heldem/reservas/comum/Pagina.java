package dev.heldem.reservas.comum;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Resposta paginada com um formato nosso, estável, em vez de serializar o PageImpl do Spring Data. */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total, int totalPaginas) {

    public static <E, T> Pagina<T> de(Page<E> pagina, Function<E, T> conversor) {
        return new Pagina<>(pagina.getContent().stream().map(conversor).toList(), pagina.getNumber(),
                pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }
}
