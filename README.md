# Kotsune 🦊

<div align="center">

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Platform](https://img.shields.io/badge/platform-Android-brightgreen)
![Language](https://img.shields.io/badge/language-Kotlin-orange)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-purple)

**A modern anime and manga tracking & consumption application for Android**

</div>

## ✨ Description

Kotsune is an elegant, feature-rich anime and manga discovery, tracking, and consumption app built with modern Android technologies like Kotlin and Jetpack Compose. Explore trending and popular series, track your progress, watch anime via AllAnime, read manga via MangaDex, and sync with your AniList account - all within a beautiful, native interface.

## 🎯 Features

- ✅ **Anime & Manga Discovery**: Browse trending, popular, and recently updated lists.
- ✅ **Detailed Views**: Access comprehensive information about anime and manga series, including descriptions, characters, trailers, related media, and statistics.
- ✅ **Anime Streaming**: Watch anime episodes directly using sources from AllAnime.
- ✅ **Manga Reading**: Read manga chapters using sources from MangaDex.
- ✅ **Search**: Find anime (via AniList) and manga (via MangaDex) with advanced filtering options.
- ✅ **AniList Integration**:
    - 🔄 Sync your anime and manga progress (Add to List, Mark as Favorite).
    - 📈 View status and score distributions.
- ✅ **Modern UI**: Built entirely with Jetpack Compose for a smooth and responsive experience.
- ✅ **Dark Mode**: Enjoy a comfortable viewing experience at night.

## 📱 Installation

1.  Go to the [Releases](https://github.com/YumeSoft/Kotsune/releases) tab on GitHub.
2.  Download the latest APK file.
3.  Install on your Android device (ensure installation from unknown sources is enabled in settings).

## 🖼️ Screenshots

*(Screenshots coming soon - Contributions welcome!)*

<details>
<summary>Click to expand</summary>

| Home Screen (Anime) | Anime Details | Manga Details | Search |
| :-----------------: | :-----------: | :-----------: | :----: |
| *Placeholder*       | *Placeholder* | *Placeholder* | *Placeholder* |
| **Watch Anime**     | **Read Manga**|               |        |
| *Placeholder*       | *Placeholder* |               |        |

</details>

## 📝 Project Status & To-dos

The project is currently in active development. Core features like browsing, searching, viewing details, streaming anime (AllAnime), and reading manga (MangaDex) are implemented.

**Key Remaining Tasks:**

-   [ ] **Tracking Screen**: Implement the dedicated screen for managing user lists (Watching, Reading, Planned, etc.).
-   [ ] **Full AniList Sync**: Expand AniList integration (e.g., updating episode/chapter progress, managing custom lists).
-   [ ] **Settings Screen**: Add user-configurable options (theme, default providers, etc.).
-   [ ] **Offline Support**: Implement caching for metadata and potentially downloaded chapters/episodes.
-   [ ] **UI/UX Refinements**: Continuously improve the user interface and experience based on feedback.
-   [ ] **Testing**: Add comprehensive unit and integration tests.

## 🛠️ Technologies Used

-   **Language**: Kotlin (including Coroutines & Flow)
-   **UI Framework**: Jetpack Compose
-   **Architecture**: MVVM (ViewModel)
-   **Networking**: Retrofit (REST), Ktor-Client (GraphQL - AniList), OkHttp
-   **Image Loading**: Coil
-   **Dependency Injection**: Hilt (TBD - *Consider adding Hilt*)
-   **Video Playback**: Media3 ExoPlayer

## 🌐 API Sources

-   **Anime/Manga Metadata & Tracking**: [AniList API (GraphQL)](https://anilist.gitbook.io/anilist-apiv2-docs)
-   **Anime Streaming**: AllAnime (via web scraping/internal API)
-   **Manga Reading**: [MangaDex API](https://api.mangadex.org/docs/)

## 🤝 Contributing

Contributions are highly welcome! If you have suggestions, find bugs, or want to add features:

1.  Fork the repository.
2.  Create a new branch (`git checkout -b feature/YourFeature` or `bugfix/YourBugfix`).
3.  Make your changes.
4.  Commit your changes (`git commit -m 'Add some feature'`).
5.  Push to the branch (`git push origin feature/YourFeature`).
6.  Open a Pull Request.

Please ensure your code follows the project's style guidelines and includes relevant tests if applicable.

## 📝 License

This project is licensed under the **GNU General Public License v3.0** - see the [LICENSE](LICENSE) file for details.

## 💖 About

Made with ❤️ by YumeLabs

---

<div align="center">
  <img src="https://img.shields.io/badge/Made%20with-Kotlin-orange?style=for-the-badge&logo=kotlin" alt="Made with Kotlin">
</div>
