"use strict";

const ENDPOINT = "https://api.frankfurter.dev/v2/rates?base=CNY&quotes=USD,AUD,CAD,CHF,EUR,GBP,HKD,INR,JPY,KRW,NZD,RUB,SGD,TWD";
exports.ENDPOINT = ENDPOINT;

/** Public reference data only: no forum cookies, AI keys, posts or caller-controlled URLs. */
exports.createExchangeRateFetcher = session => {
    session.webRequest.onBeforeRequest((details, callback) => {
        callback({cancel: details.url !== ENDPOINT});
    });
    return async () => {
        let response, reader;
        try {
            response = await session.fetch(ENDPOINT, {
                credentials: "omit", redirect: "error", cache: "no-store",
                headers: {Accept: "application/json"}, signal: AbortSignal.timeout(10000),
            });
            if (response.status !== 200) throw Error("FX_HTTP_" + response.status);
            const chunks = []; let size = 0;
            if (!response.body) throw Error("FX_EMPTY_RESPONSE");
            reader = response.body.getReader();
            for (;;) {
                const {done, value} = await reader.read();
                if (done) break;
                size += value.length;
                if (size > 65536) throw Error("FX_RESPONSE_LIMIT");
                chunks.push(Buffer.from(value));
            }
            return {body: Buffer.concat(chunks).toString("utf8")};
        } finally {
            if (reader) await reader.cancel().catch(() => {});
            else await response?.body?.cancel().catch(() => {});
        }
    };
};
