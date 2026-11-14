"use strict";

const trustedRedirect = (raw) => {
    const u = new URL(raw);
    if (u.protocol !== "https:" || u.username || u.password || (u.port && u.port !== "443")) throw Error("Untrusted plugin download URL");
    if (!["github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com"].includes(u.hostname)) throw Error("Untrusted plugin download host");
    return u;
};

/** Dedicated cookie-free marketplace session; never use the forum session. */
exports.createPluginDownloader = (session) => {
    // Electron 45 rejects redirect:'manual'. Like the client updater, follow
    // redirects only in a dedicated session whose boundary checks every request.
    session.webRequest.onBeforeRequest((details, callback) => {
        try { trustedRedirect(details.url); callback({cancel:false}); }
        catch { callback({cancel:true}); }
    });
    return async ({ url, limit }) => {
        const target = trustedRedirect(url);
        if (target.hostname !== "github.com" || !target.pathname.startsWith("/ccwan-mamoon/seektop/releases/download/")) throw Error("Untrusted plugin repository");
        if (!Number.isSafeInteger(limit) || limit < 1 || limit > 32 * 1024 * 1024) throw Error("Invalid plugin download limit");
        const signal = AbortSignal.timeout(60000);
        for (let attempt=0; attempt<3; attempt++) {
            let response, reader;
            try {
                response = await session.fetch(target.href, { credentials:"omit", redirect:"follow", cache:"no-store", signal });
                if (response.status !== 200) throw Error(`Plugin download: HTTP ${response.status}`);
                const chunks = []; let length = 0;
                if (response.body) {
                    reader = response.body.getReader();
                    for (;;) {
                        const { done, value } = await reader.read();
                        if (done) break;
                        length += value.length;
                        if (length > limit) throw Error("Plugin download exceeds size limit");
                        chunks.push(Buffer.from(value));
                    }
                }
                return {body:Buffer.concat(chunks).toString("base64")};
            } catch (error) {
                if (signal.aborted || attempt===2 || /size limit|HTTP/.test(error.message)) throw error;
            } finally {
                if (reader) await reader.cancel().catch(()=>{});
                else await response?.body?.cancel().catch(()=>{});
            }
            await new Promise(resolve=>setTimeout(resolve,250*(attempt+1)));
        }
    };
};
