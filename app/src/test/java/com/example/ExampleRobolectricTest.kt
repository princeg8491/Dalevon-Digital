package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.cache.OrbitMediaFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Orbit", appName)
  }

  @Test
  fun `video upload limit is 500MB`() {
    assertEquals(500L * 1024L * 1024L, OrbitMediaFiles.MAX_VIDEO_UPLOAD_BYTES)
  }

  @Test
  fun `isValidApkDownloadUrl rejects 404 run_app urls and accepts valid https apk urls`() {
    assertFalse(
      OrbitMediaFiles.isValidApkDownloadUrl(
        "https://ais-pre-mbidym5brb7hy6e5yt6osk-661591442621.us-east1.run.app"
      )
    )
    assertFalse(OrbitMediaFiles.isValidApkDownloadUrl(""))
    assertTrue(
      OrbitMediaFiles.isValidApkDownloadUrl("https://files.catbox.moe/orbit923.apk")
    )
  }

  @Test
  fun `buildOpenInviteShareMessage uses direct apk download url without 404 link`() {
    val directUrl = "https://files.catbox.moe/orbit923.apk"
    val message = OrbitMediaFiles.buildOpenInviteShareMessage(
      inviterUsername = "alex",
      versionName = "9.2.3",
      apkDownloadUrl = directUrl
    )
    assertTrue(message.contains(directUrl))
    assertFalse(message.contains(".run.app"))
  }

  @Test
  fun `directMessage reactionSummaries groups emojis and tracks current user reaction`() {
    val msg = com.example.data.model.DirectMessage(
      id = "msg_1",
      senderId = "alice",
      senderUsername = "alice",
      body = "Hey there!",
      reactions = linkedMapOf(
        "bob" to "❤️",
        "carol" to "❤️",
        "alice" to "🔥"
      )
    )
    assertEquals("❤️", msg.myReaction("bob"))
    assertEquals("🔥", msg.myReaction("alice"))
    assertEquals(null, msg.myReaction("dave"))

    val bobSummaries = msg.reactionSummaries("bob")
    assertEquals(2, bobSummaries.size)
    assertEquals("❤️", bobSummaries[0].emoji)
    assertEquals(2, bobSummaries[0].count)
    assertTrue(bobSummaries[0].reactedByMe)
    assertEquals("🔥", bobSummaries[1].emoji)
    assertEquals(1, bobSummaries[1].count)
    assertFalse(bobSummaries[1].reactedByMe)
  }

  @Test
  fun `normalizeTmpfilesDownloadUrl never duplicates dl path segment`() {
    assertEquals(
      "https://tmpfiles.org/dl/12345/Orbit-v23.0.apk",
      OrbitMediaFiles.normalizeTmpfilesDownloadUrl("http://tmpfiles.org/12345/Orbit-v23.0.apk")
    )
    assertEquals(
      "https://tmpfiles.org/dl/12345/Orbit-v23.0.apk",
      OrbitMediaFiles.normalizeTmpfilesDownloadUrl("https://tmpfiles.org/12345/Orbit-v23.0.apk")
    )
    assertEquals(
      "https://tmpfiles.org/dl/12345/Orbit-v23.0.apk",
      OrbitMediaFiles.normalizeTmpfilesDownloadUrl("https://tmpfiles.org/dl/dl/12345/Orbit-v23.0.apk")
    )
    assertEquals(
      "https://tmpfiles.org/dl/12345/Orbit-v23.0.apk",
      OrbitMediaFiles.resolveOpenShareableUrl("http://tmpfiles.org/dl/dl/12345/Orbit-v23.0.apk")
    )
  }

  @Test
  fun `hasApkZipMagicHeader accepts PK0304 and rejects html error pages`() {
    val validApkBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
    val htmlBytes = "<!DOCTYPE html><html><body>404</body></html>".toByteArray(Charsets.UTF_8)
    val shortBytes = byteArrayOf(0x50, 0x4B)

    assertTrue(OrbitMediaFiles.hasApkZipMagicHeader(validApkBytes))
    assertFalse(OrbitMediaFiles.hasApkZipMagicHeader(htmlBytes))
    assertFalse(OrbitMediaFiles.hasApkZipMagicHeader(shortBytes))
  }

  @Test
  fun `isReleaseUrlCurrentOrNewer rejects older versionCode downgrades`() {
    val olderRelease = com.example.data.model.AppRelease(
      versionCode = 1L,
      versionName = "1.0",
      driveDownloadUrl = "https://litterbox.catbox.moe/old.apk"
    )
    val currentRelease = com.example.data.model.AppRelease(
      versionCode = BuildConfig.VERSION_CODE.toLong(),
      versionName = BuildConfig.VERSION_NAME,
      driveDownloadUrl = "https://litterbox.catbox.moe/current.apk"
    )
    assertFalse(OrbitMediaFiles.isReleaseUrlCurrentOrNewer(olderRelease))
    assertTrue(OrbitMediaFiles.isReleaseUrlCurrentOrNewer(currentRelease))
  }

  @Test
  fun `parseOrbitRichText renders mentions grayed out and non clickable`() {
    val parsed = com.example.ui.components.parseOrbitRichText(
      raw = "Hey @jordan_dev check this out!",
      linkColor = androidx.compose.ui.graphics.Color(0xFF0066FF)
    )
    assertEquals("Hey @jordan_dev check this out!", parsed.text)
    val mentionSpan = parsed.spanStyles.firstOrNull { span ->
      parsed.text.substring(span.start, span.end) == "@jordan_dev"
    }
    assertTrue("Expected grayed-out span on @jordan_dev", mentionSpan != null)
    assertEquals(
      com.example.ui.components.OrbitMutedMentionColor,
      mentionSpan!!.item.color
    )
    assertEquals(
      androidx.compose.ui.text.style.TextDecoration.None,
      mentionSpan.item.textDecoration
    )
    assertTrue(
      "Mentions must not carry clickable annotations",
      parsed.getStringAnnotations(0, parsed.length).isEmpty()
    )
  }

  @Test
  fun `buildReplyMentionCandidates prioritizes reply author even when unfollowed`() {
    val targetAuthorPost = com.example.data.model.ThreadPost(
      id = "p1",
      authorId = "u_target",
      authorUsername = "sam_creator",
      authorDisplayName = "Sam Creator",
      body = "Hello Orbit!"
    )
    val followedUser = com.example.data.model.UserProfile(
      id = "u_follow",
      userId = "u_follow",
      username = "alex_friend",
      displayName = "Alex"
    )
    val publicUser = com.example.data.model.UserProfile(
      id = "u_public",
      userId = "u_public",
      username = "casey_public",
      displayName = "Casey"
    )
    val selfUser = com.example.data.model.UserProfile(
      id = "u_me",
      userId = "u_me",
      username = "me_user",
      displayName = "Me"
    )

    val candidates = com.example.ui.components.buildReplyMentionCandidates(
      replyTarget = targetAuthorPost,
      followedProfiles = listOf(followedUser),
      allMentionableProfiles = listOf(followedUser, publicUser, selfUser),
      currentUserId = "u_me"
    )

    assertEquals(listOf("u_target", "u_follow", "u_public"), candidates.map { it.resolvedId })
  }

  @Test
  fun `keyboard gif detection recognizes gif magic bytes and kb_gif filenames`() {
    val gif89a = "GIF89a\u0001\u0000\u0001\u0000".toByteArray(Charsets.US_ASCII)
    val gif87a = "GIF87a\u0001\u0000\u0001\u0000".toByteArray(Charsets.US_ASCII)
    val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)

    assertTrue(OrbitMediaFiles.hasGifMagicHeader(gif89a))
    assertTrue(OrbitMediaFiles.hasGifMagicHeader(gif87a))
    assertFalse(OrbitMediaFiles.hasGifMagicHeader(pngHeader))
    assertTrue(com.example.data.model.isGifUrl("file:///cache/orbit_kb_media/kb_gif_abc123.gif"))
    assertTrue(com.example.data.model.isGifUrl("https://media.tenor.com/abc/wave.gif"))
    assertFalse(com.example.data.model.isGifUrl("file:///cache/photo_123.jpg"))
  }

  @Test
  fun `inspectDirectApkDownloadUrl validates real PK0304 stream and rejects HTML pages`() =
    kotlinx.coroutines.runBlocking {
      val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
      val port = server.address.port
      val fakeApk = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00)
      val fakeHtml = "<!DOCTYPE html><html><body>Expired 404</body></html>".toByteArray(Charsets.UTF_8)

      server.createContext("/orbit-valid.apk") { exchange ->
        exchange.responseHeaders.add("Content-Type", "application/vnd.android.package-archive")
        exchange.responseHeaders.add("Content-Range", "bytes 0-3/${fakeApk.size}")
        exchange.sendResponseHeaders(206, 4)
        exchange.responseBody.use { it.write(fakeApk, 0, 4) }
      }
      server.createContext("/orbit-broken.apk") { exchange ->
        exchange.responseHeaders.add("Content-Type", "text/html")
        exchange.sendResponseHeaders(200, fakeHtml.size.toLong())
        exchange.responseBody.use { it.write(fakeHtml) }
      }
      server.start()
      try {
        val validResult = OrbitMediaFiles.inspectDirectApkDownloadUrl("http://127.0.0.1:$port/orbit-valid.apk")
        assertTrue("Expected valid APK stream result, got: $validResult", validResult.isValid)
        assertEquals(206, validResult.httpStatus)
        assertEquals(fakeApk.size.toLong(), validResult.contentLengthBytes)

        val brokenResult = OrbitMediaFiles.inspectDirectApkDownloadUrl("http://127.0.0.1:$port/orbit-broken.apk")
        assertFalse("Expected HTML page to fail APK link check", brokenResult.isValid)
      } finally {
        server.stop(0)
      }
    }

  @Test
  fun `on-demand 2K image helpers keep feed previews compact and load 2K only on open`() {
    val previewDataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/"
    val hd2kHttpsUrl = "https://files.catbox.moe/sample_2k.jpg"
    val combined = "$previewDataUri${com.example.data.cache.OrbitCacheManager.HD_2K_FRAGMENT_DELIMITER}$hd2kHttpsUrl"

    // Feed thumbnails strip the 2K fragment and decode only the compact preview
    assertEquals(previewDataUri, com.example.data.cache.OrbitCacheManager.stripHd2kFragment(combined))
    assertTrue(com.example.data.cache.OrbitCacheManager.toCoilModel(combined) is ByteArray)

    // Fullscreen lightbox resolves the dedicated 2K URL on demand
    assertEquals(hd2kHttpsUrl, com.example.data.cache.OrbitCacheManager.extractHd2kUrl(combined))
    assertTrue(com.example.data.cache.OrbitCacheManager.hasDedicated2kSource(combined))
    assertEquals(hd2kHttpsUrl, com.example.data.cache.OrbitCacheManager.resolveFullScreen2kModel(combined))

    // Remote CDN URLs upgrade to 2560px only for fullscreen 2K view
    val unsplashThumb = "https://images.unsplash.com/photo-123?w=800&q=75"
    val unsplash2k = com.example.data.cache.OrbitCacheManager.upgradeRemoteUrlTo2k(unsplashThumb)
    assertTrue(unsplash2k.contains("w=2560"))
  }

  @Test
  fun `settings subpages include About Beta and omit Navigation bar`() {
    val names = com.example.ui.screens.SettingsSubPage.entries.map { it.name }
    assertTrue("Expected ABOUT subpage in Settings", "ABOUT" in names)
    assertFalse("Expected NAV_BAR subpage to be removed from Settings", "NAV_BAR" in names)

    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Beta", context.getString(R.string.settings_about_beta_badge))
    assertEquals("Save", context.getString(R.string.lightbox_action_save))
    assertEquals("Share", context.getString(R.string.lightbox_action_share))
    assertEquals("Download", context.getString(R.string.lightbox_action_download))
    assertEquals("Bookmark", context.getString(R.string.lightbox_action_bookmark))
  }

  @Test
  fun `selective app sharing builds personal download invite for chosen recipients`() {
    val msg = OrbitMediaFiles.buildSelectiveInviteShareMessage(
      inviterUsername = "dalevondigital",
      versionName = "26.0",
      recipients = listOf("@dashante", "Alita"),
      apkDownloadUrl = "https://files.catbox.moe/orbit26.apk"
    )
    assertTrue(msg.contains("Private Orbit Invite for @dashante, Alita"))
    assertTrue(msg.contains("(@dalevondigital)"))
    assertTrue(msg.contains("https://files.catbox.moe/orbit26.apk"))
    assertTrue(
      "Expected SHARE_ACCESS subpage in Settings",
      "SHARE_ACCESS" in com.example.ui.screens.SettingsSubPage.entries.map { it.name }
    )
  }

  @Test
  fun `docking station tab reordering serializes and restores custom tab order`() {
    val customOrder = listOf(
      com.example.data.preferences.OrbitDockTab.SPACES,
      com.example.data.preferences.OrbitDockTab.DMS,
      com.example.data.preferences.OrbitDockTab.SAVED_POSTS,
      com.example.data.preferences.OrbitDockTab.PROFILE,
      com.example.data.preferences.OrbitDockTab.THEME_STUDIO
    )
    val serialized = com.example.data.preferences.OrbitDockTab.serialize(customOrder)
    val restored = com.example.data.preferences.OrbitDockTab.parseSerialized(serialized)
    assertEquals(customOrder, restored)
  }

  @Test
  fun `app fonts text size slider scales typography proportionally`() {
    val baseTypo = com.example.ui.theme.buildOrbitTypography(
      preset = com.example.data.preferences.OrbitFontPreset.ORBIT_DEFAULT,
      fontScale = 1.0f
    )
    val largeTypo = com.example.ui.theme.buildOrbitTypography(
      preset = com.example.data.preferences.OrbitFontPreset.ORBIT_DEFAULT,
      fontScale = 1.25f
    )
    assertTrue(largeTypo.bodyLarge.fontSize.value > baseTypo.bodyLarge.fontSize.value)
    assertEquals(baseTypo.bodyLarge.fontSize.value * 1.25f, largeTypo.bodyLarge.fontSize.value, 0.01f)
  }

  @Test
  fun `multi-layer feed cache serves L1 memory hits and hydrates L2 DataStore on cold start`() =
    kotlinx.coroutines.runBlocking {
      val tempDir = java.nio.file.Files.createTempDirectory("orbit_feed_cache_test").toFile()
      val job = kotlinx.coroutines.SupervisorJob()
      val scope = kotlinx.coroutines.CoroutineScope(job + kotlinx.coroutines.Dispatchers.IO)
      try {
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
          scope = scope,
          produceFile = { java.io.File(tempDir, "feed_cache.preferences_pb") }
        )
        val cache = com.example.data.cache.FeedCacheRepository(dataStore)
        val now = 1_760_000_000_000L
        val post1 = com.example.data.model.ThreadPost(
          id = "post_1",
          authorId = "u_alex",
          authorUsername = "alex",
          authorDisplayName = "Alex",
          body = "Offline first feed post #1",
          imageUrls = listOf("https://files.catbox.moe/photo1.jpg"),
          pollOptions = listOf("Yes", "No"),
          pollVotes = mapOf("u_alex" to 0L),
          likeCount = 12L,
          createdAt = com.google.firebase.Timestamp(java.util.Date(now - 5_000L))
        )
        val post2 = com.example.data.model.ThreadPost(
          id = "post_2",
          authorId = "u_sam",
          authorUsername = "sam",
          authorDisplayName = "Sam",
          body = "Second cached post with quote",
          quotedPostId = "post_1",
          quotedAuthorUsername = "alex",
          quotedBody = "Offline first feed post #1",
          createdAt = com.google.firebase.Timestamp(java.util.Date(now - 1_000L))
        )

        cache.saveFeedSnapshot(ownerUid = "user_A", posts = listOf(post1, post2), nowMillis = now)

        // L1 hot memory hit (sorted newest first)
        val l1Snap = cache.loadCachedFeed(ownerUid = "user_A", nowMillis = now + 500L)
        assertTrue("Expected L1 memory snapshot", l1Snap != null)
        assertEquals(com.example.data.cache.FeedDataSource.MEMORY_L1, l1Snap!!.source)
        assertEquals(listOf("post_2", "post_1"), l1Snap.posts.map { it.id })

        // Simulate cold start (process death clears L1 memory only) -> hydrates from L2 DataStore
        cache.clearMemoryLayerOnly()
        assertEquals(null, cache.peekMemoryFeed(ownerUid = "user_A", nowMillis = now + 1_000L))

        val l2Snap = cache.loadCachedFeed(ownerUid = "user_A", nowMillis = now + 1_000L)
        assertTrue("Expected L2 DataStore hydration after cold start", l2Snap != null)
        assertEquals(com.example.data.cache.FeedDataSource.DATASTORE_L2, l2Snap!!.source)
        assertEquals(listOf("post_2", "post_1"), l2Snap.posts.map { it.id })
        assertEquals(listOf("Yes", "No"), l2Snap.posts[1].pollOptions)
        assertEquals(0L, l2Snap.posts[1].pollVotes["u_alex"])
        assertEquals("post_1", l2Snap.posts[0].quotedPostId)

        // Next read after L2 promotion must hit L1 memory again
        val promotedSnap = cache.loadCachedFeed(ownerUid = "user_A", nowMillis = now + 1_500L)
        assertEquals(com.example.data.cache.FeedDataSource.MEMORY_L1, promotedSnap?.source)

        // Account isolation: another user must not read user_A's cache
        assertEquals(null, cache.loadCachedFeed(ownerUid = "user_B", nowMillis = now + 1_500L))

        // Incremental upsert & remove keep L1 and L2 in sync
        cache.upsertPost(
          ownerUid = "user_A",
          post = post1.copy(body = "Edited offline post #1", edited = true),
          nowMillis = now + 2_000L
        )
        cache.removePost(ownerUid = "user_A", postId = "post_2", nowMillis = now + 2_500L)
        cache.clearMemoryLayerOnly()
        val afterCrud = cache.loadCachedFeed(ownerUid = "user_A", nowMillis = now + 3_000L)
        assertEquals(listOf("post_1"), afterCrud?.posts?.map { it.id })
        assertEquals("Edited offline post #1", afterCrud?.posts?.first()?.body)
        assertTrue(afterCrud?.posts?.first()?.edited == true)

        // Hard TTL expiry invalidates stale disk cache
        val expired = cache.loadCachedFeed(
          ownerUid = "user_A",
          nowMillis = now + com.example.data.cache.FeedCacheRepository.HARD_TTL_MS + 60_000L
        )
        assertEquals(null, expired)

        // clearAll removes both L1 and L2
        cache.saveFeedSnapshot(ownerUid = "user_A", posts = listOf(post1), nowMillis = now)
        cache.clearAll()
        assertEquals(null, cache.loadCachedFeed(ownerUid = "user_A", nowMillis = now + 100L))
      } finally {
        job.cancel()
        tempDir.deleteRecursively()
      }
    }

  @Test
  fun `feed cache serializer budgets oversized inline base64 blobs while preserving post content`() {
    val oversizedBlob = "data:image/jpeg;base64," + "A".repeat(120_000) +
      "${com.example.data.cache.OrbitCacheManager.HD_2K_FRAGMENT_DELIMITER}https://files.catbox.moe/full2k.jpg"
    val post = com.example.data.model.ThreadPost(
      id = "p_media",
      authorId = "u1",
      authorUsername = "dalevondigital",
      body = "High-res shot from today",
      imageUrls = listOf(oversizedBlob, "https://files.catbox.moe/regular.jpg")
    )

    val encoded = com.example.data.cache.ThreadPostCacheSerializer.encodeList(listOf(post))
    assertTrue("Encoded JSON should stay compact", encoded.length < 10_000)

    val decoded = com.example.data.cache.ThreadPostCacheSerializer.decodeList(encoded)
    assertEquals(1, decoded.size)
    assertEquals("High-res shot from today", decoded[0].body)
    assertEquals(
      listOf("https://files.catbox.moe/full2k.jpg", "https://files.catbox.moe/regular.jpg"),
      decoded[0].imageUrls
    )
  }

  @Test
  fun `profile badges normalize deduplicate and preserve canonical display order`() {
    val normalized = com.example.data.model.OrbitProfileBadge.normalizeIds(
      listOf("DEVELOPER", "invalid_badge", "creator", "youtuber", "creator", "influencer")
    )
    assertEquals(
      listOf("creator", "youtuber", "influencer", "developer"),
      normalized
    )

    val profile = com.example.data.model.UserProfile(
      id = "u_creator",
      userId = "u_creator",
      username = "orbitdev",
      profileBadges = listOf("developer", "creator", "unknown")
    )
    assertEquals(
      listOf(
        com.example.data.model.OrbitProfileBadge.CREATOR,
        com.example.data.model.OrbitProfileBadge.DEVELOPER
      ),
      profile.resolvedProfileBadges
    )
  }

  @Test
  fun `space creator receives Admin badge while regular members do not`() {
    val space = com.example.data.model.Community(
      id = "space_1",
      name = "Android Builders",
      ownerId = "u_founder",
      ownerUsername = "founder",
      memberIds = listOf("u_founder", "u_member")
    )
    assertTrue(space.isAdmin("u_founder"))
    assertFalse(space.isAdmin("u_member"))
    assertFalse(space.isAdmin(""))
  }

  @Test
  fun `badge dropdown and admin string resources resolve cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Badge", context.getString(R.string.settings_badge_title))
    assertEquals("Admin", context.getString(R.string.badge_admin))
    assertEquals("Creator", context.getString(R.string.badge_creator))
    assertEquals("YouTuber", context.getString(R.string.badge_youtuber))
    assertEquals("Influencer", context.getString(R.string.badge_influencer))
    assertEquals("Developer", context.getString(R.string.badge_developer))
  }
}




