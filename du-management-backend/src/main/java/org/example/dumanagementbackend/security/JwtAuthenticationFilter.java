package org.example.dumanagementbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            username = jwtService.extractUsername(jwt);
        } catch (ExpiredJwtException ex) {
            request.setAttribute(
                    RestAuthenticationEntryPoint.AUTH_ERROR_MESSAGE_ATTR,
                    "Access token has expired. Please sign in again."
            );
            restAuthenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException("JWT has expired", ex)
            );
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            request.setAttribute(
                    RestAuthenticationEntryPoint.AUTH_ERROR_MESSAGE_ATTR,
                    "Access token is invalid. Please sign in again."
            );
            restAuthenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException("JWT is invalid", ex)
            );
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!userDetails.isEnabled()) {
                    request.setAttribute(
                            RestAuthenticationEntryPoint.AUTH_ERROR_MESSAGE_ATTR,
                            AccountStatusPolicy.ACCOUNT_UNAVAILABLE_MESSAGE
                    );
                    restAuthenticationEntryPoint.commence(
                            request,
                            response,
                            new InsufficientAuthenticationException(AccountStatusPolicy.ACCOUNT_UNAVAILABLE_MESSAGE)
                    );
                    return;
                }
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    request.setAttribute(
                            RestAuthenticationEntryPoint.AUTH_ERROR_MESSAGE_ATTR,
                            "Access token is invalid. Please sign in again."
                    );
                    restAuthenticationEntryPoint.commence(
                            request,
                            response,
                            new InsufficientAuthenticationException("JWT is invalid")
                    );
                    return;
                }
            } catch (Exception ex) {
                request.setAttribute(
                        RestAuthenticationEntryPoint.AUTH_ERROR_MESSAGE_ATTR,
                        "Authentication failed. Please sign in again."
                );
                restAuthenticationEntryPoint.commence(
                        request,
                        response,
                        new InsufficientAuthenticationException("Authentication failed", ex)
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
