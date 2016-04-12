package org.weijian.mydriversrss

import android.app.LoaderManager
import android.content.*
import android.database.ContentObserver
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.support.design.widget.AppBarLayout
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.Html
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import java.util.*

class DisplayActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener, LoaderManager.LoaderCallbacks<Cursor> {

    class NewsResultReceiver constructor(handler: Handler) : ResultReceiver(handler) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            super.onReceiveResult(resultCode, resultData)
        }
    }
    override fun onLoadFinished(loader: Loader<Cursor>?, data: Cursor?) {
        if (data != null) {
            var adapter = NewsAdapter(baseContext, data)
            mRecyclerView.swapAdapter(adapter, false)
            if (mProgressBar.visibility != View.GONE)
                mProgressBar.visibility = View.GONE
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor>? {
        when (id) {
            NewsProviderContract.NEWS_ALL -> {
                return CursorLoader(baseContext, NewsProviderContract.NEWS_CONTENT_URI, null, null, null, null)
            }
            else -> return null
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>?) {
        mRecyclerView.swapAdapter(null, true)
    }

    private lateinit var mToolbar: Toolbar
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mServiceIntent: Intent
    private lateinit var mStatusReciver: StatusReceiver
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mSwipeRefreshLayout: SwipeRefreshLayout
    private lateinit var mAppbarLayout: AppBarLayout
    private lateinit var mStatusIntentFilter: IntentFilter

    private lateinit var mSignId: String
    private lateinit var mXaid: String
    private lateinit var mUdid: String
    private lateinit var mMinid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display)

        // Init variables
        mToolbar = findViewById(R.id.toolbar) as Toolbar
        mRecyclerView = findViewById(R.id.recycler_view) as RecyclerView
        mProgressBar = findViewById(R.id.progress_bar) as ProgressBar
        mSwipeRefreshLayout = findViewById(R.id.swipe_refresh) as SwipeRefreshLayout
        mAppbarLayout = findViewById(R.id.appbarLayout) as AppBarLayout


        // Set toolbar
        setSupportActionBar(mToolbar)
        // Set RecyclerView layoutManager
        var linearLayoutManager = LinearLayoutManager(this@DisplayActivity.baseContext)
        mRecyclerView.layoutManager = linearLayoutManager
        mSwipeRefreshLayout.setOnRefreshListener(this)


        // get or generate rss parameters
        mSignId = savedInstanceState?.getString(Constants.RSS_SIGN_ID) ?: { Random().nextInt().toString() }()
        mXaid = savedInstanceState?.getString(Constants.RSS_XAID) ?: {
            // Generate random XAID
            var stringBuilder = StringBuilder()
            val random = Random()
            kotlin.repeat(8) {
                stringBuilder.append(Integer.toHexString(random.nextInt(16)))
            }
            stringBuilder.toString()
        }()
        mUdid = savedInstanceState?.getString(Constants.RSS_UDID) ?: { Random().nextLong().toString() }()
        mMinid = savedInstanceState?.getString(Constants.RSS_MINID) ?: "0"


        // start RssPullService at startup
        mServiceIntent = Intent(this, RssPullService::class.java)
        mServiceIntent.putExtra(Constants.RSS_MINID, mMinid)
        mServiceIntent.putExtra(Constants.RSS_SIGN_ID, mSignId)
        mServiceIntent.putExtra(Constants.RSS_XAID, mXaid)
        mServiceIntent.putExtra(Constants.RSS_UDID, mUdid)
        startService(mServiceIntent)

        // register broadcast receiver
        mStatusIntentFilter = IntentFilter(Constants.BROADCAST_ACTION)
        mStatusIntentFilter.addCategory(Intent.CATEGORY_DEFAULT)
        mStatusReciver = StatusReceiver()
        LocalBroadcastManager.getInstance(this).registerReceiver(mStatusReciver, mStatusIntentFilter)

        loaderManager.initLoader(NewsProviderContract.NEWS_ALL, null, this)
        contentResolver.registerContentObserver(NewsProviderContract.NEWS_CONTENT_URI, true, NewsObserver(Handler()))

    }


    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mStatusReciver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_display, menu)
        return true
    }

    override fun onRefresh() {
        startService(mServiceIntent)
    }

    fun notifyRecyclerView() {
        mProgressBar.visibility = View.GONE
        mSwipeRefreshLayout.isRefreshing = false
    }


    inner class NewsObserver constructor(handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            loaderManager.getLoader<Cursor>(NewsProviderContract.NEWS_ALL).forceLoad()
        }
    }

    inner class StatusReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.extras.get(Constants.EXTENDED_DATA_STATUS)
            when (status) {
                Constants.STATE_ACTION_COMPLETE -> {
                    Log.d("BROADCAST_RECEIVER", "STATE_ACTION_COMPLETE")
                    if (mSwipeRefreshLayout.isRefreshing) {
                        mSwipeRefreshLayout.isRefreshing = false
                    }
                }
            }
        }

    }

    inner class NewsAdapter constructor(context: Context, newsCursor: Cursor) :
            CursorRecyclerViewAdapter<RecyclerView.ViewHolder>(context, newsCursor) {
        override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, cursor: Cursor?) {
            var newsHolder = viewHolder as NewsHolder
            if (cursor != null) {
                newsHolder.titleView.text = cursor.getString(cursor.getColumnIndex(NewsProvider.COLUMN_TITLE))
                newsHolder.titleView.paint?.isFakeBoldText = true
                newsHolder.titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16F)
                var description = cursor.getString(cursor.getColumnIndex(NewsProvider.COLUMN_DESCRIPTION))
                newsHolder.descriptionView.text = Html.fromHtml(description).toString().substring(0..25) + "..."
                newsHolder.descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15F)

                newsHolder.guid = cursor.getString(cursor.getColumnIndex(NewsProvider.COLUMN_GUID))
                newsHolder.cardView.setOnClickListener {
                    var intent = Intent(baseContext, NewsDetailActivity::class.java)
                    intent.putExtra(NewsDetailActivity.NEWS_URL, newsHolder.guid)
                    startActivity(intent)
                }
            }
        }

        inner class NewsHolder constructor(var cardView: CardView) : RecyclerView.ViewHolder(cardView) {
            lateinit var titleView: TextView
            lateinit var descriptionView: TextView
            lateinit var newsImageView: ImageView
            lateinit var guid: String

            init {
                titleView = cardView.findViewById(R.id.news_title) as TextView
                descriptionView = cardView.findViewById(R.id.news_description) as TextView
                newsImageView = cardView.findViewById(R.id.news_image) as ImageView
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder? {
            var newsCard = LayoutInflater.from(this@DisplayActivity).inflate(R.layout.newscard_view, parent, false) as CardView
            var newsHolder = NewsHolder(newsCard)
            return newsHolder
        }

    }
}
