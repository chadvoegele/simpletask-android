package nl.mpcjanssen.simpletask

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.*

abstract class ThemedNoActionBarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(TodoApplication.config.activeTheme)
        if (TodoApplication.config.forceEnglish) {
            val conf = resources.configuration
            conf.locale = Locale.ENGLISH
            resources.updateConfiguration(conf, resources.displayMetrics)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        val content = findViewById<ViewGroup>(android.R.id.content)
        val topBar = (content.findViewById<View>(R.id.main_actionbar) as View?)
                ?: (content.findViewById<View>(R.id.toolbar_edit_filter) as View?)
        applySystemBarInsets(content, topBar, applyTopToRoot = topBar == null)
    }
}

abstract class ThemedActionBarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(TodoApplication.config.activeActionBarTheme)
        if (TodoApplication.config.forceEnglish) {
            val conf = resources.configuration
            conf.locale = Locale.ENGLISH
            resources.updateConfiguration(conf, resources.displayMetrics)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applySystemBarInsets(findViewById(android.R.id.content), null, applyTopToRoot = false)
    }
}

abstract class ThemedPreferenceActivity : AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(TodoApplication.config.activeActionBarTheme)
        if (TodoApplication.config.forceEnglish) {
            val conf = resources.configuration
            conf.locale = Locale.ENGLISH
            resources.updateConfiguration(conf, resources.displayMetrics)
        }
        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applySystemBarInsets(findViewById(android.R.id.content), null, applyTopToRoot = false)
    }
}

private fun applySystemBarInsets(root: ViewGroup, topBar: View?, applyTopToRoot: Boolean) {
    val rootPaddingTop = root.paddingTop
    val rootPaddingBottom = root.paddingBottom
    val topBarPaddingTop = topBar?.paddingTop ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val statusBarInset = if (applyTopToRoot) {
            insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        } else {
            0
        }
        val navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.setPadding(
                view.paddingLeft,
                rootPaddingTop + statusBarInset,
                view.paddingRight,
                rootPaddingBottom + navigationBarInset
        )
        insets
    }
    topBar?.let { bar ->
        ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(
                    view.paddingLeft,
                    topBarPaddingTop + statusBarInset,
                    view.paddingRight,
                    view.paddingBottom
            )
            insets
        }
    }
    ViewCompat.requestApplyInsets(root)
}

