package com.gridcc.doomscore.android.network

import android.content.Context
import android.os.Build
import com.gridcc.doomscore.android.BuildConfig
import com.gridcc.doomscore.android.core.InputRules
import com.gridcc.doomscore.android.core.NetworkRules
import com.gridcc.doomscore.android.data.DoomStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class BattleRow(val id: String, val name: String, val handle: String, val emoji: String, val reels: Int, val me: Boolean)
data class BattleState(val signedIn: Boolean = false, val profile: JSONObject? = null, val rows: List<BattleRow> = emptyList(), val busy: Boolean = false, val error: String? = null)

class BattleClient(context: Context, private val store: DoomStore) {
    private val vault = SecureVault(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val epoch = AtomicLong(0)
    private var operationEpoch = 0L
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_KEY
    val configured = (BuildConfig.BATTLES_ENABLED || BuildConfig.LEAGUE_ONLINE_ENABLED) && runCatching { InputRules.backend(base, key) }.isSuccess
    private var session = vault.get("session")?.let { runCatching { trimSession(JSONObject(it), cached = true) }.getOrNull() }
    private val sharing = AtomicBoolean(session != null)
    val state = MutableStateFlow(BattleState(signedIn = session != null, profile = if (session != null) vault.get("profile")?.let { runCatching { trimProfile(JSONObject(it)) }.getOrNull() } else null))
    private var lastSync = 0L
    val accountId: String? get() = session?.getJSONObject("user")?.getString("id")
    private var period = "day"
    private val profileFields = "id,handle,display_name,emoji,color"

    private fun currentOperation() {
        if (operationEpoch != epoch.get()) throw CancellationException("Account disconnected")
    }
    private fun request(path: String, method: String = "POST", body: Any? = null, token: String? = null, device: String? = null, prefer: String? = null): String {
        check(configured) { "Battles unavailable in this version." }
        InputRules.backend(base, key)
        currentOperation()
        if (device != null && !sharing.get()) throw CancellationException("Sharing disconnected")
        val connection = URL("$base/$path").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.requestMethod = method; connection.connectTimeout = 12_000; connection.readTimeout = 15_000
            connection.setRequestProperty("apikey", key)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            token?.let { require(Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").matches(it) && it.length <= 8192); connection.setRequestProperty("Authorization", "Bearer $it") }
            device?.let { require(Regex("[0-9a-f]{64}").matches(it)); connection.setRequestProperty("x-device-token", it) }
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                require(bytes.size <= 8192) { "Request is too large" }
                currentOperation()
                if (device != null && !sharing.get()) throw CancellationException("Sharing disconnected")
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            check(status !in 300..399) { "Unexpected server redirect" }
            val limit = if (status in 200..299) NetworkRules.MAX_RESPONSE else NetworkRules.MAX_ERROR
            require(connection.contentLengthLong <= limit) { "Invalid server response" }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { NetworkRules.readBounded(it, limit) }.orEmpty()
            if (text.isNotBlank()) {
                val type = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
                require(type == "application/json" || type.startsWith("application/") && type.endsWith("+json")) { "Invalid server response" }
            }
            currentOperation()
            if (status !in 200..299) {
                val error = runCatching { JSONObject(text) }.getOrNull()
                val code = connection.getHeaderField("x-doomscore-error") ?: error?.optString("code").orEmpty()
                // Server text may contain database details or submitted values. Never display it.
                val internal = error?.optString("message").orEmpty()
                val message = when {
                    code == "username_taken" || error?.optString("error") == "username_taken" -> "That username is taken. Try another."
                    code == "23505" -> "That handle is taken. Try another."
                    status == 429 || code == "rate_limited" || internal == "rate_limited" -> "Please wait a little before trying again."
                    code == "captcha_failed" || code == "captcha_required" -> "Complete verification to connect battles."
                    code == "invite_self" || internal == "invite_self" -> "That's your own invite."
                    code == "invite_invalid" || internal == "invite_invalid" -> "That invite is invalid or expired."
                    code == "too_many_friends" || internal == "too_many_friends" -> "This battle's friend limit has been reached."
                    status == 401 -> "Your session expired. Please reconnect."
                    else -> "Could not complete the request. Please try again."
                }
                throw IllegalStateException(message)
            }
            return text
        } finally { connection.disconnect() }
    }
    private fun integer(value: JSONObject, name: String, max: Long): Long {
        val raw = value.get(name)
        require(raw is Number && raw.toLong().toDouble() == raw.toDouble() && raw.toLong() in 0..max) { "Invalid server response" }
        return raw.toLong()
    }
    private fun trimSession(raw: JSONObject, cached: Boolean = false): JSONObject {
        val access = raw.getString("access_token")
        val refresh = raw.getString("refresh_token")
        require(access.length in 16..8192 && Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").matches(access))
        require(Regex("[A-Za-z0-9_-]{8,512}").matches(refresh))
        val now = System.currentTimeMillis() / 1000
        val expires = if (raw.has("expires_at")) integer(raw, "expires_at", 4_102_444_800) else now + integer(raw, "expires_in", 86_400)
        require(cached || expires in now - 60..now + 86_400) { "Invalid session expiration" }
        val id = InputRules.uuid(raw.getJSONObject("user").getString("id"))
        return JSONObject().put("access_token", access).put("refresh_token", refresh).put("expires_at", expires).put("user", JSONObject().put("id", id))
    }
    private fun trimProfile(raw: JSONObject): JSONObject {
        val id = InputRules.uuid(raw.getString("id"))
        require(id == session?.getJSONObject("user")?.getString("id")) { "Invalid profile owner" }
        val color = raw.getString("color").also { require(Regex("#[0-9a-fA-F]{6}").matches(it)) }
        return JSONObject().put("id", id).put("handle", InputRules.handle(raw.getString("handle")))
            .put("display_name", InputRules.safeText(raw.getString("display_name"), 30))
            .put("emoji", InputRules.safeText(raw.getString("emoji"), 8)).put("color", color)
    }
    private fun saveSession(raw: JSONObject) {
        currentOperation()
        val fresh = trimSession(raw)
        session?.getJSONObject("user")?.getString("id")?.let { require(fresh.getJSONObject("user").getString("id") == it) { "Invalid session owner" } }
        vault.put("session", fresh.toString()); session = fresh
        state.update { it.copy(signedIn = true) }
    }
    private fun token(): String {
        val old = session ?: error("Connect your account first")
        if (old.getLong("expires_at") > System.currentTimeMillis() / 1000 + 60) return old.getString("access_token")
        val fresh = JSONObject(request("auth/v1/token?grant_type=refresh_token", body = JSONObject().put("refresh_token", old.getString("refresh_token"))))
        saveSession(fresh)
        return fresh.getString("access_token")
    }
    private fun rpc(name: String, body: JSONObject = JSONObject()) = request("rest/v1/rpc/$name", body = body, token = token())
    private fun friendly(e: Exception) = when (e) {
        is IOException -> "Could not connect. Check your internet."
        is IllegalArgumentException, is IllegalStateException -> e.message?.take(200) ?: "Could not complete the request."
        else -> "Invalid server response. Please try again."
    }
    private suspend fun <T> resultOperation(allowDisconnected: Boolean = false, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!allowDisconnected && !sharing.get()) throw CancellationException("Account disconnected")
            operationEpoch = epoch.get()
            state.update { it.copy(busy = true, error = null) }
            try { block() }
            catch (e: Exception) { if (e is CancellationException) throw e; val message = friendly(e); state.update { it.copy(error = message) }; throw IllegalStateException(message) }
            finally { state.update { it.copy(busy = false) } }
        }
    }
    private suspend fun operation(allowDisconnected: Boolean = false, block: suspend () -> Unit) {
        try { resultOperation(allowDisconnected, block) } catch (e: Exception) { if (e is CancellationException) throw e }
    }
    private fun createAnonymousSession(captchaToken: String?) {
        if(session != null) return
        val payload = JSONObject().put("data", JSONObject())
        captchaToken?.let {
            require(it.length in 1..4096 && it.none { c -> Character.isISOControl(c) }) { "Invalid verification token" }
            payload.put("gotrue_meta_security", JSONObject().put("captcha_token", it))
        }
        saveSession(JSONObject(request("auth/v1/signup", body = payload)))
    }
    suspend fun joinLeague(body: JSONObject, captchaToken: String?): String {
        sharing.set(true)
        return resultOperation {
            createAnonymousSession(captchaToken)
            rpc("save_league_profile", body)
        }
    }
    suspend fun leagueRpc(name: String, body: JSONObject = JSONObject(), publicRead: Boolean = false): String =
        resultOperation(allowDisconnected = publicRead) {
            require(name in setOf("get_global_leaderboard", "get_my_league_profile", "submit_league_counts", "league_profile_action", "get_my_trophy_proofs"))
            require(!publicRead || name == "get_global_leaderboard")
            request("rest/v1/rpc/$name", body = body, token = if(session != null) token() else null)
        }
    suspend fun connect(captchaToken: String? = null) {
        sharing.set(true)
        operation {
            if (session == null) {
                val payload = JSONObject().put("data", JSONObject())
                captchaToken?.let {
                    require(it.length in 1..4096 && it.none { c -> Character.isISOControl(c) }) { "Invalid verification token" }
                    payload.put("gotrue_meta_security", JSONObject().put("captcha_token", it))
                }
                saveSession(JSONObject(request("auth/v1/signup", body = payload)))
            }
            loadProfile()
        }
        if (session == null) sharing.set(false)
    }
    suspend fun bootstrap() { if (BuildConfig.BATTLES_ENABLED && configured && session != null) operation { loadProfile(); if (state.value.profile != null) { register(); loadRows() } } }
    private fun loadProfile() {
        val id = InputRules.uuid(session?.getJSONObject("user")?.getString("id") ?: return)
        val result = JSONArray(request("rest/v1/profiles?id=eq.$id&select=$profileFields&limit=1", "GET", token = token()))
        require(result.length() <= 1)
        val profile = result.optJSONObject(0)?.let(::trimProfile)
        if (profile != null) vault.put("profile", profile.toString())
        state.update { it.copy(profile = profile) }
    }
    suspend fun profile(handle: String, displayName: String, emoji: String) = operation {
        val normalized = InputRules.handle(handle)
        val id = InputRules.uuid(session?.getJSONObject("user")?.getString("id") ?: error("Connect first"))
        val payload = JSONObject().put("id", id).put("handle", normalized).put("display_name", InputRules.displayName(displayName, normalized))
            .put("emoji", InputRules.avatar(emoji)).put("color", "#C6FF3D")
        val result = JSONArray(request("rest/v1/profiles?on_conflict=id&select=$profileFields", body = payload, token = token(), prefer = "resolution=merge-duplicates,return=representation"))
        require(result.length() == 1)
        val saved = trimProfile(result.getJSONObject(0)); vault.put("profile", saved.toString()); state.update { it.copy(profile = saved) }
        register(); sync(force = true); loadRows()
    }
    private fun register() {
        val stored = vault.get("device")
        if (stored == null || !Regex("[0-9a-f]{64}").matches(stored)) {
            val label = InputRules.safeText("Android ${Build.MANUFACTURER} ${Build.MODEL}".take(60), 60)
            val raw = JSONTokener(rpc("register_device", JSONObject().put("p_label", label))).nextValue()
            require(raw is String && Regex("[0-9a-f]{64}").matches(raw)) { "Device registration failed" }
            vault.put("device", raw)
        }
    }
    suspend fun refresh(newPeriod: String = period) = operation { period = InputRules.period(newPeriod); if (state.value.profile != null) { sync(); loadRows() } }
    private fun loadRows() {
        val fields = "user_id,handle,display_name,emoji,reels,is_me"
        val rows = JSONArray(request("rest/v1/rpc/get_leaderboard?select=$fields&limit=201", body = JSONObject().put("p_period", InputRules.period(period)).put("p_day", LocalDate.now().toString()), token = token()))
        require(rows.length() <= 201)
        val parsed = (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            require(it.get("is_me") is Boolean)
            val id = InputRules.uuid(it.getString("user_id"))
            require(!it.getBoolean("is_me") || id == session!!.getJSONObject("user").getString("id"))
            BattleRow(id, InputRules.safeText(it.getString("display_name"), 30), InputRules.handle(it.getString("handle")), InputRules.safeText(it.getString("emoji"), 8), integer(it, "reels", 3_100_000).toInt(), it.getBoolean("is_me"))
        } }
        require(parsed.map { it.id }.distinct().size == parsed.size)
        state.update { it.copy(rows = parsed) }
    }
    suspend fun invite(): String = resultOperation {
        val result = JSONArray(rpc("create_invite")); require(result.length() == 1)
        "https://doomscore.gridcc.tech/i/" + InputRules.invite(result.getJSONObject(0).getString("invite_code"))
    }
    suspend fun accept(code: String) = operation { rpc("accept_invite", JSONObject().put("p_code", InputRules.invite(code))); loadRows() }
    suspend fun removeFriend(id: String) = operation { rpc("remove_friend", JSONObject().put("p_friend", InputRules.uuid(id))); loadRows() }
    private fun disconnectLocal() { vault.clear(); session = null; lastSync = 0; state.value = BattleState() }
    suspend fun signOut() {
        sharing.set(false); epoch.incrementAndGet()
        withContext(Dispatchers.IO) { mutex.withLock {
            operationEpoch = epoch.get()
            try {
                if (session != null) {
                    vault.get("device")?.let { device -> runCatching {
                        val hash = MessageDigest.getInstance("SHA-256").digest(device.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                        rpc("revoke_device", JSONObject().put("p_token_hash", hash))
                    } }
                    runCatching { request("auth/v1/logout", body = JSONObject(), token = token()) }
                }
            } finally { disconnectLocal() }
        } }
    }
    suspend fun deleteAccount() {
        sharing.set(false); epoch.incrementAndGet()
        var deleted = false
        operation(allowDisconnected = true) { rpc("delete_account"); deleted = true; disconnectLocal() }
        if (!deleted && session != null) sharing.set(true)
    }
    fun syncAsync() { scope.launch { if (mutex.tryLock()) try { operationEpoch = epoch.get(); runCatching { sync() } } finally { mutex.unlock() } } }
    private fun sync(force: Boolean = false) {
        if (!BuildConfig.BATTLES_ENABLED || !configured || !sharing.get() || state.value.profile == null || session == null) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSync in 0..59_999) return
        register(); val device = vault.get("device") ?: return
        store.days(3).filter { it.total > 0 || it.watchMs > 0 }.forEachIndexed { index, day ->
            currentOperation()
            if (!sharing.get()) throw CancellationException("Sharing disconnected")
            if (index > 0) delayBetweenUploads()
            val apps = JSONArray()
            day.apps.forEach { (source, stats) -> apps.put(JSONObject().put("app", source.key).put("reels", stats.count.coerceIn(0, 20000)).put("watchSeconds", (stats.watchMs/1000).coerceIn(0, 86400)).put("adsSkipped", stats.ads.coerceIn(0, 20000))) }
            val offset = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds / 60
            val body = JSONObject().put("day", day.day.toString()).put("tzOffsetMinutes", offset).put("apps", apps).put("clientTime", System.currentTimeMillis()/1000.0).put("appVersion", BuildConfig.VERSION_NAME)
            request("functions/v1/ingest", body = body, device = device)
        }
        lastSync = System.currentTimeMillis()
    }
    private fun delayBetweenUploads() { Thread.sleep(1700); currentOperation() }
}
