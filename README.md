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
- **Notebooks** to keep notes apart. Tap **+ Notebook** to start one. Each
  notebook is just a folder inside `Notes`, so you can also make and fill them
  from a computer, and notebooks can hold notebooks of their own.
- Long-press a note to **move** it to another notebook (or straight into a new
  one) or delete it; long-press a notebook to rename or delete it
- The first line of a note is its title; the list shows the title, a preview, and when you last edited it
- A running word count at the foot of the page
- **Aa** button in the editor: make the text bigger or smaller, pick any
  typeface installed on the Kindle, and choose the line spacing (Compact,
  Normal, Relaxed or Wide). Your choices are remembered.
- Want another font (e.g. one of your favorite book faces)? Copy its `.ttf` or
  `.otf` file into `Notes/Fonts` on the device and it appears in the typeface list.
- Empty notes are discarded automatically
- No internet permission, accounts or tracking

If the shared `Notes` folder can't be written (this can happen on newer
Android versions), the app falls back to its own storage folder. The path in
use is always shown at the bottom of the list.

## Getting the APK

**From GitHub Actions (no tools needed):** every push runs the *Build APK*
workflow. Open the repository's **Actions** tab, choose the latest run, and
download the `PaperNotes-apk` artifact (a zip containing `PaperNotes.apk`).

**Building locally:** install JDK 17 and the Android SDK (platform 34), then run

```sh
gradle assembleRelease   # Gradle 8.9 recommended
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. It is
signed with `app/papernotes.keystore`, a key committed to the repo on purpose
(it isn't secret) so that every build installs over the previous one.

## Sideloading onto the Kindle Fire HDX

1. On the Kindle: **Settings → Security → Apps from Unknown Sources → On**.
2. Copy `PaperNotes.apk` to the Kindle, either over USB (drop it in the
   `Download` folder) or by emailing it to yourself or putting it in cloud storage.
3. Open the APK with the Kindle's **Docs** app or a file manager (e.g. ES File
   Explorer) and tap **Install**.
4. Alternatively, with USB debugging on: `adb install -r PaperNotes.apk`.

"Paper Notes" then appears under **Apps**.

Updating from version 1.0: that first build was signed with a throwaway key,
so uninstall it once before installing 1.1. Your notes live in the `Notes`
folder on device storage and are not affected. From 1.1 on, new builds install
straight over the old one.

## Project layout

```
app/src/main/java/com/papernotes/
  MainActivity.java    a folder's notebooks and notes
  EditorActivity.java  the writing page
  ChoiceDialog.java    the typeface and "Move to" pickers
  NoteStore.java       reading and writing the .txt files
  Fonts.java           finding the typefaces installed on the device
  Prefs.java           remembered typeface, text size and line spacing
app/src/main/res/      layouts, colors and the paper background
```
