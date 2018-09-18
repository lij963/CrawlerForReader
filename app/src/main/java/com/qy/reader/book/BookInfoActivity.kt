package com.qy.reader.book

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.qy.reader.R
import com.qy.reader.common.base.BaseActivity
import com.qy.reader.common.entity.book.SearchBook
import com.qy.reader.common.entity.chapter.Chapter
import com.qy.reader.common.utils.Nav
import com.qy.reader.common.utils.StringUtils
import com.qy.reader.common.widgets.ListDialog
import com.qy.reader.common.widgets.Sneaker
import com.qy.reader.crawler.Crawler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_book_info.*
import java.util.*

/**
 * Created by quezhongsang on 2018/1/13.
 */

class BookInfoActivity : BaseActivity() {

    private val mChapterList = ArrayList<Chapter>()
    private lateinit var mSearchBook: SearchBook
    private lateinit var mBookInfoAdapter: BookInfoAdapter

    private val mSourceList = ArrayList<SearchBook.SL>()
    var disposable: Disposable? = null
    private val mStrDesc = "倒序"
    private val mStrAsc = "正序"


    private var mCurrentSourcePosition = 0


    private val sourceStr: String
        get() {
            var sourceStr = "来源(" + mSearchBook.sources?.size + ")："
            if (mCurrentSourcePosition < mSearchBook.sources.size) {
                val sl = mSearchBook.sources[mCurrentSourcePosition]
                if (sl?.source != null) {
                    sourceStr += StringUtils.getStr(sl.source.name)
                }
            }

            return sourceStr
        }

    private val sl: SearchBook.SL?
        get() = if (mCurrentSourcePosition < mSearchBook.sources.size) {
            mSearchBook.sources[mCurrentSourcePosition]
        } else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_info)
        initToolbar()
        mBookInfoAdapter = BookInfoAdapter(this, mChapterList)
        mSearchBook = intent.getSerializableExtra("search_book") as SearchBook
        initView()
    }

    override fun getToolbarTitle(): String {
        return "书籍详情"
    }

    private fun initView() {
        mSearchBook.sources?.let {
            mSourceList.addAll(it)
        }
//        mRecyclerView = findViewById(R.id.rv_search_list)
        //book
        Glide.with(this).load(mSearchBook.cover).into(findViewById<View>(R.id.iv_book_cover) as ImageView)
        (findViewById<View>(R.id.tv_book_title) as TextView).text = StringUtils.getStr(mSearchBook.title)
        (findViewById<View>(R.id.tv_book_author) as TextView).text = StringUtils.getStr(mSearchBook.author)
        (findViewById<View>(R.id.tv_book_desc) as TextView).text = StringUtils.getStr(mSearchBook.desc)

//         tv_book_newest_chapter = findViewById(R.id.tv_book_newest_chapter)
//        tv_book_source = findViewById(R.id.tv_book_source)
//         tv_order_by = findViewById(R.id.tv_order_by)
        //list
        rv_search_list.layoutManager = LinearLayoutManager(this)
        rv_search_list.adapter = mBookInfoAdapter
        mBookInfoAdapter.setOnItemClickListener { view, position, item ->
            val bundle = Bundle()
            bundle.putSerializable("book", mSearchBook)
            bundle.putSerializable("chapter_list", mChapterList)
            bundle.putSerializable("chapter", item)
            bundle.putSerializable("source", mSourceList[mCurrentSourcePosition])
            Nav.from(mContext).setExtras(bundle).start("qyreader://read")
        }

        findViewById<View>(R.id.tv_book_source).setOnClickListener(View.OnClickListener {
            if (mSourceList.size == 0)
                return@OnClickListener
            val items = arrayOfNulls<String>(mSourceList.size)
            for (i in items.indices) {
                items[i] = mSourceList[i].source.name
            }
            ListDialog.Builder(mContext)
                    .setList(items) { materialDialog, position, content ->
                        if (mCurrentSourcePosition != position) {
                            mCurrentSourcePosition = position
                            requestNet()
                        }
                    }
                    .show()
        })

        tv_order_by.setOnClickListener {
            val str = tv_order_by.text.toString()
            if (mStrAsc.equals(str, ignoreCase = true)) {//倒序
                mBookInfoAdapter.orderByDesc()
                tv_order_by.text = mStrDesc
            } else if (mStrDesc.equals(str, ignoreCase = true)) {//正序
                mBookInfoAdapter.orderByAsc()
                tv_order_by.text = mStrAsc
            }
        }

        requestNet()
    }

    @SuppressLint("CheckResult")
    private fun requestNet() {
        val sl = sl ?: return
        tv_book_source.text = sourceStr

        mBookInfoAdapter.clear()
        tv_order_by.text = "加载中..."
        disposable?.let {
            if (it.isDisposed) {
                it.dispose()
            }
        }
        disposable = Crawler.catalog(sl)
//                .bindToLifecycle(this)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ chapters ->
                    if (chapters == null || chapters.isEmpty()) {
                        return@subscribe
                    }
                    mBookInfoAdapter.addAll(chapters)

                    //set  最新章节
                    val lastChapter = chapters[chapters.size - 1]
                    tv_book_newest_chapter.text = "最新章节：" + StringUtils.getStr(lastChapter.title)

                    //set 排序名字
                    if (mBookInfoAdapter.isAsc) {
                        tv_order_by.text = mStrAsc
                    } else {
                        tv_order_by.text = mStrDesc
                    }
                }, { throwable ->
                    Sneaker.with(mContext)
                            .setTitle("加载失败")
                            .setMessage(throwable.message)
                            .sneakWarning()

                    tv_order_by.text = "加载失败"
                })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable?.let {
            if (it.isDisposed) {
                it.dispose()
            }
        }
    }
}
