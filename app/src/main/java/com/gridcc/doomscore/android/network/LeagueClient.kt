package com.gridcc.doomscore.android.network

import android.content.Context
import com.gridcc.doomscore.android.BuildConfig
import com.gridcc.doomscore.android.core.InputRules
import com.gridcc.doomscore.android.core.LeagueRules
import com.gridcc.doomscore.android.core.TrophyProofs
import com.gridcc.doomscore.android.data.DoomStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

data class LeagueProfile(val username: String, val instagram: String = "", val emoji: String = "🫠", val visible: Boolean = true, val reserved: Boolean = false)
data class LeagueRow(val rank: Int, val username: String, val instagram: String, val emoji: String, val reels: Long)
data class LeagueBoard(val month: YearMonth, val participants: Long, val top: List<LeagueRow>, val myRank: Int?, val myPercent: Int?, val myReels: Long?, val podium: List<LeagueRow>, val previousMonth: YearMonth)
data class LeagueState(val profile: LeagueProfile? = null, val board: LeagueBoard? = null, val busy: Boolean = false, val error: String? = null, val trophyProofs: TrophyProofs? = null)

class LeagueClient(context: Context, private val store: DoomStore, private val identity: BattleClient) {
    private val vault = SecureVault(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSync = 0L
    val configured = BuildConfig.LEAGUE_ONLINE_ENABLED && identity.configured
    val state = MutableStateFlow(LeagueState(profile = vault.get("league_profile")?.let { runCatching { profile(JSONObject(it), cached = true) }.getOrNull() },
        trophyProofs=vault.get("trophy_proofs")?.let {runCatching {proofs(JSONObject(it))}.getOrNull()}))

    private fun proofs(raw: JSONObject): TrophyProofs {
        val owner=InputRules.uuid(raw.getString("owner_id"))
        require(owner==identity.accountId) { "Invalid trophy owner" }
        require(raw.get("global_top10") is Boolean)
        val wins=integer(raw,"battle_wins",1_000_000).toInt()
        val streak=integer(raw,"best_win_streak",1_000_000).toInt()
        require(streak<=wins)
        return TrophyProofs(owner,wins,streak,raw.getBoolean("global_top10"))
    }
    private suspend fun loadTrophyProofs() {
        if(!identity.state.value.signedIn) return
        val fresh=proofs(JSONObject(identity.leagueRpc("get_my_trophy_proofs")))
        vault.put("trophy_proofs",JSONObject().put("owner_id",fresh.owner).put("battle_wins",fresh.battleWins)
            .put("best_win_streak",fresh.bestWinStreak).put("global_top10",fresh.globalTop10).toString())
        state.update {it.copy(trophyProofs=fresh)}
    }

    private fun profile(raw: JSONObject, cached: Boolean = false): LeagueProfile {
        require(raw.get("visible") is Boolean)
        return LeagueProfile(InputRules.handle(raw.getString("username")), LeagueRules.instagram(raw.getString("instagram_username")),
            InputRules.avatar(raw.getString("emoji")), raw.getBoolean("visible"), if(cached) raw.optBoolean("reserved", false) else true)
    }
    private fun persist(value: LeagueProfile) {
        vault.put("league_profile", JSONObject().put("username", value.username).put("instagram_username", value.instagram)
            .put("emoji", value.emoji).put("visible", value.visible).put("reserved", value.reserved).toString())
        state.update { it.copy(profile = value) }
    }
    private suspend fun operation(block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            state.update { it.copy(busy = true, error = null) }
            try { block() }
            catch(e: Exception) {
                if(e is CancellationException) throw e
                state.update { it.copy(error = e.message?.take(200) ?: "Could not load the league. Try again.") }
            } finally { state.update { it.copy(busy = false) } }
        }
    }
    suspend fun bootstrap() = operation {
        if(configured && identity.state.value.signedIn) {
            val raw = identity.leagueRpc("get_my_league_profile")
            if(raw != "null" && raw.isNotBlank()) persist(profile(JSONObject(raw)))
            loadTrophyProofs()
        }
    }
    suspend fun save(username: String, instagram: String, emoji: String, visible: Boolean, captcha: String? = null) = operation {
        val candidate = LeagueProfile(InputRules.handle(username), LeagueRules.instagram(instagram), InputRules.avatar(emoji), visible)
        if(!configured) { persist(candidate); return@operation }
        val raw = identity.joinLeague(JSONObject().put("p_username", candidate.username).put("p_instagram", candidate.instagram)
            .put("p_emoji", candidate.emoji).put("p_visible", visible), captcha)
        persist(profile(JSONObject(raw)))
        lastSync = 0; sync(); loadBoard()
    }
    suspend fun refresh() = operation { if(configured) { sync(); loadBoard() } }
    fun syncAsync() { if(configured && state.value.profile?.reserved == true && state.value.profile?.visible == true) scope.launch { operation { sync() } } }
    private suspend fun sync() {
        if(state.value.profile?.reserved != true || state.value.profile?.visible != true || !identity.state.value.signedIn) return
        val now = System.currentTimeMillis()
        if(now-lastSync in 0..59_999) return
        val rows = JSONArray()
        store.leagueDays().forEach { (day, total) -> rows.put(JSONObject().put("day", day.toString()).put("reels", total.coerceIn(0, 100000))) }
        if(rows.length()>0) {
            val result = JSONObject(identity.leagueRpc("submit_league_counts", JSONObject().put("p_rows", rows)))
            if(!result.optBoolean("ok")) error(when(result.optString("error")) {
                "rate_limited" -> "Your score is syncing. Check again in a minute."
                else -> "Your score could not sync. Try again."
            })
        }
        lastSync = now
    }
    private fun integer(raw: JSONObject, name: String, max: Long): Long {
        val number = raw.get(name)
        require(number is Number && number.toLong().toDouble() == number.toDouble() && number.toLong() in 0..max) { "Invalid league response" }
        return number.toLong()
    }
    private fun rows(raw: JSONArray, limit: Int): List<LeagueRow> {
        require(raw.length()<=limit)
        val parsed = (0 until raw.length()).map { index -> raw.getJSONObject(index).let {
            val rank = integer(it,"rank",limit.toLong()).toInt().also { n -> require(n>0) }
            LeagueRow(rank, InputRules.handle(it.getString("username")), LeagueRules.instagram(it.getString("instagram_username")),
                InputRules.avatar(it.getString("emoji")), integer(it,"reels",3_100_000))
        } }
        require(parsed.map { it.rank }.distinct().size==parsed.size && parsed.map { it.username }.distinct().size==parsed.size)
        require(parsed.zipWithNext().all { (a,b) -> a.rank<b.rank && a.reels>=b.reels })
        return parsed
    }
    private suspend fun loadBoard() {
        val raw = JSONObject(identity.leagueRpc("get_global_leaderboard", publicRead = true))
        val month = YearMonth.from(java.time.LocalDate.parse(raw.getString("month")))
        val previous = YearMonth.from(java.time.LocalDate.parse(raw.getString("previous_month")))
        require(previous == month.minusMonths(1))
        val me = raw.optJSONObject("me")
        val rank = me?.let { if(it.isNull("rank")) null else integer(it,"rank",200).toInt().also { r -> require(r>0) } }
        val percent = me?.let { integer(it,"top_percent",100).toInt().also { p -> require(p>0) } }
        val board = LeagueBoard(month, integer(raw,"participants",1_000_000_000), rows(raw.getJSONArray("top"),50), rank, percent,
            me?.let { integer(it,"reels",3_100_000) }, rows(raw.getJSONArray("podium"),3), previous)
        state.update { it.copy(board = board) }
        loadTrophyProofs()
    }
    suspend fun profileAction(username: String, action: String, reason: String = "spam") = operation {
        check(configured && identity.state.value.signedIn) { "Join the league before reporting or blocking a profile." }
        require(action in setOf("block", "report", "unblock_all") && reason in setOf("spam", "impersonation", "offensive"))
        val result=JSONObject(identity.leagueRpc("league_profile_action",JSONObject().put("p_username",if(action=="unblock_all") "" else InputRules.handle(username)).put("p_action",action).put("p_reason",reason)))
        check(result.optBoolean("ok")) { "Could not complete that action. Please try again later." }
        loadBoard()
    }
    suspend fun delete() {
        operation {
            if(state.value.profile?.reserved == true || identity.state.value.signedIn) {
                identity.deleteAccount()
                identity.state.value.error?.let { error(it) }
            } else vault.remove("league_profile")
            lastSync=0; state.value=LeagueState()
        }
    }
}
