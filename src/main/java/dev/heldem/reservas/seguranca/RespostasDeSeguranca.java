package dev.heldem.reservas.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 401 e 403 no mesmo formato dos demais erros da API (ProblemDetail, RFC 9457). Eles acontecem nos filtros de
 * segurança, antes do Spring MVC, então o {@code @RestControllerAdvice} não os alcança. Os textos são fixos: nada do
 * que o cliente mandou (token, caminho) é ecoado na resposta.
 */
public class RespostasDeSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escrever(response, HttpStatus.UNAUTHORIZED, "Não autenticado",
                "Envie um token válido no cabeçalho Authorization: Bearer <token>.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        escrever(response, HttpStatus.FORBIDDEN, "Acesso negado", "Você não tem permissão para esta operação.");
    }

    private static void escrever(HttpServletResponse response, HttpStatus status, String titulo, String detalhe)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detalhe + "\"}");
    }
}
