package com.example.fitapp.config;

import com.example.fitapp.auth.JwtAuthFilter;
import com.example.fitapp.auth.JwtService;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.common.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            RateLimitProperties rateLimitProperties,
            ObjectMapper mapper)
            throws Exception {
        JwtAuthFilter jwtFilter = new JwtAuthFilter(jwtService);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimitProperties, mapper);

        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        a ->
                                a.requestMatchers(
                                                "/api/auth/**",
                                                "/swagger-ui",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/actuator/health/**")
                                        .permitAll()
                                        .requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                (req, res, ex) ->
                                                        write(
                                                                mapper,
                                                                res,
                                                                ErrorCode.UNAUTHORIZED,
                                                                "Authentication required"))
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        write(
                                                                mapper,
                                                                res,
                                                                ErrorCode.FORBIDDEN,
                                                                "Access denied")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthFilter.class);
        return http.build();
    }

    private static void write(
            ObjectMapper mapper, HttpServletResponse res, ErrorCode code, String detail)
            throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ProblemDetails.of(code, detail));
    }
}
