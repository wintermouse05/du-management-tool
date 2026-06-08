package org.example.dumanagementbackend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Test
    void doFilterInternal_rejectsInactiveAccountWithGenericMessage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UserDetails disabledUser = User.withUsername("alice")
                .password("hashed")
                .authorities("ROLE_MEMBER")
                .disabled(true)
                .build();

        when(jwtService.extractUsername("access-token")).thenReturn("alice");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(disabledUser);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(
                AccountStatusPolicy.ACCOUNT_UNAVAILABLE_MESSAGE,
                request.getAttribute(RestAuthenticationEntryPoint.AUTH_ERROR_MESSAGE_ATTR)
        );
        verify(restAuthenticationEntryPoint).commence(
                eq(request),
                eq(response),
                any(AuthenticationException.class)
        );
        verify(jwtService, never()).isTokenValid(anyString(), any());
        verify(filterChain, never()).doFilter(any(), any());
    }
}
