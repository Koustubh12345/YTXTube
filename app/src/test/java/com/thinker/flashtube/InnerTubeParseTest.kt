package com.thinker.flashtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test






class InnerTubeParseTest {
    private fun fixture(name: String) = JSONObject(javaClass.classLoader!!.getResource("$name.json")!!.readText())

    @Test fun playlistPage() {
        val page = InnerTube.parseFeed(fixture("playlist"))
        assertTrue("expected ~100 videos, got ${page.videos.size}", page.videos.size >= 90)
        assertNotNull("playlist continuation token", page.next)
        page.videos.forEach { v ->
            assertEquals(11, v.id.length)
            assertTrue("title for ${v.id}", v.title.isNotBlank())
        }
        assertTrue("most items have a duration", page.videos.count { it.durationSec > 0 } >= page.videos.size * 0.9)
        assertTrue(
            "lockup feeds must not keep 336px hqdefault URLs",
            page.videos.none { it.thumbnail.orEmpty().contains("/hqdefault") },
        )
        assertTrue(
            "playlist rows should carry channel avatars",
            page.videos.count { it.avatar != null } >= page.videos.size * 0.9,
        )
        page.videos.filter { "/ytc/" in it.avatar.orEmpty() }.forEach { v ->
            val edge = Regex("=s(\\d+)").find(v.avatar!!)!!.groupValues[1].toInt()
            assertTrue("ytc avatar must not be over-sharpened: ${v.avatar}", edge <= 96)
        }
    }

    @Test fun ultrawideQualityTiers() {
        assertEquals(2160, qualityTier(3840, 1634))
        assertEquals(1080, qualityTier(1920, 818))
        assertEquals(720, qualityTier(1280, 544))
        assertEquals(1080, qualityTier(1920, 1080))
    }

    @Test fun normalizeYtcToGoogleusercontent() {
        val ggpht = "https://yt3.ggpht.com/ytc/AIdro_n1Ribd7LwdP_qKtqWL3ZDfIgv9M1d6g78VwpHGXVR2Ir4=s6"
        val out = normalizeChannelAvatarUrl(ggpht)
        assertTrue(out.contains("googleusercontent.com/ytc/"))
        assertTrue(out.contains("-c-k-c0x00ffffff-no-rj"))
    }

    @Test fun sharpenYtcChannelAvatar() {
        val raw = "https://yt3.ggpht.com/ytc/AIdro_n1Ribd7LwdP_qKtqWL3ZDfIgv9M1d6g78VwpHGXVR2Ir4=s6"
        val out = sharpenChannelAvatarUrl(raw, 176)
        assertTrue(out.contains("=s96"))
        assertTrue(!out.contains("=s176"))
        val broken = raw.replace("=s6", "=s176")
        assertTrue(sharpenChannelAvatarUrl(broken, 48).contains("=s96"))
    }

    @Test fun searchResults() {
        val page = InnerTube.parseFeed(fixture("search"))
        assertTrue("got ${page.videos.size}", page.videos.size >= 10)
        assertNotNull("search continuation", page.next)
        assertTrue("channel names", page.videos.count { it.channel.isNotBlank() } >= page.videos.size * 0.8)
        assertTrue("view counts", page.videos.count { it.views > 0 } >= page.videos.size * 0.8)
        assertTrue(
            "high-res thumbnails from InnerTube, not hqdefault placeholders",
            page.videos.count { !it.thumbnail.orEmpty().endsWith("/hqdefault.jpg") } >= page.videos.size * 0.8,
        )
    }

    @Test fun watchNextRelated() {
        val page = InnerTube.parseFeed(fixture("next"))
        assertTrue("got ${page.videos.size}", page.videos.size >= 10)
        assertTrue("channel names", page.videos.count { it.channel.isNotBlank() } >= page.videos.size * 0.8)

        assertTrue("fundraiser video kept", page.videos.any { it.id == "-7Gl-yKF6Y4" })
    }


    @Test fun channelVideosDropMembersOnly() {
        val page = InnerTube.parseFeed(fixture("channel_videos"))
        assertTrue("got ${page.videos.size}", page.videos.size >= 10)
        assertTrue("members-only leaked: ${page.videos.filter { "Insider Edition" in it.title }.map { it.title }}",
            page.videos.none { "Insider Edition" in it.title })
        assertNotNull("channel continuation", page.next)
    }

    



    @Test fun moviesDropped() {
        val page = InnerTube.parseFeed(fixture("movies"))
        assertTrue("movies leaked: ${page.videos.filter { "HBO Max" in it.channel }.map { it.title }}",
            page.videos.none { "HBO Max" in it.channel })
        assertEquals("ordinary videos kept", 4, page.videos.size)
    }

    @Test fun viewCounts() {
        assertEquals(1_234_567L, InnerTube.parseViews("1,234,567 views"))
        assertEquals(1_200_000L, InnerTube.parseViews("1.2M views"))
        assertEquals(36_000L, InnerTube.parseViews("36K views"))
        assertEquals(4_850_000L, InnerTube.parseViews("4.85M subscribers"))
        assertEquals(1_100L, InnerTube.parseViews("1.1K videos"))
        assertEquals(0L, InnerTube.parseViews("No views"))
        assertEquals(-1L, InnerTube.parseViews(null))
    }

    @Test fun channelHeaderFromVideosTab() {
        val meta = InnerTube.parseChannelHeader(fixture("channel_videos"))
        assertEquals("lessbitter", meta?.handle)
        assertEquals(21_000L, meta?.subscribers)
        assertEquals(27L, meta?.videoCount)
        assertEquals(false, meta?.verified)
    }

    @Test fun channelVerifiedFromHeaderBadge() {
        val header = org.json.JSONObject(
            """{"pageHeaderRenderer":{"content":{"pageHeaderViewModel":{"title":{"dynamicTextViewModel":{}}}}},
               "c4TabbedHeaderRenderer":{"badges":[{"metadataBadgeRenderer":{"style":"BADGE_STYLE_TYPE_VERIFIED"}}]}}""",
        )
        assertTrue(InnerTube.parseChannelVerified(header))
    }

    @Test fun channelVerifiedFromTitleAccessibilityLabel() {
        val header = JSONObject(
            """{"pageHeaderRenderer":{"content":{"pageHeaderViewModel":{"title":{"dynamicTextViewModel":{
                "rendererContext":{"accessibilityContext":{"label":"MrBeast, Verified"}}
            }}}}}}}""",
        )
        assertTrue(InnerTube.parseChannelVerified(header))
        val meta = InnerTube.parseChannelHeader(
            JSONObject().put("header", header).put("responseContext", JSONObject()),
        )
        assertEquals(true, meta?.verified)
    }
}
