package org.weijian.mydriversrss

import android.app.IntentService
import android.content.Intent
import java.io.BufferedInputStream
import java.net.URL

/**
 * Created by weijian on 16-4-1.
 */
class RssPullService constructor() : IntentService("RssPullService") {
    var mBroadcastNotifier: BroadcastNotifier? = null
    override fun onHandleIntent(intent: Intent) {
        val dataString = intent.dataString
        mBroadcastNotifier = BroadcastNotifier(this)
        val url = URL(Constants.RSS_ADDRESS)
        mBroadcastNotifier?.broadcastIntentWithStatus(Constants.STATE_ACTION_STARTING)
        val httpConnection = url.openConnection()
        // TODO fix exception when no network available
        val inputStream = BufferedInputStream(httpConnection.inputStream)
        val parser = RssPullParser()
        val newsList = parser.parse(inputStream)
        mBroadcastNotifier?.broadcastIntentWithStatus(Constants.STATE_ACTION_RETRIEVED)
        contentResolver.bulkInsert(NewsProviderContract.NEWS_CONTENT_URI, newsList.toTypedArray())
        mBroadcastNotifier?.broadcastIntentWithStatus(Constants.STATE_ACTION_COMPLETE)
    }
}
