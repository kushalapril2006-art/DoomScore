package com.gridcc.doomscore.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gridcc.doomscore.android.DoomApplication
import com.gridcc.doomscore.android.core.*
import com.gridcc.doomscore.android.network.BattleClient
import com.gridcc.doomscore.android.tracking.ReelAccessibilityService
import com.gridcc.doomscore.android.widget.CounterWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class Dashboard(val today: DayStats,val streak: Pair<Int,Int>,val best: Int,val monthly: Long)
private data class History(val range: Int,val days: List<DayStats>)

@OptIn(ExperimentalMaterial3Api::class,FlowPreview::class)
@Composable fun DoomApp(app: DoomApplication, pendingInvite: MutableStateFlow<String?>) = DoomTheme {
    val prefs = app.store.preferences
    val revision by remember(app.store) {app.store.changes.sample(300)}.collectAsStateWithLifecycle(initialValue=0)
    val settingsRevision by prefs.revisions.collectAsStateWithLifecycle()
    val tracking by ReelAccessibilityService.state.collectAsStateWithLifecycle()
    val invite by pendingInvite.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resume by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val screenState=rememberSaveableStateHolder()
    val focus=LocalFocusManager.current
    var settings by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    var wrapped by remember { mutableIntStateOf(0) }
    var privacy by remember { mutableStateOf(false) }
    var trophies by remember { mutableStateOf(false) }
    var shareError by remember {mutableStateOf(false)}
    val battles = com.gridcc.doomscore.android.BuildConfig.BATTLES_ENABLED
    val tabs = if(battles) listOf("today" to Mark.REELS, "league" to Mark.PODIUM, "stats" to Mark.CHART, "battle" to Mark.BATTLE) else listOf("today" to Mark.REELS, "league" to Mark.PODIUM, "stats" to Mark.CHART)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { resume++; scope.launch(Dispatchers.IO){CounterWidget.updateAll(context)}; app.battles.syncAsync(); app.league.syncAsync() } }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { app.battles.bootstrap(); app.league.bootstrap() }
    val lifecycleState by owner.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {if(lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) while (true) {delay(15_000);resume++}}
    LaunchedEffect(invite) { if (battles && invite != null) tab = 3 }
    val dashboard by storeSnapshot(app.store,revision,resume) {Dashboard(app.store.day(),app.store.streak(),app.store.personalBest(),app.store.monthTotal())}
    BackHandler(enabled=prefs.onboarding && tab!=0 && !settings && !setup && !privacy && !trophies && wrapped==0){focus.clearFocus();tab=0}
    val connected = remember(resume, tracking,settingsRevision) { ReelAccessibilityService.isEnabled(context) }
    if (!prefs.onboarding) {
        Onboarding(onStart = { setup = true }, onExplore = { prefs.onboarding = true }, onPrivacy={privacy=true})
    } else {
        Scaffold(containerColor = Palette.Bg,
            topBar = {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=20.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
                    Text("doomscore", color=Palette.Text, fontFamily=DoomFonts.Display,letterSpacing=(-.8).sp,fontSize=26.sp, fontWeight=FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    val armed = connected && prefs.enabled && prefs.disclosed
                    StatusPill(if (armed && tracking.connected) "● live" else if(armed) "○ connecting" else "○ paused", if (armed) Palette.Cyan else Palette.Dim)
                    Spacer(Modifier.width(6.dp))
                    IconButton(onClick={ settings = true },modifier=Modifier.semantics{contentDescription="Settings"}) { DoomIcon(Mark.SETTINGS) }
                }
            },
            bottomBar = {
                NavigationBar(containerColor=Palette.Surface, tonalElevation=0.dp) {
                    tabs.forEachIndexed { index, item ->
                        val scale by animateFloatAsState(if(tab==index) 1.14f else 1f,spring(dampingRatio=.8f,stiffness=500f),label="tab icon")
                        NavigationBarItem(selected=tab==index, onClick={focus.clearFocus();tab=index}, icon={DoomIcon(item.second,tint=if(tab==index) Palette.Lime else Palette.Dim,modifier=Modifier.graphicsLayer{scaleX=scale;scaleY=scale})}, label={Text(item.first)},
                            colors=NavigationBarItemDefaults.colors(selectedIconColor=Palette.Lime, selectedTextColor=Palette.Lime, indicatorColor=Palette.High))
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(targetState=tab,modifier=Modifier.fillMaxSize(),transitionSpec={
                    val direction=if(targetState>initialState) 1 else -1
                    (fadeIn(tween(170))+slideInHorizontally(tween(200)){it/14*direction}) togetherWith fadeOut(tween(110))
                },label="screen navigation") {screen ->
                    screenState.SaveableStateProvider(screen) {
                        when(screen) {
                            0 -> dashboard.data?.let {TodayScreen(app,it,connected,tracking.sessionCount,onSetup={setup=true},onWrapped={wrapped=7},onTrophies={trophies=true})}
                                ?: LoadingScores(dashboard.error){resume++}
                            1 -> LeagueScreen(app,revision+resume)
                            3 -> BattleScreen(app.battles,invite,onInviteConsumed={pendingInvite.value=null})
                            else -> StatsScreen(app,revision+resume,onWrapped={wrapped=it},onTrophies={trophies=true})
                        }
                    }
                }
            }
        }
    }
    if (setup) SetupDialog(onDismiss={setup=false}, onAgree={
        prefs.disclosed = true; prefs.onboarding = true; prefs.enabled = true; setup=false
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    })
    if (settings) ModalBottomSheet(onDismissRequest={settings=false}, containerColor=Palette.Surface,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) { SettingsScreen(app, connected, onSetup={settings=false;setup=true}, onClose={settings=false},onPrivacy={privacy=true}) }
    if(privacy) PrivacyDialog(onDismiss={privacy=false})
    if(trophies) ModalBottomSheet(onDismissRequest={trophies=false},containerColor=Palette.Bg,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        TrophyCabinet(app,onClose={trophies=false})
    }
    if(shareError) AlertDialog(onDismissRequest={shareError=false},title={Text("Could not share recap")},text={Text("Check your available storage and try again.")},confirmButton={TextButton(onClick={shareError=false}){Text("OK")}})
    if (wrapped > 0) ModalBottomSheet(onDismissRequest={wrapped=0}, containerColor=Palette.Bg,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        WrappedScreen(app,wrapped,onClose={wrapped=0},onShare={scope.launch {
            val days=withContext(Dispatchers.IO){app.store.days(wrapped)}
            if(!RecapShare.share(context,days)) shareError=true
        }})
    }
}

@Composable private fun StatusPill(label: String, color: Color) {
    Text(label, color=color, fontSize=12.sp, fontWeight=FontWeight.Bold,
        modifier=Modifier.animateContentSize().clip(RoundedCornerShape(20.dp)).background(color.copy(alpha=.10f)).padding(horizontal=10.dp, vertical=6.dp))
}
@Composable private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=24.dp), verticalArrangement=Arrangement.spacedBy(16.dp), content=content)
}
@Composable private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.Surface).border(1.dp, Color.White.copy(alpha=.055f), RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp), content=content)
}
@Composable private fun Title(text: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
        Text(text, fontFamily=DoomFonts.Display,fontSize=17.sp, fontWeight=FontWeight.SemiBold, color=Palette.Text,modifier=Modifier.weight(1f))
        detail?.let { Text(it, fontSize=11.sp,lineHeight=16.sp, color=Palette.Dim,textAlign=TextAlign.End,modifier=Modifier.padding(start=12.dp).widthIn(max=130.dp)) }
    }
}
@Composable private fun Metric(value: String, label: String, glyph: Mark, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Palette.Surface).padding(16.dp).semantics(mergeDescendants=true){contentDescription="$value $label"}, verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha=.08f)),contentAlignment=Alignment.Center) {DoomIcon(glyph,tint,Modifier.size(19.dp))}
        AnimatedContent(value,transitionSpec={fadeIn(tween(140)) togetherWith fadeOut(tween(90))},label="metric value") {shown ->
            Text(shown,fontFamily=DoomFonts.Display,fontSize=28.sp,fontWeight=FontWeight.Bold,color=Palette.Text,style=MaterialTheme.typography.displaySmall.copy(fontFeatureSettings="tnum"))
        }
        Text(label, fontSize=12.sp, color=Palette.Dim, minLines=2)
    }
}
fun duration(ms: Long): String {
    val minutes = ms/60_000
    return when { minutes >= 60 -> "${minutes/60}h ${minutes%60}m"; minutes > 0 -> "${minutes}m"; else -> "${ms/1000}s" }
}

@Composable private fun TodayScreen(app: DoomApplication, dashboard: Dashboard, connected: Boolean, session: Int, onSetup: () -> Unit, onWrapped: () -> Unit,onTrophies:()->Unit) {
    val prefs=app.store.preferences
    val today=dashboard.today
    val tier=remember(today.total){ScrollTier.of(today.total)}
    val streak=dashboard.streak
    val best=dashboard.best
    val animateRank=animateFloatAsState(tier.progress(today.total),tween(300),label="rank progress")
    Page {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Palette.Surface).border(1.dp,Palette.High,RoundedCornerShape(28.dp)).padding(20.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("TODAY'S SCORE",style=MaterialTheme.typography.labelSmall,color=Palette.Dim,letterSpacing=1.5.sp)
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM")),style=MaterialTheme.typography.labelSmall,color=Palette.Dim)
            }
            Text(tier.quip, color=Palette.Dim, fontSize=13.sp, textAlign=TextAlign.Center,
                modifier=Modifier.clip(RoundedCornerShape(18.dp)).background(Palette.High).padding(horizontal=16.dp, vertical=10.dp))
            Goob(tier,modifier=Modifier.size(112.dp))
            ScoreText(today.total,fontFamily=DoomFonts.Display,fontSize=72.sp,color=Palette.Text,lineHeight=78.sp,modifier=Modifier.semantics{contentDescription="${today.total} reels today"})
            AnimatedContent(tier.title,transitionSpec={fadeIn(tween(180)) togetherWith fadeOut(tween(100))},label="scroll rank") {title ->
                Text("reels today · $title",fontSize=16.sp,color=Palette.Dim,fontWeight=FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress={animateRank.value}, modifier=Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color=Palette.rank(tier.level), trackColor=Palette.High)
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text("session: $session", fontSize=12.sp, color=Palette.Faint)
                Text(tier.next?.let {"next rank at $it"} ?: "max rank · score keeps climbing", fontSize=12.sp, color=Palette.Dim)
            }
            Text(tier.nextTitle?.let {"next up: $it"} ?: "the leaderboard is the next boss.",fontSize=12.sp,color=Palette.Cyan)
        }
        if (!connected || !prefs.disclosed) Panel {
            Title("count while you scroll", "ONE-TIME SETUP")
            Text("One-time setup. Then just open Instagram and scroll — your counter starts automatically.", color=Palette.Dim, fontSize=14.sp)
            Button(onClick=onSetup, modifier=Modifier.fillMaxWidth()) { Text("Enable reel counter", fontWeight=FontWeight.Bold) }
        } else if (!prefs.enabled) Panel {
            Title("counter is paused")
            Button(onClick={prefs.enabled=true}, modifier=Modifier.fillMaxWidth()) { Text("Resume counting") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            prefs.tracked.sortedBy { it.ordinal }.forEach { source -> StatusPill("${source.label}  ${today.apps[source]?.count ?: 0}", when(source) {SourceApp.INSTAGRAM->Palette.Pink;SourceApp.YOUTUBE->Palette.Orange;SourceApp.TIKTOK->Palette.Cyan;else->Palette.Lime}) }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Metric("${streak.first}d", "scroll streak · best ${streak.second}d", Mark.STREAK, Palette.Cyan, Modifier.weight(1f))
            Metric("$best", "personal best · reels in a day", Mark.TROPHY, Palette.Lime, Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Metric("${today.ads}", "recognized ads skipped", Mark.SHIELD, Palette.Lime, Modifier.weight(1f))
            Metric("${today.repeats}", "recent rewatches skipped", Mark.REPEAT, Palette.Pink, Modifier.weight(1f))
        }
        Panel {
            val peak = today.hourly.maxOrNull() ?: 0
            Title("today by hour", if(peak>0) "peak ${today.hourly.indexOf(peak)}:00" else "waiting for your first score")
            BarChart(today.hourly)
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { listOf("00", "06", "12", "18", "23").forEach { Text(it, fontSize=10.sp, color=Palette.Faint) } }
        }
        Panel {
            val monthly=dashboard.monthly
            Title("this month's score", LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM")))
            Text("$monthly", fontFamily=DoomFonts.Display,fontSize=38.sp, fontWeight=FontWeight.Bold, color=Palette.Lime)
            Text("reels watched this calendar month · every day included", color=Palette.Dim, fontSize=13.sp)
            Text("more reels. higher on the board.",color=Palette.Cyan,fontWeight=FontWeight.Bold,fontSize=13.sp)
        }
        Panel(Modifier.clickable(onClick=onTrophies)) {
            Title("Brainrot Trophy Cabinet", "↗")
            Text("Eight badges. One very cooked thumb.",color=Palette.Dim,fontSize=14.sp)
            Text("open your trophies →",color=Palette.Lime,fontWeight=FontWeight.Bold,fontSize=13.sp)
        }
        Panel(Modifier.clickable(onClick=onWrapped)) {
            Title("your flex card", "↗")
            Text("A week of reels. Receipts for the group chat.", color=Palette.Dim, fontSize=14.sp)
            Text("open Wrapped →", color=Palette.Cyan, fontWeight=FontWeight.Bold, fontSize=13.sp)
        }
    }
}

@Composable private fun BarChart(values: List<Int>) {
    val max=(values.maxOrNull() ?: 0).coerceAtLeast(1)
    val fractions=values.map {animateFloatAsState(it.toFloat()/max,tween(320,easing=FastOutSlowInEasing),label="chart bar")}
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val slot = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { i, value ->
            val height = if(value == 0) 3.dp.toPx() else (size.height-6.dp.toPx())*fractions[i].value
            drawRoundRect(if(value>0) Palette.Cyan else Palette.High, topLeft=Offset(slot*i+slot*.15f,size.height-height), size=Size(slot*.7f,height), cornerRadius=androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
    }
}

@Composable private fun Onboarding(onStart: () -> Unit,onExplore: () -> Unit,onPrivacy: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding().padding(horizontal=24.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top=16.dp,bottom=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("doomscore",color=Palette.Lime,fontFamily=DoomFonts.Display,fontSize=32.sp,fontWeight=FontWeight.Bold)
            StatusPill("COMPETITIVE DOOMSCROLLING",Palette.Cyan)
            Goob(ScrollTier.of(500),modifier=Modifier.size(160.dp))
            Text("make your\nthumb famous.",fontFamily=DoomFonts.Display,fontSize=34.sp,fontWeight=FontWeight.Bold,color=Palette.Text,textAlign=TextAlign.Center,lineHeight=39.sp)
            Text("Reels are your score. Stack your count, unlock brainrot badges and bring receipts to the leaderboard. Recognized ads and recent rewatches don't pad the score.",color=Palette.Dim,fontSize=15.sp,lineHeight=23.sp,textAlign=TextAlign.Center)
            Panel {
                Text("◎   Automatic after one-time setup",color=Palette.Text,fontSize=14.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {DoomIcon(Mark.SHIELD,Palette.Cyan);Text("No screenshots or screen recording",color=Palette.Text,fontSize=14.sp)}
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {DoomIcon(Mark.TROPHY,Palette.Lime);Text("Scroll ranks, badges and personal bests",color=Palette.Text,fontSize=14.sp)}
            }
        }
        Button(onClick=onStart,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Let's count some reels",fontWeight=FontWeight.Bold,fontSize=16.sp)}
        Row(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
            TextButton(onClick=onExplore,modifier=Modifier.weight(1f)){Text("Explore first",color=Palette.Dim)}
            TextButton(onClick=onPrivacy,modifier=Modifier.weight(1f)){Text("Privacy & data",color=Palette.Cyan)}
        }
    }
}

@Composable private fun SetupDialog(onDismiss: () -> Unit, onAgree: () -> Unit) {
    AlertDialog(onDismissRequest=onDismiss, containerColor=Palette.Surface, title={Text("Enable automatic counting", fontWeight=FontWeight.Bold)},
        text={ Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Doomscore uses Android Accessibility to read visible interface text, descriptions and control IDs in your selected reel apps. This identifies reel changes, ads and repeat views.", color=Palette.Text)
            Text("No screenshots or recordings are taken. Captions and video content are not saved or uploaded. Reel identifiers are stored as hashes on your phone: recent hashes support five-minute rewatch recognition; bounded unique hashes support trophy milestones until earned or deleted. Other apps are ignored.", color=Palette.Dim)
            Text(if(com.gridcc.doomscore.android.BuildConfig.FIREBASE_PROJECT.isNotBlank() || com.gridcc.doomscore.android.BuildConfig.LEAGUE_ONLINE_ENABLED) "Joining the global league is optional. It publishes your chosen username, optional Instagram handle and monthly reel total; UTC daily counts sync only after you join. Screen labels and reel identifiers never upload." else if(com.gridcc.doomscore.android.BuildConfig.BATTLES_ENABLED) "Friend battles are optional. Daily reel counts, viewing durations and recognized-ad totals sync only after you connect an account, and are visible to friends you invite or accept." else "Your counts and recent reel identifiers stay on this phone. This version does not upload your counting data.", color=Palette.Dim)
            Text("Next: choose Doomscore reel counter, then turn on the service. You can turn it off at any time in Settings.", color=Palette.Cyan)
        } }, confirmButton={TextButton(onClick=onAgree) {Text("Agree & open settings")}}, dismissButton={TextButton(onClick=onDismiss) {Text("Not now")}})
}

@Composable private fun SettingsScreen(app: DoomApplication, connected: Boolean, onSetup: () -> Unit, onClose: () -> Unit,onPrivacy:()->Unit) {
    val prefs = app.store.preferences
    val context = LocalContext.current
    var clear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var notificationDenied by rememberSaveable {mutableStateOf(false)}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        prefs.liveNotification=granted;notificationDenied=!granted
    }
    val scope = rememberCoroutineScope()
    val battle by app.battles.state.collectAsState()
    val league by app.league.state.collectAsState()
    val googleAccount by app.firebase.account.collectAsState()
    val preferenceRevision by prefs.revisions.collectAsState()
    val counterState by ReelAccessibilityService.state.collectAsStateWithLifecycle()
    val lastFeedStatus by ReelAccessibilityService.lastFeedStatus.collectAsStateWithLifecycle()
    val lastFeedDetails by ReelAccessibilityService.lastFeedDetails.collectAsStateWithLifecycle()
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    var copiedCheck by remember {mutableStateOf(false)}
    var phoneRefresh by remember {mutableIntStateOf(0)}
    LaunchedEffect(Unit) {while(true) {delay(2000);phoneRefresh++}}
    val nativeStatus=remember(phoneRefresh,preferenceRevision) {com.gridcc.doomscore.android.tracking.LiveCounterNotification.setupStatus(context)}
    val automatic = remember(preferenceRevision,connected) {prefs.enabled && connected && prefs.disclosed}
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp).navigationBarsPadding(), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Title("counter setup", "doomscore")
        battle.error?.let {Text(it,color=Palette.Pink)}
        league.error?.let {Text(it,color=Palette.Pink)}
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Automatic counting", fontWeight=FontWeight.Bold); Text(if(counterState.connected) "Connected to Android Accessibility" else if(connected) "Permission is on · waiting for Android to connect" else "Finish one-time setup", fontSize=12.sp, color=Palette.Dim) }
            Switch(checked=automatic, onCheckedChange={ if(it && (!connected || !prefs.disclosed)) onSetup() else prefs.enabled=it })
        }
        Panel {
            Title("phone setup", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
            Text(if(!prefs.disclosed) "Finish counting setup" else if(!connected) "Accessibility is off" else if(!counterState.connected) "Android hasn't connected the counter. Open Accessibility and turn DoomScore off, then on." else if(!prefs.enabled) "Counter paused" else counterState.status,color=Palette.Cyan,fontSize=13.sp)
            Text("Last feed check: $lastFeedStatus",color=Palette.Dim,fontSize=12.sp)
            TextButton(onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString("DoomScore counter check\n$lastFeedStatus\n$lastFeedDetails"));copiedCheck=true}) {Text(if(copiedCheck) "Counter check copied" else "Copy counter check")}
            Text("The check contains only app/Android versions, counts and detection status. No captions, handles or screen contents.",color=Palette.Faint,fontSize=11.sp)
            Text(if(!prefs.bubble) "Floating Goob is off · turn it on below" else if(counterState.floatingVisible) "Floating Goob is showing" else "Floating Goob is ready · appears on a readable reel feed",color=Palette.Dim,fontSize=12.sp)
            if(counterState.presentation.isNotBlank()) Text(counterState.presentation,color=Palette.Pink,fontSize=12.sp)
            Text(if(prefs.liveNotification) nativeStatus else "Native island counter is off · turn it on below",color=Palette.Dim,fontSize=12.sp)
            Text("If counting stops in the background, check DoomScore's battery/background settings. On POCO, Xiaomi and Redmi, also check HyperOS background autostart. These options vary by phone.",color=Palette.Faint,fontSize=12.sp,lineHeight=18.sp)
            OutlinedButton(onClick={runCatching {context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.fromParts("package",context.packageName,null)))}},modifier=Modifier.fillMaxWidth()) {Text("Open DoomScore phone settings")}
        }
        Title("UPI compatibility")
        Text("Some payment apps, including BHIM, block an enabled accessibility counter. Pausing counting leaves accessibility connected. Disconnect it before payments, then re-enable DoomScore in Android Accessibility when you want to count again. Your history and profile stay saved.",color=Palette.Dim,fontSize=12.sp,lineHeight=18.sp)
        OutlinedButton(onClick={
            prefs.bubble=false
            if(!ReelAccessibilityService.disconnectForPayments()) runCatching {context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}
        },enabled=connected,modifier=Modifier.fillMaxWidth()){Text("Disconnect counter for payments")}
        HorizontalDivider(color=Palette.High)
        Title("count these apps")
        SourceApp.entries.forEach { source ->
            Row(verticalAlignment=Alignment.CenterVertically) {
                DoomIcon(source.mark(),Palette.Cyan,Modifier.padding(end=10.dp))
                Column(Modifier.weight(1f)) { Text(source.label); if(source==SourceApp.TIKTOK || source==SourceApp.SNAPCHAT) Text("Beta · depends on exposed labels", fontSize=11.sp, color=Palette.Dim) }
                Switch(checked=source in prefs.tracked, onCheckedChange={prefs.tracked=if(it) prefs.tracked+source else prefs.tracked-source})
            }
        }
        Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("On-screen Goob counter"); Text("Mascot + today's reels. Drag to move.", fontSize=12.sp, color=Palette.Dim) }; Switch(checked=prefs.bubble,onCheckedChange={prefs.bubble=it}) }
        Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Floating Goob pill"); Text("A compact pill below the camera area", fontSize=12.sp, color=Palette.Dim) }; Switch(checked=prefs.island && prefs.bubble,onCheckedChange={prefs.island=it;if(it) prefs.bubble=true}) }
        Text("Floating counters are opt-in because overlays can conflict with payment apps. The notification counter uses Android's notification system; accessibility-based counting can still be blocked by a payment app.",color=Palette.Faint,fontSize=12.sp,lineHeight=18.sp)
        Text("Goob changes colour at 1, 100, 500, 1,000, 2,500 and 5,000 reels today, matching your scroll rank. The island appears only in reel feeds and hides when you pause or leave.",color=Palette.Dim,fontSize=12.sp,lineHeight=18.sp)
        Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Native island / Live Update"); Text("Goob notification · phone chooses island placement", fontSize=12.sp, color=Palette.Dim) }; Switch(checked=prefs.liveNotification,onCheckedChange={
            if(!it) {prefs.liveNotification=false;com.gridcc.doomscore.android.tracking.LiveCounterNotification(context).clear(force=true)}
            else if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else prefs.liveNotification=true
        }) }
        if(notificationDenied) Text("Notifications weren't enabled. Counting and the optional floating pill can still work; the native island needs notifications. Enable them in Android settings.",color=Palette.Pink,fontSize=12.sp)
        if(prefs.liveNotification || notificationDenied) OutlinedButton(onClick={runCatching {context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}},modifier=Modifier.fillMaxWidth()){Text("Android notification settings")}
        if(Build.VERSION.SDK_INT>=36) OutlinedButton(onClick={
            val preferred=Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).setData(android.net.Uri.fromParts("package",context.packageName,null))
            if(runCatching {context.startActivity(preferred)}.isFailure) runCatching {context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}
        },modifier=Modifier.fillMaxWidth()) {Text("Phone Live Update / island settings")}
        Text("Android 16: requests a native Live Update while you scroll. Enable Live Alerts or Live Updates for Doomscore in your phone settings if available. Your phone controls placement, mascot colours and support. POCO/Xiaomi HyperIsland supports selected apps and firmware versions; Android 16 alone does not guarantee support. Use Floating Goob pill below the camera when the native island does not show it. Turn off the on-screen counter to avoid two pills. Older phones receive a regular notification; notification island apps can also use it.",color=Palette.Faint,fontSize=12.sp,lineHeight=18.sp)
        Text("A reel counts after 0.75 seconds of stable visibility. Recent identifiers are remembered for 5 minutes. Ads are identified from visible labels. App updates and language changes can affect detection.", color=Palette.Dim,fontSize=12.sp,lineHeight=18.sp)
        Text("If Android blocks the service after installing an APK: open App info → ⋮ → Allow restricted settings, then enable Accessibility. The available steps vary by Android version.", color=Palette.Faint,fontSize=12.sp,lineHeight=18.sp)
        OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))},modifier=Modifier.fillMaxWidth()) {Text("Android accessibility settings")}
        OutlinedButton(onClick={clear=true},modifier=Modifier.fillMaxWidth()) {Text("Delete local counting history",color=Palette.Pink)}
        if(battle.signedIn) {
            if(league.profile?.reserved != true) OutlinedButton(onClick={scope.launch {app.battles.signOut()}},enabled=!battle.busy,modifier=Modifier.fillMaxWidth()) {Text("Disconnect battles")}
            TextButton(onClick={confirmDelete=true},enabled=!battle.busy,modifier=Modifier.fillMaxWidth()) {Text("Delete online identity",color=Palette.Pink)}
        }
        if(app.league===app.firebase && googleAccount.signedIn) {
            if(!googleAccount.guest) TextButton(onClick={scope.launch{app.firebase.signOut()}},enabled=!league.busy,modifier=Modifier.fillMaxWidth()){Text("Sign out of DoomScore")}
            TextButton(onClick={confirmDelete=true},enabled=!league.busy,modifier=Modifier.fillMaxWidth()){Text("Delete online account",color=Palette.Pink)}
        }
        Text("Local history stays on this phone. Joining the global league publishes your chosen username, optional Instagram username and monthly reel total. Rewatch identifiers, screen labels and captions never sync.",color=Palette.Faint,fontSize=12.sp,lineHeight=18.sp)
        OutlinedButton(onClick=onPrivacy,modifier=Modifier.fillMaxWidth()) {Text("Privacy & data")}
        Button(onClick=onClose,modifier=Modifier.fillMaxWidth()) {Text("Done")}
    }
    if(clear) AlertDialog(onDismissRequest={clear=false},title={Text("Delete local history?")},text={Text("This permanently removes this phone's counts, reel hashes and local trophy progress. Previously synced totals and verified online trophies remain with your online identity.")},confirmButton={TextButton(onClick={app.store.clear();if(app.league===app.firebase) app.firebase.localHistoryCleared();CounterWidget.updateAll(context);clear=false}) {Text("Delete")}},dismissButton={TextButton(onClick={clear=false}) {Text("Cancel")}})
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete online identity?")},text={Text("Your online profiles, friendships, league scores and podium entries will be permanently deleted. Local counts remain on this phone.")},confirmButton={TextButton(onClick={scope.launch {app.league.delete()};confirmDelete=false}) {Text("Delete account")}},dismissButton={TextButton(onClick={confirmDelete=false}) {Text("Cancel")}})
}

@Composable private fun StatsScreen(app: DoomApplication, revision: Int, onWrapped: (Int) -> Unit,onTrophies:()->Unit) {
    var count by rememberSaveable {mutableIntStateOf(7)}
    var retry by remember {mutableIntStateOf(0)}
    val history by storeSnapshot(app.store,count,revision,retry) {History(count,if(count==-1) app.store.monthDays() else app.store.days(count))}
    val data=history.data
    // Don't measure a short placeholder page: it would clamp a restored scroll offset to zero.
    if(data==null) {LoadingScores(history.error){retry++};return}
    val days=data.days
    val period=data.range
    Page {
        Text("the receipts",fontFamily=DoomFonts.Display,fontSize=30.sp,fontWeight=FontWeight.Bold,color=Palette.Text)
        OutlinedButton(onClick=onTrophies,modifier=Modifier.fillMaxWidth()) {Text("Brainrot Trophy Cabinet",color=Palette.Lime)}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(1 to "today",7 to "week",-1 to "this month",365 to "year").forEach { (n,label)->FilterChip(selected=count==n,onClick={count=n},label={Text(label)}) }
        }
        if(period!=count) LinearProgressIndicator(Modifier.fillMaxWidth(),color=Palette.Cyan)
        val total=days.sumOf {it.total}
        val watch=days.sumOf {it.watchMs}
        val apps=SourceApp.entries.associateWith {source->days.sumOf {it.apps[source]?.count ?: 0}}
        Panel {
            Title("$total reels", "${days.first().day.format(DateTimeFormatter.ofPattern("d MMM"))} – today")
            BarChart(if(period==1) days[0].hourly else if(period==365) days.chunked(7).map {week->week.sumOf{it.total}} else days.map{it.total})
            Text(if(period==1) "each bar = one hour" else if(period==365) "each bar = one week" else "each bar = one day",color=Palette.Faint,fontSize=11.sp)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            val elapsed = days.count {it.day >= app.store.preferences.installed}.coerceAtLeast(1)
            Metric("%.1f".format(total.toDouble()/elapsed),"average reels per day",Mark.REELS,Palette.Cyan,Modifier.weight(1f))
            Metric(duration(watch),"time in reel feeds",Mark.CLOCK,Palette.Text,Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Metric("${days.maxOf{it.total}}","highest day",Mark.STREAK,Palette.Orange,Modifier.weight(1f))
            Metric("${days.sumOf{it.ads}}","recognized ads skipped",Mark.SHIELD,Palette.Lime,Modifier.weight(1f))
        }
        Panel {
            Title("where the scrolling happened")
            apps.forEach { (source,n)->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("${source.label}",color=Palette.Dim);Text("$n",fontWeight=FontWeight.Bold,color=Palette.Text)}
                LinearProgressIndicator(progress={if(total==0) 0f else n.toFloat()/total},modifier=Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),color=Palette.Cyan,trackColor=Palette.High)
            }
        }
        Panel {
            val hours = (0..23).map {hour->days.sumOf{it.hourly[hour]}}
            val peak = hours.maxOrNull() ?: 0
            Title("your doom hour",if(peak>0) "${hours.indexOf(peak)}:00" else "not enough data")
            BarChart(hours)
        }
        Button(onClick={onWrapped(if(period==1) 7 else days.size)},modifier=Modifier.fillMaxWidth().height(50.dp)) {Text("Open your Wrapped ↗",fontWeight=FontWeight.Bold)}
    }
}

@Composable private fun WrappedScreen(app: DoomApplication,count: Int,onClose: () -> Unit,onShare: () -> Unit) {
    var retry by remember {mutableIntStateOf(0)}
    val history by storeSnapshot(app.store,count,retry){app.store.days(count)}
    val days=history.data
    if(days==null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            TextButton(onClick=onClose,modifier=Modifier.fillMaxWidth()){Text("Close",color=Palette.Cyan)}
            LoadingScores(history.error){retry++}
        };return
    }
    val total = days.sumOf {it.total}
    val hours = (0..23).map {hour->days.sumOf {it.hourly[hour]}}
    val peak = hours.indexOf(hours.maxOrNull() ?: 0)
    val title = when {total==0->"your debut is loading";peak>=22 || peak<4->"the after-hours challenger";total>count*100->"certified scroll goblin";else->"the algorithm's main character"}
    Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).navigationBarsPadding().padding(horizontal=24.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("your flex card",color=Palette.Text,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            TextButton(onClick=onClose){Text("Close",color=Palette.Cyan)}
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        StatusPill("YOUR ${if(count==7) "WEEK" else if(count in 1..31) "MONTH" else if(count==365) "YEAR" else "$count DAYS"}, WRAPPED",Palette.Pink)
        Goob(ScrollTier.of(total/count.coerceAtLeast(1)))
        Text(title,fontFamily=DoomFonts.Display,fontSize=30.sp,fontWeight=FontWeight.Bold,color=Palette.Text,textAlign=TextAlign.Center)
        Text("$total",fontFamily=DoomFonts.Display,fontSize=68.sp,fontWeight=FontWeight.Bold,color=Palette.Lime)
        Text("reels. ${duration(days.sumOf{it.watchMs})} of scrolling.",color=Palette.Dim)
        Panel {
            Title("the recap")
            Text("${days.sumOf{it.ads}} recognized ads skipped",color=Palette.Text)
            Text("${days.sumOf{it.repeats}} recent rewatches skipped",color=Palette.Text)
            Text("wildest day: ${days.maxOf{it.total}} reels",color=Palette.Text)
            Text(if(total>0) "🌙  doom hour: $peak:00" else "🎮  first score pending",color=Palette.Text)
        }
        Spacer(Modifier.height(16.dp))
        }
        Button(onClick=onShare,modifier=Modifier.fillMaxWidth().padding(vertical=12.dp).heightIn(min=50.dp)) {Text("Share your recap",fontWeight=FontWeight.Bold)}
    }
}

@Composable private fun BattleScreen(client: BattleClient, invite: String?, onInviteConsumed: () -> Unit) {
    val state by client.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var handle by remember {mutableStateOf("")}
    var name by remember {mutableStateOf("")}
    var emoji by remember {mutableStateOf("🫠")}
    var code by remember(invite) {mutableStateOf(invite.orEmpty())}
    var week by remember {mutableStateOf(false)}
    var localError by remember {mutableStateOf<String?>(null)}
    var challenge by remember {mutableStateOf(false)}
    if(challenge) BattleChallenge(onCancel={challenge=false},onToken={token -> challenge=false;scope.launch{client.connect(token)}})
    LaunchedEffect(state.profile,week) {if(state.profile!=null) {while(true) {client.refresh(if(week) "week" else "day");delay(30_000)}}}
    Page {
        Text("scroll battle",fontFamily=DoomFonts.Display,fontSize=30.sp,fontWeight=FontWeight.Bold,color=Palette.Text)
        Text("your friends. your counts. zero excuses.",fontSize=14.sp,color=Palette.Dim)
        if(state.busy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth(),color=Palette.Cyan)
        (state.error ?: localError)?.let {Text(it,color=Palette.Pink,fontSize=13.sp)}
        when {
            !state.signedIn -> Panel {
                Text("Who's the most cooked?",fontFamily=DoomFonts.Display,fontSize=24.sp,fontWeight=FontWeight.Bold,color=Palette.Text)
                Text("Connect a battle profile to compare with friends on iOS and Android. Only your daily counts, viewing durations and recognized-ad totals sync. Your screen and reel identifiers stay on your phone.",color=Palette.Dim,fontSize=14.sp,lineHeight=22.sp)
                Text("Quick start creates an anonymous account saved on this phone. It won't follow you to another device.",color=Palette.Faint,fontSize=12.sp)
                if(!client.configured) Text("Battles are unavailable in this version. Your local reel counter still works.",color=Palette.Dim,fontSize=12.sp)
                Button(onClick={if(com.gridcc.doomscore.android.BuildConfig.CAPTCHA_URL.isNotBlank()) challenge=true else scope.launch{client.connect()}},enabled=client.configured && !state.busy,modifier=Modifier.fillMaxWidth()) {Text("Agree & quick start")}
            }
            state.profile==null -> Panel {
                Title("pick your battle identity")
                OutlinedTextField(value=handle,onValueChange={handle=it.take(20)},label={Text("Handle")},placeholder={Text("sleepy.goblin")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=name,onValueChange={name=it.take(30)},label={Text("Display name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {InputRules.avatars.forEach {face->FilterChip(selected=emoji==face,onClick={emoji=face},label={Text(face,fontFamily=DoomFonts.Display,fontSize=22.sp)})}}
                Button(onClick={scope.launch{client.profile(handle,name,emoji)}},enabled=!state.busy && handle.length>=3,modifier=Modifier.fillMaxWidth()) {Text("Let's battle")}
            }
            else -> {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(selected=!week,onClick={week=false},label={Text("today")});FilterChip(selected=week,onClick={week=true},label={Text("this week")})}
                Panel {
                    Title("the leaderboard", "${state.rows.count{!it.me}} friends")
                    val sorted = state.rows.sortedWith(compareByDescending<com.gridcc.doomscore.android.network.BattleRow>{it.reels}.thenBy {it.id})
                    if(sorted.isEmpty()) Text("Your first totals will appear after syncing a reel session.",color=Palette.Dim,fontSize=13.sp)
                    sorted.forEachIndexed {index,row ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if(row.me) Palette.Lime.copy(alpha=.06f) else Palette.High).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text("${index+1}",color=Palette.Faint,modifier=Modifier.width(24.dp));Text(row.emoji,fontFamily=DoomFonts.Display,fontSize=26.sp,modifier=Modifier.width(40.dp))
                            Column(Modifier.weight(1f)) {Text(row.name+if(row.me) " (you)" else "",color=Palette.Text,fontWeight=FontWeight.Bold,fontSize=14.sp);Text("@${row.handle}",color=Palette.Dim,fontSize=11.sp)}
                            Text("${row.reels}",color=if(row.me) Palette.Lime else Palette.Text,fontSize=21.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                }
                Button(onClick={scope.launch {
                    try {val url=client.invite();context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Who's the most cooked? Join my Doomscore battle: $url"),"Invite a friend"))}
                    catch(e:Exception) {localError=e.message}
                }},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text("Invite a friend ↗")}
                Panel {
                    Title("got an invite?")
                    OutlinedTextField(value=code,onValueChange={if(it.length<=256) code=it},label={Text("Invite link or code")},singleLine=true,modifier=Modifier.fillMaxWidth())
                    OutlinedButton(onClick={scope.launch {client.accept(code);if(client.state.value.error==null){code="";onInviteConsumed()}}},enabled=code.isNotBlank() && !state.busy,modifier=Modifier.fillMaxWidth()) {Text("Join battle")}
                }
            }
        }
    }
}
