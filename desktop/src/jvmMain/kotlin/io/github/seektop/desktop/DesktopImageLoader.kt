package io.github.seektop.desktop

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.NetworkClient
import coil3.network.NetworkFetcher
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.NetworkResponseBody
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.svg.SvgDecoder
import io.github.seektop.core.NodeSeekSite
import io.github.seektop.foundation.designsys.image.LongLivedImageCacheStrategy
import okio.Buffer

@OptIn(coil3.annotation.ExperimentalCoilApi::class)
fun desktopImageLoader(context: PlatformContext, browser: DesktopBrowser): ImageLoader =
    ImageLoader.Builder(context).components {
        add(SvgDecoder.Factory())
        add(
            NetworkFetcher.Factory(
                networkClient = {
                    object : NetworkClient {
                        override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T {
                            val (response, bytes) = browser.image(request.url, 30L * 1024 * 1024)
                            return block(
                                NetworkResponse(
                                    code = response.code,
                                    headers = NetworkHeaders.Builder().apply { response.headers.forEach { (k, v) -> add(k, v) } }.build(),
                                    body = NetworkResponseBody(Buffer().write(bytes)),
                                ),
                            )
                        }
                    }
                },
                cacheStrategy = { LongLivedImageCacheStrategy(CacheControlCacheStrategy(), NodeSeekSite::isAvatarUrl) },
            ),
        )
    }.build()
