package cn.denua.v2ex.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.blankj.utilcode.util.ToastUtils;
import com.orhanobut.logger.Logger;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import cn.denua.v2ex.R;
import cn.denua.v2ex.adapter.PullRefreshReplyAdapter;
import cn.denua.v2ex.adapter.ReplyRecyclerViewAdapter;
import cn.denua.v2ex.base.BaseNetworkActivity;
import cn.denua.v2ex.interfaces.IResponsibleView;
import cn.denua.v2ex.interfaces.ResponseListener;
import cn.denua.v2ex.model.Reply;
import cn.denua.v2ex.model.Topic;
import cn.denua.v2ex.service.TopicService;
import cn.denua.v2ex.utils.HtmlUtil;
import cn.denua.v2ex.widget.TopicView;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class TopicActivity extends BaseNetworkActivity implements ResponseListener<List<Topic>> {

    private WebView mWebView;
    private TopicView mTopicView;
    private LinearLayout mLlHeader;

    @BindView(R.id.refresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.rv_reply)
    RecyclerView mRecyclerView;

    private int mTopicId = -1;
    private Topic mTopic = null;
    private ReplyRecyclerViewAdapter mRecyclerViewAdapter;
    private PullRefreshReplyAdapter mPullRecyclerAdapter;
    private TopicService mTopicService;

    private int mPageCount;
    private int mCurrentPage = 1;
    private boolean mIsInit = true;

    private ResponseListener<List<Reply>> mRepliesListener = new ResponseListener<List<Reply>>() {
        @Override
        public void onComplete(List<Reply> result) {
            mRecyclerViewAdapter.addReplies(result);
            if (mPullRecyclerAdapter != null){
                if (mPageCount == mCurrentPage) {
                    mPullRecyclerAdapter.setStatus(PullRefreshReplyAdapter.FooterStatus.COMPLETE);
                }else {
                    mPullRecyclerAdapter.setStatus(PullRefreshReplyAdapter.FooterStatus.LOADING);
                }
                mPullRecyclerAdapter.notifyDataSetChanged();
            }
        }
        @Override
        public void onFailed(String msg) {
            ToastUtils.showShort(msg);
        }
    };

    public static void start(Context context, int topicId){

        Intent intent = new Intent(context, TopicActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("topicId", topicId);
        context.startActivity(intent);
    }
    public static void start(Context context, Topic topic){

        Intent intent = new Intent(context, TopicActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("topic", topic);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_topic);
        ButterKnife.bind(this);

        setTitle(R.string.topic);
        mTopic = getIntent().getParcelableExtra("topic");
        if (mTopic == null){
            this.mTopicId = getIntent().getIntExtra("topicId",-1);
        }else {
            mPageCount = mTopic.getReplies() / 100 + 1;
        }
        mTopicService = new TopicService(this, this);

        initView();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mSwipeRefreshLayout.setRefreshing(true);
        onRefresh();
    }

    protected void initView(){

        mRecyclerViewAdapter = new ReplyRecyclerViewAdapter(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setNestedScrollingEnabled(false);
        mRecyclerView.setItemViewCacheSize(20);
        mRecyclerView.setDrawingCacheEnabled(true);
        mRecyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        if (mPageCount > 1){
            mPullRecyclerAdapter = new PullRefreshReplyAdapter(mRecyclerViewAdapter);
            mPullRecyclerAdapter.setOnPullUpListener(this::loadNextPage);
            mRecyclerView.setAdapter(mPullRecyclerAdapter);
        }else{
            mRecyclerView.setAdapter(mRecyclerViewAdapter);
        }

        initHeaderView();
        setSwipeRefreshTheme(mSwipeRefreshLayout);
        mSwipeRefreshLayout.setOnRefreshListener(this::onRefresh);
    }

    /**
     * 初始化话题相关信息的 view
     */
    private void initHeaderView(){

        LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mLlHeader = new LinearLayout(this);
        mLlHeader.setGravity(Gravity.CENTER_HORIZONTAL);
        mLlHeader.setOrientation(LinearLayout.VERTICAL);
        mLlHeader.setLayoutParams(linearLayoutParams);
        
        mTopicView = new TopicView(this, false);
        mTopicView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200));


        mWebView = new WebView(this);
        mWebView.setNetworkAvailable(true);
        mWebView.setVerticalScrollBarEnabled(false);
        mWebView.setHorizontalScrollBarEnabled(false);
        mWebView.setNetworkAvailable(true);
        mWebView.setFocusable(false);

        if (mTopic != null){
            mTopicView.loadDataFromTopic(mTopic);
            if (mTopic.getContent_rendered()!=null){
                mWebView.loadData(HtmlUtil.applyHtmlStyle(mTopic.getContent_rendered()),
                        "text/html", "utf-8");
            }
        }

        mLlHeader.addView(mTopicView);
        mLlHeader.addView(mWebView);
        mRecyclerViewAdapter.setHeaderView(mLlHeader);
        mRecyclerViewAdapter.notifyDataSetChanged();
    }

    private void onRefresh(){

        if (mTopic == null &&  mIsInit){
            mTopicService.getTopicAndReply(mTopicId);
            return;
        }
        mTopicService.getReply(mTopic, 0);
    }

    private void loadNextPage(){

        TopicService.getReply(this, mTopic.getId(), ++mCurrentPage, mRepliesListener);
//        mTopicService.getReply(mTopic, ++mCurrentPage);
    }

    @Override
    public void onStartRequest() {
        super.onStartRequest();
    }

    @Override
    public void onCompleteRequest() {
        super.onCompleteRequest();
        mSwipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void onComplete(List<Topic> result) {

        if (mIsInit){
            mTopic = result.get(0);
            if (mTopicId > 0){
                mWebView.loadData(HtmlUtil.applyHtmlStyle(
                        mTopic.getContent_rendered()), "text/html", "utf-8");
            }
            mPageCount = mTopic.getReplies() / 100 + 1;
            mTopicView.loadDataFromTopic(mTopic);
            mTopicView.setLastTouched(mTopic.getAgo());
            mIsInit = false;
        }

        mRecyclerViewAdapter.addReplies(result.get(0).getReplyList());
        if (mPullRecyclerAdapter != null){
            if (mPageCount == mCurrentPage) {
                mPullRecyclerAdapter.setStatus(PullRefreshReplyAdapter.FooterStatus.COMPLETE);
            }else {
                mPullRecyclerAdapter.setStatus(PullRefreshReplyAdapter.FooterStatus.LOADING);
            }
            mPullRecyclerAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onFailed(String msg) {

        mSwipeRefreshLayout.setRefreshing(false);
        ToastUtils.showShort(msg);
        TextView mTvError = new TextView(this);
        mTvError.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 600));
        mTvError.setTextSize(25);
        mTvError.setTextColor(getColorAccent());
        mTvError.setGravity(Gravity.CENTER);
        mTvError.setText(msg);

        mLlHeader.addView(mTvError);
    }

    @Override
    public int getContextStatus() {
        super.getContextStatus();
        return IResponsibleView.VIEW_STATUS_ACTIVATED;
    }


}
