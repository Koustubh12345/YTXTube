package com.tensei.ytxtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test


class CommentPostingParseTest {
    private val page = JSONObject(javaClass.classLoader!!.getResource("comments.json")!!.readText())

    @Test fun threadsMapToToolbarEntities() {
        val parsed = CommentPosting.parsePage(page)
        assertEquals(20, parsed.threadsWithToolbar.size)

        assertEquals(0, parsed.replyParams.size)
        assertNull(parsed.createComment)
    }

    @Test fun nextPageToken() {
        assertNotNull(CommentPosting.parsePage(page).next)
    }
}
