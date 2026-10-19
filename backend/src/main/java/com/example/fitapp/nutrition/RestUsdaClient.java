package com.example.fitapp.nutrition;

import com.example.fitapp.common.ApiException;
import com.example.fitapp.common.ErrorCode;
import com.example.fitapp.config.UsdaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RestUsdaClient implements UsdaClient {

    private static final Logger log = LoggerFactory.getLogger(RestUsdaClient.class);
    private static final String DATA_TYPES = "Foundation,SR Legacy,Survey (FNDDS)";
    private static final int PAGE_SIZE = 25;

    private final RestClient client;
    private final String apiKey;

    public RestUsdaClient(UsdaProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.client = RestClient.builder().baseUrl(props.baseUrlOrDefault()).requestFactory(factory).build();
        this.apiKey = props.apiKey();
    }

    @Override
    public List<FoodData> search(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "Food search is not configured");
        }
        try {
            // Values go in as URI variables so they are encoded strictly (a "&" in a query stays data).
            JsonNode body =
                    client.get()
                            .uri(
                                    "/foods/search?api_key={key}&query={q}&pageSize={n}&dataType={types}",
                                    apiKey,
                                    query,
                                    PAGE_SIZE,
                                    DATA_TYPES)
                            .retrieve()
                            .body(JsonNode.class);
            return UsdaParser.parseSearch(body);
        } catch (RestClientResponseException e) {
            // Never log the exception message: it contains the request URL and therefore the API key.
            log.warn("USDA search failed with HTTP {}", e.getStatusCode().value());
            if (e.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                throw new ApiException(
                        ErrorCode.UPSTREAM_UNAVAILABLE, "The food database is busy, try again in a minute");
            }
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The food database is unavailable");
        } catch (RestClientException e) {
            log.warn("USDA search failed: {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.UPSTREAM_UNAVAILABLE, "The food database is unavailable");
        }
    }
}
