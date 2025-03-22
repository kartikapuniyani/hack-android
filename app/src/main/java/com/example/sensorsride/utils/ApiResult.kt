package com.example.sensorsride.utils

sealed interface ApiResult<out R> {
    data object Loading : ApiResult<Nothing>
    data class Success<out T>(val data: T) : ApiResult<T>
    data class Error<out T>(val message: String, val code: Int, val data: Any? = null) :
        ApiResult<T>
    data object None : ApiResult<Nothing>
}