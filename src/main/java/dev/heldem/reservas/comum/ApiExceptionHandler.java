package dev.heldem.reservas.comum;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Respostas de erro padronizadas (ProblemDetail, RFC 9457): 400 entrada inválida, 403 reserva de outra pessoa, 404, 409 e
 * 500 sem stack trace. O 401 e o 403 de papel acontecem nos filtros de segurança (veja RespostasDeSeguranca).
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(RecursoNaoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso não encontrado", ex.getMessage());
    }

    @ExceptionHandler(EntradaInvalidaException.class)
    ProblemDetail entradaInvalida(EntradaInvalidaException ex) {
        return problema(HttpStatus.BAD_REQUEST, "Entrada inválida", ex.getMessage());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ProblemDetail acessoNegado(AcessoNegadoException ex) {
        return problema(HttpStatus.FORBIDDEN, "Acesso negado", ex.getMessage());
    }

    @ExceptionHandler(ChaveReutilizadaException.class)
    ProblemDetail chaveReutilizada(ChaveReutilizadaException ex) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency-Key reutilizada", ex.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    ProblemDetail conflito(ConflitoException ex) {
        // INFO, não WARN: 409 é uma resposta correta da regra de negócio. O JSON do log carrega o traceId desta requisição.
        log.info("Conflito (409): {}", ex.getMessage());
        return problema(HttpStatus.CONFLICT, "Conflito", ex.getMessage());
    }

    // Último recurso: o detalhe vai só para o log, nunca para o cliente (sem stack trace na resposta).
    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        log.error("Erro inesperado", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Tente novamente mais tarde.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> erros = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> erros.putIfAbsent(e.getField(), e.getDefaultMessage()));

        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Erro de validação", "Dados inválidos.");
        problema.setProperty("erros", erros);
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    // @Min/@Max em parâmetros de consulta (pagina e tamanho): mesmo formato do erro de validação do corpo
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> erros = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(resultado -> resultado.getResolvableErrors()
                .forEach(e -> erros.putIfAbsent(resultado.getMethodParameter().getParameterName(),
                        e.getDefaultMessage())));

        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Erro de validação", "Parâmetros inválidos.");
        problema.setProperty("erros", erros);
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    // JSON malformado, data fora do formato ISO-8601 ou UUID inválido no corpo
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Corpo inválido",
                "O corpo da requisição está malformado ou tem um valor inválido (por exemplo, uma data fora do "
                        + "formato ISO-8601 ou um id que não é UUID).");
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    // id que não é UUID no caminho, ou data inválida em ?de= e ?ate=
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        String parametro = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Parâmetro inválido",
                "Valor inválido para o parâmetro '" + parametro + "': '" + ex.getValue() + "'.");
        return handleExceptionInternal(ex, problema, headers, status, request);
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
