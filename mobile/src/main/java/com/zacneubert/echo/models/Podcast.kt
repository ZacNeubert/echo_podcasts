package com.zacneubert.echo.models

import android.annotation.SuppressLint
import android.os.Parcel
import android.os.Parcelable
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.ParsingFeedException
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import com.zacneubert.echo.EchoApplication
import com.zacneubert.echo.api.itunes.PodcastMetaApi
import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.relation.ToMany
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.IOException
import java.net.URL

@SuppressLint("ParcelCreator")
@Entity
class Podcast() : Parcelable {
    @Id
    var id: Long = 0

    var title: String = ""
    var description: String = ""
    var artist: String = ""
    var feedUrl: String = ""
    var feedLastModified: Long = 0
    var artUri: String = ""

    @Backlink
    lateinit var episodes: ToMany<Episode>

    constructor(parcel: Parcel) : this() {
        id = parcel.readLong()
        title = parcel.readString()
        description = parcel.readString()
        artist = parcel.readString()
        feedUrl = parcel.readString()
        artUri = parcel.readString()
    }

    constructor(link: String) : this() {
        feedUrl = link
    }

    fun getSyndFeed(): SyndFeed? {
        val lastModified = PodcastMetaApi.getLastModified(feedUrl)
        return if (lastModified > feedLastModified || lastModified == 0L) {
            feedLastModified = lastModified

            Podcast.urlToFeed(feedUrl)
        } else {
            null
        }
    }


    fun getFeedAndUpdateAttrs(application: EchoApplication): SyndFeed? {
        val feed = getSyndFeed()

        application.podcastBox().put(this)

        return if (feed != null) {
            updateFromFeed(feed, application)
            feed
        } else {
            null
        }
    }

    private fun updateFromFeed(feed: SyndFeed, application: EchoApplication) {
        if (this.description.isEmpty()) {
            description = if (feed.description.isNotEmpty()) feed.description.trim() else this.description
        }

        if (this.title.isEmpty()) {
            title = if (feed.title.trim().isNotEmpty()) feed.title.trim() else this.title
        }

        if (this.artUri.isEmpty()) {
            artUri = if (feed.image != null && feed.image.url.isNotEmpty()) feed.image.url else this.artUri
        }

        if (this.artist.isEmpty()) {
            artist = if (feed.author != null && feed.author.isNotEmpty()) feed.author else this.artist
        }

        application.podcastBox().put(this)
    }

    fun checkFeedUrl(): Boolean {
        val feed = getSyndFeed()

        if (feed != null && feed.entries.size > 0 && feed.entries.get(0).enclosures.size > 0) {
            return true
        }
        return false
    }

    fun getRefreshedOrCurrentEpisodeList(application: EchoApplication): List<Episode> {
        if (id == 0L) {
            return listOf()
        }

        val allUIDs = episodes.map { e -> e.uid }

        val feed = getFeedAndUpdateAttrs(application)

        if (feed != null) {
            feed.entries.forEach {
                if (!allUIDs.contains(it.uri)) {
                    val episode = Episode(this, it)
                    this.episodes.add(episode)
                }
            }
            application.podcastBox()!!.put(this)

        }

        return this.episodes.toList()
    }

    fun refreshEpisodeList(application: EchoApplication, onComplete: ((application: EchoApplication, podcast: Podcast, episodes: List<Episode>) -> Unit)?) {
        if (id == 0L) {
            return
        }

        doAsync {
            val allUIDs = episodes.map { e -> e.uid }
            val podcast = Podcast@ this.weakRef.get()!!

            val feed = getFeedAndUpdateAttrs(application)
            if (feed != null) {
                feed.entries.forEach {
                    if (!allUIDs.contains(it.uri)) {
                        val episode = Episode(Podcast@ this.weakRef.get()!!, it)
                        podcast.episodes.add(episode)
                    }
                }
                application.podcastBox()!!.put(podcast)
            }

            uiThread {
                if (onComplete != null) {
                    onComplete(application, podcast, podcast.chronologicalEpisodes())
                }
            }
        }
    }

    fun topNChronological(n: Int): List<Episode> {
        val chronologicalEpisodes = this.chronologicalEpisodes()
        return chronologicalEpisodes.subList(0, minOf(n, chronologicalEpisodes.size))
    }

    fun chronologicalEpisodes(): List<Episode> {
        return episodes.sortedByDescending { e -> e.publishDate }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(title)
        parcel.writeString(description)
        parcel.writeString(artist)
        parcel.writeString(feedUrl)
        parcel.writeString(artUri)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Podcast> {
        override fun createFromParcel(parcel: Parcel): Podcast {
            return Podcast(parcel)
        }

        override fun newArray(size: Int): Array<Podcast?> {
            return arrayOfNulls(size)
        }

        fun urlToFeed(url: String): SyndFeed? {
            val timeout = 5000

            val feedUrlConnection = URL(url).openConnection()
            feedUrlConnection.connectTimeout = timeout
            feedUrlConnection.readTimeout = timeout

            return try {
                val syndFeedInput = SyndFeedInput()
                val feed = syndFeedInput.build(XmlReader(feedUrlConnection))
                feed
            } catch (exception: ParsingFeedException) {
                null
            } catch (exception: IOException) {
                val httpsUrl = url.replace("http", "https")
                if (httpsUrl.startsWith("https://")) {
                    urlToFeed(httpsUrl)
                } else {
                    null // it was already https
                }
            }
        }

    }
}