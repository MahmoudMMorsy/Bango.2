package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.MainViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChromeBrowserFeaturesTest {

    @Test
    fun `test bookmarks adding and removing in MainViewModel`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(application)

        // Clear existing bookmarks to start clean
        viewModel.bookmarks.clear()

        // Verify bookmark addition
        viewModel.addBookmark("My Custom Page", "https://example.com/custom")
        assertEquals(1, viewModel.bookmarks.size)
        assertEquals("My Custom Page", viewModel.bookmarks[0].first)
        assertEquals("https://example.com/custom", viewModel.bookmarks[0].second)

        // Verify duplicate prevention
        viewModel.addBookmark("My Custom Page Duplicate", "https://example.com/custom")
        assertEquals(1, viewModel.bookmarks.size)
        assertEquals("My Custom Page Duplicate", viewModel.bookmarks[0].first)

        // Verify bookmark removal
        viewModel.removeBookmark("https://example.com/custom")
        assertEquals(0, viewModel.bookmarks.size)
    }

    @Test
    fun `test custom shortcuts adding and removing in MainViewModel`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(application)

        // Clear existing shortcuts to start clean
        viewModel.customShortcuts.clear()

        // Verify shortcut addition
        viewModel.addCustomShortcut("New Shortcut", "https://news.com", "📰")
        assertEquals(1, viewModel.customShortcuts.size)
        assertEquals("New Shortcut", viewModel.customShortcuts[0].first)
        assertEquals("https://news.com", viewModel.customShortcuts[0].second)
        assertEquals("📰", viewModel.customShortcuts[0].third)

        // Verify shortcut removal
        viewModel.removeCustomShortcut("https://news.com")
        assertEquals(0, viewModel.customShortcuts.size)
    }

    @Test
    fun `test Egyptian proxy index bounds and rotating`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(application)

        // Select Egypt VPN
        viewModel.selectedVpnServerId = "vpn_eg"
        val initialIndex = viewModel.activeEgyptProxyIndex

        // Rotate
        viewModel.rotateEgyptProxy()
        assertEquals((initialIndex + 1) % viewModel.egyptianProxies.size, viewModel.activeEgyptProxyIndex)
    }
}
