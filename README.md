# Paper Notes

A small, distraction-free notetaking app for the **Kindle Fire HDX (3rd gen)**
and other Android 4.2+ devices. Pages are cream-colored like a paperback, the
text is set in a serif face with generous book-like margins, and every note
is saved as a plain `.txt` file on the device.

## Features

- Cream "novel page" background with softly shaded edges, serif type throughout
- Notes saved automatically when you leave the editor. No save button to forget.
- Each note is a plain UTF-8 text file in a **`Notes`** folder at the root of
  device storage, so you can copy them to or from a computer over USB
- The first line of a note is its title; the list shows the title, a preview, and when you last edited it
- A running word count at the foot of the page
- Delete from inside a note, or long-press a note in the list
- Empty notes are discarded automatically
- No internet permission, accounts or tracking

If the shared `Notes` folder can't be written (this can happen on newer
Android versions), the app falls back to its own storage folder. The path in
use is always shown at the bottom of the notebook screen.

## Getting the APK

**From GitHub Actions (no tools needed):** every push runs the *Build APK*
workflow. Open the repository's **Actions** tab, choose the latest run, and
download the `PaperNotes-apk` artifact (a zip containing `PaperNotes.apk`).

**Building locally:** install JDK 17 and the Android SDK (platform 34), then run

```sh
gradle assembleRelease   # Gradle 8.9 recommended
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. It is
signed with the Android debug key so it can be sideloaded as is.

## Sideloading onto the Kindle Fire HDX

1. On the Kindle: **Settings → Security → Apps from Unknown Sources → On**.
2. Copy `PaperNotes.apk` to the Kindle, either over USB (drop it in the
   `Download` folder) or by emailing it to yourself or putting it in cloud storage.
3. Open the APK with the Kindle's **Docs** app or a file manager (e.g. ES File
   Explorer) and tap **Install**.
4. Alternatively, with USB debugging on: `adb install PaperNotes.apk`.

"Paper Notes" then appears under **Apps**.

## Project layout

```
app/src/main/java/com/papernotes/
  MainActivity.java    notebook list
  EditorActivity.java  the writing page
  NoteStore.java       reading and writing the .txt files
app/src/main/res/      layouts, colors and the paper background
```
