package com.example.myapplication.data

import retrofit2.Response
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Outcome of one API call, returned by every repository.
 *
 *  - [Success]      2xx, with the parsed body.
 *  - [HttpError]    the server answered with a non-2xx code; [message] is its localized
 *                   `message` when present. A 2xx answer whose body can't be read is
 *                   reported as HttpError with code [UNREADABLE_BODY].
 *  - [NetworkError] no answer at all (offline, DNS, timeout).
 *
 * Repositories never throw (except coroutine cancellation), so callers just `when` over this.
 */
sealed interface ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>
    data class HttpError(val code: Int, val message: String?) : ApiResult<Nothing>
    data class NetworkError(val cause: IOException) : ApiResult<Nothing>

    val isSuccess: Boolean get() = this is Success
    fun getOrNull(): T? = (this as? Success)?.data

    companion object {
        const val UNREADABLE_BODY = -1
    }
}

/** Runs [call] and maps its response with [transform] when successful. */
suspend fun <T, R> apiCall(call: suspend () -> Response<T>, transform: (Response<T>) -> R): ApiResult<R> =
    try {
        val response = call()
        if (response.isSuccessful) ApiResult.Success(transform(response))
        else ApiResult.HttpError(response.code(), response.serverMessage())
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        ApiResult.NetworkError(e)
    } catch (e: Exception) {
        ApiResult.HttpError(ApiResult.UNREADABLE_BODY, null)
    }

/** For calls where only success/failure matters. */
suspend fun <T> apiCall(call: suspend () -> Response<T>): ApiResult<Unit> = apiCall(call) { }
