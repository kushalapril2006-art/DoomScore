// POST /functions/v1/ingest — single-file Edge Function (paste-able into the Supabase dashboard).
// Called by the Screen Time monitor (each minute of use), the broadcast extension (every
// few seconds while precise mode counts) and the app (catch-up sync).
// Auth: scoped device token in the `x-device-token` header.
// Body: { day, tzOffsetMinutes, apps: [{app, reels, watchSeconds, adsSkipped}],
//         live?: {todayCount, sessionCount, goal, armed, appName, sessionStarted,
//                 estimated?, sessionStart?, streak?, ended?},
//         clientTime, appVersion }
// Secrets (optional, for Dynamic Island updates): APNS_KEY_ID, APNS_TEAM_ID,
// APNS_PRIVATE_KEY (.p8 contents), APNS_BUNDLE_ID.
import { createClient } from "npm:@supabase/supabase-js@2.117.2";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false, autoRefreshToken: false },
});

import { parse, readBody, type Live } from "./payload.ts";

// ─── helpers ────────────────────────────────────────────────────────────────

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "cache-control": "no-store", "x-content-type-options": "nosniff" },
  });
}

async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isInt(value: unknown, min: number, max: number): value is number {
  return typeof value === "number" && Number.isInteger(value) && value >= min && value <= max;
}

// ─── APNs (Live Activity pushes) ────────────────────────────────────────────

const APNS_KEY_ID = Deno.env.get("APNS_KEY_ID") ?? "";
const APNS_TEAM_ID = Deno.env.get("APNS_TEAM_ID") ?? "";
const APNS_PRIVATE_KEY = Deno.env.get("APNS_PRIVATE_KEY") ?? "";
const APNS_BUNDLE_ID = Deno.env.get("APNS_BUNDLE_ID") ?? "";
const apnsConfigured = APNS_KEY_ID !== "" && APNS_TEAM_ID !== "" && APNS_PRIVATE_KEY !== "" && APNS_BUNDLE_ID !== "";

let cachedJwt: { token: string; issuedAt: number } | null = null;
let cachedKey: CryptoKey | null = null;

function base64url(bytes: Uint8Array): string {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): Uint8Array {
  const body = pem.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, "").replace(/\\n/g, "").replace(/\s+/g, "");
  const binary = atob(body);
  const out = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
  return out;
}

/** ES256 provider token, cached for 50 minutes (Apple allows up to 60). */
async function providerToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedJwt && now - cachedJwt.issuedAt < 50 * 60) return cachedJwt.token;
  if (!cachedKey) {
    cachedKey = await crypto.subtle.importKey("pkcs8", pemToDer(APNS_PRIVATE_KEY), { name: "ECDSA", namedCurve: "P-256" }, false, [
      "sign",
    ]);
  }
  const encoder = new TextEncoder();
  const header = base64url(encoder.encode(JSON.stringify({ alg: "ES256", kid: APNS_KEY_ID })));
  const claims = base64url(encoder.encode(JSON.stringify({ iss: APNS_TEAM_ID, iat: now })));
  const signingInput = `${header}.${claims}`;
  // WebCrypto returns the raw r||s (IEEE P1363) signature that JWS expects.
  const signature = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, cachedKey, encoder.encode(signingInput)));
  const token = `${signingInput}.${base64url(signature)}`;
  cachedJwt = { token, issuedAt: now };
  return token;
}

interface ApnsResult {
  status: number;
  reason?: string;
}

async function sendLiveActivityPush(deviceToken: string, env: string, payload: Record<string, unknown>, priority: 5 | 10): Promise<ApnsResult> {
  const host = env === "production" ? "api.push.apple.com" : "api.sandbox.push.apple.com";
  const response = await fetch(`https://${host}/3/device/${deviceToken}`, {
    method: "POST",
    headers: {
      authorization: `bearer ${await providerToken()}`,
      "apns-topic": `${APNS_BUNDLE_ID}.push-type.liveactivity`,
      "apns-push-type": "liveactivity",
      "apns-priority": String(priority),
      "content-type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  if (response.status === 200) return { status: 200 };
  let reason: string | undefined;
  try {
    reason = (await response.json())?.reason;
  } catch {
    reason = undefined;
  }
  return { status: response.status, reason };
}

function isDeadToken(result: ApnsResult): boolean {
  return result.status === 410 || result.reason === "BadDeviceToken" || result.reason === "Unregistered" || result.reason === "ExpiredToken";
}

// ─── request handling ───────────────────────────────────────────────────────

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  // Authenticate the scoped device token (only its SHA-256 is stored server-side).
  const token = req.headers.get("x-device-token") ?? "";
  if (!/^[0-9a-f]{64}$/.test(token)) return json({ error: "unauthorized" }, 401);
  const tokenHash = await sha256Hex(token);
  const { data: userId, error: authError } = await admin.rpc("device_user", { p_token_hash: tokenHash });
  if (authError) {
    console.error("device_user failed", authError.message);
    return json({ error: "server_error" }, 500);
  }
  if (!userId) return json({ error: "unauthorized" }, 401);

  let raw: string;
  try { raw = await readBody(req); }
  catch (error) {
    const message = error instanceof Error ? error.message : "bad_body";
    const code = ["payload_too_large", "request_timeout", "bad_content_type", "bad_body"].includes(message) ? message : "bad_body";
    return json({ error: code }, code === "payload_too_large" ? 413 : code === "request_timeout" ? 408 : 400);
  }
  let body: unknown;
  try {
    body = JSON.parse(raw);
  } catch {
    return json({ error: "bad_json" }, 400);
  }
  const parsed = parse(body);
  if (typeof parsed === "string") return json({ error: parsed }, 400);

  let rateLimited = false;
  if (parsed.apps.length > 0) {
    const { data, error } = await admin.rpc("ingest_stats", {
      p_user: userId,
      p_device_hash: tokenHash,
      p_day: parsed.day,
      p_rows: parsed.apps,
    });
    if (error) {
      console.error("ingest_stats failed", error.message);
      return json({ error: "server_error" }, 500);
    }
    if (isRecord(data) && data.ok === false) {
      // A visit starting or ending still reaches the Dynamic Island.
      const transition = parsed.live && (parsed.live.sessionStarted || parsed.live.ended);
      if (data.error !== "rate_limited" || !transition) {
        const code = ["rate_limited", "unauthorized", "bad_day", "bad_rows", "bad_device", "implausible_growth"].includes(String(data.error)) ? String(data.error) : "invalid_stats";
        return json({ ok: false, error: code }, code === "rate_limited" ? 429 : code === "unauthorized" ? 401 : 400);
      }
      rateLimited = true;
    }
  }

  if (parsed.live && apnsConfigured) {
    try {
      await mirrorToLiveActivity(userId as string, parsed.live);
    } catch (e) {
      console.error("apns", e);
    }
  }
  return rateLimited ? json({ ok: false, error: "rate_limited" }, 429) : json({ ok: true });
});

interface UpdateTokenRow {
  token: string;
  apns_env: string;
  updated_at: string;
  last_push_at: string | null;
  last_high_priority_at: string | null;
}

/** Ends a Live Activity and forgets its token. */
async function endActivity(row: { token: string; apns_env: string }, contentState: Record<string, unknown>, now: number, dismissIn: number) {
  await sendLiveActivityPush(row.token, row.apns_env, {
    aps: { timestamp: now, event: "end", "content-state": contentState, "dismissal-date": now + dismissIn },
  }, 10);
  await admin.from("live_activity_tokens").delete().eq("token", row.token);
}

/** Pushes the new count to the user's Live Activity (or starts / ends one). */
async function mirrorToLiveActivity(userId: string, live: Live) {
  const now = Math.floor(Date.now() / 1000);
  const contentState = {
    todayCount: live.todayCount,
    sessionCount: live.sessionCount,
    goal: live.goal,
    armed: live.armed,
    appName: live.appName,
    updatedAt: now,
    estimated: live.estimated,
    sessionStart: live.sessionStart ?? 0,
    streak: live.streak,
  };
  // Auto mode reports every minute of use; a few quiet minutes = paused.
  const staleDate = now + (live.estimated ? 240 : 1800);

  const since = new Date(Date.now() - 8 * 3600 * 1000).toISOString();
  const { data } = await admin
    .from("live_activity_tokens")
    .select("token, apns_env, updated_at, last_push_at, last_high_priority_at")
    .eq("user_id", userId)
    .eq("kind", "update")
    .gte("updated_at", since);
  let updateTokens = (data ?? []) as UpdateTokenRow[];

  // The visit is over: end the activity, keep the final count on the Lock Screen briefly.
  if (live.ended) {
    for (const row of updateTokens) await endActivity(row, contentState, now, 120);
    return;
  }

  // A new auto-mode visit: retire activities left over from earlier visits
  // (ones registered before this visit began), then start a fresh one below.
  if (live.sessionStarted && live.estimated) {
    const cutoff = (live.sessionStart ?? now) - 60;
    const old = updateTokens.filter((row) => Date.parse(row.updated_at) / 1000 < cutoff);
    for (const row of old) await endActivity(row, contentState, now, 0);
    updateTokens = updateTokens.filter((row) => !old.includes(row));
  }

  if (updateTokens.length > 0) {
    for (const row of updateTokens) {
      const lastHigh = row.last_high_priority_at ? Date.parse(row.last_high_priority_at) / 1000 : 0;
      // High priority at most every ~20 s; the rest go out as low priority to
      // stay inside Apple's Live Activity update budget.
      const priority: 5 | 10 = now - lastHigh > 20 || !live.armed ? 10 : 5;
      const result = await sendLiveActivityPush(row.token, row.apns_env, {
        aps: { timestamp: now, event: "update", "content-state": contentState, "stale-date": staleDate },
      }, priority);
      if (isDeadToken(result)) {
        await admin.from("live_activity_tokens").delete().eq("token", row.token);
      } else if (result.status === 200) {
        const stamp = new Date().toISOString();
        const patch: Record<string, string> = { last_push_at: stamp };
        if (priority === 10) patch.last_high_priority_at = stamp;
        await admin.from("live_activity_tokens").update(patch).eq("token", row.token);
      } else {
        console.warn("apns update", result.status, result.reason);
      }
    }
    return;
  }

  // No running activity: start one remotely (iOS 17.2+ push-to-start), at most
  // once per 5 minutes, only when a scrolling session begins.
  if (!live.sessionStarted || !live.armed) return;
  const { data: startTokens } = await admin
    .from("live_activity_tokens")
    .select("token, apns_env, last_push_at")
    .eq("user_id", userId)
    .eq("kind", "start");
  for (const row of startTokens ?? []) {
    const last = row.last_push_at ? Date.parse(row.last_push_at) / 1000 : 0;
    if (now - last < 300) continue;
    const result = await sendLiveActivityPush(row.token, row.apns_env, {
      aps: {
        timestamp: now,
        event: "start",
        "content-state": contentState,
        "attributes-type": "DoomActivityAttributes",
        attributes: { sessionStartEpoch: live.sessionStart ?? now },
        "stale-date": staleDate,
        alert: {
          title: live.estimated ? "tracking your scroll 👀" : "counting your reels 👀",
          body: `${live.estimated ? "≈" : ""}${live.todayCount} today · ${live.appName}`,
        },
      },
    }, 10);
    if (isDeadToken(result)) {
      await admin.from("live_activity_tokens").delete().eq("token", row.token);
    } else if (result.status === 200) {
      await admin.from("live_activity_tokens").update({ last_push_at: new Date().toISOString() }).eq("token", row.token);
    }
  }
}
