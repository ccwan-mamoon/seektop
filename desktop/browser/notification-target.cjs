const { randomUUID } = require("node:crypto");
function destination(value) {
    if (!value || !["INTERACTIONS", "MESSAGES", "REMINDERS"].includes(value.tab)) throw Error("Invalid notification destination");
    for (const key of ["postId", "uid"]) {
        if (value[key] != null && (!Number.isSafeInteger(value[key]) || value[key] <= 0)) throw Error("Invalid notification identifier");
    }
    for (const key of ["floor", "userName", "reminderId"]) {
        if (value[key] != null && (typeof value[key] !== "string" || value[key].length > 512)) throw Error("Invalid notification text");
    }
    if (value.account != null && !Number.isInteger(value.account)) throw Error("Invalid notification account");
    return Object.fromEntries(["tab", "postId", "uid", "floor", "userName", "reminderId", "account"].filter(key => value[key] != null).map(key => [key, value[key]]));
}
function activation(target = { tab: "INTERACTIONS" }) { return { id: randomUUID(), destination: destination(target) }; }
function encode(value) { return Buffer.from(JSON.stringify(value)).toString("base64url"); }
function decode(value) {
    if (typeof value !== "string" || value.length > 8192) throw Error("Invalid notification activation");
    const parsed = JSON.parse(Buffer.from(value.replace(/^seektop:/, ""), "base64url").toString("utf8"));
    if (typeof parsed.id !== "string" || parsed.id.length > 128) throw Error("Invalid notification activation ID");
    return { id: parsed.id, destination: destination(parsed.destination) };
}
function xml(value) { return String(value).replace(/[<>&"']/g, character => ({ "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;", "'": "&apos;" })[character]); }
module.exports = { activation, encode, decode, xml };
