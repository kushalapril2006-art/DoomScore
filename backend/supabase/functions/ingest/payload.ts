const APPS = new Set(["instagram", "youtube", "tiktok", "snapchat", "other"]);
const TOP_FIELDS = new Set(["day", "tzOffsetMinutes", "apps", "live", "clientTime", "appVersion"]);
const APP_FIELDS = new Set(["app", "reels", "watchSeconds", "adsSkipped"]);
const LIVE_FIELDS = new Set(["todayCount", "sessionCount", "goal", "armed", "appName", "sessionStarted", "estimated", "sessionStart", "streak", "ended"]);
const record = (v: unknown): v is Record<string, unknown> => typeof v === "object" && v !== null && !Array.isArray(v);
const integer = (v: unknown, min: number, max: number): v is number => typeof v === "number" && Number.isInteger(v) && v >= min && v <= max;
const allowed = (v: Record<string, unknown>, fields: Set<string>) => Object.keys(v).every(key => fields.has(key));

export interface Live {
  todayCount: number; sessionCount: number; goal: number; armed: boolean; appName: string;
  sessionStarted: boolean; estimated: boolean; sessionStart: number | null; streak: number; ended: boolean;
}
interface AppTotal { app: string; reels: number; watchSeconds: number; adsSkipped: number; }

export function parse(body: unknown): { day: string; apps: AppTotal[]; live?: Live } | string {
  if (!record(body) || !allowed(body, TOP_FIELDS)) return "bad_body";
  const day = body.day;
  if (typeof day !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(day) || !Number.isFinite(Date.parse(day)) || new Date(day).toISOString().slice(0, 10) !== day) return "bad_day";
  if (body.tzOffsetMinutes !== undefined && !integer(body.tzOffsetMinutes, -840, 840)) return "bad_timezone";
  if (body.clientTime !== undefined && (typeof body.clientTime !== "number" || !Number.isFinite(body.clientTime) || body.clientTime < 0 || body.clientTime > 4_102_444_800)) return "bad_clock";
  if (body.appVersion !== undefined && (typeof body.appVersion !== "string" || !/^[A-Za-z0-9.+_-]{1,40}$/.test(body.appVersion))) return "bad_version";
  if (!Array.isArray(body.apps) || body.apps.length > 5) return "bad_apps";
  const apps: AppTotal[] = []; const seen = new Set<string>();
  for (const entry of body.apps) {
    if (!record(entry) || !allowed(entry, APP_FIELDS) || typeof entry.app !== "string" || !APPS.has(entry.app) || seen.has(entry.app)) return "bad_app";
    if (!integer(entry.reels, 0, 20000) || !integer(entry.watchSeconds, 0, 86400) || !integer(entry.adsSkipped, 0, 20000)) return "bad_numbers";
    seen.add(entry.app); apps.push({ app: entry.app, reels: entry.reels, watchSeconds: entry.watchSeconds, adsSkipped: entry.adsSkipped });
  }
  let live: Live | undefined;
  if (body.live !== undefined && body.live !== null) {
    const l = body.live;
    if (!record(l) || !allowed(l, LIVE_FIELDS) || !integer(l.todayCount, 0, 100000) || !integer(l.sessionCount, 0, 100000) || !integer(l.goal, 1, 5000) || typeof l.armed !== "boolean" || typeof l.appName !== "string" || [...l.appName].length > 24 || /[\x00-\x1f\x7f]/.test(l.appName) || typeof l.sessionStarted !== "boolean") return "bad_live";
    if (l.estimated != null && typeof l.estimated !== "boolean" || l.ended != null && typeof l.ended !== "boolean" || l.streak != null && !integer(l.streak, 0, 100000)) return "bad_live";
    if (l.sessionStart != null && (typeof l.sessionStart !== "number" || !Number.isFinite(l.sessionStart))) return "bad_live";
    const now = Date.now() / 1000;
    const start = typeof l.sessionStart === "number" && l.sessionStart > now - 172800 && l.sessionStart < now + 300 ? Math.floor(l.sessionStart) : null;
    live = { todayCount: l.todayCount, sessionCount: l.sessionCount, goal: l.goal, armed: l.armed, appName: l.appName,
      sessionStarted: l.sessionStarted, estimated: l.estimated === true, sessionStart: start, streak: typeof l.streak === "number" ? l.streak : 0, ended: l.ended === true };
  }
  if (apps.length === 0 && live === undefined) return "empty_body";
  return { day, apps, live };
}

/** Enforce a byte cap during reading, rather than after allocating the entire body. */
export async function readBody(req: Request): Promise<string> {
  if (req.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") throw new Error("bad_content_type");
  const declared = req.headers.get("content-length");
  if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) > 8192)) throw new Error("payload_too_large");
  const reader = req.body?.getReader();
  if (!reader) throw new Error("bad_body");
  const chunks: Uint8Array[] = []; let total = 0; let complete = false;
  let timeout: ReturnType<typeof setTimeout> | undefined;
  const expires = new Promise<never>((_, reject) => { timeout = setTimeout(() => reject(new Error("request_timeout")), 8000); });
  try {
    while (true) {
      const { value, done } = await Promise.race([reader.read(), expires]);
      if (done) { complete = true; break; }
      total += value.byteLength;
      if (total > 8192) throw new Error("payload_too_large");
      chunks.push(value);
    }
    const bytes = new Uint8Array(total); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    try { return new TextDecoder("utf-8", { fatal: true }).decode(bytes); }
    catch { throw new Error("bad_encoding"); }
  } finally {
    clearTimeout(timeout);
    if (!complete) void reader.cancel().catch(() => {});
  }
}
