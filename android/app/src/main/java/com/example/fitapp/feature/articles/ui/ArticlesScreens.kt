package com.example.fitapp.feature.articles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fitapp.core.designsystem.ManTheme
import com.example.fitapp.core.designsystem.components.EmptyState
import com.example.fitapp.core.designsystem.components.ErrorState
import com.example.fitapp.core.designsystem.components.LoadingState
import com.example.fitapp.core.designsystem.components.ManCard
import com.example.fitapp.core.designsystem.components.ManChip
import com.example.fitapp.feature.articles.domain.Article
import com.example.fitapp.feature.articles.domain.ArticleCategory
import com.example.fitapp.feature.articles.domain.ArticleText
import com.example.fitapp.feature.articles.domain.Markdown
import com.example.fitapp.feature.articles.domain.MdBlock
import com.example.fitapp.feature.workout.ui.WorkoutScaffold

@Composable
fun ArticlesScreen(
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    viewModel: ArticlesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "Learn", onBack = onBack) { padding ->
        ArticlesContent(state, viewModel::onCategory, viewModel::load, onOpenArticle, Modifier.padding(padding))
    }
}

@Composable
fun ArticlesContent(
    state: ArticlesUiState,
    onCategory: (ArticleCategory?) -> Unit,
    onRetry: () -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().testTag("articles")) {
        LazyRow(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ManChip("All", selected = state.category == null, onClick = { onCategory(null) }, modifier = Modifier.testTag("article_cat_all")) }
            items(ArticleCategory.entries) { category ->
                ManChip(category.label, selected = state.category == category, onClick = { onCategory(category) }, modifier = Modifier.testTag("article_cat_${category.name}"))
            }
        }
        when (val list = state.list) {
            ArticleListState.Loading -> LoadingState()
            is ArticleListState.Error -> ErrorState(message = list.message, onRetry = onRetry)
            is ArticleListState.Content ->
                if (list.articles.isEmpty()) {
                    EmptyState(title = "No articles here yet", message = "Pick another category.")
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (list.savedCopy) {
                            Text("Showing the copy saved on this phone", style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
                        }
                        list.articles.forEach { article ->
                            ManCard(onClick = { onOpenArticle(article.slug) }, modifier = Modifier.fillMaxWidth().testTag("article_${article.slug}")) {
                                Text(article.title, style = MaterialTheme.typography.titleMedium)
                                Text(article.summary, style = MaterialTheme.typography.bodyMedium)
                                Text(ArticleText.meta(article.category, article.readMinutes), style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
                            }
                        }
                    }
                }
        }
    }
}

@Composable
fun ArticleScreen(
    onBack: () -> Unit,
    viewModel: ArticleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScaffold(title = "Article", onBack = onBack) { padding ->
        ArticleContent(state, viewModel::load, Modifier.padding(padding))
    }
}

@Composable
fun ArticleContent(
    state: ArticleState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ArticleState.Loading -> LoadingState(modifier = modifier)
        is ArticleState.Error -> ErrorState(message = state.message, onRetry = onRetry, modifier = modifier)
        is ArticleState.Content -> ArticleBody(state.article, state.savedCopy, modifier)
    }
}

@Composable
private fun ArticleBody(
    article: Article,
    savedCopy: Boolean,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("article"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(article.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(ArticleText.meta(article.category, article.readMinutes), style = MaterialTheme.typography.labelLarge, color = ManTheme.colors.muted)
        if (savedCopy) {
            Text("Showing the copy saved on this phone", style = MaterialTheme.typography.labelMedium, color = ManTheme.colors.muted)
        }
        MarkdownView(article.bodyMarkdown)
    }
}

/** Renders the article's Markdown: headings, paragraphs, bullets, numbered items and bold. */
@Composable
fun MarkdownView(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().testTag("markdown"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Markdown.parse(markdown).forEach { block ->
            when (block) {
                is MdBlock.Heading ->
                    Text(
                        text = block.text,
                        style = if (block.level <= 2) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 6.dp).semantics { heading() },
                    )
                is MdBlock.Paragraph -> Text(styled(block.text), style = MaterialTheme.typography.bodyLarge)
                is MdBlock.Bullet -> ListLine("\u2022", block.text)
                is MdBlock.Numbered -> ListLine("${block.number}.", block.text)
            }
        }
    }
}

@Composable
private fun ListLine(
    marker: String,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(marker, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp))
        Text(styled(text), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

private fun styled(text: String) =
    buildAnnotatedString {
        Markdown.inline(text).forEach { span ->
            if (span.bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) } else append(span.text)
        }
    }