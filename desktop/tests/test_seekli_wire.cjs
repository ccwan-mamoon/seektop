"use strict";
const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs/promises");
const os = require("node:os");
const path = require("node:path");
const {createSeekLiService} = require("../browser/seekli.cjs");
(async () => {
    const directory = await fs.mkdtemp(path.join(os.tmpdir(), "seekli-wire-"));
    const received = [];
    const server = http.createServer(async (request, response) => {
        const chunks = [];
        for await (const chunk of request) chunks.push(chunk);
        received.push({method:request.method, headers:request.headers, body:JSON.parse(Buffer.concat(chunks).toString("utf8"))});
        response.writeHead(200, {"Content-Type":"application/json"});
        response.end("{}");
    });
    try {
        await new Promise(resolve => server.listen(0,"127.0.0.1",resolve));
        const session = {
            webRequest:{onBeforeRequest:()=>{}},
            fetch:async (url, options) => {
                assert.equal(url,"https://seek.li/openapi/v1/send");
                return fetch(`http://127.0.0.1:${server.address().port}/send`, options);
            },
        };
        const safeStorage = {isAsyncEncryptionAvailable:async()=>true,encryptStringAsync:async s=>Buffer.from(s)};
        const service=createSeekLiService({session,safeStorage,directory});
        await service.save({key:"sk_live_local_fixture"});
        const payload={mailboxId:42,to:"recipient@example.com,second@example.com",cc:"copy@example.com",bcc:"hidden@example.com",subject:"fixture subject",bodyText:"line 1\nline 2",bodyHtml:"<p>fixture</p>"};
        await service.request({url:"https://seek.li/openapi/v1/send",method:"POST",body:JSON.stringify(payload)});
        assert.equal(received.length,1);
        assert.equal(received[0].method,"POST");
        assert.match(received[0].headers["content-type"],/^application\/json/);
        assert.deepEqual(received[0].body,payload);
        assert.equal(typeof received[0].body.mailboxId,"number");
        assert.equal(typeof received[0].body.to,"string");
        assert.equal(typeof received[0].body.cc,"string");
        assert.equal(typeof received[0].body.bcc,"string");
        assert.equal(received[0].headers.cookie,undefined);
        console.log("PASS: local HTTP wire receives one JSON object with numeric sender, address strings and intact body; no real email sent");
    } finally {
        await new Promise(resolve=>server.close(resolve));
        await fs.rm(directory,{recursive:true,force:true});
    }
})().catch(error=>{console.error(error);process.exitCode=1;});
