package com.sms.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.sms.Fixtures;

/** Signs in through the real API and attaches the bearer token to requests. */
final class ApiTestSupport {

    private ApiTestSupport() {
    }

    /** The full token response body for a Fixtures user. */
    static String loginBody(MockMvc mvc, String login) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + login + "\",\"password\":\"" + Fixtures.PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    static String accessToken(MockMvc mvc, String login) throws Exception {
        return JsonPath.read(loginBody(mvc, login), "$.accessToken");
    }

    static RequestPostProcessor bearer(String accessToken) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
            return request;
        };
    }
}
