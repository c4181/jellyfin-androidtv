package tv.mediabrowser.mediabrowsertv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v4.app.ActivityOptionsCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.Adapter;
import android.widget.Toast;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import mediabrowser.apiinteraction.ApiClient;
import mediabrowser.apiinteraction.Response;
import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.dto.UserItemDataDto;
import mediabrowser.model.querying.ItemFields;
import mediabrowser.model.querying.ItemsResult;
import mediabrowser.model.querying.NextUpQuery;
import mediabrowser.model.querying.SeasonQuery;
import mediabrowser.model.querying.SimilarItemsQuery;
import mediabrowser.model.querying.UpcomingEpisodesQuery;


public class BaseItemDetailsFragment extends DetailsFragment {
    private static final String TAG = "BaseItemDetailsFragment";

    private static final int ACTION_PLAY = 1;
    private static final int ACTION_RESUME = 2;
    private static final int ACTION_DETAILS = 3;

    private static final int DETAIL_THUMB_WIDTH = 150;
    private static final int DETAIL_THUMB_HEIGHT = 150;

    protected BaseItemDto mBaseItem;
    protected ApiClient mApiClient;
    protected DetailsActivity mActivity;

    private Drawable mDefaultBackground;
    private Target mBackgroundTarget;
    private DisplayMetrics mMetrics;
    private DetailsOverviewRowPresenter mDorPresenter;
    private DetailRowBuilderTask mDetailRowBuilderTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate DetailsFragment");
        super.onCreate(savedInstanceState);
        mApiClient = TvApp.getApplication().getApiClient();

        mDorPresenter =
                new DetailsOverviewRowPresenter(new DetailsDescriptionPresenter());

        BackgroundManager backgroundManager = BackgroundManager.getInstance(getActivity());
        backgroundManager.attach(getActivity().getWindow());
        mBackgroundTarget = new PicassoBackgroundManagerTarget(backgroundManager);

        mDefaultBackground = getResources().getDrawable(R.drawable.default_background);

        mMetrics = new DisplayMetrics();
        mActivity = (DetailsActivity) getActivity();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        mBaseItem = TvApp.getApplication().getSerializer().DeserializeFromString(mActivity.getIntent().getStringExtra("BaseItemDto"), BaseItemDto.class);
        mDetailRowBuilderTask = (DetailRowBuilderTask) new DetailRowBuilderTask().execute(mBaseItem);
        mDorPresenter.setSharedElementEnterTransition(getActivity(),
                DetailsActivity.SHARED_ELEMENT_NAME);

        updateBackground(Utils.getBackdropImageUrl(mBaseItem, TvApp.getApplication().getApiClient(), true));
        setOnItemViewClickedListener(new ItemViewClickedListener());

    }

    @Override
    public void onResume() {
        super.onResume();
        TvApp.getApplication().getLogger().Debug("Resuming details fragment...");
        //reload with our updated item
    }

    @Override
    public void onStop() {
        mDetailRowBuilderTask.cancel(true);
        super.onStop();
    }

    protected void addItemRow(ArrayObjectAdapter parent, ItemRowAdapter row, int index, String headerText) {
        HeaderItem header = new HeaderItem(index, headerText, null);
        parent.add(new ListRow(header, row));
        row.Retrieve();
    }

    private class DetailRowBuilderTask extends AsyncTask<BaseItemDto, Integer, DetailsOverviewRow> {
        @Override
        protected DetailsOverviewRow doInBackground(BaseItemDto... baseItem) {


            DetailsOverviewRow row = new DetailsOverviewRow(mBaseItem);
            try {
                Bitmap poster = Picasso.with(getActivity())
                        .load(Utils.getPrimaryImageUrl(mBaseItem, mApiClient, true))
                                .resize(Utils.convertDpToPixel(getActivity().getApplicationContext(), DETAIL_THUMB_WIDTH),
                                        Utils.convertDpToPixel(getActivity().getApplicationContext(), DETAIL_THUMB_HEIGHT))
                                .centerInside()
                                .get();
                row.setImageBitmap(getActivity(), poster);
            } catch (IOException e) {
            }

            UserItemDataDto userData = mBaseItem.getUserData();
            if (userData != null && userData.getPlaybackPositionTicks() > 0) {
                row.addAction(new Action(ACTION_RESUME, "Resume"));
            }

            row.addAction(new Action(ACTION_PLAY, "Play"));
            row.addAction(new Action(ACTION_DETAILS, "Full Details"));
            return row;
        }

        protected void play(final BaseItemDto item, final int pos) {
            Utils.getItemsToPlay(item, new Response<String[]>() {
                @Override
                public void onResponse(String[] response) {
                    Intent intent = new Intent(getActivity(), PlaybackOverlayActivity.class);
                    intent.putExtra("Items", response);
                    intent.putExtra("Position", pos);
                    startActivity(intent);
                }
            });

        }

        @Override
        protected void onPostExecute(DetailsOverviewRow detailRow) {
            ClassPresenterSelector ps = new ClassPresenterSelector();
            // set detail background and style
            mDorPresenter.setBackgroundColor(getResources().getColor(R.color.detail_background));
            mDorPresenter.setStyleLarge(true);
            mDorPresenter.setOnActionClickedListener(new OnActionClickedListener() {
                @Override
                public void onActionClicked(Action action) {
                    if (action.getId() == ACTION_PLAY) {
                        play(mBaseItem, 0);
                    } else {
                        if (action.getId() == ACTION_RESUME) {
                            Long pos = mBaseItem.getUserData().getPlaybackPositionTicks() / 10000;
                            play(mBaseItem, pos.intValue());
                        } else {
                            Toast.makeText(getActivity(), action.toString() + " not implemented", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

            ps.addClassPresenter(DetailsOverviewRow.class, mDorPresenter);
            ps.addClassPresenter(ListRow.class,
                    new ListRowPresenter());

            final ArrayObjectAdapter adapter = new ArrayObjectAdapter(ps);
            adapter.add(detailRow);

            setAdapter(adapter);

            addAdditionalRows(adapter);
        }

    }

    protected void addAdditionalRows(ArrayObjectAdapter adapter) {
        switch (mBaseItem.getType()) {
            case "Movie":
                SimilarItemsQuery similar = new SimilarItemsQuery();
                similar.setFields(new ItemFields[] {ItemFields.PrimaryImageAspectRatio});
                similar.setUserId(TvApp.getApplication().getCurrentUser().getId());
                similar.setId(mBaseItem.getId());
                similar.setLimit(10);

                ItemRowAdapter listRowAdapter = new ItemRowAdapter(similar, QueryType.SimilarMovies, new CardPresenter(), adapter);
                addItemRow(adapter, listRowAdapter, 0, "Similar Movies");
                break;
            case "Series":
                SeasonQuery seasons = new SeasonQuery();
                seasons.setSeriesId(mBaseItem.getId());
                seasons.setUserId(TvApp.getApplication().getCurrentUser().getId());
                ItemRowAdapter seasonsAdapter = new ItemRowAdapter(seasons, new CardPresenter(), adapter);
                addItemRow(adapter, seasonsAdapter, 0, "Seasons");

                NextUpQuery nextUpQuery = new NextUpQuery();
                nextUpQuery.setUserId(TvApp.getApplication().getCurrentUser().getId());
                nextUpQuery.setSeriesId(mBaseItem.getId());
                nextUpQuery.setFields(new ItemFields[]{ItemFields.PrimaryImageAspectRatio});
                ItemRowAdapter nextUpAdapter = new ItemRowAdapter(nextUpQuery, new CardPresenter(), adapter);
                addItemRow(adapter, nextUpAdapter, 1, "Next Up");

                UpcomingEpisodesQuery upcoming = new UpcomingEpisodesQuery();
                upcoming.setUserId(TvApp.getApplication().getCurrentUser().getId());
                upcoming.setParentId(mBaseItem.getId());
                upcoming.setFields(new ItemFields[]{ItemFields.PrimaryImageAspectRatio});
                ItemRowAdapter upcomingAdapter = new ItemRowAdapter(upcoming, new CardPresenter(), adapter);
                addItemRow(adapter, upcomingAdapter, 2, "Upcoming");

                SimilarItemsQuery similarSeries = new SimilarItemsQuery();
                similarSeries.setFields(new ItemFields[]{ItemFields.PrimaryImageAspectRatio});
                similarSeries.setUserId(TvApp.getApplication().getCurrentUser().getId());
                similarSeries.setId(mBaseItem.getId());
                similarSeries.setLimit(20);
                ItemRowAdapter similarAdapter = new ItemRowAdapter(similarSeries, QueryType.SimilarSeries, new CardPresenter(), adapter);
                addItemRow(adapter, similarAdapter, 1, "Similar Series");
                break;
        }


    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(final Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (!(item instanceof BaseRowItem)) return;
            BaseRowItem rowItem = (BaseRowItem) item;

            final BaseItemDto baseItem = rowItem.getBaseItem();
            //Retrieve full item for display and playback
            mApiClient.GetItemAsync(baseItem.getId(), TvApp.getApplication().getCurrentUser().getId(), new Response<BaseItemDto>() {
                @Override
                public void onResponse(BaseItemDto response) {
                    Intent intent = new Intent(getActivity(), DetailsActivity.class);
                    intent.putExtra("BaseItemDto", TvApp.getApplication().getSerializer().SerializeToString(response));

                    Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(
                            getActivity(),
                            ((ImageCardView) itemViewHolder.view).getMainImageView(),
                            DetailsActivity.SHARED_ELEMENT_NAME).toBundle();
                    getActivity().startActivity(intent, bundle);

                }

                @Override
                public void onError(Exception exception) {
                    TvApp.getApplication().getLogger().ErrorException("Error retrieving full object", exception);
                    exception.printStackTrace();
                }
            });
        }
    }

    protected void updateBackground(String url) {
        Log.d(TAG, "url" + url);
        Log.d(TAG, "metrics" + mMetrics.toString());
        Picasso.with(getActivity())
                .load(url)
                .resize(mMetrics.widthPixels, mMetrics.heightPixels)
                .error(mDefaultBackground)
                .into(mBackgroundTarget);
    }

}
