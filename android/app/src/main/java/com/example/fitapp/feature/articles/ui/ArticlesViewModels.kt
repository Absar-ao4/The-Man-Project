package com.example.fitapp.feature.articles.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitapp.feature.articles.domain.Article
import com.example.fitapp.feature.articles.domain.ArticleCategory
import com.example.fitapp.feature.articles.domain.ArticleRepository
import com.example.fitapp.feature.articles.domain.ArticleSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ArticleListState {
    data object Loading : ArticleListState

    data class Content(val articles: List<ArticleSummary>, val savedCopy: Boolean) : ArticleListState

    data class Error(val message: String) : ArticleListState
}

data class ArticlesUiState(
    /** Null means every category. */
    val category: ArticleCategory? = null,
    val list: ArticleListState = ArticleListState.Loading,
)

@HiltViewModel
class ArticlesViewModel
    @Inject
    constructor(
        private val repository: ArticleRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ArticlesUiState())
        val state: StateFlow<ArticlesUiState> = _state.asStateFlow()

        init {
            load()
        }

        fun onCategory(category: ArticleCategory?) {
            if (_state.value.category == category) return
            _state.update { it.copy(category = category) }
            load()
        }

        fun load() {
            val category = _state.value.category
            _state.update { it.copy(list = ArticleListState.Loading) }
            viewModelScope.launch {
                val load = repository.list(category)
                // Ignore an answer for a category the user has already moved away from.
                if (_state.value.category != category) return@launch
                val data = load.data
                _state.update {
                    it.copy(list = if (data == null) ArticleListState.Error(load.error?.message ?: "Could not load the articles.") else ArticleListState.Content(data, load.showingSavedCopy))
                }
            }
        }
    }

sealed interface ArticleState {
    data object Loading : ArticleState

    data class Content(val article: Article, val savedCopy: Boolean) : ArticleState

    data class Error(val message: String) : ArticleState
}

@HiltViewModel
class ArticleViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val repository: ArticleRepository,
    ) : ViewModel() {
        private val slug: String = checkNotNull(savedState["slug"])

        private val _state = MutableStateFlow<ArticleState>(ArticleState.Loading)
        val state: StateFlow<ArticleState> = _state.asStateFlow()

        init {
            load()
        }

        fun load() {
            _state.value = ArticleState.Loading
            viewModelScope.launch {
                val load = repository.article(slug)
                val data = load.data
                _state.value = if (data == null) ArticleState.Error(load.error?.message ?: "Could not load this article.") else ArticleState.Content(data, load.showingSavedCopy)
            }
        }
    }