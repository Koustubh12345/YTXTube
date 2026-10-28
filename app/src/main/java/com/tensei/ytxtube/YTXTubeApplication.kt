package com.tensei.ytxtube

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.tensei.ytxtube.potoken.PoTokenGenerator
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

class YTXTubeApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        TvMode.init(this)
        NewPipe.init(NewPipeDownloader.instance)


        val poTokenGenerator = PoTokenGenerator()
        poTokenGenerator.initialize()
        YoutubeStreamExtractor.setPoTokenProvider(YtxPoTokenProvider(poTokenGenerator))


        YoutubeStreamExtractor.setFetchIosClient(true)
        ThemeSettings.init(this)
        QualityPreference.init(this)
        PlaybackSpeed.init(this)
        AudioOnly.init(this)
        AudioTrackPreference.init(this)
        CaptionPreference.init(this)
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
