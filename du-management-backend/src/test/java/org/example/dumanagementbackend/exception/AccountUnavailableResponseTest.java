package org.example.dumanagementbackend.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.example.dumanagementbackend.security.AccountStatusPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class AccountUnavailableResponseTest {

    @Test
    void handleUnauthorized_returnsGenericAccountUnavailableProblem() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        UnauthorizedException exception = new UnauthorizedException(
                AccountStatusPolicy.ACCOUNT_UNAVAILABLE_CODE,
                AccountStatusPolicy.ACCOUNT_UNAVAILABLE_MESSAGE
        );

        ResponseEntity<ProblemDetail> response = handler.handleUnauthorized(exception, request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(AccountStatusPolicy.ACCOUNT_UNAVAILABLE_MESSAGE, response.getBody().getDetail());
        assertEquals(
                AccountStatusPolicy.ACCOUNT_UNAVAILABLE_CODE,
                response.getBody().getProperties().get("errorCode")
        );
    }
}
