package com.sidbh.tvlauncher

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.sidbh.tvlauncher.ui.DefaultTint
import com.sidbh.tvlauncher.ui.InputTint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the screens read and the actions they trigger. Observable fields are Compose state. */
class LauncherState(
    private val apps: AppRepository,
    private val inputs: InputRepository,
    private val prefs: LauncherPrefs,
    private val pinStore: PinStore,
    private val weatherRepo: WeatherRepository,
    private val content: ContentRepository,
    private val remoteImages: RemoteImages,
    private val bluetooth: BluetoothRepository,
    private val scope: CoroutineScope,
) {
    private var loaded by mutableStateOf(emptyList<Item>())
    private var overrides by mutableStateOf(prefs.overrides())
    private var lastUsed by mutableStateOf(prefs.lastUsed())

    var loadedOnce by mutableStateOf(false)
        private set
    var pinned by mutableStateOf(pinStore.load())
        private set
    var weather by mutableStateOf(weatherRepo.cached())
        private set
    var cityName by mutableStateOf(prefs.city)
        private set

    var cards by mutableStateOf(emptyList<Card>())
        private set
    var focusedCard by mutableStateOf<Card?>(null)
        private set

    var btDevices by mutableStateOf(emptyList<BtDevice>())
        private set
    var btStatus by mutableStateOf<String?>(null)
    var showBluetooth by mutableStateOf(false)

    var homeStyle by mutableStateOf(prefs.homeStyle)
        private set

    var focused by mutableStateOf<Item?>(null)
    var showAll by mutableStateOf(false)
    var showQuick by mutableStateOf(false)
    var showWeather by mutableStateOf(false)
    var showGame by mutableStateOf(false)
    var launchTick by mutableIntStateOf(0) // counts successful launches, so the screen can play a "select" sound
        private set
    var showStylePicker by mutableStateOf(false)
    var arcadeSound by mutableStateOf(prefs.arcadeSound)
        private set
    var retroCursor by mutableIntStateOf(0) // which "game" the Retro menu has selected
    var chompHigh by mutableIntStateOf(prefs.chompHigh)
        private set
    var weatherDetail by mutableStateOf<WeatherDetail?>(null)
        private set
    var menuFor by mutableStateOf<Item?>(null)
    var focusAllApps by mutableStateOf(false)
    var homeTick by mutableIntStateOf(0)
        private set

    val items: List<Item> by derivedStateOf {
        loaded.map { item -> overrides[item.id]?.let { item.copy(category = it) } ?: item }
    }
    val favorites: List<Item> by derivedStateOf {
        val byId = items.associateBy { it.id }
        pinned.mapNotNull { byId[it] }
    }
    // Pinned items already sit in the dock, so Recent shows everything else.
    val recents: List<Item> by derivedStateOf {
        val pins = pinned.toSet()
        items.filter { it.id !in pins && it.id in lastUsed }
            .sortedByDescending { lastUsed[it.id] }
            .take(MAX_RECENT)
    }

    // All Apps shows the latest launches first, pinned or not.
    val recentAll: List<Item> by derivedStateOf {
        items.filter { it.id in lastUsed }.sortedByDescending { lastUsed[it.id] }.take(RECENT_ROW)
    }

    // What the hero and the background follow: a Discover card if one has focus, otherwise the focused item.
    val focusKey: String? by derivedStateOf { focusedCard?.let { "card:" + it.id } ?: focused?.id }

    fun focusItem(item: Item) {
        focused = item
        focusedCard = null
    }

    fun focusCard(card: Card) {
        focusedCard = card
    }

    fun lastUsedAt(id: String): Long? = lastUsed[id]

    suspend fun reload() {
        val list = withContext(Dispatchers.IO) { apps.loadApps() + inputs.loadInputs() }
        loaded = list
        // Pin the usual suspects once, so the dock isn't empty on first run.
        if (!prefs.seeded && list.isNotEmpty()) {
            val present = list.map { it.id }.toSet()
            pinned = pinStore.replace((pinned + SEED_PINS.filter { it in present }).distinct())
            prefs.seeded = true
        }
        if (focused == null) focused = favorites.firstOrNull() ?: recents.firstOrNull() ?: list.firstOrNull()
        loadedOnce = true
    }

    fun invalidateImages() = apps.invalidateImages()

    fun recordChompScore(score: Int) {
        if (score > chompHigh) {
            chompHigh = score
            prefs.chompHigh = score
        }
    }

    /** Switches the home layout and drops the Discover cards, so only the chosen style's data stays in memory. */
    fun chooseHomeStyle(style: HomeStyle) {
        if (style == homeStyle) return
        homeStyle = style
        prefs.homeStyle = style
        if (style != HomeStyle.Classic) {
            cards = emptyList()
            focusedCard = null
        }
        // The icon cache is capped at 16 MB, so it is kept: switching styles then shows icons at once.
        homeTick++
    }

    fun toggleArcadeSound() {
        arcadeSound = !arcadeSound
        prefs.arcadeSound = arcadeSound
    }

    suspend fun image(item: Item): AppImage? =
        withContext(Dispatchers.IO) { runCatching { apps.loadImage(item) }.getOrNull() }

    suspend fun largeIcon(item: Item): ImageBitmap? =
        withContext(Dispatchers.IO) { runCatching { apps.loadLargeIcon(item) }.getOrNull() }

    suspend fun smallIcon(item: Item): ImageBitmap? =
        withContext(Dispatchers.IO) { runCatching { apps.loadSmallIcon(item) }.getOrNull() }

    suspend fun tint(item: Item): Color =
        if (item.target is Target.Input) InputTint else image(item)?.tint ?: DefaultTint

    suspend fun focusTint(): Color {
        focusedCard?.let { return cardImage(it)?.tint ?: DefaultTint }
        return focused?.let { tint(it) } ?: DefaultTint
    }

    suspend fun cardImage(card: Card): RemoteArt? = remoteImages.load(card.imageUrl)

    /** Keeps the previous row if the fetch comes back empty (offline), so Discover never blanks out. */
    suspend fun refreshContent() {
        // Streaming channels are only included for installed apps, so the app list has to exist first.
        if (!loadedOnce) reload()
        val installed = loaded.mapTo(HashSet()) { it.id }
        val fresh = content.load { it in installed }
        if (fresh.isNotEmpty()) cards = fresh
    }

    fun openCard(card: Card) {
        val uri = Uri.parse("https://www.youtube.com/watch?v=${card.id}")
        apps.startFirst(
            Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube.tv"),
            Intent(Intent.ACTION_VIEW, uri),
        )
    }

    fun openCardApp(card: Card) {
        card.appPackage?.let { apps.launchPackage(it) }
    }

    suspend fun refreshWeather() {
        val fresh = withContext(Dispatchers.IO) { weatherRepo.refresh() }
        if (fresh != null) weather = fresh
    }

    /** Loads the full-screen page's data; the compact weather line is refreshed along the way. */
    suspend fun loadWeatherDetail() {
        val fresh = withContext(Dispatchers.IO) { weatherRepo.detail() }
        if (fresh != null) weatherDetail = fresh
    }

    /** Replaces the saved city with the one for this connection's IP, then reloads. */
    suspend fun useCurrentLocation(): Boolean {
        val ok = withContext(Dispatchers.IO) { weatherRepo.locateByIp() }
        if (!ok) return false
        cityName = prefs.city
        weatherDetail = null
        refreshWeather()
        loadWeatherDetail()
        return true
    }

    suspend fun setCity(name: String): Boolean {
        val found = withContext(Dispatchers.IO) { weatherRepo.resolveCity(name) }
        if (!found) return false
        cityName = prefs.city
        weatherDetail = null
        refreshWeather()
        if (showWeather) loadWeatherDetail()
        return true
    }

    /** From `adb shell am start ... --es city <name>`; a fallback for typing on the TV. */
    fun requestCity(name: String) {
        scope.launch { setCity(name) }
    }

    fun launch(item: Item) {
        val ok = when (val t = item.target) {
            is Target.App -> apps.launch(t.component)
            is Target.Input -> inputs.launch(t.inputId)
        }
        if (ok) {
            prefs.markLaunched(item.id)
            launchTick++
            lastUsed = prefs.lastUsed()
        }
        if (showAll) closeAll()
    }

    fun togglePin(id: String) {
        pinned = pinStore.toggle(id)
    }

    fun setCategory(id: String, category: Category) {
        prefs.setOverride(id, category)
        overrides = prefs.overrides()
    }

    fun closeAll() {
        showAll = false
        focusAllApps = true
    }

    fun resetToHome() {
        showAll = false
        showQuick = false
        showWeather = false
        showGame = false
        showStylePicker = false
        showBluetooth = false
        menuFor = null
        homeTick++
    }

    fun openAssistant() {
        apps.startFirst(Intent(Intent.ACTION_ASSIST), Intent(Intent.ACTION_VOICE_COMMAND))
    }

    fun openStore() {
        apps.launchPackage("com.android.vending")
    }

    /** Play Store's search for games; the store on a TV only lists what works with a remote. */
    fun searchGames() {
        val search = "market://search?q=games&c=apps"
        val found = apps.startFirst(
            Intent(Intent.ACTION_VIEW, Uri.parse(search)).setPackage("com.android.vending"),
            Intent(Intent.ACTION_VIEW, Uri.parse(search)),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=games&c=apps")),
        )
        if (!found) openStore()
    }

    fun openSettings() {
        apps.startFirst(Intent(Settings.ACTION_SETTINGS))
    }

    fun openWifi() {
        apps.startFirst(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    }

    // Opens our own list of paired devices; nothing scans until "Pair a new device" is chosen.
    // (Sound and Display are left to the Settings pill: this TCL's SoundActivity crashes on launch.)
    fun openBluetooth() {
        showBluetooth = true
    }

    suspend fun refreshBluetooth() {
        btDevices = withContext(Dispatchers.IO) { bluetooth.bonded() }
    }

    fun connectBluetooth(device: BtDevice) {
        btStatus = "Connecting to ${device.name}…"
        scope.launch {
            if (!bluetooth.connect(device)) {
                btStatus = "Couldn't connect from here. Use the device's own button or TV Settings."
                return@launch
            }
            delay(2_000) // the link comes up shortly after the request
            refreshBluetooth()
            val now = btDevices.firstOrNull { it.address == device.address }
            btStatus = if (now?.connected == true) {
                "Connected to ${device.name}"
            } else {
                "${device.name} isn't connected yet. Check that it is on and nearby."
            }
        }
    }

    /** The only thing that scans, and only when the user asks. */
    fun pairNewDevice() {
        showBluetooth = false
        showQuick = false
        apps.startFirst(
            explicit("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    /** The stock launcher is never disabled; this is the way back to it. */
    fun openStockHome() {
        apps.startFirst(explicit("com.google.android.tvlauncher", "com.google.android.tvlauncher.MainActivity"))
    }

    fun openAppInfo(item: Item) {
        apps.openAppInfo(item.id)
    }

    private fun explicit(pkg: String, cls: String) =
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, cls))

    private companion object {
        const val MAX_RECENT = 8
        const val RECENT_ROW = 5
        val SEED_PINS = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.android.vending",
            "com.android.tv.settings",
            "com.tcl.tv",
        )
    }
}
