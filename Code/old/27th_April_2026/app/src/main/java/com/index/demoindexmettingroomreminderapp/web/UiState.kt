package com.index.demoindexmettingroomreminderapp.web

/**
 * A generic sealed class to represent UI states for any asynchronous operation.
 * @param T The type of the data to be held in the Success state.
 */
sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
