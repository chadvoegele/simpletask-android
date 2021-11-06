package nl.mpcjanssen.simpletask.remote

import android.Manifest
import android.content.pm.PackageManager
import android.os.*
import androidx.core.content.ContextCompat
import android.util.Log
import androidx.annotation.RequiresApi
import nl.mpcjanssen.simpletask.R
import nl.mpcjanssen.simpletask.TodoApplication
import nl.mpcjanssen.simpletask.util.join
import nl.mpcjanssen.simpletask.util.writeToFile
import other.de.stanetz.jpencconverter.JavaPasswordbasedCryption
import other.de.stanetz.jpencconverter.JavaPasswordbasedCryption.EncryptionFailedException
import java.io.File
import java.io.FilenameFilter
import java.security.SecureRandom
import java.util.*
import kotlin.reflect.KClass

object FileStore : IFileStore {
    private var lastSeenRemoteId by TodoApplication.config.StringOrNullPreference(R.string.file_current_version_id)


    override val isOnline = true
    private const val TAG = "FileStore"
    private var observer: TodoObserver? = null

    init {
        Log.i(TAG, "onCreate")
        Log.i(TAG, "Default path: ${getDefaultFile().path}")
        observer = null
    }

    override val isAuthenticated: Boolean
        get() {
            val permissionCheck = ContextCompat.checkSelfPermission(TodoApplication.app,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return permissionCheck == PackageManager.PERMISSION_GRANTED
        }

    override fun loadTasksFromFile(file: File): List<String> {
        Log.i(TAG, "Loading tasks")
        val lines = file.readLines()
        Log.i(TAG, "Read ${lines.size} lines from $file")
        setWatching(file)
        lastSeenRemoteId = file.lastModified().toString()
        return lines
    }

    override fun needSync(file: File): Boolean {
        return lastSeenRemoteId != file.lastModified().toString()
    }

    override fun todoNameChanged() {
        lastSeenRemoteId = ""
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun writeFile(file: File, contents: String) {
        writeEncryptedToFile(file, contents)
    }

    override fun readFile(file: File, fileRead: (contents: String) -> Unit) {
        Log.i(TAG, "Reading file: ${file.path}")
        val contents: String
        val lines = file.readLines() // TODO decrypt if needed
        contents = join(lines, "\n")
        fileRead(contents)
    }

    override fun loginActivity(): KClass<*>? {
        return LoginScreen::class
    }

    private fun setWatching(file: File) {
        Log.i(TAG, "Observer: adding folder watcher on ${file.parent}")
        val obs = observer
        if (obs != null && file.canonicalPath == obs.fileName) {
            Log.w(TAG, "Observer: already watching: ${file.canonicalPath}")
            return
        } else if (obs != null) {
            Log.w(TAG, "Observer: already watching different path: ${obs.fileName}")
            obs.ignoreEvents(true)
            obs.stopWatching()
            observer = null
        }
        observer = TodoObserver(file)
        Log.i(TAG, "Observer: modifying done")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun saveTasksToFile(file: File, lines: List<String>, eol: String) : File {
        Log.i(TAG, "Saving tasks to file: ${file.path}")
        val obs = observer
        obs?.ignoreEvents(true)
        writeEncryptedToFile(file, lines, eol)
        obs?.delayedStartListen(1000)
        lastSeenRemoteId = file.lastModified().toString()
        return file
    }

    private fun isEncrypted(file: File): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && file.name.endsWith(
            JavaPasswordbasedCryption.DEFAULT_ENCRYPTION_EXTENSION
        )
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private fun getPasswordWithWarning(): CharArray? {
//        val pw: CharArray = AppSettings(context).getDefaultPassword()
        val pw: CharArray = "TODO".toCharArray()
        if (pw.isEmpty()) {
            val warningText = "No password!"
//            Toast.makeText(context, warningText, Toast.LENGTH_LONG).show() // TODO
            Log.w(TAG, warningText)
            return null
        }
        return pw
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun writeEncryptedToFile(file: File, lines: List<String>, eol: String) {
        val content = lines.joinToString(eol)
        writeEncryptedToFile(file, content)
    }
    @RequiresApi(Build.VERSION_CODES.M)
    fun writeEncryptedToFile(file: File, content: String) {
        Log.i(TAG, "Writing file to  ${file.canonicalPath}")
        try {
            val pw = getPasswordWithWarning()
            val contentAsBytes: ByteArray = if (isEncrypted(file) && pw != null) {
                JavaPasswordbasedCryption(
                    Build.VERSION.SDK_INT,
                    SecureRandom()
                ).encrypt(content, pw)
            } else {
                content.toByteArray()
            }
            writeToFile(contentAsBytes, file)
        } catch (e: EncryptionFailedException) {
            Log.w(TAG, "Failed to encrypt! $e")
        }
    }

    override fun appendTaskToFile(file: File, lines: List<String>, eol: String) {
        Log.i(TAG, "Appending ${lines.size} tasks to ${file.path}")
        writeToFile(lines, eol, file, true)
    }

    override fun logout() {

    }

    override fun getDefaultFile(): File {
        return File(TodoApplication.app.getExternalFilesDir(null), "todo.txt")
    }

    override fun loadFileList(file: File, txtOnly: Boolean): List<FileEntry> {
        val result = ArrayList<FileEntry>()
        if (file.canonicalPath == "/") {
            TodoApplication.app.getExternalFilesDir(null)?.let {
                result.add(FileEntry(it, true))
            }
        }

        val filter = FilenameFilter { dir, filename ->
            val sel = File(dir,filename)
            if (!sel.canRead())
                false
            else {
                if (sel.isDirectory) {
                    result.add(FileEntry(File(filename), true))
                } else {
                    !txtOnly || filename.toLowerCase(Locale.getDefault()).endsWith(".txt")
                    result.add(FileEntry(File(filename), false))
                }
            }
        }
        // Run the file applyFilter for side effects
        file.list(filter)
        return result
    }

    class TodoObserver(val file: File) : FileObserver(file.canonicalPath) {
        private val tag = "FileWatchService"
        val fileName : String = file.canonicalPath
        private var ignoreEvents: Boolean = false
        private val handler: Handler

        private val delayedEnable = Runnable {
            Log.i(tag, "Observer: Delayed enabling events for: $fileName ")
            ignoreEvents(false)
        }

        init {
            this.startWatching()
            Log.i(tag, "Observer: creating observer on: $fileName")
            this.ignoreEvents = false
            this.handler = Handler(Looper.getMainLooper())

        }

        fun ignoreEvents(ignore: Boolean) {
            Log.i(tag, "Observer: observing events on $fileName? ignoreEvents: $ignore")
            this.ignoreEvents = ignore
        }

        override fun onEvent(event: Int, eventPath: String?) {
            if (eventPath != null && eventPath == fileName) {
                Log.d(tag, "Observer event: $fileName:$event")
                if (event == CLOSE_WRITE ||
                        event == MODIFY ||
                        event == MOVED_TO) {
                    if (ignoreEvents) {
                        Log.i(tag, "Observer: ignored event on: $fileName")
                    } else {
                        Log.i(tag, "File changed {}$fileName")
                        FileStore.remoteTodoFileChanged()
                    }
                }
            }

        }

        fun delayedStartListen(ms: Int) {
            // Cancel any running timers
            handler.removeCallbacks(delayedEnable)
            // Reschedule
            Log.i(tag, "Observer: Adding delayed enabling to todoQueue")
            handler.postDelayed(delayedEnable, ms.toLong())
        }

    }
}
