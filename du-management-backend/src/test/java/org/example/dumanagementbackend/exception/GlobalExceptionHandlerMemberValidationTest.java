package org.example.dumanagementbackend.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import org.example.dumanagementbackend.dto.auth.RegisterRequest;
import org.example.dumanagementbackend.dto.member.MemberRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerMemberValidationTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new DummyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();

    @Test
    void createMember_invalidPassword_returnsValidationErrorDetail() throws Exception {
        String payload = """
                {
                  "roleId": 1,
                  "username": "member1",
                  "email": "member1@example.com",
                  "password": "abc123",
                  "fullName": "Member One",
                  "dob": null,
                  "joinDate": null,
                  "status": "ACTIVE"
                }
                """;

        String responseBody = mockMvc.perform(post("/test/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"errorCode\":\"VALIDATION_ERROR\""));
        assertTrue(responseBody.contains("Password must be 8-128 characters and include uppercase, lowercase, number, and special character."));
    }

    @Test
    void register_invalidPassword_returnsClearPasswordFormatMessage() throws Exception {
        String payload = """
                {
                  "username": "member1",
                  "email": "member1@example.com",
                  "fullName": "Member One",
                  "password": "abc123",
                  "dob": null
                }
                """;

        String responseBody = mockMvc.perform(post("/test/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"errorCode\":\"VALIDATION_ERROR\""));
        assertTrue(responseBody.contains("Password does not match the required format"));
        assertFalse(responseBody.contains("Invalid request content"));
    }

    @Test
    void badRequest_technicalMessage_returnsGenericSafeDetail() throws Exception {
        String responseBody = mockMvc.perform(get("/test/sql-bad-request"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"errorCode\":\"BAD_REQUEST\""));
        assertTrue(responseBody.contains("The request is invalid. Please check your input and try again."));
        assertFalse(responseBody.toLowerCase().contains("select"));
        assertFalse(responseBody.toLowerCase().contains("password"));
    }

    @Test
    void notFound_internalMessage_returnsGenericSafeDetail() throws Exception {
        String responseBody = mockMvc.perform(get("/test/internal-not-found"))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"errorCode\":\"RESOURCE_NOT_FOUND\""));
        assertTrue(responseBody.contains("The requested resource was not found."));
        assertFalse(responseBody.contains("Required role MEMBER is missing"));
    }

    @Test
    void missingRequestParameter_returnsClearProblemDetail() throws Exception {
        String responseBody = mockMvc.perform(get("/test/search"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"errorCode\":\"MISSING_REQUEST_PARAMETER\""));
        assertTrue(responseBody.contains("Missing required parameter 'q'."));
    }

    @Test
    void invalidPathVariable_returnsClearProblemDetail() throws Exception {
        String responseBody = mockMvc.perform(get("/test/items/not-a-number"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(responseBody.contains("\"errorCode\":\"INVALID_REQUEST_PARAMETER\""));
        assertTrue(responseBody.contains("Invalid value for parameter 'id'."));
        assertFalse(responseBody.contains("NumberFormatException"));
    }

    @RestController
    static class DummyController {
        @PostMapping("/test/members")
        public String create(@Valid @RequestBody MemberRequest request) {
            return "ok";
        }

        @PostMapping("/test/register")
        public String register(@Valid @RequestBody RegisterRequest request) {
            return "ok";
        }

        @GetMapping("/test/sql-bad-request")
        public String sqlBadRequest() {
            throw new BadRequestException("select * from users where password='secret'");
        }

        @GetMapping("/test/internal-not-found")
        public String internalNotFound() {
            throw new ResourceNotFoundException("Required role MEMBER is missing");
        }

        @GetMapping("/test/search")
        public String search(@RequestParam String q) {
            return q;
        }

        @GetMapping("/test/items/{id}")
        public String getItem(@PathVariable Long id) {
            return String.valueOf(id);
        }
    }
}
