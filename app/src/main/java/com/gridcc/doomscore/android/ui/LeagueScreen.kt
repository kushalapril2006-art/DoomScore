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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import java.time.Instant
import java.text.NumberFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    val account by app.firebase.account.collectAsStateWithLifecycle()
    val firebase=client===app.firebase
    val context=LocalContext.current
    val scope = rememberCoroutineScope()
    var accountPanel by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var challenge by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<LeagueProfile?>(null) }
    var report by remember { mutableStateOf<LeagueRow?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val localScore by storeSnapshot(app.store,revision){app.store.leagueDays().values.sumOf {it.toLong()}}
    val localTotal=localScore.data
    LaunchedEffect(Unit) { if(client.configured) {client.bootstrap();while(true) { delay(60_000);client.refresh() }} }
    val board = state.board
    val month = board?.month ?: LeagueRules.month()
    val largeText=LocalDensity.current.fontScale>1.2f
    val rank = when {
        state.profile?.visible == false -> "hidden"
        board?.myRank != null -> "#${board.myRank}"
        board?.myPercent != null -> "top ${board.myPercent}%"
        else -> "unranked"
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("global leaderboard",color=Palette.Text,fontFamily=DoomFonts.Display,fontSize=24.sp,fontWeight=FontWeight.Bold)
                        Text("${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))} season · UTC",color=Palette.Cyan,fontSize=12.sp)
                    }
                    IconButton(onClick={scope.launch {client.refresh()}},enabled=client.configured && !state.busy,modifier=Modifier.semantics {contentDescription="Refresh leaderboard"}) {DoomIcon(Mark.REPEAT,Palette.Cyan)}
                }
            }
            if(state.busy) item {LinearProgressIndicator(Modifier.fillMaxWidth(),color=Palette.Cyan,trackColor=Palette.High)}
            state.error?.let {message -> item {Text(message,color=Palette.Pink,fontSize=13.sp);TextButton(onClick={scope.launch {client.refresh()}},enabled=!state.busy){Text("Try again")}}}
            if(board==null && state.busy) item {Text("Loading the standings…",color=Palette.Dim,fontSize=13.sp)}
            else if(board?.top.isNullOrEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical=28.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    DoomIcon(Mark.PODIUM,Palette.Lime,Modifier.size(32.dp))
                    Text(if(client.configured) "the podium is open." else "the arena is warming up.",fontFamily=DoomFonts.Display,fontWeight=FontWeight.Bold,color=Palette.Text,fontSize=22.sp)
                    Text(if(client.configured) "No public scores yet. Choose a username, scroll and sync to join the standings." else "Global rankings aren't connected yet. Your local counts stay on this phone.",color=Palette.Dim,fontSize=13.sp)
                }
            }
            val leaders=board?.top.orEmpty().filter {it.rank in 1..3}
            if(leaders.isNotEmpty()) item {
                LeaguePodium(leaders,state.profile?.username,onReport={report=it},onBlock={row->scope.launch {client.profileAction(row.username,"block")}})
            }
            if(board?.podium?.isNotEmpty()==true && board.month==LeagueRules.month() && board.previousMonth==board.month.minusMonths(1) && LeagueRules.spotlight(Instant.now())) item {
                LeagueCard {
                    Text("LAST MONTH'S WINNERS",color=Palette.Pink,fontSize=12.sp,fontWeight=FontWeight.Bold)
                    Text("${board.previousMonth.format(DateTimeFormatter.ofPattern("MMMM"))} podium · spotlight through the 7th",color=Palette.Dim,fontSize=12.sp)
                    board.podium.forEach {row -> LeagueEntry(row,row.username==state.profile?.username,
                        onReport={report=row},onBlock={scope.launch {client.profileAction(row.username,"block")}})}
                }
            }
            if(!board?.top.isNullOrEmpty()) item {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("POS",color=Palette.Faint,fontSize=10.sp,modifier=Modifier.width(38.dp))
                    Text("PLAYER",color=Palette.Faint,fontSize=10.sp,modifier=Modifier.weight(1f))
                    if(!largeText) Text("REELS",color=Palette.Faint,fontSize=10.sp,textAlign=TextAlign.End,modifier=Modifier.width(82.dp))
                    Spacer(Modifier.width(48.dp))
                }
            }
            items(board?.top.orEmpty().filter {it.rank>3},key={it.username}) {row ->
                LeagueEntry(row,row.username==state.profile?.username,onReport={report=row},onBlock={scope.launch {client.profileAction(row.username,"block")}},
                    modifier=Modifier.animateItem(fadeInSpec=tween(140),placementSpec=tween(180),fadeOutSpec=tween(100)))
            }
            if(firebase && state.more) item {OutlinedButton(onClick={scope.launch {client.loadMore()}},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Load more scrollers")}}
            notice?.let {item {Text(it,color=Palette.Cyan,fontSize=12.sp)}}
            item {Text("More reels, higher rank. Ties use a fixed order. Exact ranks through #200; everyone below gets a top-percentage badge. New month, new leaderboard.",color=Palette.Faint,fontSize=11.sp)}
        }
        // Outside the scrolling list, so your position stays visible at any scroll offset.
        Column(Modifier.fillMaxWidth().background(Palette.Surface).padding(horizontal=20.dp).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            HorizontalDivider(color=Palette.High)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("YOUR POSITION",color=Palette.Faint,fontSize=10.sp,letterSpacing=1.sp,modifier=Modifier.weight(1f))
                TextButton(onClick={accountPanel=true}) {Text("Account",color=Palette.Cyan)}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(rank,color=Palette.Lime,fontFamily=DoomFonts.Display,fontWeight=FontWeight.Bold,fontSize=24.sp)
                    Text(state.profile?.let {"@${it.username}"} ?: "Choose your league username",color=Palette.Dim,fontFamily=DoomFonts.Body,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment=Alignment.End) {
                    Text(board?.myReels?.let(::leagueNumber) ?: localTotal?.let(::leagueNumber) ?: "…",fontFamily=DoomFonts.Display,fontWeight=FontWeight.Bold,fontSize=26.sp,color=Palette.Text)
                    Text("${if(board?.myReels==null) "local " else ""}season reels",color=Palette.Faint,fontSize=11.sp)
                }
            }
        }
    }
    if(accountPanel) ModalBottomSheet(onDismissRequest={accountPanel=false},containerColor=Palette.Surface,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).navigationBarsPadding().padding(horizontal=22.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("League account",fontFamily=DoomFonts.Display,fontWeight=FontWeight.Bold,fontSize=25.sp,color=Palette.Text,modifier=Modifier.weight(1f))
                TextButton(onClick={accountPanel=false}) {Text("Close",color=Palette.Cyan)}
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                if(firebase) {
                    Text(if(account.signedIn && !account.guest) "Google account connected" else "Keep your scroll identity",color=Palette.Cyan,fontWeight=FontWeight.Bold)
                    Text("Your Google email, name and photo never appear on this board. Choose your own public username.",color=Palette.Dim,fontSize=13.sp)
                    Button(onClick={scope.launch {
                        app.firebase.googleSignIn(context)
                        if(app.firebase.account.value.signedIn && app.firebase.state.value.profile==null) {accountPanel=false;edit=true}
                    }},enabled=!account.busy && !state.busy,modifier=Modifier.fillMaxWidth()) {Text(if(account.busy) "Connecting…" else if(account.signedIn && !account.guest) "Verify Google account" else "Sign in with Google")}
                    if(account.signedIn && !account.guest) TextButton(onClick={scope.launch {app.firebase.signOut()}},enabled=!state.busy && !account.busy) {Text("Sign out",color=Palette.Dim)}
                    account.error?.let {Text(it,color=Palette.Pink,fontSize=13.sp)}
                    HorizontalDivider(color=Palette.High)
                }
                Text(state.profile?.let {"@${it.username}"} ?: "Your public identity",fontFamily=DoomFonts.Body,fontWeight=FontWeight.Bold,color=Palette.Text)
                Text(if(state.profile?.reserved==true) "Your username and optional Instagram link identify you on the standings." else "Pick a username to compete. You can browse without signing in.",color=Palette.Dim,fontSize=13.sp)
                OutlinedButton(onClick={accountPanel=false;edit=true},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {Text(if(state.profile==null) "Choose username" else "Edit profile")}
                if(state.profile?.visible==false) Text("You're hidden from the leaderboard and podium. Enable your public profile to compete.",color=Palette.Dim,fontSize=13.sp)
                if(!client.configured) Text("Global rankings aren't connected yet. You can save your profile on this phone; your username is reserved when the league goes online.",color=Palette.Dim,fontSize=13.sp)
                else if(state.profile?.reserved!=true) Text("Join with your chosen username to publish your season score.",color=Palette.Dim,fontSize=13.sp)
                Text("${board?.participants ?: 0} ranked scrollers",color=Palette.Dim,fontSize=13.sp)
                Text("Season clock: UTC. Scores sync periodically. Your full local calendar-month total is in Stats.",color=Palette.Faint,fontSize=12.sp)
                if(state.profile?.reserved==true) TextButton(onClick={scope.launch {client.profileAction("","unblock_all")}},enabled=!state.busy) {Text("Reset blocked profiles",color=Palette.Dim)}
            }
        }
    }
    if(edit) ModalBottomSheet(onDismissRequest={edit=false}, containerColor=Palette.Surface,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        LeagueProfileEditor(state.profile, state.busy, state.error, client.configured, firebase,
            onSave={ candidate ->
                pending=candidate
                if(!firebase && client.configured && !app.battles.state.value.signedIn && BuildConfig.CAPTCHA_URL.isNotBlank()) {edit=false;challenge=true}
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

private fun leagueNumber(value: Long)=NumberFormat.getIntegerInstance().format(value)

@Composable private fun LeaguePodium(rows: List<LeagueRow>,username: String?,onReport: (LeagueRow) -> Unit,onBlock: (LeagueRow) -> Unit) {
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth<320.dp || fontScale>1.2f) {
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                rows.sortedBy {it.rank}.forEach {row->LeagueEntry(row,row.username==username,onReport={onReport(row)},onBlock={onBlock(row)})}
            }
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Bottom) {
            listOf(2,1,3).forEach {rank ->
                val row=rows.firstOrNull {it.rank==rank}
                if(row==null) Spacer(Modifier.weight(1f)) else {
                    val tint=when(rank) {1->Palette.Lime;2->Palette.Cyan;else->Palette.Pink}
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha=.07f)).border(1.dp,tint.copy(alpha=if(row.username==username) .6f else .18f),RoundedCornerShape(16.dp))
                        .padding(horizontal=8.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        if(rank==1) DoomIcon(Mark.TROPHY,tint,Modifier.size(24.dp))
                        Text("#$rank",fontFamily=DoomFonts.Display,fontSize=24.sp,fontWeight=FontWeight.Bold,color=tint)
                        Text("@${row.username}",fontFamily=DoomFonts.Body,fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=Palette.Text,maxLines=2,overflow=TextOverflow.Ellipsis,textAlign=TextAlign.Center)
                        if(row.username==username) Text("you",color=tint,fontSize=11.sp)
                        Text(leagueNumber(row.reels),fontFamily=DoomFonts.Display,fontSize=20.sp,fontWeight=FontWeight.Bold,color=Palette.Text,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text("reels",color=Palette.Faint,fontSize=10.sp)
                        InstagramLink(row.instagram)
                        if(row.username!=username) ProfileMenu(onReport={onReport(row)},onBlock={onBlock(row)})
                    }
                }
            }
        }
    }
}

@Composable private fun LeagueEntry(row: LeagueRow,me: Boolean,onReport: () -> Unit,onBlock: () -> Unit,modifier: Modifier=Modifier) {
    val largeText=LocalDensity.current.fontScale>1.2f
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if(me) Palette.Lime.copy(alpha=.07f) else Palette.Bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("${row.rank}",color=if(row.rank<=3 || me) Palette.Lime else Palette.Faint,fontFamily=DoomFonts.Display,fontSize=16.sp,fontWeight=FontWeight.Bold,modifier=Modifier.width(38.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text("@${row.username}"+if(me) " · you" else "",fontFamily=DoomFonts.Body,color=Palette.Text,fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(largeText) Text("${leagueNumber(row.reels)} reels",fontFamily=DoomFonts.Display,fontSize=20.sp,fontWeight=FontWeight.Bold,color=if(me) Palette.Lime else Palette.Text)
                InstagramLink(row.instagram)
            }
            if(!largeText) Text(leagueNumber(row.reels),color=if(me) Palette.Lime else Palette.Text,fontFamily=DoomFonts.Display,fontSize=20.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.End,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.width(82.dp))
            if(!me) ProfileMenu(onReport,onBlock) else Spacer(Modifier.width(48.dp))
        }
        HorizontalDivider(color=Palette.High)
    }
}

@Composable private fun InstagramLink(instagram: String) {
    if(instagram.isEmpty()) return
    val context=LocalContext.current
    var linkError by remember(instagram) {mutableStateOf(false)}
    TextButton(onClick={
        val handle=LeagueRules.instagram(instagram)
        try {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.instagram.com/$handle/")))} catch(_:Exception){linkError=true}
    },contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp)) {
        Text("IG @$instagram ↗",fontFamily=DoomFonts.Body,color=Palette.Cyan,fontSize=11.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
    }
    if(linkError) Text("Couldn't open Instagram.",color=Palette.Pink,fontSize=11.sp)
}

@Composable private fun ProfileMenu(onReport: () -> Unit,onBlock: () -> Unit) {
    var menu by remember {mutableStateOf(false)}
    Box {
        IconButton(onClick={menu=true},modifier=Modifier.semantics {contentDescription="Profile options"}) {Text("⋮",fontFamily=DoomFonts.Body,fontSize=22.sp,color=Palette.Dim)}
        DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
            DropdownMenuItem(text={Text("Report profile")},onClick={menu=false;onReport()})
            DropdownMenuItem(text={Text("Block profile")},onClick={menu=false;onBlock()})
        }
    }
}

@Composable private fun LeagueProfileEditor(profile: LeagueProfile?, busy: Boolean, serverError: String?, online: Boolean, firebase:Boolean=false,
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
            Text("your scroll identity",fontFamily=DoomFonts.Display,fontSize=25.sp,fontWeight=FontWeight.Bold,color=Palette.Text,modifier=Modifier.weight(1f))
            TextButton(onClick={focus.clearFocus();onClose()}){Text("done",color=Palette.Cyan)}
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(if(firebase) "Choose your public name. Google sign-in can recover a linked identity on another phone. Guest identities stay on this installation; a username alone cannot recover them." else "One unique username. No email or password. Your identity stays on this installation; a username alone can't recover it after uninstalling.",color=Palette.Dim,fontSize=13.sp)
        OutlinedTextField(value=username,onValueChange={username=it.take(20)},label={Text("Doomscore username")},isError=usernameError!=null,supportingText={usernameError?.let {Text(it,color=Palette.Pink)}},placeholder={Text("certified.goblin")},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii,imeAction=ImeAction.Next),keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Down)}),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(value=instagram,onValueChange={instagram=it.take(32)},label={Text("Instagram username · optional")},isError=instagramError!=null,supportingText={instagramError?.let {Text(it,color=Palette.Pink)}},placeholder={Text("@your.handle")},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),modifier=Modifier.fillMaxWidth())
        Text("Your Instagram handle is self-reported. Adding it shares a public link on your leaderboard row and, if you finish top 3, next month's podium.",color=Palette.Faint,fontSize=12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {InputRules.avatars.forEach { face -> FilterChip(selected=emoji==face,onClick={emoji=face},enabled=!busy,label={Text(face,fontFamily=DoomFonts.Display,fontSize=24.sp)})}}
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
