package me.thuanc177.kotsune.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.thuanc177.kotsune.libs.anime.AnimeProvider

class Config(private val context: Context, private val noConfig: Boolean = false) {
    companion object {
        private const val TAG = "Config"

        // Constants
        val ASSETS_DIR = "assets"
        val USER_VIDEOS_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).path
        val USER_CONFIG_PATH = "/data/data/me.thuanc177.kotsune/files/config.properties"
        val USER_DATA_PATH = "/data/data/me.thuanc177.kotsune/files/user_data.json"
        val USER_WATCH_HISTORY_PATH = "/data/data/me.thuanc177.kotsune/files/watch_history.json"
    }

    // Properties
    var manga: Boolean = false
    var syncPlay: Boolean = false
    var animeList: List<Any> = listOf()
    var watchHistory: MutableMap<String, Map<String, Any>> = mutableMapOf()
    val fastanimeAnilistAppLoginUrl = "https://anilist.co/api/v2/oauth/authorize?client_id=20148&response_type=token"
    lateinit var animeProvider: AnimeProvider

    var userData: MutableMap<String, Any> = mutableMapOf(
        "recent_anime" to listOf<Any>(),
        "animelist" to listOf<Any>(),
        "user" to mapOf<String, Any>(),
        "meta" to mapOf("last_updated" to 0)
    )

    // Configuration properties
    private lateinit var configparser: Properties

    // Config properties
    var autoNext: Boolean = false
    var autoSelect: Boolean = true
    var cacheRequests: Boolean = true
    var checkForUpdates: Boolean = true
    var continueFromHistory: Boolean = true
    var defaultMediaListTracking: String = "None"
    var disableMpvPopen: Boolean = true
    var discord: Boolean = false
    var downloadsDir: String = USER_VIDEOS_DIR
    var episodeCompleteAt: Int = 80
    var ffmpegthumbnailerSeekTime: Int = -1
    var forceForwardTracking: Boolean = true
    var forceWindow: String = "immediate"
    var format: String = "best[height<=1080]/bestvideo[height<=1080]+bestaudio/best"
    var fzfOpts: String = ""
    var headerColor: String = "95,135,175"
    var headerAsciiArt: String = ""
    var icons: Boolean = false
    var imagePreviews: Boolean = true
    var imageRenderer: String = "chafa"
    var normalizeTitles: Boolean = true
    var notificationDuration: Int = 120
    private var _maxCacheLifetime: String = "03:00:00"
    var maxCacheLifetime: Int = 0
    var mpvArgs: String = ""
    var mpvPreArgs: String = ""
    var perPage: String = "15"
    var player: String = "mpv"
    var preferredHistory: String = "local"
    var preferredLanguage: String = "english"
    var preview: Boolean = false
    var previewHeaderColor: String = "215,0,95"
    var previewSeparatorColor: String = "208,208,208"
    var provider: String = "allanime"
    var quality: String = "1080"
    var recent: Int = 50
    var rofiTheme: String = ""
    var rofiThemePreview: String = ""
    var rofiThemeConfirm: String = ""
    var rofiThemeInput: String = ""
    var server: String = "top"
    var skip: Boolean = false
    var sortBy: String = "search match"
    var menuOrder: String = ""
    var subLang: String = "eng"
    var translationType: String = "sub"
    var useFzf: Boolean = false
    var usePersistentProviderStore: Boolean = false
    var usePythonMpv: Boolean = false
    var useRofi: Boolean = false

    // Default configuration map
    private val defaultConfig = mapOf(
        "auto_next" to "False",
        "menu_order" to "",
        "auto_select" to "True",
        "cache_requests" to "true",
        "check_for_updates" to "True",
        "continue_from_history" to "True",
        "default_media_list_tracking" to "None",
        "downloads_dir" to USER_VIDEOS_DIR,
        "disable_mpv_popen" to "True",
        "discord" to "False",
        "episode_complete_at" to "80",
        "ffmpegthumbnailer_seek_time" to "-1",
        "force_forward_tracking" to "true",
        "force_window" to "immediate",
        "fzf_opts" to "",
        "header_color" to "95,135,175",
        "header_ascii_art" to "",
        "format" to "best[height<=1080]/bestvideo[height<=1080]+bestaudio/best",
        "icons" to "false",
        "image_previews" to "True",
        "image_renderer" to "chafa",
        "normalize_titles" to "True",
        "notification_duration" to "120",
        "max_cache_lifetime" to "03:00:00",
        "mpv_args" to "",
        "mpv_pre_args" to "",
        "per_page" to "15",
        "player" to "mpv",
        "preferred_history" to "local",
        "preferred_language" to "english",
        "preview" to "False",
        "preview_header_color" to "215,0,95",
        "preview_separator_color" to "208,208,208",
        "provider" to "allanime",
        "quality" to "1080",
        "recent" to "50",
        "rofi_theme" to File(ASSETS_DIR, "rofi_theme.rasi").path,
        "rofi_theme_preview" to File(ASSETS_DIR, "rofi_theme_preview.rasi").path,
        "rofi_theme_confirm" to File(ASSETS_DIR, "rofi_theme_confirm.rasi").path,
        "rofi_theme_input" to File(ASSETS_DIR, "rofi_theme_input.rasi").path,
        "server" to "top",
        "skip" to "false",
        "sort_by" to "search match",
        "sub_lang" to "eng",
        "translation_type" to "sub",
        "use_fzf" to "False",
        "use_persistent_provider_store" to "false",
        "use_python_mpv" to "false",
        "use_rofi" to "false"
    )

    init {
        initializeUserDataAndWatchHistory()
        loadConfig(noConfig)
    }

    private fun loadConfig(noConfig: Boolean = false) {
        configparser = Properties()

        // Apply default values
        for ((key, value) in defaultConfig) {
            configparser.setProperty(key, value)
        }

        // Read from file if it exists
        try {
            val configFile = File(USER_CONFIG_PATH)
            if (configFile.exists() && !noConfig) {
                FileReader(configFile).use { reader ->
                    configparser.load(reader)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read config file: ${e.message}")
        }

        // Get configuration values
        autoNext = configparser.getProperty("auto_next", "False").toBoolean()
        autoSelect = configparser.getProperty("auto_select", "True").toBoolean()
        cacheRequests = configparser.getProperty("cache_requests", "true").toBoolean()
        checkForUpdates = configparser.getProperty("check_for_updates", "True").toBoolean()
        continueFromHistory = configparser.getProperty("continue_from_history", "True").toBoolean()
        defaultMediaListTracking = configparser.getProperty("default_media_list_tracking", "None")
        disableMpvPopen = configparser.getProperty("disable_mpv_popen", "True").toBoolean()
        discord = configparser.getProperty("discord", "False").toBoolean()
        downloadsDir = configparser.getProperty("downloads_dir", USER_VIDEOS_DIR)
        episodeCompleteAt = configparser.getProperty("episode_complete_at", "80").toInt()
        ffmpegthumbnailerSeekTime = configparser.getProperty("ffmpegthumbnailer_seek_time", "-1").toInt()
        forceForwardTracking = configparser.getProperty("force_forward_tracking", "true").toBoolean()
        forceWindow = configparser.getProperty("force_window", "immediate")
        format = configparser.getProperty("format", "best[height<=1080]/bestvideo[height<=1080]+bestaudio/best")
        fzfOpts = configparser.getProperty("fzf_opts", "")
        headerColor = configparser.getProperty("header_color", "95,135,175")
        headerAsciiArt = configparser.getProperty("header_ascii_art", "")
        icons = configparser.getProperty("icons", "false").toBoolean()
        imagePreviews = configparser.getProperty("image_previews", "True").toBoolean()
        imageRenderer = configparser.getProperty("image_renderer", "chafa")
        normalizeTitles = configparser.getProperty("normalize_titles", "True").toBoolean()
        notificationDuration = configparser.getProperty("notification_duration", "120").toInt()
        _maxCacheLifetime = configparser.getProperty("max_cache_lifetime", "03:00:00")

        // Parse max cache lifetime
        val maxCacheLifetimeValues = _maxCacheLifetime.split(":").map { it.toInt() }
        maxCacheLifetime = maxCacheLifetimeValues[0] * 86400 +
                maxCacheLifetimeValues[1] * 3600 +
                maxCacheLifetimeValues[2] * 60

        mpvArgs = configparser.getProperty("mpv_args", "")
        mpvPreArgs = configparser.getProperty("mpv_pre_args", "")
        perPage = configparser.getProperty("per_page", "15")
        player = configparser.getProperty("player", "mpv")
        preferredHistory = configparser.getProperty("preferred_history", "local")
        preferredLanguage = configparser.getProperty("preferred_language", "english")
        preview = configparser.getProperty("preview", "False").toBoolean()
        previewHeaderColor = configparser.getProperty("preview_header_color", "215,0,95")
        previewSeparatorColor = configparser.getProperty("preview_separator_color", "208,208,208")
        provider = configparser.getProperty("provider", "allanime")
        quality = configparser.getProperty("quality", "1080")
        recent = configparser.getProperty("recent", "50").toInt()
        rofiThemeConfirm = configparser.getProperty("rofi_theme_confirm", "")
        rofiThemeInput = configparser.getProperty("rofi_theme_input", "")
        rofiTheme = configparser.getProperty("rofi_theme", "")
        rofiThemePreview = configparser.getProperty("rofi_theme_preview", "")
        server = configparser.getProperty("server", "top")
        skip = configparser.getProperty("skip", "false").toBoolean()
        sortBy = configparser.getProperty("sort_by", "search match")
        menuOrder = configparser.getProperty("menu_order", "")
        subLang = configparser.getProperty("sub_lang", "eng")
        translationType = configparser.getProperty("translation_type", "sub")
        useFzf = configparser.getProperty("use_fzf", "False").toBoolean()
        usePythonMpv = configparser.getProperty("use_python_mpv", "false").toBoolean()
        useRofi = configparser.getProperty("use_rofi", "false").toBoolean()
        usePersistentProviderStore = configparser.getProperty("use_persistent_provider_store", "false").toBoolean()

        // Setup user data
        animeList = userData["animelist"] as List<Any>? ?: listOf()

        // Create config file if it doesn't exist
        if (!File(USER_CONFIG_PATH).exists()) {
            FileWriter(USER_CONFIG_PATH).use { writer ->
                configparser.store(writer, "Kotsune Configuration")
            }
        }
    }

    fun setConfigEnvVars() {
        for (key in defaultConfig.keys) {
            if (System.getenv("KOTSUNE_${key.uppercase()}") == null) {
                try {
                    val value = this::class.java.getDeclaredField(key.replace("_", "")).get(this).toString()
                    System.setProperty("KOTSUNE_${key.uppercase()}", value)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting environment variable: ${e.message}")
                }
            }
        }
    }

    fun updateUser(user: Map<String, Any>) {
        userData["user"] = user
        _updateUserData()
    }

    fun updateRecent(recentAnime: List<Map<String, Any>>) {
        val recentAnimeIds = mutableListOf<Int>()
        val _recentAnime = mutableListOf<Map<String, Any>>()

        for (anime in recentAnime) {
            val animeId = anime["id"] as? Int ?: continue
            if (animeId !in recentAnimeIds && recentAnimeIds.size <= recent) {
                _recentAnime.add(anime)
                recentAnimeIds.add(animeId)
            }
        }

        userData["recent_anime"] = _recentAnime
        _updateUserData()
    }

    fun mediaListTrack(
        animeId: Int,
        episodeNo: String,
        episodeStoppedAt: String = "0",
        episodeTotalLength: String = "0",
        progressTracking: String = "prompt"
    ) {
        watchHistory[animeId.toString()] = mapOf(
            "episode_no" to episodeNo,
            "episode_stopped_at" to episodeStoppedAt,
            "episode_total_length" to episodeTotalLength,
            "progress_tracking" to progressTracking
        )

        File(USER_WATCH_HISTORY_PATH).writeText(Gson().toJson(watchHistory))
    }

    private fun initializeUserDataAndWatchHistory() {
        try {
            val userDataFile = File(USER_DATA_PATH)
            if (userDataFile.exists()) {
                val jsonString = userDataFile.readText()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val loadedUserData: Map<String, Any> = Gson().fromJson(jsonString, type)
                userData.putAll(loadedUserData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user data: ${e.message}")
        }

        try {
            val watchHistoryFile = File(USER_WATCH_HISTORY_PATH)
            if (watchHistoryFile.exists()) {
                val jsonString = watchHistoryFile.readText()
                val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type
                val loadedWatchHistory: Map<String, Map<String, Any>> = Gson().fromJson(jsonString, type)
                watchHistory.putAll(loadedWatchHistory)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading watch history: ${e.message}")
        }
    }

    private fun _updateUserData() {
        try {
            val file = File(USER_DATA_PATH)
            file.parentFile?.mkdirs()
            file.writeText(Gson().toJson(userData))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user data: ${e.message}")
        }
    }

    fun updateConfig(section: String, key: String, value: String) {
        configparser.setProperty("$section.$key", value)
        try {
            FileWriter(USER_CONFIG_PATH).use { writer ->
                configparser.store(writer, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating config: ${e.message}")
        }
        }
    override fun toString(): String {
        val newLine = "\n"
        val tab = "\t"

        // Process header ASCII art and fzf options with indentation
        val processedHeaderArt = headerAsciiArt.split(newLine).joinToString(newLine) { tab + it }
        val processedFzfOpts = fzfOpts.split(newLine).joinToString(newLine) { tab + it }

        val currentConfigState = """
    #
    #    ███████╗░█████╗░░██████╗████████╗░█████╗░███╗░░██╗██╗███╗░░░███╗███████╗  ░█████╗░░█████╗░███╗░░██╗███████╗██╗░██████╗░
    #    ██╔════╝██╔══██╗██╔════╝╚══██╔══╝██╔══██╗████╗░██║██║████╗░████║██╔════╝  ██╔══██╗██╔══██╗████╗░██║██╔════╝██║██╔════╝░
    #    █████╗░░███████║╚█████╗░░░░██║░░░███████║██╔██╗██║██║██╔████╔██║█████╗░░  ██║░░╚═╝██║░░██║██╔██╗██║█████╗░░██║██║░░██╗░
    #    ██╔══╝░░██╔══██║░╚═══██╗░░░██║░░░██╔══██║██║╚████║██║██║╚██╔╝██║██╔══╝░░  ██║░░██╗██║░░██║██║╚████║██╔══╝░░██║██║░░╚██╗
    #    ██║░░░░░██║░░██║██████╔╝░░░██║░░░██║░░██║██║░╚███║██║██║░╚═╝░██║███████╗  ╚█████╔╝╚█████╔╝██║░╚███║██║░░░░░██║╚██████╔╝
    #    ╚═╝░░░░░╚═╝░░╚═╝╚═════╝░░░░╚═╝░░░╚═╝░░╚═╝╚═╝░░╚══╝╚═╝╚═╝░░░░░╚═╝╚══════╝  ░╚════╝░░╚════╝░╚═╝░░╚══╝╚═╝░░░░░╚═╝░╚═════╝░
    #
    [general]
    # Can you rice it?
    # For the preview pane
    preview_separator_color = $previewSeparatorColor
    
    preview_header_color = $previewHeaderColor
    
    # For the header 
    # Be sure to indent
    header_ascii_art = $processedHeaderArt
    
    header_color = $headerColor
    
    # the image renderer to use [icat/chafa]
    image_renderer = $imageRenderer
     
    # To be passed to fzf
    # Be sure to indent
    fzf_opts = $processedFzfOpts
    
    # Whether to show the icons in the TUI [True/False]
    # More like emojis
    icons = $icons
    
    # Whether to normalize provider titles [True/False]
    normalize_titles = $normalizeTitles
    
    # Whether to check for updates every time you run the script [True/False]
    check_for_updates = $checkForUpdates
    
    # Can be [allanime, animepahe, hianime, nyaa, yugen]
    provider = $provider
    
    # Display language [english, romaji]
    preferred_language = $preferredLanguage
    
    # Download directory
    downloads_dir = $downloadsDir
    
    # Whether to show a preview window when using fzf or rofi [True/False]
    preview = $preview 
    
    # Whether to show images in the preview [True/False]
    image_previews = $imagePreviews
    
    # the time to seek when using ffmpegthumbnailer [-1 to 100]
    ffmpegthumbnailer_seek_time = $ffmpegthumbnailerSeekTime
    
    # specify the order of menu items in a comma-separated list.
    menu_order = $menuOrder
    
    # whether to use fzf as the interface for the anilist command and others. [True/False]
    use_fzf = $useFzf 
    
    # whether to use rofi for the UI [True/False]
    use_rofi = $useRofi
    
    # rofi themes to use <path>
    rofi_theme = $rofiTheme
    rofi_theme_preview = $rofiThemePreview
    rofi_theme_input = $rofiThemeInput
    rofi_theme_confirm = $rofiThemeConfirm
    
    # the duration in minutes a notification will stay on the screen.
    notification_duration = $notificationDuration
    
    # used when the provider offers subtitles in different languages.
    sub_lang = $subLang
    
    # what is your default media list tracking [track/disabled/prompt]
    default_media_list_tracking = $defaultMediaListTracking
    
    # whether media list tracking should only be updated when the next episode is greater than the previous.
    force_forward_tracking = $forceForwardTracking
    
    # whether to cache requests [true/false]
    cache_requests = $cacheRequests
    
    # the max lifetime for a cached request <days:hours:minutes>
    max_cache_lifetime = $maxCacheLifetime
    
    # whether to use a persistent store
    use_persistent_provider_store = $usePersistentProviderStore
    
    # number of recent anime to keep [0-50].
    recent = $recent
    
    # enable or disable Discord activity updater.
    discord = $discord
    
    # comma separated list of args that will be passed to mpv
    mpv_args = $mpvArgs
    
    # command line options passed before the mpv command
    mpv_pre_args = $mpvPreArgs
    
    [stream]
    # the quality of the stream [1080,720,480,360]
    quality = $quality
    
    # Auto continue from watch history [True/False]
    continue_from_history = $continueFromHistory
    
    # which history to use [local/remote]
    preferred_history = $preferredHistory
    
    # Preferred language for anime [dub/sub]
    translation_type = $translationType
    
    # what server to use for a particular provider
    server = $server
    
    # Auto select next episode [True/False]
    auto_next = $autoNext
    
    # Auto select the anime provider results with fuzzy find. [True/False]
    auto_select = $autoSelect
    
    # whether to skip the opening and ending theme songs [True/False]
    skip = $skip
    
    # at what percentage progress should the episode be considered as completed [0-100]
    episode_complete_at = $episodeCompleteAt
    
    # whether to use python-mpv [True/False]
    use_python_mpv = $usePythonMpv
    
    # whether to use popen to get the timestamps for continue_from_history
    disable_mpv_popen = $disableMpvPopen
    
    # force mpv window
    force_window = immediate
    
    # the format of downloaded anime and trailer
    format = $format
    
    # set the player to use for streaming [mpv/vlc]
    player = $player
    
    [anilist]
    per_page = $perPage
    
    #
    # HOPE YOU ENJOY KOTSUNE AND BE SURE TO STAR THE PROJECT ON GITHUB
    #
    """
        return currentConfigState
    }
}