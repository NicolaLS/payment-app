package xyz.lilsus.papp.data.blink

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.json.BufferedSourceJsonReader
import com.apollographql.apollo.api.parseResponse
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.network.NetworkTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okio.Buffer

internal class BlinkApolloTestTransport(private val handler: (ApolloRequest<*>) -> ApolloResponse<*>) : NetworkTransport {

    val requests = mutableListOf<ApolloRequest<*>>()

    override fun <D : Operation.Data> execute(request: ApolloRequest<D>): Flow<ApolloResponse<D>> {
        requests += request

        @Suppress("UNCHECKED_CAST")
        return flowOf(handler(request) as ApolloResponse<D>)
    }

    override fun dispose() = Unit
}

internal fun createBlinkApolloTestClient(transport: BlinkApolloTestTransport): ApolloClient = ApolloClient.Builder()
    .networkTransport(transport)
    .build()

internal fun <D : Operation.Data> ApolloRequest<D>.responseFromJson(json: String): ApolloResponse<D> = operation.parseResponse(
    jsonReader = BufferedSourceJsonReader(Buffer().writeUtf8(json)),
    requestUuid = requestUuid
)

internal fun <D : Operation.Data> ApolloRequest<D>.httpErrorResponse(
    statusCode: Int,
    message: String = "HTTP $statusCode"
): ApolloResponse<D> = exceptionResponse(
    ApolloHttpException(
        statusCode = statusCode,
        headers = emptyList(),
        body = null,
        message = message
    )
)

internal fun <D : Operation.Data> ApolloRequest<D>.exceptionResponse(exception: ApolloException): ApolloResponse<D> =
    ApolloResponse.Builder(operation = operation, requestUuid = requestUuid)
        .exception(exception)
        .build()

internal fun ApolloRequest<*>.apiKeyHeader(): String? = httpHeaders
    ?.lastOrNull { header -> header.name.equals(API_KEY_HEADER, ignoreCase = true) }
    ?.value

private const val API_KEY_HEADER = "X-API-KEY"
