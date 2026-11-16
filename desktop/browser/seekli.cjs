"use strict";
const fs = require("node:fs/promises");
const path = require("node:path");
const BASE = "https://seek.li/openapi/v1";
exports.createSeekLiService = ({session, safeStorage, directory}) => {
    const file = path.join(directory, "credentials.bin");
    let key;
    async function load() {
        if (key !== undefined) return key;
        try { key = await safeStorage.decryptStringAsync(await fs.readFile(file)); }
        catch (e) { if (e.code === "ENOENT") key = ""; else throw Error("MAIL_CREDENTIAL_READ"); }
        return key;
    }
    session.webRequest.onBeforeRequest((details, callback) => {
        callback({cancel: !details.url.startsWith(BASE + "/")});
    });
    return {
        async settings() { return {connected: Boolean(await load())}; },
        async save(q) {
            if (typeof q.key !== "string" || !/^sk_live_[^\s]+$/.test(q.key)) throw Error("MAIL_INVALID_KEY");
            if (!await safeStorage.isAsyncEncryptionAvailable()) throw Error("MAIL_ENCRYPTION_UNAVAILABLE");
            const encrypted = await safeStorage.encryptStringAsync(q.key);
            await fs.mkdir(directory, {recursive:true});
            await fs.writeFile(file + ".tmp", encrypted);
            await fs.rename(file + ".tmp", file);
            key = q.key;
            return {connected:true};
        },
        async clear() { await fs.rm(file, {force:true}); key = ""; return {connected:false}; },
        async request(q) {
            const credential = await load();
            if (!credential) return {code:401, body:""};
            const url = new URL(q.url);
            if (url.origin !== "https://seek.li" || !url.pathname.startsWith("/openapi/v1/")) throw Error("MAIL_URL_REFUSED");
            const route = url.pathname.slice("/openapi/v1".length);
            const method = q.method || "GET";
            const allowed = [
                ["POST", /^\/upload\/attachment$/],
                ["GET",/^\/mailboxes$/],
                ["POST",/^\/mailboxes$/],
                ["GET",/^\/mailboxes\/[0-9]+$/],
                ["DELETE",/^\/mailboxes\/[0-9]+$/],
                ["GET",/^\/mailbox-release-holds$/],
                ["POST",/^\/mailbox-release-holds\/[0-9]+\/restore$/],
                ["POST",/^\/mailboxes\/check$/],
                ["PUT",/^\/mailboxes\/[0-9]+\/settings$/],
                ["GET",/^\/mailboxes\/[0-9]+\/mails$/],
                ["GET",/^\/mails$/],
                ["GET",/^\/mails\/unread$/],
                ["GET",/^\/mails\/[0-9]+$/],
                ["DELETE",/^\/mails\/[0-9]+$/],
                ["GET",/^\/mails\/[0-9]+\/raw$/],
                ["GET",/^\/mails\/counts$/],
                ["PUT",/^\/mails\/[0-9]+\/read$/],
                ["PUT",/^\/mails\/[0-9]+\/flag$/],
                ["PUT",/^\/mails\/[0-9]+\/folder$/],
                ["POST",/^\/mails\/[0-9]+\/tags$/],
                ["DELETE",/^\/mails\/[0-9]+\/tags\/[0-9]+$/],
                ["POST",/^\/send$/],
                ["GET",/^\/mails\/[0-9]+\/attachments$/],
                ["GET",/^\/mails\/[0-9]+\/attachments\/[0-9]+$/],
                ["GET",/^\/folders$/],
                ["POST",/^\/folders$/],
                ["PUT",/^\/folders\/[0-9]+$/],
                ["DELETE",/^\/folders\/[0-9]+$/],
                ["GET",/^\/tags$/],
                ["POST",/^\/tags$/],
                ["PUT",/^\/tags\/[0-9]+$/],
                ["DELETE",/^\/tags\/[0-9]+$/],
                ["GET",/^\/contacts$/],
                ["POST",/^\/contacts$/],
                ["PUT",/^\/contacts\/[0-9]+$/],
                ["DELETE",/^\/contacts\/[0-9]+$/],
                ["GET",/^\/filters$/],
                ["POST",/^\/filters$/],
                ["PUT",/^\/filters\/[0-9]+$/],
                ["DELETE",/^\/filters\/[0-9]+$/],
                ["GET",/^\/signatures$/],
                ["POST",/^\/signatures$/],
                ["PUT",/^\/signatures\/[0-9]+$/],
                ["DELETE",/^\/signatures\/[0-9]+$/],
                ["GET",/^\/storage$/],
                ["GET",/^\/quotas$/],
            ].some(([verb,pattern])=>verb===method && pattern.test(route));
            if (!allowed) throw Error("MAIL_OPERATION_REFUSED");
            const headers={Authorization:"Bearer " + credential, Accept:q.binary ? "*/*" : "application/json"};
            let body=q.body;
            if(q.upload) {
                if(method!=="POST" || route!=="/upload/attachment") throw Error("MAIL_UPLOAD_REFUSED");
                const bytes=Buffer.from(q.upload.bytes,"base64");
                if(bytes.length>24*1024*1024) throw Error("MAIL_UPLOAD_LIMIT");
                body=new FormData();
                body.append("file",new Blob([bytes],{type:q.upload.mime || "application/octet-stream"}),q.upload.name);
            } else headers["Content-Type"]="application/json";
            const response = await session.fetch(url.href, {
                method, credentials:"omit", redirect:"error", cache:"no-store",
                headers, body, signal:AbortSignal.timeout(30000),
            });
            const chunks=[]; let size=0;
            const reader=response.body?.getReader();
            try {
                if (reader) for (;;) {
                    const part=await reader.read(); if (part.done) break;
                    size+=part.value.length; if(size>(q.binary ? 32 : 4)*1024*1024) throw Error("MAIL_RESPONSE_LIMIT");
                    chunks.push(Buffer.from(part.value));
                }
                if(q.binary) return {code:response.status,body:Buffer.concat(chunks).toString("base64")};
                const text = Buffer.concat(chunks).toString("utf8");
                if (/^\/mails\/\d+$/.test(route)) {
                    const shape = value => value && typeof value === "object" && !Array.isArray(value)
                        ? Object.fromEntries(Object.entries(value).filter(([name])=>/^[a-zA-Z][a-zA-Z0-9_]{0,39}$/.test(name)).map(([name,v])=>[name,v === null ? "null" : Array.isArray(v) ? "array" : typeof v]))
                        : value === null ? "null" : typeof value;
                    let parsed; try { parsed = JSON.parse(text); } catch {}
                    await fs.mkdir(directory,{recursive:true});
                    await fs.writeFile(path.join(directory,"last-detail-shape.json"),JSON.stringify({status:response.status,bytes:size,root:shape(parsed),data:shape(parsed?.data),mail:shape(parsed?.data?.mail)})).catch(()=>{});
                }
                return {code:response.status,body:text};
            } finally { await reader?.cancel().catch(()=>{}); }
        },
    };
};
