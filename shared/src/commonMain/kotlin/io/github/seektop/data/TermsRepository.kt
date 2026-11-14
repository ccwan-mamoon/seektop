package io.github.seektop.data

import io.github.seektop.core.NodeSeekSite
import io.github.seektop.core.html.TermsParser
import io.github.seektop.foundation.core.net.HtmlSource
import io.github.seektop.model.TermsDocument

fun interface TermsRepository {
    suspend fun terms(): TermsDocument
}

class NetworkTermsRepository(
    private val htmlSource: HtmlSource,
) : TermsRepository {
    override suspend fun terms(): TermsDocument =
        TermsParser.parse(htmlSource.getHtml(NodeSeekSite.TERMS_OF_SERVICE_PATH))
}
