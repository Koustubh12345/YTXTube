package com.tensei.ytxtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test


class CommentFeedParseTest {
    private val page = JSONObject(javaClass.classLoader!!.getResource("comments.json")!!.readText())

    @Test fun threadsWithAuthorsAndAvatars() {
        val parsed = CommentFeed.parsePage(page)
        assertEquals(20, parsed.comments.size)
        val first = parsed.comments.first()
        assertEquals("UgxFmxXwREkYrlxxXqN4AaABAg", first.id)
        assertEquals("@elbownio5820", first.author)
        assertEquals("UCAHtFmjxglaQb3TPPanpogw", first.channelId)
        assertTrue(first.avatar!!.startsWith("https://yt3.ggpht.com/"))
        assertTrue(first.text.startsWith("“Well you see thermal conductivity"))
        assertEquals(5400, first.likes)
        assertEquals("6 years ago", first.ago)
        assertEquals(20, first.replyCount)
        assertNotNull(first.repliesToken)
        assertTrue(parsed.comments.all { it.avatar != null && it.author.startsWith("@") })
    }

    @Test fun countAndNextPage() {
        val parsed = CommentFeed.parsePage(page)
        assertEquals(7983, parsed.total)
        assertNotNull(parsed.next)
    }

    @Test fun feedTitleIsTheName() {
        val xml = """<?xml version="1.0"?><feed><id>yt:channel:x</id><title>Tom &amp; Jerry&#39;s</title><author><name>x</name></author>"""
        assertEquals("Tom & Jerry's", ChannelNames.parseTitle(xml))
    }
}
