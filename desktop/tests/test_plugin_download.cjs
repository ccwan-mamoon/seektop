"use strict";
const assert = require("node:assert/strict");
const { createPluginDownloader } = require("../browser/plugin-download.cjs");
const url = "https://github.com/ccwan-mamoon/seektop/releases/download/plugin-market/catalog.json";
async function main() {
    const requests = [];
    let boundary;
    const webRequest={onBeforeRequest:handler=>{boundary=handler;}};
    const fetch = createPluginDownloader({ webRequest, fetch: async (address, options) => {
        requests.push({ address, options });
        return new Response("signed catalog");
    }});
    assert.equal(Buffer.from((await fetch({url,limit:100})).body,"base64").toString(),"signed catalog");
    assert(requests.every(r => r.options.credentials === "omit" && r.options.redirect === "follow"));
    await assert.rejects(fetch({ url:"https://github.com/other/repo/releases/download/tag/file",limit:100 }), /repository/);
    await assert.rejects(fetch({ url:"http://github.com/ccwan-mamoon/seektop/releases/download/tag/file",limit:100 }), /Untrusted/);
    boundary({url:"https://localhost/private"}, result => assert.equal(result.cancel,true));
    boundary({url:"https://release-assets.githubusercontent.com/test"}, result => assert.equal(result.cancel,false));
    const large = createPluginDownloader({webRequest,fetch:async () => new Response("too large")});
    await assert.rejects(large({url,limit:2}),/size limit/);
    console.log("PASS: plugin repository, redirects, cookie isolation and bounded downloads");
}
main().catch(e=>{console.error(e);process.exitCode=1;});
