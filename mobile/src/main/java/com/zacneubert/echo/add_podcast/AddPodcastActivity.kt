package com.zacneubert.echo.add_podcast

import android.os.Bundle
import android.os.Handler
import android.provider.ContactsContract
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import com.zacneubert.echo.R
import com.zacneubert.echo.api.callback.HandledCallback
import com.zacneubert.echo.api.itunes.ItunesApi
import com.zacneubert.echo.api.itunes.ItunesApiBuilder
import com.zacneubert.echo.api.itunes.models.SearchResult
import retrofit2.Call
import retrofit2.Response
import java.util.*

class AddPodcastActivity : AppCompatActivity() {
    val query: String = ""
    lateinit var queryEditText: EditText
    lateinit var itunesPodcastRecycler: RecyclerView

    var stoppedTypingTimer: Timer = Timer()
    var stoppedTypingSet: Boolean = false
    val handler: Handler = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_podcast)

        itunesPodcastRecycler = findViewById<RecyclerView>(R.id.itunes_podcast_recycler) as RecyclerView
        queryEditText = findViewById<EditText>(R.id.add_podcast_query) as EditText
        queryEditText.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (stoppedTypingSet) {
                    stoppedTypingTimer.cancel()
                    stoppedTypingTimer.purge()
                }

                stoppedTypingTimer = Timer()
                stoppedTypingTimer.schedule(object : TimerTask() {
                    override fun run() {
                        handler.post {
                            initializeListFromQuery()
                        }
                    }
                }, 300)
                stoppedTypingSet = true
            }
        })
    }

    fun initializeListFromQuery() {
        val itunesApi = ItunesApiBuilder.getItunesApi()
        ItunesApi.Meta.searchPodcasts(itunesApi, queryEditText.context, queryEditText.text.toString(), object : HandledCallback<SearchResult>(queryEditText.context) {
            override fun onFinally() {}

            override fun onTotalFailure(call: Call<SearchResult>?, stringId: Int) {
                Toast.makeText(queryEditText.context, "Search failed. Check your internet connection.", Toast.LENGTH_SHORT).show()
            }

            override fun onSuccessfulResponse(call: Call<SearchResult>?, response: Response<SearchResult>?) {
                response?.apply {
                    itunesPodcastRecycler.adapter = ItunesPodcastRecyclerAdapter(
                            this.body()!!.results
                    )
                    itunesPodcastRecycler.layoutManager = LinearLayoutManager(this@AddPodcastActivity)
                }
            }
        })
    }
}