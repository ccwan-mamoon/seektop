"use strict";
const assert = require("node:assert/strict");
const {createExchangeRateFetcher, ENDPOINT} = require("../browser/exchange-rates.cjs");
(async () => {
    let guard, response = new Response('[{"rate":0.125}]'), cancelled = false;
    const session = {
        webRequest: {onBeforeRequest(fn) { guard = fn; }},
        async fetch(url, options) {
            assert.equal(url, ENDPOINT);
            assert.equal(options.credentials, "omit");
            assert.equal(options.redirect, "error");
            assert.deepEqual(options.headers, {Accept:"application/json"});
            assert.ok(options.signal);
            return response;
        },
    };
    const fetch = createExchangeRateFetcher(session);
    assert.deepEqual(await fetch({url:"https://attacker.invalid", headers:{Authorization:"secret"}}), {body:'[{"rate":0.125}]'});
    guard({url:ENDPOINT}, result => {cancelled = result.cancel;});
    assert.equal(cancelled, false);
    for (const url of ["https://www.nodeseek.com/", "http://127.0.0.1/", ENDPOINT + "&cookie=secret", "https://api.frankfurter.dev/redirect"]) {
        guard({url}, result => {cancelled = result.cancel;});
        assert.equal(cancelled, true);
    }
    response = new Response("upstream error", {status:503});
    await assert.rejects(fetch, /FX_HTTP_503/);
    response = new Response("x".repeat(65537));
    await assert.rejects(fetch, /FX_RESPONSE_LIMIT/);
    console.log("PASS: fixed FX endpoint, cookie/credential isolation, redirect refusal and bounded responses");
})().catch(error => {console.error(error); process.exitCode = 1;});
