package com.gridcc.doomscore.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gridcc.doomscore.android.BuildConfig
import com.gridcc.doomscore.android.DoomApplication
import com.gridcc.doomscore.android.core.InputRules
import com.gridcc.doomscore.android.core.LeagueRules
import com.gridcc.doomscore.android.network.LeagueProfile
import com.gridcc.doomscore.android.network.LeagueRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LeagueScreen(app: DoomApplication, revision: Int) {
    val client = app.league
    val state by client.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var challenge by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<LeagueProfile?>(null) }
    var report by remember { mutableStateOf<LeagueRow?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val localScore by storeSnapshot(app.store,revision){app.store.leagueDays().values.sumOf {it.toLong()}}
    val localTotal=localScore.data
    LaunchedEffect(Unit) { if(client.configured) while(true) { client.refresh(); delay(30_000) } }
    val board = state.board
    val month = board?.month ?: LeagueRules.month()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp), verticalArrangement=Arrangement.spacedBy(14.dp), contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Palette.High,Palette.Pink.copy(alpha=.12f)))).padding(22.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("THE DOOM LEAGUE", color=Palette.Cyan, fontWeight=FontWeight.Black, fontSize=12.sp)
                Text("your thumb has\ncompetition.", fontSize=32.sp, lineHeight=36.sp, fontWeight=FontWeight.Black, color=Palette.Text)
                Text("${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))} · monthly season", color=Palette.Dim, fontSize=14.sp)
                Text("More reels, higher rank. The receipts reset every month.", color=Palette.Dim, fontSize=13.sp)
            }
        }
        item {
            LeagueCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.profile?.let { "${it.emoji} @${it.username}" } ?: "who's this scroll goblin?", fontWeight=FontWeight.Bold, color=Palette.Text)
                        Text(if(state.profile?.reserved == true) "your public identity" else "no email. no password.", color=Palette.Dim, fontSize=12.sp)
                    }
                    TextButton(onClick={edit=true}, enabled=!state.busy) { Text(if(state.profile==null) "pick a name" else "edit", color=Palette.Lime) }
                }
                val rank = when {
                    state.profile?.visible == false -> "hidden"
                    board?.myRank != null -> "#${board.myRank}"
                    board?.myPercent != null -> "top ${board.myPercent}%"
                    else -> "unranked"
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Column {
                        AnimatedContent(board?.myReels?.toString() ?: localTotal?.toString() ?: "…",transitionSpec={fadeIn(tween(180)) togetherWith fadeOut(tween(90))},label="season score") {score ->
                            Text(score,color=Palette.Lime,fontWeight=FontWeight.Black,fontSize=34.sp)
                        }
                        Text("reels this season",color=Palette.Dim,fontSize=12.sp)
                    }
                    Column(horizontalAlignment=Alignment.End) { Text(rank, color=Palette.Pink,fontWeight=FontWeight.Black,fontSize=28.sp); Text("${board?.participants ?: 0} ranked scrollers",color=Palette.Dim,fontSize=12.sp) }
                }
                if(!client.configured) Text("Global rankings aren't connected yet. You can save your profile on this phone; your username is reserved when the league goes online.", color=Palette.Dim, fontSize=12.sp)
                else if(state.profile?.reserved != true) Text("Pick a username and join to publish your score. Browsing the top 50 doesn't need a profile.",color=Palette.Dim,fontSize=12.sp)
                else if(state.profile?.visible == false) Text("You're hidden from the board and the podium. Turn public profile back on to compete.",color=Palette.Dim,fontSize=12.sp)
                Text("Season clock: UTC · counts from this update onward. Your full local calendar-month total is on Today and Stats.",color=Palette.Faint,fontSize=11.sp)
            }
        }
        if(state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color=Palette.Cyan) }
        state.error?.let { message -> item { Text(message,color=Palette.Pink,fontSize=13.sp); TextButton(onClick={scope.launch{client.refresh()}},enabled=!state.busy){Text("try again")} } }
        if(board?.podium?.isNotEmpty() == true) item {
            LeagueCard {
                Text("LAST MONTH'S FINAL BOSSES 🏆",color=Palette.Pink,fontSize=13.sp,fontWeight=FontWeight.Black)
                Text("${board.previousMonth.format(DateTimeFormatter.ofPattern("MMMM"))} hall of fame · spotlight on days 1–7",color=Palette.Dim,fontSize=12.sp)
                board.podium.forEach { row -> LeagueEntry(row, row.username == state.profile?.username, champion = true,
                    onReport={report=row},onBlock={scope.launch{client.profileAction(row.username,"block")}}) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("the top 50 🔥",color=Palette.Text,fontSize=22.sp,fontWeight=FontWeight.Black,modifier=Modifier.weight(1f))
                TextButton(onClick={scope.launch{client.refresh()}},enabled=client.configured && !state.busy){Text("refresh",color=Palette.Cyan)}
            }
        }
        if(board==null && state.busy) item {LoadingScores()}
        else if(board?.top.isNullOrEmpty()) item {
            LeagueCard {
                Text(if(client.configured) "the throne is empty." else "the arena is warming up.",fontWeight=FontWeight.Bold,color=Palette.Text)
                Text(if(client.configured) "Scroll, sync, and your first season score appears here." else "Real global scores will appear after the shared leaderboard is connected.",color=Palette.Dim,fontSize=13.sp)
            }
        }
        items(board?.top.orEmpty(), key={it.username}) { row -> LeagueEntry(row,row.username==state.profile?.username,
            onReport={report=row},onBlock={scope.launch{client.profileAction(row.username,"block")}},modifier=Modifier.animateItem()) }
        notice?.let {item { Text(it,color=Palette.Cyan,fontSize=12.sp) }}
        if(state.profile?.reserved == true) item { TextButton(onClick={scope.launch{client.profileAction("","unblock_all")}},enabled=!state.busy){Text("Reset blocked profiles",color=Palette.Dim)} }
        item { Text("More reels, higher rank. Ties use a fixed order. Exact ranks through #200; everyone below gets a top-percentage badge. New month, new leaderboard.",color=Palette.Faint,fontSize=11.sp) }
    }
    if(edit) ModalBottomSheet(onDismissRequest={edit=false}, containerColor=Palette.Surface,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        LeagueProfileEditor(state.profile, state.busy, state.error, client.configured,
            onSave={ candidate ->
                pending=candidate
                if(client.configured && !app.battles.state.value.signedIn && BuildConfig.CAPTCHA_URL.isNotBlank()) {edit=false;challenge=true}
                else scope.launch {client.save(candidate.username,candidate.instagram,candidate.emoji,candidate.visible);if(client.state.value.error==null) edit=false}
            }, onDelete={delete=true}, onClose={edit=false})
    }
    if(challenge) BattleChallenge(onCancel={challenge=false;edit=true},onToken={token ->
        challenge=false;scope.launch {pending?.let {client.save(it.username,it.instagram,it.emoji,it.visible,token)};if(client.state.value.error!=null) edit=true}
    })
    if(delete) AlertDialog(onDismissRequest={delete=false},title={Text("Delete your identity?")},
        text={Text("This removes your online profiles, league scores, podium entries and friend connections. Your local reel history stays on this phone.")},
        confirmButton={TextButton(onClick={delete=false;scope.launch{client.delete();if(client.state.value.error==null) edit=false}}){Text("Delete",color=Palette.Pink)}},
        dismissButton={TextButton(onClick={delete=false}){Text("Cancel")}})
    report?.let { row -> AlertDialog(onDismissRequest={report=null},title={Text("Report @${row.username}")},
        text={Column {Text("Choose a reason. Reports go to the app's moderators.");listOf("impersonation","offensive","spam").forEach {reason ->
            TextButton(onClick={report=null;scope.launch{client.profileAction(row.username,"report",reason);if(client.state.value.error==null) notice="Report submitted. Thank you."}}){Text(reason)}
        }}},confirmButton={TextButton(onClick={report=null}){Text("Cancel")}}) }
}

@Composable private fun LeagueCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Palette.Surface).border(1.dp,Palette.High,RoundedCornerShape(22.dp)).padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
}

@Composable private fun LeagueEntry(row: LeagueRow,me: Boolean,champion: Boolean=false,onReport: () -> Unit,onBlock: () -> Unit,modifier: Modifier=Modifier) {
    val context=LocalContext.current
    var linkError by remember {mutableStateOf(false)}
    var menu by remember {mutableStateOf(false)}
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if(me) Palette.Lime.copy(alpha=.08f) else Palette.Surface).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(if(champion) listOf("🥇","🥈","🥉")[row.rank-1] else "#${row.rank}",color=if(row.rank<=3) Palette.Lime else Palette.Faint,fontSize=14.sp,fontWeight=FontWeight.Black,modifier=Modifier.width(42.dp))
        Text(row.emoji,fontSize=24.sp,modifier=Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text("@${row.username}"+if(me) " · you" else "",color=Palette.Text,fontWeight=FontWeight.Bold,fontSize=14.sp)
            if(row.instagram.isNotEmpty()) TextButton(onClick={
                val handle=LeagueRules.instagram(row.instagram)
                try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.instagram.com/$handle/")))} catch(_:Exception){linkError=true}
            },contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp)) {Text("IG @${row.instagram} ↗",color=Palette.Cyan,fontSize=11.sp)}
            if(linkError) Text("No app available to open Instagram.",color=Palette.Pink,fontSize=11.sp)
        }
        Column(horizontalAlignment=Alignment.End) {Text("${row.reels}",color=if(me) Palette.Lime else Palette.Text,fontSize=22.sp,fontWeight=FontWeight.Black);Text("reels",color=Palette.Faint,fontSize=10.sp)}
        if(!me) Box {
            TextButton(onClick={menu=true},contentPadding=PaddingValues(0.dp),modifier=Modifier.widthIn(min=48.dp)){Text("⋮",fontSize=22.sp,color=Palette.Dim)}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                DropdownMenuItem(text={Text("Report profile")},onClick={menu=false;onReport()})
                DropdownMenuItem(text={Text("Block profile")},onClick={menu=false;onBlock()})
            }
        }
    }
}

@Composable private fun LeagueProfileEditor(profile: LeagueProfile?, busy: Boolean, serverError: String?, online: Boolean,
    onSave: (LeagueProfile) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    var username by remember(profile) {mutableStateOf(profile?.username.orEmpty())}
    var instagram by remember(profile) {mutableStateOf(profile?.instagram.orEmpty())}
    var emoji by remember(profile) {mutableStateOf(profile?.emoji ?: "🫠")}
    var visible by remember(profile) {mutableStateOf(profile?.visible ?: true)}
    var terms by remember(profile) {mutableStateOf(profile?.reserved == true)}
    var termsError by remember {mutableStateOf(false)}
    val context=LocalContext.current
    val focus=LocalFocusManager.current
    val usernameCheck=remember(username){runCatching {InputRules.handle(username)}}
    val instagramCheck=remember(instagram){runCatching {LeagueRules.instagram(instagram)}}
    val usernameError=usernameCheck.exceptionOrNull()?.message?.takeIf {username.isNotBlank()}
    val instagramError=instagramCheck.exceptionOrNull()?.message
    val valid=usernameCheck.isSuccess && instagramCheck.isSuccess
    Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).navigationBarsPadding().imePadding().padding(horizontal=22.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("your scroll identity",fontSize=25.sp,fontWeight=FontWeight.Black,color=Palette.Text,modifier=Modifier.weight(1f))
            TextButton(onClick={focus.clearFocus();onClose()}){Text("done",color=Palette.Cyan)}
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("One unique username. No email or password. Your identity stays on this installation; a username alone can't recover it after uninstalling.",color=Palette.Dim,fontSize=13.sp)
        OutlinedTextField(value=username,onValueChange={username=it.take(20)},label={Text("Doomscore username")},isError=usernameError!=null,supportingText={usernameError?.let {Text(it,color=Palette.Pink)}},placeholder={Text("certified.goblin")},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii,imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Down)}),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=instagram,onValueChange={instagram=it.take(32)},label={Text("Instagram username · optional")},isError=instagramError!=null,supportingText={instagramError?.let {Text(it,color=Palette.Pink)}},placeholder={Text("@your.handle")},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),modifier=Modifier.fillMaxWidth())
        Text("Your Instagram handle is self-reported. Adding it shares a public link on your leaderboard row and, if you finish top 3, next month's podium.",color=Palette.Faint,fontSize=12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {InputRules.avatars.forEach { face -> FilterChip(selected=emoji==face,onClick={emoji=face},enabled=!busy,label={Text(face,fontSize=24.sp)})}}
        Row(verticalAlignment=Alignment.CenterVertically) {Text("Public profile & monthly score",modifier=Modifier.weight(1f),color=Palette.Text);Switch(checked=visible,onCheckedChange={visible=it},enabled=!busy)}
        Text("Joining publishes your username, avatar, optional Instagram handle and monthly reel count. Only UTC daily counts sync; no captions, videos or reel identifiers.",color=Palette.Dim,fontSize=12.sp)
        if(online) {
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(checked=terms,onCheckedChange={terms=it},enabled=!busy);Text("I agree to the community terms. No impersonation, offensive names or fake scores.",color=Palette.Dim,fontSize=12.sp,modifier=Modifier.weight(1f))}
            if(BuildConfig.TERMS_URL.isNotBlank()) TextButton(onClick={try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(BuildConfig.TERMS_URL)))} catch(_:Exception){termsError=true}}){Text("Read community terms",color=Palette.Cyan)}
            if(termsError) Text("No app is available to open the terms.",color=Palette.Pink,fontSize=12.sp)
        }
        Spacer(Modifier.height(8.dp))
        }
        serverError?.let {Text(it,color=Palette.Pink,fontSize=12.sp)}
        Button(onClick={focus.clearFocus();onSave(LeagueProfile(username,instagram,emoji,visible))},enabled=!busy && valid && (!online || terms),modifier=Modifier.fillMaxWidth()) {Text(if(online) {if(profile?.reserved==true) "save your identity" else "agree & join the league"} else "save on this phone")}
        if(profile!=null) TextButton(onClick=onDelete,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Delete identity",color=Palette.Pink)}

    }
}
