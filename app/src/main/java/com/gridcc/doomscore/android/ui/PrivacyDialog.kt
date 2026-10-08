package com.gridcc.doomscore.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gridcc.doomscore.android.BuildConfig

@Composable fun PrivacyDialog(onDismiss:()->Unit) {
    val context=LocalContext.current
    var error by remember {mutableStateOf<String?>(null)}
    fun open(value:String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(value))) }
        catch (_:Exception) {error="No app is available to open this link."}
    }
    AlertDialog(onDismissRequest=onDismiss,containerColor=Palette.Surface,title={Text("Privacy & data")},
        confirmButton={TextButton(onClick=onDismiss){Text("Close")}},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Doomscore ${BuildConfig.VERSION_NAME}")
                if(BuildConfig.DEVELOPER_NAME.isNotBlank()) Text("Provided by ${BuildConfig.DEVELOPER_NAME}")
                Text("With your consent, Android Accessibility reads visible text, descriptions and control IDs in the reel apps you select, even when Doomscore is in the background. This detects reel changes, visible ad labels and recent repeat views.")
                Text("There are no screenshots or screen recordings. Captions and screen labels are processed temporarily and are not saved. This app does not access your camera, microphone, contacts or location.")
                Text("Floating Goob displays are off by default. Some payment apps block enabled accessibility services even when counting is paused. Use Settings → Disconnect counter for payments to turn off the service while preserving your data, and re-enable it through Android Accessibility when ready. This does not promise approval by any payment app.")
                Text("The optional Goob island uses the same Accessibility permission to draw a small counter over selected reel feeds. It does not capture your screen. Optional island-app notifications publish today's count, rank and source app to Android; notification-listener apps you authorize may read them. Notifications are silent and hide the count on the lock screen unless your Android settings allow private content. Both displays stop when you leave a detected feed or pause counting.")
                Text("Your phone stores daily and hourly counts, viewing time, recognized-ad and rewatch totals, preferences, and salted hashes for recent reels. Hashes support a five-minute rewatch window; expired entries are removed during use or when you next open the app. Captions and raw reel identifiers are never uploaded.")
                Text("The Brainrot Trophy Cabinet also remembers salted hashes for unique-reel milestones: at most 10,000 lifetime hashes and 500 daily hashes. Lifetime hashes are removed after the final unique-reel badge is earned; daily hashes reset with the calendar day and stop being stored after the 500-in-a-day badge is earned. Badge progress and earned dates remain until you delete local counting history. No raw captions or video IDs are stored for badges.")
                Text("Battle and global-rank badges use private server-verified results when online features are connected. Cached proofs are encrypted, tied to your anonymous identity, and removed when that identity is deleted. A live Battle lead is not a completed win.")
                if(BuildConfig.BATTLES_ENABLED) {
                    Text("Battle accounts are optional. Connecting creates an anonymous account on Supabase. Your handle, display name, avatar, daily app counts, viewing durations, recognized-ad totals and a device make/model label are sent over HTTPS. Supabase receives the connection's IP address. Friends you connect with can access your profile and daily aggregates. Credentials and cached profile data are encrypted on your phone.")
                    Text("Anonymous accounts stay on this installation. A handle cannot recover an account if you lose its credentials. Disconnect stops future sync but keeps server data. To remove your account, friendships and synced totals, use Settings → Delete battle account. Failed deletion is reported and can be retried.")
                    Text("Battle signup may use Cloudflare Turnstile to prevent abuse. Loading that optional check sends connection/browser information to Cloudflare. Local reel counting does not use the check.")
                    if(BuildConfig.DELETION_URL.isNotBlank()) TextButton(onClick={open(BuildConfig.DELETION_URL)}){Text("Account deletion help")}
                } else Text("Friend Battles are disabled in this version.")
                Text("The global league is optional. Joining publishes a unique Doomscore username, chosen avatar, optional self-reported Instagram username and monthly reel count. Public participants can be browsed in pages of 50; your own exact rank is shown through 200, then only your top percentage. Last month's top three are highlighted on UTC days 1–7. Public Instagram links do not prove ownership of an Instagram account.")
                if(BuildConfig.FIREBASE_PROJECT.isNotBlank()) Text("Google sign-in and guest identities use Firebase Authentication. Firebase may retain email and provider profile information in its private authentication service; DoomScore does not copy it into Firestore profiles or public leaderboard records. Only a Firebase user ID and short-lived session credentials are kept encrypted in Android Keystore. Joining publishes your chosen profile and UTC monthly totals; Google sign-in alone does not publish a profile. Google and Firebase receive connection information such as your IP address. Guest identities cannot be recovered after uninstalling unless linked to Google. Hide your profile or delete the online account in the app; signing out alone does not remove public records.")
                else if(BuildConfig.LEAGUE_ONLINE_ENABLED) Text("Joining creates a private anonymous Supabase identity without an email or password. UTC daily reel totals sync for the current calendar month. Device credentials are encrypted with Android Keystore. Your username alone cannot recover access after uninstalling. Hide your profile or remove the Instagram link in League → Edit, or use Delete identity to remove server profiles, scores and podium entries.")
                else Text("The shared global league is not connected in this build. Profile edits stay encrypted on this phone, usernames are not reserved globally, and league counts are not uploaded.")
                Text("Turn off Automatic counting or disable Doomscore in Android Accessibility to stop counting. Delete local counting history in Settings, or uninstall to remove local app data. Data backups are disabled. Sharing a recap sends only the image and text you choose to the receiving app.")
                Text("No advertising or analytics SDK is included. Doomscore does not sell your data. Detection can change when other apps update; identical exposed metadata and unlabelled ads can affect accuracy.")
                if(BuildConfig.PRIVACY_URL.isNotBlank()) TextButton(onClick={open(BuildConfig.PRIVACY_URL)}){Text("Full privacy policy")}
                if(BuildConfig.SUPPORT_EMAIL.isNotBlank()) TextButton(onClick={open("mailto:"+Uri.encode(BuildConfig.SUPPORT_EMAIL,"@."))}){Text("Contact support")}
                Text("Typography: Space Grotesk and DM Sans, licensed under SIL Open Font License 1.1. Copyright and license notices are bundled with the app.",style=MaterialTheme.typography.bodySmall,color=Palette.Dim)
                error?.let {Text(it,color=Palette.Pink)}
            }
        })
}
