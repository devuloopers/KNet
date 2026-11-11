package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Settings contain saved-policy controls only; automatic compatibility has been retired. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class ProtectedTrafficWorkspaceTest {
    @Test fun `normal and compact settings expose content without automatic compatibility controls`()=EventQueue.invokeAndWait {
        listOf(1280 to 1f, 976 to 1.25f).forEach { (width, fontScale) ->
            ImageComposeScene(width,560,Density(1f,fontScale),content={
                KNetTheme { ProtectedTrafficWorkspace { Text("Policy controls") } }
            }).use { scene ->
                repeat(3) { scene.render((it+1)*1_000_000_000L).close() }
                val nodes=scene.semanticsOwners.flatMap { owner -> descendants(owner.rootSemanticsNode) }
                assertTrue(nodes.any {it.text()=="Protected Traffic"})
                assertTrue(nodes.any {it.text()=="Policy controls"})
                assertTrue(nodes.any {it.text().contains("View connection decisions in Traffic")})
                assertFalse(nodes.any {it.text().contains("Smart") || it.text() in listOf("Observed connections","Overview","Advanced","Decision details")})
                assertFalse(nodes.any {it.config.getOrNull(SemanticsProperties.ToggleableState)!=null})
                val title=nodes.first {it.text()=="Protected Traffic"}.boundsInRoot
                assertTrue(title.left>=0 && title.right<=width && title.top>=0)
                val directory=File("build/reports/protected-traffic-ui").apply {mkdirs()}
                scene.render(4_000_000_000L).use { picture -> picture.encodeToData()?.use {data ->
                    File(directory,"settings-shell-$width.png").writeBytes(data.bytes)
                } }
            }
        }
    }

    private fun descendants(node:SemanticsNode):List<SemanticsNode> = listOf(node)+node.children.flatMap(::descendants)
    private fun SemanticsNode.text()=config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" "){it.text}
}
