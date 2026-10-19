package com.example.fitapp.articles;

import com.example.fitapp.articles.ArticleDtos.ArticleDto;
import com.example.fitapp.articles.ArticleDtos.ArticleSummaryDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/articles")
@Tag(name = "Articles")
public class ArticleController {

    private final ArticleService service;

    public ArticleController(ArticleService service) {
        this.service = service;
    }

    @GetMapping
    public List<ArticleSummaryDto> list(@RequestParam(required = false) ArticleCategory category) {
        return service.list(category);
    }

    @GetMapping("/{slug}")
    public ArticleDto get(@PathVariable String slug) {
        return service.get(slug);
    }
}
