# Melodia – odtwarzacz muzyki na Androida

Natywna aplikacja: Kotlin + Jetpack Compose (Material 3) + Media3/ExoPlayer.
Android 8.0+ (minSdk 26), target Android 15.

## Funkcje

**Biblioteka**
- automatyczne skanowanie muzyki w telefonie (MediaStore), odświeża się sama po dodaniu plików
- zakładki: Utwory, Albumy (siatka z okładkami), Wykonawcy, Foldery
- sortowanie utworów: tytuł / wykonawca / album / ostatnio dodane / długość (polska kolejność alfabetyczna)
- wyszukiwarka utworów, albumów i wykonawców, działa bez polskich znaków („zolw” znajdzie „Żółw”)
- „Otwórz za pomocą…” – odtwarzanie pojedynczego pliku z menedżera plików

**Odtwarzanie**
- odtwarzanie w tle, powiadomienie z kontrolkami, sterowanie z ekranu blokady, słuchawek i Bluetooth
- pauza po wyjęciu słuchawek, obsługa fokusu audio (np. połączenia)
- losowo, powtarzanie (wszystko / jeden), poprzedni / następny, przewijanie
- przesuwanie okładki w lewo/prawo zmienia utwór
- kolejka: podgląd w kolejności odtwarzania, skok do utworu, usuwanie, czyszczenie
- „Odtwórz jako następny” i „Dodaj do kolejki”
- prędkość odtwarzania 0,5×–2×
- wyłącznik czasowy (5–90 min, z płynnym wyciszeniem) lub „po zakończeniu utworu”
- zapamiętanie kolejki i pozycji po zamknięciu aplikacji + wznawianie z systemowego panelu multimediów

**Equalizer**
- wielopasmowy equalizer (liczba pasm zależy od telefonu, zwykle 5) z pionowymi suwakami
- presety systemowe (Rock, Pop, Jazz, Classical…)
- wzmocnienie basu, dźwięk przestrzenny, wzmocnienie głośności
- ustawienia zapisują się między uruchomieniami
- zewnętrzne aplikacje equalizera (np. Wavelet) też zadziałają – sesja audio jest rozgłaszana

**Playlisty**
- tworzenie, zmiana nazwy, usuwanie
- dodawanie z menu utworu, usuwanie, przesuwanie w górę/dół
- listy automatyczne: Ulubione (serduszko), Ostatnio dodane, Najczęściej odtwarzane, Ostatnio odtwarzane

**Wygląd**
- Material You – kolory z tapety na Androidzie 12+
- tryb jasny i ciemny wg ustawień systemu
- ekran „Teraz odtwarzane” z rozmytą okładką w tle

## Jak zbudować

### Opcja A – Android Studio (najprościej)
1. Zainstaluj Android Studio (Ladybug lub nowsze).
2. File → Open → wskaż folder `Melodia`.
3. Poczekaj na synchronizację Gradle (pierwszy raz kilka minut).
4. Podłącz telefon z włączonym debugowaniem USB → zielony ▶ Run.
   Albo: Build → Build APK(s) → plik w `app/build/outputs/apk/debug/`.

Jeśli Studio zaproponuje aktualizację wersji AGP/Kotlina/bibliotek, możesz się zgodzić.

### Opcja B – GitHub Actions (bez instalowania niczego)
1. Wrzuć folder do nowego repozytorium na GitHubie.
2. Zakładka **Actions** → workflow „Zbuduj APK” uruchomi się sam (albo „Run workflow”).
3. Po ~5 minutach pobierz artefakt **Melodia-APK** i zainstaluj APK na telefonie.

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
├── playback/
│   ├── PlaybackService.kt   – MediaSessionService + ExoPlayer
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
