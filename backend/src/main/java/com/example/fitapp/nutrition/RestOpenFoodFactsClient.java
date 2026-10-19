package com.example.fitapp.nutrition;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.OpenFoodFactsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RestOpenFoodFactsClient implements OpenFoodFactsClient {

    private static final Logger log = LoggerFactory.getLogger(RestOpenFoodFactsClient.class);
    private static final String FIELDS =
            "product_name,product_name_en,generic_name,brands,nutriments,serving_size,serving_quantity";

    private final RestClient client;

    public RestOpenFoodFactsClient(OpenFoodFactsProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.client =
                RestClient.builder()
                        .baseUrl(props.baseUrlOrDefault())
                        .requestFactory(factory)
                        .defaultHeader(HttpHeaders.USER_AGENT, props.userAgentOrDefault())
                        .build();
    }

    @Override
    public Optional<FoodData> byBarcode(String barcode) {
        try {
            JsonNode body =
                    client.get()
                            .uri("/api/v2/product/{code}.json?fields={fields}", barcode, FIELDS)
                            .retrieve()
                            .body(JsonNode.class);
            return OffParser.parse(barcode, body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return Optional.empty();
            }
            log.warn("Open Food Facts lookup failed with HTTP {}", e.getStatusCode().value());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The barcode database is unavailable");
        } catch (RestClientException e) {
            log.warn("Open Food Facts lookup failed: {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The barcode database is unavailable");
        }
    }
}
