package com.gridcc.doomscore.android.network

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.gridcc.doomscore.android.BuildConfig
import com.gridcc.doomscore.android.core.*
import com.gridcc.doomscore.android.data.DoomStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import java.util.UUID

data class GoogleAccountState(val signedIn:Boolean=false,val guest:Boolean=false,val busy:Boolean=false,val error:String?=null)

/** Auth REST keeps only a trimmed session in Keystore, never Google profile data/provider tokens. */
class FirebaseLeague(private val context:Context,private val store:DoomStore):LeagueGateway {
    override val configured=BuildConfig.FIREBASE_PROJECT.isNotBlank() && BuildConfig.FIREBASE_KEY.isNotBlank()
    val googleConfigured=configured && BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()
    private val vault=SecureVault(context)
    private val mutex=Mutex()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var session=vault.get("firebase_session")?.let {runCatching {trimSession(JSONObject(it),true)}.getOrNull()}
    override val accountId:String? get()=session?.getString("uid")
    val account=MutableStateFlow(GoogleAccountState(session!=null,session?.optBoolean("guest")==true))
    override val state=MutableStateFlow(LeagueState())
    private val project=BuildConfig.FIREBASE_PROJECT
    private val root="projects/$project/databases/(default)/documents"
    private val endpoint="https://firestore.googleapis.com/v1/$root"
    private var cursor:JSONObject?=null
    private var lastSync=0L
    private var lastBoard=0L
    private var profileVersion:String?=null
    private val names=mutableMapOf<String,String>()
    private var consumed=0
    private var blocked=emptySet<String>()
    private val certificate by lazy {runCatching {
        val pm=context.packageManager
        val bytes=if(android.os.Build.VERSION.SDK_INT>=28) pm.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            else pm.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
        bytes?.let {java.security.MessageDigest.getInstance("SHA-1").digest(it).joinToString(""){byte->"%02X".format(byte)}}
    }.getOrNull()}

    private fun uid(s:String)=s.also {require(Regex("[A-Za-z0-9_-]{1,128}").matches(it))}
    private fun encode(s:String)=URLEncoder.encode(s,"UTF-8")
    private fun tokenPattern(s:String)=s.also {require(s.length in 16..8192 && Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+").matches(it))}
    private fun trimSession(raw:JSONObject,cached:Boolean=false):JSONObject {
        val id=uid(raw.getString(if(cached) "uid" else if(raw.has("localId")) "localId" else "user_id"))
        val token=tokenPattern(raw.getString(if(cached || raw.has("idToken")) "idToken" else "id_token"))
        val refresh=raw.getString(if(cached || raw.has("refreshToken")) "refreshToken" else "refresh_token")
        require(refresh.length in 16..2048 && refresh.none {it.isISOControl()})
        val expires=if(cached) raw.getLong("expiresAt") else System.currentTimeMillis()/1000+raw.getString(if(raw.has("expiresIn")) "expiresIn" else "expires_in").toLong().also {require(it in 1..86400)}
        return JSONObject().put("uid",id).put("idToken",token).put("refreshToken",refresh).put("expiresAt",expires).put("guest",raw.optBoolean("guest",false))
    }
    private fun saveSession(raw:JSONObject,guest:Boolean,refresh:Boolean=false) {
        val fresh=trimSession(raw.put("guest",guest))
        if(refresh) require(fresh.getString("uid")==accountId) {"Invalid session owner"}
        vault.put("firebase_session",fresh.toString());session=fresh
        account.value=GoogleAccountState(true,guest)
    }
    private fun http(url:String,method:String="POST",body:Any?=null,bearer:String?=null,missingOkay:Boolean=false,form:Boolean=false):String {
        require(configured && url.startsWith("https://"))
        val parsed=URL(url);require(parsed.host in setOf("identitytoolkit.googleapis.com","securetoken.googleapis.com","firestore.googleapis.com"))
        val connection=parsed.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects=false;connection.useCaches=false;connection.requestMethod=method
            connection.connectTimeout=12000;connection.readTimeout=15000
            connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("Cache-Control","no-store")
            connection.setRequestProperty("X-Android-Package",context.packageName)
            certificate?.let {connection.setRequestProperty("X-Android-Cert",it)}
            bearer?.let {connection.setRequestProperty("Authorization","Bearer ${tokenPattern(it)}")}
            if(body!=null) {
                val bytes=body.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=65536)
                connection.doOutput=true;connection.setRequestProperty("Content-Type",if(form) "application/x-www-form-urlencoded" else "application/json")
                connection.setFixedLengthStreamingMode(bytes.size);connection.outputStream.use {it.write(bytes)}
            }
            val code=connection.responseCode
            if(code==404 && missingOkay) return ""
            require(code !in 300..399)
            val max=if(code in 200..299) NetworkRules.MAX_RESPONSE else NetworkRules.MAX_ERROR
            require(connection.contentLengthLong<=max)
            val text=(if(code in 200..299) connection.inputStream else connection.errorStream)?.use {NetworkRules.readBounded(it,max)}.orEmpty()
            if(code !in 200..299) {
                val error=runCatching {JSONObject(text).getJSONObject("error").optString("message")}.getOrDefault("")
                throw IllegalStateException(when {
                    error.startsWith("OPERATION_NOT_ALLOWED") -> "This sign-in option isn't enabled in Firebase yet."
                    error.startsWith("CREDENTIAL_TOO_OLD_LOGIN_AGAIN") -> "Sign in with Google again before deleting your account."
                    code==409 || code==412 -> "Your profile or score changed. Refresh and try again."
                    code==429 -> "Please wait before trying again."
                    code==403 -> "The leaderboard needs its Firebase access rules and indexes configured."
                    code==404 -> "Create the Firestore database in Firebase first."
                    code==401 || error.startsWith("INVALID_ID_TOKEN") || error.startsWith("TOKEN_EXPIRED") -> "Your session expired. Sign in again."
                    else -> "Couldn't complete the request. Check your connection and try again."
                })
            }
            if(text.isNotBlank()) require(connection.contentType.orEmpty().substringBefore(';').lowercase().endsWith("json"))
            return text
        } finally {connection.disconnect()}
    }
    private fun auth(path:String,body:JSONObject)=JSONObject(http("https://identitytoolkit.googleapis.com/v1/accounts:$path?key=${BuildConfig.FIREBASE_KEY}",body=body))
    private fun token():String {
        val old=session ?: error("Sign in or choose a guest identity first.")
        if(old.getLong("expiresAt")>System.currentTimeMillis()/1000+60) return old.getString("idToken")
        val fresh=JSONObject(http("https://securetoken.googleapis.com/v1/token?key=${BuildConfig.FIREBASE_KEY}",body="grant_type=refresh_token&refresh_token=${encode(old.getString("refreshToken"))}",form=true))
        saveSession(fresh,old.optBoolean("guest"),true);return session!!.getString("idToken")
    }
    private suspend fun operation(block:suspend ()->Unit)=withContext(Dispatchers.IO) {mutex.withLock {
        state.update {it.copy(busy=true,error=null)}
        try {block()} catch(e:Exception) {if(e is CancellationException) throw e;state.update {it.copy(error=if(e is IllegalStateException) e.message else "Couldn't connect. Please try again.")}}
        finally {state.update {it.copy(busy=false)}}
    }}
    suspend fun googleSignIn(activity:Context) {
        if(!googleConfigured) {account.update {it.copy(error="Enable Google sign-in in Firebase and download the updated app configuration.")};return}
        if(account.value.busy) return
        account.update {it.copy(busy=true,error=null)}
        try {
            val nonceBytes=ByteArray(32).also {SecureRandom().nextBytes(it)}
            val nonce=Base64.encodeToString(nonceBytes,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val option=GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_CLIENT_ID).setNonce(nonce).build()
            val result=CredentialManager.create(activity).getCredential(activity,GetCredentialRequest.Builder().addCredentialOption(option).build())
            val credential=result.credential
            require(credential is CustomCredential && credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val idToken=tokenPattern(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            // Checking the fresh nonce does not replace Firebase's signature/audience verification.
            val claims=JSONObject(String(Base64.decode(idToken.split('.')[1],Base64.URL_SAFE or Base64.NO_WRAP),Charsets.UTF_8))
            require(claims.optString("nonce")==nonce) {"Invalid sign-in response"}
            operation {
                val payload=JSONObject().put("postBody","id_token=${encode(idToken)}&providerId=google.com")
                    .put("requestUri","https://$project.firebaseapp.com").put("returnSecureToken",true).put("returnIdpCredential",false)
                // Link a guest identity instead of discarding its reserved username/scores.
                if(account.value.guest && session!=null) payload.put("idToken",token())
                val expected=accountId
                val fresh=auth("signInWithIdp",payload)
                require(fresh.optString("providerId")=="google.com")
                check(expected==null || fresh.optString("localId")==expected) {"Use the Google account linked to this profile, or sign out before changing accounts."}
                val old=accountId;saveSession(fresh,false)
                if(old!=accountId) {state.value=LeagueState();names.clear();cursor=null;lastSync=0;lastBoard=0}
                bind();loadProfile();loadBoard()
            }
            state.value.error?.let {message->account.update {it.copy(error=message)}}
        } catch(e:Exception) {
            if(e is CancellationException) throw e
            if(e !is GetCredentialCancellationException) account.update {it.copy(error="Google sign-in didn't complete. Check the provider setup and this app's signing fingerprint.")}
        } finally {account.update {it.copy(busy=false)}}
    }
    suspend fun signOut()=operation {
        check(!account.value.guest) {"Link Google before signing out so you can recover this identity."}
        vault.remove("firebase_session");session=null;account.value=GoogleAccountState();state.value=LeagueState();names.clear();cursor=null;lastSync=0;lastBoard=0
        withContext(Dispatchers.Main) {runCatching {CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())}}
        loadBoard()
    }
    fun localHistoryCleared() {
        val owner=accountId ?: return
        // A fresh contribution lets new viewing add to retained online totals after local deletion.
        vault.put("firebase_binding",JSONObject().put("uid",owner).put("month",month()).put("baseline",0).put("device",UUID.randomUUID().toString().replace("-","")).toString())
        lastSync=0
    }
    private fun month()=YearMonth.now(ZoneOffset.UTC).toString()
    private fun localTotal()=store.leagueDays().values.sumOf {it.toLong()}.coerceIn(0,3_100_000)
    private fun bind() {
        val owner=accountId ?: return
        val old=vault.get("firebase_binding")?.let {runCatching {JSONObject(it)}.getOrNull()}
        if(old?.optString("uid")==owner && old.optString("month")==month()) return
        val baseline=if(old!=null && old.optString("uid")!=owner) localTotal() else 0
        vault.put("firebase_binding",JSONObject().put("uid",owner).put("month",month()).put("baseline",baseline).put("device",UUID.randomUUID().toString().replace("-","")).toString())
    }
    private fun binding():JSONObject {bind();return JSONObject(vault.get("firebase_binding") ?: error("Choose an identity first."))}
    private fun value(v:Any):JSONObject=when(v) {
        is String -> JSONObject().put("stringValue",v)
        is Boolean -> JSONObject().put("booleanValue",v)
        is Number -> JSONObject().put("integerValue",v.toLong().toString())
        else -> error("Invalid field")
    }
    private fun fields(map:Map<String,Any>)=JSONObject().apply {map.forEach {(k,v)->put(k,value(v))}}
    private fun get(path:String,owner:Boolean=true):JSONObject? {
        val text=http("$endpoint/$path","GET",bearer=if(owner) token() else session?.let {token()},missingOkay=true)
        return if(text.isBlank()) null else JSONObject(text)
    }
    private fun write(path:String,data:Map<String,Any>,timestamp:String="updatedAt",version:String?=null,create:Boolean=false)=JSONObject()
        .put("update",JSONObject().put("name","$root/$path").put("fields",fields(data)))
        .put("updateTransforms",JSONArray().put(JSONObject().put("fieldPath",timestamp).put("setToServerValue","REQUEST_TIME"))).apply {
            if(version!=null) put("currentDocument",JSONObject().put("updateTime",version))
            else if(create) put("currentDocument",JSONObject().put("exists",false))
        }
    private fun remove(path:String)=JSONObject().put("delete","$root/$path")
    private fun commit(writes:JSONArray) {require(writes.length() in 1..100);http("${endpoint.substringBeforeLast("/documents")}/documents:commit",body=JSONObject().put("writes",writes),bearer=token())}
    private fun str(doc:JSONObject,name:String)=doc.getJSONObject("fields").getJSONObject(name).getString("stringValue")
    private fun num(doc:JSONObject,name:String,max:Long=3_100_000)=doc.getJSONObject("fields").getJSONObject(name).getString("integerValue").toLong().also {require(it in 0..max)}
    private fun bool(doc:JSONObject,name:String)=doc.getJSONObject("fields").getJSONObject(name).getBoolean("booleanValue")
    private fun filter(field:String,op:String,v:JSONObject)=JSONObject().put("fieldFilter",JSONObject().put("field",JSONObject().put("fieldPath",field)).put("op",op).put("value",v))
    private fun and(vararg parts:JSONObject)=if(parts.size==1) parts[0] else JSONObject().put("compositeFilter",JSONObject().put("op","AND").put("filters",JSONArray(parts.toList())))
    private fun ordered()=JSONArray().put(JSONObject().put("field",JSONObject().put("fieldPath","reels")).put("direction","DESCENDING"))
        .put(JSONObject().put("field",JSONObject().put("fieldPath","__name__")).put("direction","ASCENDING"))
    private fun query(parent:String,collection:String,where:JSONObject?=null,limit:Int=51,order:JSONArray?=null,after:JSONObject?=null,group:Boolean=false):List<JSONObject> {
        val q=JSONObject().put("from",JSONArray().put(JSONObject().put("collectionId",collection).put("allDescendants",group))).put("limit",limit)
        where?.let {q.put("where",it)}
        order?.let {q.put("orderBy",it)};after?.let {q.put("startAt",it)}
        val raw=JSONArray(http("$endpoint${if(parent.isBlank()) "" else "/$parent"}:runQuery",body=JSONObject().put("structuredQuery",q),bearer=session?.let {token()}))
        require(raw.length()<=limit+1)
        return (0 until raw.length()).mapNotNull {raw.getJSONObject(it).optJSONObject("document")}
    }
    private fun count(where:JSONObject):Long {
        val q=JSONObject().put("from",JSONArray().put(JSONObject().put("collectionId","entries"))).put("where",where)
        val agg=JSONObject().put("structuredQuery",q).put("aggregations",JSONArray().put(JSONObject().put("alias","total").put("count",JSONObject())))
        val raw=JSONArray(http("$endpoint/seasons/${month()}:runAggregationQuery",body=JSONObject().put("structuredAggregationQuery",agg),bearer=session?.let {token()}))
        require(raw.length()<=2)
        return (0 until raw.length()).mapNotNull {raw.getJSONObject(it).optJSONObject("result")?.optJSONObject("aggregateFields")?.optJSONObject("total")?.optString("integerValue")?.toLongOrNull()}.single().also {require(it in 0..1_000_000_000)}
    }
    private fun parse(doc:JSONObject):LeagueProfile=LeagueProfile(InputRules.handle(str(doc,"username")),LeagueRules.instagram(str(doc,"instagram")),InputRules.avatar(str(doc,"emoji")),bool(doc,"visible"),true)
    private fun loadProfile() {
        if(session==null) return
        val doc=get("profiles/${accountId}");profileVersion=doc?.optString("updateTime")
        state.update {it.copy(profile=doc?.let(::parse),trophyProofs=null)}
    }
    override suspend fun bootstrap()=operation {if(configured) {if(session!=null) {bind();loadProfile();sync(true)};loadBoard()}}
    override suspend fun save(username:String,instagram:String,emoji:String,visible:Boolean,captcha:String?)=operation {
        val p=LeagueProfile(InputRules.handle(username),LeagueRules.instagram(instagram),InputRules.avatar(emoji),visible,true)
        check(configured) {"Add your Firebase app configuration first."}
        if(session==null) saveSession(auth("signUp",JSONObject().put("returnSecureToken",true)),true)
        bind();loadProfile();val id=requireNotNull(accountId);val old=state.value.profile
        val registry=get("usernames/${p.username}",false)
        check(registry==null || str(registry,"uid")==id) {"That username is taken. Try another."}
        val data=mapOf<String,Any>("username" to p.username,"instagram" to p.instagram,"emoji" to p.emoji,"visible" to p.visible)
        val writes=JSONArray().put(write("profiles/$id",data,version=profileVersion,create=old==null))
        if(old?.username!=p.username) {
            writes.put(write("usernames/${p.username}",mapOf("uid" to id),create=true))
            old?.let {writes.put(remove("usernames/${it.username}"))}
        }
        // Every historic public link is updated as well, so an edited Instagram handle cannot linger.
        val owned=ownedEntries()
        owned.forEach {doc ->writes.put(write(doc.getString("name").substringAfter("$root/"),entryData(doc,p),version=doc.getString("updateTime")))}
        val current="seasons/${month()}/entries/$id"
        if(owned.none {it.getString("name")=="$root/$current"}) {
            val b=binding();val total=(localTotal()-b.getLong("baseline")).coerceAtLeast(0)
            val contribution=b.getString("device")+"_"+month()
            writes.put(write("contributions/$id/devices/$contribution",mapOf("month" to month(),"total" to total),create=true))
            writes.put(write(current,data+mapOf("uid" to id,"reels" to total,"contribution" to contribution),create=true))
        }
        commit(writes);loadProfile();lastSync=System.currentTimeMillis();lastBoard=0;loadBoard()
    }
    private fun entryData(doc:JSONObject,p:LeagueProfile)=mapOf<String,Any>("uid" to str(doc,"uid"),"username" to p.username,"instagram" to p.instagram,"emoji" to p.emoji,"visible" to p.visible,"reels" to num(doc,"reels"),"contribution" to str(doc,"contribution"))
    private fun ownedEntries(max:Int=60):List<JSONObject> {
        val owner=accountId ?: return emptyList();val out=mutableListOf<JSONObject>();var after:JSONObject?=null
        do {
            val docs=query("","entries",filter("uid","EQUAL",value(owner)),50,JSONArray().put(JSONObject().put("field",JSONObject().put("fieldPath","__name__")).put("direction","ASCENDING")),after,true)
            out+=docs;require(out.size<=max) {"Your account history needs support-assisted removal."}
            after=docs.lastOrNull()?.let {JSONObject().put("values",JSONArray().put(JSONObject().put("referenceValue",it.getString("name")))).put("before",false)}
        } while(docs.size==50)
        return out
    }
    private fun sync(foreground:Boolean=false) {
        val interval=if(foreground) 60_000L else 300_000L
        if(session==null || state.value.profile?.visible!=true || System.currentTimeMillis()-lastSync in 0 until interval) return
        val p=state.value.profile ?: return;val id=requireNotNull(accountId);val b=binding();val contribution=b.getString("device")+"_"+month()
        val path="seasons/${month()}/entries/$id";val remote=get(path)
        val cp="contributions/$id/devices/$contribution";val prior=get(cp)
        val total=(localTotal()-b.getLong("baseline")).coerceAtLeast(0)
        val old=prior?.let {num(it,"total")} ?: 0;val next=maxOf(total,old)
        if(remote!=null && next==old) {lastSync=System.currentTimeMillis();return}
        val combined=(remote?.let {num(it,"reels")} ?: 0)+next-old;require(combined<=3_100_000)
        val data=mapOf<String,Any>("uid" to id,"username" to p.username,"instagram" to p.instagram,"emoji" to p.emoji,"visible" to p.visible,"reels" to combined,"contribution" to contribution)
        commit(JSONArray().put(write(cp,mapOf("month" to month(),"total" to next),version=prior?.getString("updateTime"),create=prior==null))
            .put(write(path,data,version=remote?.getString("updateTime"),create=remote==null)))
        lastSync=System.currentTimeMillis()
    }
    private fun page(parent:String,after:JSONObject?=null,limit:Int=51)=query(parent,"entries",filter("visible","EQUAL",value(true)),limit,ordered(),after)
    private fun cursor(doc:JSONObject)=JSONObject().put("values",JSONArray().put(value(num(doc,"reels"))).put(JSONObject().put("referenceValue",doc.getString("name")))).put("before",false)
    private fun row(doc:JSONObject,rank:Int):LeagueRow {
        val id=uid(str(doc,"uid"));require(doc.getString("name").substringAfterLast('/')==id)
        val p=parse(doc);names[p.username]=id
        return LeagueRow(rank,p.username,p.instagram,p.emoji,num(doc,"reels"))
    }
    private fun loadBoard() {
        blocked=accountId?.let {id->query("blocks/$id","items",filter("active","EQUAL",value(true)),100).map {it.getString("name").substringAfterLast('/')}.toSet()} ?: emptySet()
        val public=filter("visible","EQUAL",value(true));val participants=count(public)
        val docs=page("seasons/${month()}");cursor=docs.take(50).lastOrNull()?.let(::cursor);consumed=docs.take(50).size
        val top=docs.take(50).mapIndexed {i,doc->row(doc,i+1)}.filter {names[it.username] !in blocked}
        val me=accountId?.let {get("seasons/${month()}/entries/$it")}
        val score=me?.takeIf {bool(it,"visible")}?.let {num(it,"reels")}
        val rank=score?.let {
            val ahead=JSONObject().put("compositeFilter",JSONObject().put("op","OR").put("filters",JSONArray()
                .put(filter("reels","GREATER_THAN",value(it)))
                .put(and(filter("reels","EQUAL",value(it)),filter("__name__","LESS_THAN",JSONObject().put("referenceValue","$root/seasons/${month()}/entries/$accountId"))))))
            count(and(public,ahead))+1
        }
        val current=YearMonth.parse(month());val previous=current.minusMonths(1)
        val podium=if(LocalDate.now(ZoneOffset.UTC).dayOfMonth<=7) page("seasons/$previous",limit=3).filter {num(it,"reels")>0}.mapIndexed {i,d->row(d,i+1)}.filter {names[it.username] !in blocked} else emptyList()
        state.update {it.copy(board=LeagueBoard(current,participants,top,rank?.takeIf {it<=200}?.toInt(),rank?.let {r->kotlin.math.ceil(r*100.0/maxOf(participants,1)).toInt().coerceIn(1,100)},score,podium,previous),more=docs.size>50)}
        lastBoard=System.currentTimeMillis()
    }
    override suspend fun refresh()=operation {if(configured) {sync(true);if(state.value.board==null || System.currentTimeMillis()-lastBoard>=60_000) loadBoard()}}
    override suspend fun loadMore()=operation {
        if(!state.value.more || cursor==null) return@operation
        val docs=page("seasons/${month()}",cursor);val old=state.value.board ?: return@operation
        val existing=old.top.map {it.username}.toSet();val added=docs.take(50).mapIndexed {i,d->row(d,consumed+i+1)}.filter {it.username !in existing && names[it.username] !in blocked}
        consumed+=docs.take(50).size
        cursor=docs.take(50).lastOrNull()?.let(::cursor)
        state.update {it.copy(board=old.copy(top=old.top+added),more=docs.size>50)}
    }
    private var uploadJob:Job?=null
    @Synchronized override fun syncAsync() {
        if(!configured || session==null || uploadJob?.isActive==true || System.currentTimeMillis()-lastSync in 0 until 300_000L) return
        uploadJob=scope.launch {operation {sync()}}
    }
    override suspend fun profileAction(username:String,action:String,reason:String)=operation {
        check(session!=null) {"Choose a public identity before reporting or blocking."}
        val owner=requireNotNull(accountId)
        if(action=="unblock_all") {
            do {
                val docs=query("blocks/$owner","items",limit=40)
                if(docs.isNotEmpty()) commit(JSONArray(docs.map {remove(it.getString("name").substringAfter("$root/"))}))
            } while(docs.size==40)
            blocked=emptySet();lastBoard=0;loadBoard();return@operation
        }
        require(action in setOf("block","report") && reason in setOf("spam","impersonation","offensive"))
        val target=names[InputRules.handle(username)] ?: error("Refresh the list before taking this action.")
        require(target!=owner)
        val limiter=get("limits/$owner")
        commit(JSONArray().put(write(if(action=="block") "blocks/$owner/items/$target" else "reports/$owner/items/$target",if(action=="block") mapOf("active" to true) else mapOf("reason" to reason)))
            .put(write("limits/$owner",mapOf("kind" to action,"target" to target),version=limiter?.getString("updateTime"),create=limiter==null)))
        if(action=="block") state.update {it.copy(board=it.board?.let {b->b.copy(top=b.top.filter {it.username!=username},podium=b.podium.filter {it.username!=username})})}
    }
    override suspend fun delete()=operation {
        val owner=accountId ?: return@operation
        val entries=ownedEntries(10000);val profile=get("profiles/$owner")
        // Delete scores first; rules prevent resetting a contribution while its score exists.
        entries.chunked(40).forEach {docs->commit(JSONArray(docs.map {remove(it.getString("name").substringAfter("$root/"))}))}
        for(collection in listOf("contributions" to "devices","blocks" to "items","reports" to "items")) {
            do {
                val docs=query("${collection.first}/$owner",collection.second,limit=10)
                if(docs.isNotEmpty()) commit(JSONArray(docs.map {remove(it.getString("name").substringAfter("$root/"))}))
            } while(docs.size==10)
        }
        if(profile!=null) commit(JSONArray().put(remove("usernames/${str(profile,"username")}")).put(remove("profiles/$owner")))
        if(get("limits/$owner")!=null) commit(JSONArray().put(remove("limits/$owner")))
        auth("delete",JSONObject().put("idToken",token()))
        vault.remove("firebase_session");session=null;account.value=GoogleAccountState();state.value=LeagueState();names.clear();cursor=null;lastSync=0;lastBoard=0;loadBoard()
    }
}
