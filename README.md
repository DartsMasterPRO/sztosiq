# SztosiQ – odtwarzacz muzyki na Androida

Natywna aplikacja: Kotlin + Jetpack Compose (Material 3) + Media3/ExoPlayer.
Android 8.0+ (minSdk 26), target Android 15.

## Funkcje

**Biblioteka**
- utwory, albumy, wykonawcy, **gatunki**, foldery – skanowane automatycznie
- sortowanie, wyszukiwarka bez polskich znaków, „Otwórz za pomocą…”
- listy automatyczne: Ulubione, Ostatnio dodane, Najczęściej / Ostatnio odtwarzane, Jeszcze nie słuchane

**Odtwarzanie**
- w tle, powiadomienie, ekran blokady, Bluetooth, pauza po wyjęciu słuchawek, audio focus
- **Android Auto** (przeglądanie biblioteki w aucie)
- **widżet** na ekran główny
- losowo / powtarzanie, kolejka (zmiana kolejności, usuwanie), zapis kolejki
- **regulacja głośności** na ekranie odtwarzania
- prędkość 0,5–2×, wyłącznik czasowy, **płynne przejścia między utworami**
- **informacja o formacie** (FLAC / MP3 / AAC / OGG / Opus / WAV / ALAC, kHz, bit, kbps) + znaczek **Hi-Res**

**Dźwięk**
- equalizer 5–10 pasm (zależnie od telefonu), 14 presetów (Rock, Pop, Jazz, Klasyczna, Elektroniczna, Hip-Hop…)
- **własne presety** – zapisywanie i usuwanie
- basy, **soprany**, dźwięk przestrzenny, wzmocnienie głośności
- **balans L/P**, **ReplayGain** (utwór / album)

**Filmy**
- zakładka Filmy: miniaturki, czas, jakość (720p/1080p/4K), rozmiar, filtrowanie po folderach, sortowanie
- zapamiętywanie miejsca w filmie (pasek postępu na miniaturce, wznowienie od tego miejsca)
- pełnoekranowy odtwarzacz: automatyczny obrót, przycisk obrotu, blokada ekranu
- gesty: lewa strona w górę/dół = jasność, prawa = głośność, poziomo = przewijanie, podwójne stuknięcie = ±10 s
- dopasowanie obrazu (dopasuj / wypełnij / rozciągnij), prędkość, następny / poprzedni film z folderu
- napisy .srt / .vtt (automatycznie z tą samą nazwą obok filmu lub wczytane ręcznie)
- obraz w obrazie (PiP) – też automatycznie po wyjściu z aplikacji
- dźwięk filmu w tle (np. koncerty), sterowanie z powiadomienia
- equalizer, balans i soprany działają też na filmy
- otwieranie filmów z galerii i menedżerów plików („Otwórz za pomocą” → SztosiQ → „Zawsze”)

**Teksty piosenek**
- pliki **LRC** zsynchronizowane z muzyką – automatyczne przewijanie, kliknięcie linii przewija utwór
- wczytywanie pliku LRC lub wklejanie tekstu; plik .lrc obok utworu jest wykrywany automatycznie (gdy Android pozwala)

**Wizualizacje**
- spectrum analyzer (słupki) lub fala dźwiękowa
- okładka i animowane tło pulsują z basem

**Personalizacja**
- tryb jasny / ciemny / systemowy, czarne tło AMOLED
- kolory: z tapety (Android 12+), **z okładki albumu** lub 10 gotowych motywów
- ekran odtwarzania: klasyczny / **winyl** (obracająca się płyta) / pełny ekran
- rozmiar okładki, animowane tło, styl list (z okładkami / kompaktowe)

**Playlisty**
- tworzenie, edycja, zmiana kolejności, **import / eksport M3U**

**Statystyki**
- łączny i miesięczny czas słuchania, top wykonawcy i utwory, podsumowania miesięczne, historia

### Uwagi
- **Domyślny odtwarzacz filmów:** otwórz film w galerii lub menedżerze plików → wybierz SztosiQ → „Zawsze”. Jeśli wcześniej wybrano inny odtwarzacz: Ustawienia → Aplikacje → (tamten odtwarzacz) → Otwieraj domyślnie → Wyczyść ustawienia domyślne. Google Zdjęcia i niektóre galerie odtwarzają filmy wbudowanym odtwarzaczem i nie pytają o inną aplikację.
- **Android Auto** z aplikacją spoza Sklepu Play: w Android Auto → Ustawienia → stuknij 10× „Wersja” → menu (⋮) → Ustawienia programisty → włącz „Nieznane źródła”.
- **Wizualizacja** wymaga zgody „Mikrofon” – tak Android nazywa dostęp do analizy dźwięku, nic nie jest nagrywane.
- **Chromecast** nie jest obsługiwany (wymaga Google Cast SDK i serwera plików w telefonie).

## Jak zbudować

### Opcja A – Android Studio (najprościej)
1. Zainstaluj Android Studio (Ladybug lub nowsze).
2. File → Open → wskaż folder `SztosiQ`.
3. Poczekaj na synchronizację Gradle (pierwszy raz kilka minut).
4. Podłącz telefon z włączonym debugowaniem USB → zielony ▶ Run.
   Albo: Build → Build APK(s) → plik w `app/build/outputs/apk/debug/`.

Jeśli Studio zaproponuje aktualizację wersji AGP/Kotlina/bibliotek, możesz się zgodzić.

### Opcja B – GitHub Actions (bez instalowania niczego)
1. Wrzuć folder do nowego repozytorium na GitHubie.
2. Zakładka **Actions** → workflow „Zbuduj APK” uruchomi się sam (albo „Run workflow”).
3. Po ~5 minutach pobierz artefakt **SztosiQ-APK** i zainstaluj APK na telefonie.

Wersja release jest podpisana kluczem debug, więc instaluje się od razu. Do Google Play podmień podpis na własny.

## Struktura

```
app/src/main/java/com/melodia/player/
├── MainActivity.kt          – wejście do aplikacji, obsługa „Otwórz za pomocą”
├── MelodiaApp.kt            – inicjalizacja repozytoriów
├── MusicViewModel.kt        – stan UI, połączenie z serwisem (MediaController)
├── data/
│   ├── Models.kt            – Song/Album/Artist/Folder/Playlist, sortowanie, wyszukiwanie
│   ├── MusicRepository.kt   – skanowanie MediaStore
│   ├── LibraryStore.kt      – playlisty, ulubione, statystyki (JSON)
│   └── Settings.kt          – ustawienia (SharedPreferences)
├── video/                 – odtwarzacz filmów (VideoActivity, gesty, PiP)
├── widget/                – widżet na ekran główny
├── playback/
│   ├── PlaybackService.kt   – MediaLibraryService + ExoPlayer, Android Auto
│   ├── SoundProcessor.kt    – balans, soprany, ReplayGain
│   ├── VisualizerController.kt – FFT do wizualizacji
│   ├── AudioEffects.kt      – Equalizer, BassBoost, Virtualizer, LoudnessEnhancer
│   └── SleepTimer.kt        – wyłącznik czasowy
└── ui/
    ├── MainScreen.kt        – uprawnienia, nawigacja dolna, mini-odtwarzacz
    ├── LibraryScreens.kt    – biblioteka, album, wykonawca, folder, wyszukiwarka
    ├── PlaylistScreens.kt   – playlisty i listy automatyczne
    ├── NowPlayingScreen.kt  – pełny odtwarzacz, kolejka, timer, prędkość
    ├── EqualizerScreen.kt   – equalizer i efekty
    ├── Components.kt        – wspólne elementy (wiersz utworu, menu, dialogi)
    └── theme/Theme.kt
```
