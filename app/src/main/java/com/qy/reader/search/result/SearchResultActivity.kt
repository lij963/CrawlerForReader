package com.qy.reader.search.result

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.text.TextUtils
import com.qy.reader.R
import com.qy.reader.common.base.BaseActivity
import com.qy.reader.common.entity.book.SearchBook
import com.qy.reader.common.utils.Nav
import com.qy.reader.common.widgets.Sneaker
import com.qy.reader.crawler.Crawler
import com.qy.reader.crawler.source.callback.SearchCallback
import com.qy.reader.support.DividerItemDecoration
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_search_result.*
import java.util.*

/**
 * Created by yuyuhang on 2018/1/9.
 */
class SearchResultActivity : BaseActivity() {

    private lateinit var mAdapter: SearchResultAdapter

    private val mList = ArrayList<SearchBook>()

    override fun getToolbarTitle(): String {
        return "搜索结果"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_result)
        initToolbar()
        mAdapter = SearchResultAdapter(this, mList)

        val title = intent.getStringExtra("text")
        if (TextUtils.isEmpty(title)) {
            Sneaker.with(this)
                    .setTitle("搜索词不能为空哦！")
                    .sneakWarning()
            return
        }

        rv_search_list.layoutManager = LinearLayoutManager(this)
        rv_search_list.addItemDecoration(DividerItemDecoration())

        rv_search_list.adapter = mAdapter
        mAdapter.setOnItemClickListener { _, _, item ->
            item?.let {
                val bundle = Bundle()
                bundle.putSerializable("search_book", item)
                Nav.from(this@SearchResultActivity).setExtras(bundle).start("qyreader://bookinfo")
            }
        }

        search(title)
    }

    @SuppressLint("CheckResult")
    private fun search(title: String) {
        mAdapter.setTitle(title)
        Observable.create(ObservableOnSubscribe<List<SearchBook>> { emitter ->
            Crawler.search(this@SearchResultActivity, title, object : SearchCallback {
                override fun onResponse(keyword: String, appendList: List<SearchBook>) {
                    emitter.onNext(appendList)
                }

                override fun onFinish() {
                    emitter.onComplete()
                }

                override fun onError(msg: String) {
                    if (!emitter.isDisposed) {
                        emitter.onError(Throwable(msg))
                    }
                }
            })
        })
                .bindToLifecycle(this)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { appendList ->
                            for (newBook in appendList) {
                                var exists = false
                                for (book in mList) {
                                    if (TextUtils.equals(book.title, newBook.title) && !newBook.sources.isEmpty()) {
                                        if (TextUtils.isEmpty(book.cover) && !TextUtils.isEmpty(newBook.cover)) {
                                            book.cover = newBook.cover
                                        }
                                        book.sources.add(newBook.sources[0])
                                        exists = true
                                        break
                                    }
                                }
                                if (!exists) {
                                    mList.add(newBook)
                                }
                            }

                            mAdapter.notifyDataSetChanged()
                        },
                        { throwable ->
                            Sneaker.with(this@SearchResultActivity)
                                    .setTitle("搜索失败")
                                    .setMessage(throwable.message)
                                    .sneakWarning()
                        },
                        {
                            Sneaker.with(this@SearchResultActivity)
                                    .setTitle("搜索完毕")
                                    .setMessage("共搜索到" + mList.size + "本书")
                                    .sneakSuccess()
                        })
    }
}
