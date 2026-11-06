package org.pianopath

import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pianopath.core.Hand
import org.pianopath.core.ScaleKind
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class RendererRecoveryTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun rendererGoneCallbackOffersRetryAndRemountsScore() {
        lateinit var vm: PianoViewModel
        compose.runOnUiThread {vm=ViewModelProvider(compose.activity)[PianoViewModel::class.java];vm.scale(0,ScaleKind.MAJOR,1,Hand.RIGHT,true)}
        compose.waitUntil(15_000){vm.ui.value.score!=null}
        compose.runOnUiThread {vm.setView(ScoreView.SHEET)}
        fun find(view: View): WebView? {
            if(view is WebView)return view
            if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let{return it}
            return null
        }
        val prior=AtomicReference<WebView?>()
        compose.waitUntil(15_000){compose.runOnUiThread {prior.set(find(compose.activity.window.decorView))};prior.get()!=null}
        compose.runOnUiThread {
            val view=requireNotNull(prior.get())
            val detail=object: RenderProcessGoneDetail() {
                override fun didCrash()=true
                override fun rendererPriorityAtExit()=0
            }
            assertTrue(view.webViewClient.onRenderProcessGone(view,detail))
        }
        compose.onNodeWithText("Retry score rendering").assertIsDisplayed().performClick()
        val after=AtomicReference<WebView?>()
        compose.waitUntil(15_000){compose.runOnUiThread {after.set(find(compose.activity.window.decorView))};after.get()!=null && after.get()!==prior.get()}
        assertNotNull(vm.ui.value.score)
    }
}
