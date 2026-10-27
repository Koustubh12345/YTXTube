package com.thinker.flashtube

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.thinker.flashtube.potoken.PoTokenGenerator
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

class FlashTubeApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        NewPipe.init(NewPipeDownloader.instance)


        val poTokenGenerator = PoTokenGenerator()
        poTokenGenerator.initialize()
        YoutubeStreamExtractor.setPoTokenProvider(FlashtubePoTokenProvider(poTokenGenerator))


        YoutubeStreamExtractor.setFetchIosClient(true)
        ThemeSettings.init(this)
        QualityPreference.init(this)
        PlaybackSpeed.init(this)
        AudioOnly.init(this)
        ResumeStore.init(this)
        Account.init(this)
        SponsorBlock.init(this)
        SearchHistory.init(this)
        Downloads.init(this)

        getSystemService(android.net.ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
            object : android.net.ConnectivityManager.NetworkCallback() {
                private var last: android.net.Network? = null
                override fun onAvailable(network: android.net.Network) {
                    if (last != null && last != network) StreamCache.invalidateAll()
                    last = network
                }
            },
        )
    }


    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(180)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .build()
}
