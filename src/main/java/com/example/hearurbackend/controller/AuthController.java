package com.example.hearurbackend.controller;

import com.example.hearurbackend.dto.AuthRequest;
import com.example.hearurbackend.entity.UserEntity;
import com.example.hearurbackend.service.AuthService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import net.minidev.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Controller
@RequestMapping("/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    private final RestTemplate restTemplate;

    public AuthController(AuthService authService, RestTemplate restTemplate) {
        this.authService = authService;
        this.restTemplate = restTemplate;
    }

    @Operation(summary = "안드로이드 앱 소셜 로그인 처리")
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody AuthRequest request) throws JsonProcessingException {
        String provider = request.getProvider();
        String providerId;
        String email;
        String name;

        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        headers.set("Authorization", "Bearer " + request.getAccessToken());

        // 모바일 앱에서 전송한 인증 정보를 받아서 처리
        if (request.getProvider().equals("naver")) {
            String userInfoUri = "https://openapi.naver.com/v1/nid/me";
            ResponseEntity<String> response = restTemplate.exchange(userInfoUri, HttpMethod.GET, entity, String.class);
            ObjectMapper objectMapper = new ObjectMapper();

            // JSON 문자열을 Java 객체로 변환
            Map<String, Object> jsonMap = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {
            });
            String resultCode = (String) jsonMap.get("resultcode");

            // 인증
            if (resultCode == null || !resultCode.equals("00"))
                return ResponseEntity.badRequest().body("{\"code\": \"400\"}");

            Map<String, Object> responseData = (Map<String, Object>) jsonMap.get("response");

            providerId = (String) responseData.get("id");
            email = (String) responseData.get("email");
            name = (String) responseData.get("name");
        } else {
            return ResponseEntity.badRequest().body("{\"code\": \"400\"}");
        }

        String username = provider + " " + providerId;
        log.info("username: {}", username);
        UserEntity newUser = authService.saveUser(username, email, name, "ROLE_USER");

        // AuthService를 통해 JWT 토큰 생성 및 반환
        String token = authService.generateJwtToken(newUser.getUsername());

        // JWT 토큰을 헤더에 담아 응답
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return ResponseEntity.ok().headers(responseHeaders).body("{\"code\": \"200\"}");
    }

    @Operation(summary = "JWT 토큰 헤더로 반환")
    @GetMapping("/jwt")
    public ResponseEntity<String> getJWT(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        String token = "";
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("Authorization")) {
                    token = cookie.getValue();
                }
            }
        }
        if (token.equals("")) {
            return ResponseEntity.badRequest().body("{\"code\": \"400\"}");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        JSONObject responseData = new JSONObject();
        responseData.put("code", "200");
        return ResponseEntity.ok().headers(headers).body(responseData.toString());
    }
}
