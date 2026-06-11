package eu.kanade.tachiyomi.ui.novel

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat

class NovelBrowseScreenModel : StateScreenModel<NovelBrowseScreenModel.State>(State()) {

    init {
        refreshSaved()
        load(reset = true)
    }

    fun onQueryChange(query: String) = mutableState.update { it.copy(query = query) }

    fun setLang(lang: NovelLang) {
        if (state.value.lang == lang) return
        mutableState.update { it.copy(lang = lang) }
        load(reset = true)
    }

    fun toggleSearch() {
        val active = !state.value.searchActive
        mutableState.update { it.copy(searchActive = active) }
        if (!active && state.value.submittedQuery.isNotEmpty()) {
            mutableState.update { it.copy(query = "", submittedQuery = "") }
            load(reset = true)
        }
    }

    fun submitSearch() {
        mutableState.update { it.copy(submittedQuery = it.query.trim()) }
        load(reset = true)
    }

    fun toggleSavedMode() {
        val saved = !state.value.savedMode
        mutableState.update { it.copy(savedMode = saved) }
        if (saved) refreshSaved()
    }

    fun refreshSaved() {
        mutableState.update { it.copy(saved = NovelStore.getSaved()) }
    }

    fun loadMore() {
        if (state.value.isLoading || !state.value.canLoadMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean) {
        screenModelScope.launchIO {
            val current = state.value
            val page = if (reset) 1 else current.page + 1
            val query = current.submittedQuery
            mutableState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    items = if (reset) emptyList() else it.items,
                )
            }
            try {
                val result = if (query.isBlank()) {
                    NovelSource.popular(current.lang, page)
                } else {
                    NovelSource.search(current.lang, query, page)
                }
                mutableState.update {
                    val merged = (if (reset) result else it.items + result).distinctBy { n -> n.url }
                    it.copy(
                        items = merged,
                        page = page,
                        isLoading = false,
                        canLoadMore = result.isNotEmpty(),
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                mutableState.update {
                    it.copy(isLoading = false, error = "Gagal memuat: ${e.message}")
                }
            }
        }
    }

    @Immutable
    data class State(
        val lang: NovelLang = NovelLang.EN,
        val savedMode: Boolean = false,
        val searchActive: Boolean = false,
        val query: String = "",
        val submittedQuery: String = "",
        val items: List<NovelItem> = emptyList(),
        val saved: List<NovelItem> = emptyList(),
        val page: Int = 1,
        val isLoading: Boolean = false,
        val canLoadMore: Boolean = true,
        val error: String? = null,
    )
}
