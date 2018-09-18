package com.qy.reader.crawler

import android.text.TextUtils
import android.util.Log
import android.util.SparseBooleanArray
import com.qy.reader.common.base.BaseActivity
import com.qy.reader.common.entity.book.SearchBook
import com.qy.reader.common.entity.chapter.Chapter
import com.qy.reader.common.entity.source.SourceID
import com.qy.reader.common.utils.LogUtils
import com.qy.reader.common.utils.StringUtils
import com.qy.reader.crawler.source.SourceManager
import com.qy.reader.crawler.source.callback.ChapterCallback
import com.qy.reader.crawler.source.callback.ContentCallback
import com.qy.reader.crawler.source.callback.SearchCallback
import com.qy.reader.crawler.xpath.exception.XpathSyntaxErrorException
import com.qy.reader.crawler.xpath.model.JXDocument
import com.qy.reader.crawler.xpath.model.JXNode
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.jsoup.Jsoup
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * 爬虫
 *
 *
 * Created by yuyuhang on 2018/1/8.
 */
object Crawler {

    private const val TAG = "qy.Crawler"

    fun search(activity: BaseActivity, keyword: String, callback: SearchCallback?):Disposable {
        val checkedMap = SourceManager.getSourceEnableSparseArray()
        val scheduler = Schedulers.from(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1))
        return Observable.range(0, SourceManager.CONFIGS.size() - 1)
//
                .flatMap {
                    Observable.just(it)
                            .subscribeOn(scheduler)
                            .map {
                                parseNode(it, checkedMap, keyword, callback)
                            }
                }
                .bindToLifecycle(activity)
                .doFinally { scheduler.shutdown() }
                .subscribe(
                        {
                            LogUtils.e("Tsing", "books.size = ${it?.size}")
                        },
                        {
                            LogUtils.e("Tsing", "onError")
                            callback?.onError(it.toString())
                        },
                        {
                            LogUtils.e("Tsing", "onFinish")
                            callback?.onFinish()
                        }
                )
    }

    @Suppress("DEPRECATION")
    private fun parseNode(it: Int, checkedMap: SparseBooleanArray, keyword: String, callback: SearchCallback?): ArrayList<SearchBook> {

        val books = arrayListOf<SearchBook>()
        try {                // 提高容错性
            val id = SourceManager.CONFIGS.keyAt(it)
            //                                LogUtils.e("Tsing", "index = $it , id = $id")
            if (checkedMap.get(id)) {
                val config = SourceManager.CONFIGS.valueAt(it)
                val source = SourceManager.SOURCES.get(id)
                val url = if (!TextUtils.isEmpty(config.search.charset)) {
                    String.format(source.searchURL, URLEncoder.encode(keyword, config.search.charset))
                } else {
                    String.format(source.searchURL, keyword)
                }
                Log.i(TAG, "url=$url")
                val jxDocument = JXDocument(Jsoup.connect(url).validateTLSCertificates(false).get())
                val rs = jxDocument.selN(config.search.xpath)
                for (jxNode in rs) {
                    val book = SearchBook()

                    book.cover = urlVerification(getNodeStr(jxNode, config.search.coverXpath), url)
                    Log.i(TAG, "cover=" + book.cover)

                    book.title = getNodeStr(jxNode, config.search.titleXpath)
                    Log.i(TAG, "title=" + book.title)

                    var link = urlVerification(getNodeStr(jxNode, config.search.linkXpath), url)
                    if (source.id == SourceID.CHINESEWUZHOU ||
                            source.id == SourceID.YANMOXUAN ||
                            source.id == SourceID.QIANQIANXIAOSHUO ||
                            source.id == SourceID.PIAOTIANWENXUE) {
                        link = link?.substring(0, link.lastIndexOf('/') + 1)
                    }
                    Log.i(TAG, "link= $link")
                    book.sources.add(SearchBook.SL(link, source))

                    book.author = getNodeStr(jxNode, config.search.authorXpath)
                    if (source.id == SourceID.CHINESEZHUOBI || source.id == SourceID.CHINESEXIAOSHUO) {
                        book.author = book.author.replace("作者：", "")
                    }
                    Log.i(TAG, "author=" + book.author)

                    book.desc = getNodeStr(jxNode, config.search.descXpath).trim { it <= ' ' }
                    Log.i(TAG, "desc=" + book.desc)

                    if (!TextUtils.isEmpty(link)) {//过滤无效信息
                        books.add(book)
                    }
                }
                //                                    LogUtils.e("Tsing", "source = ${source.name} ,books.size = ${books.size}")
                callback?.onResponse(keyword, books)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
//            callback?.onError(e.toString())
        }
        return books
    }

    fun catalog(sl: SearchBook.SL?, callback: ChapterCallback?) {
        if (sl?.source == null || TextUtils.isEmpty(sl.link)) {
            callback?.onError("")
            return
        }

        val sourceId = sl.source.id
        val config = SourceManager.CONFIGS.get(sourceId)
//        val source = SourceManager.SOURCES.get(sourceId)

        if (config.catalog == null) {
            return
        }

        if (sourceId == SourceID.CHINESEWUZHOU) { // 梧州中文台
            val ba = sl.link.indexOf("ba")
            val shtml = sl.link.lastIndexOf(".")
            if (ba in 1..(shtml - 1)) {
                val id = sl.link.substring(ba + 2, shtml)
                val front = id.substring(0, 2)
                try {
                    val original = URI(sl.link)
                    val uri = URI(original.scheme, original.authority, "/$front/$id/", null, null)
                    sl.link = uri.toString()
                } catch (e: URISyntaxException) {
                    e.printStackTrace()
                }

            }
        } else if (sourceId == SourceID.AIQIWENXUE) { // https://m.i7wx.com/book/3787.html --> https://m.i7wx.com/3/3787/
            val id = sl.link.substring(sl.link.lastIndexOf("/") + 1, sl.link.lastIndexOf("."))
            val front = id.substring(0, 1)
            try {
                val original = URI(sl.link)
                val uri = URI(original.scheme, original.authority, "/$front/$id/", null, null)
                sl.link = uri.toString()
            } catch (e: URISyntaxException) {
                e.printStackTrace()
            }

        }

        var rs: List<JXNode>? = null
        try {
            @Suppress("DEPRECATION")
            val jxDocument = JXDocument(Jsoup.connect(sl.link).validateTLSCertificates(false).get())
            rs = jxDocument.selN(config.catalog.xpath)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }

        if (rs == null || rs.isEmpty()) {
            callback?.onError("请求失败")
            return
        }

        val chapters = ArrayList<Chapter>()
        try {
            for (jxNode in rs) {
                val chapter = Chapter()

                val link = getNodeStr(jxNode, config.catalog.linkXpath)
                if (!TextUtils.isEmpty(link)) {
                    chapter.link = urlVerification(link, sl.link)
                    Log.i(TAG, "link=" + chapter.link)

                    chapter.title = getNodeStr(jxNode, config.catalog.titleXpath)
                    Log.i(TAG, "title=" + chapter.title)
                }

                chapters.add(chapter)
            }

            callback?.onResponse(chapters)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
            callback?.onError("请求失败")
        }

    }

    fun content(sl: SearchBook.SL?, url: String, callback: ContentCallback?) {
        Log.i(TAG, "content  url=$url")
        if (sl?.source == null || TextUtils.isEmpty(sl.link) || TextUtils.isEmpty(url)) {
            callback?.onError("")
            return
        }
        val sourceId = sl.source.id
        val config = SourceManager.CONFIGS.get(sourceId)
//        val source = SourceManager.SOURCES.get(sourceId)

        if (config.content == null) {
            callback?.onError("")
            return
        }

        try {
            val link = urlVerification(url, sl.link)
            Log.i(TAG, "link =  $link ")
            @Suppress("DEPRECATION")
            val jxDocument = JXDocument(Jsoup.connect(link).validateTLSCertificates(false).get())

            var content = getNodeStr(jxDocument, config.content.xpath)

            // 换行
            val builder = StringBuilder()
            val lines = content.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (l in lines) {
                val line = StringUtils.trim(l)
                if (!TextUtils.isEmpty(line)) {
                    builder.append("        ").append(line).append("\n")
                }
            }

            content = builder.toString()
            Log.i(TAG, "content =$content")
            callback?.onResponse(content)
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }

    }


    /**
     * 获取 通过xpath 查找到的字符串
     *
     * @param startNode 只有JXDocument   和  JXNode 两种
     * @param xpath
     * @return
     */
    private fun getNodeStr(startNode: Any, xpath: String): String {
        val rs = StringBuilder()
        try {
            val list: List<*> = when (startNode) {
                is JXDocument -> startNode.sel(xpath)
                is JXNode -> startNode.sel(xpath)
                else -> return ""
            }

            for (node in list) {
                rs.append(node.toString())
            }

        } catch (e: XpathSyntaxErrorException) {
            Log.e(TAG, e.toString())
        }

        return rs.toString()
    }

    @Throws(URISyntaxException::class)
    private fun urlVerification(link_: String, linkWithHost_: String): String? {
        var link = link_
        var linkWithHost = linkWithHost_
        if (TextUtils.isEmpty(link)) {
            return link
        }
        if (link.startsWith("/")) {
            val original = URI(linkWithHost)
            val uri = URI(original.scheme, original.authority, link, null)
            link = uri.toString()
        } else if (!link.startsWith("http://") && !link.startsWith("https://")) {
            if (linkWithHost.endsWith("html") || linkWithHost.endsWith("htm")) {
                linkWithHost = linkWithHost.substring(0, linkWithHost.lastIndexOf("/") + 1)
            } else if (!linkWithHost.endsWith("/")) {
                linkWithHost = "$linkWithHost/"
            }
            link = linkWithHost + link
        }
        return link
    }

    @JvmStatic
    fun main(args: Array<String>) {

    }
}
