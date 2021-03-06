/*
 * Copyright (C) 2016 Fabian Lupa
 */

package com.flaiker.popularmovies;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.flaiker.popularmovies.contentprovider.MovieContract;
import com.squareup.picasso.Picasso;

import java.util.List;

/**
 * Activity for showing movies loaded by {@link FetchMovieTask} in a grid.
 * <p/>
 * On tablets a detail fragment is loaded in the view, on phones {@link MovieDetailActivity} is
 * launched to provide details.
 */
public class MovieListActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor>, FavoritesHelper.FavoriteChangeListener {

    private static final int MOVIE_POPULAR_LOADER = 1;
    private static final int MOVIE_TOP_RATED_LOADER = 2;
    private static final int MOVIE_FAVORITE_LOADER = 3;
    private static final String SAVED_LOADER = "loader";

    private boolean mTwoPane;
    private RecyclerView mRecyclerView;

    private FavoritesHelper mFavoriteHelper;
    private int mCurrentLoader = MOVIE_POPULAR_LOADER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        View recyclerView = findViewById(R.id.movie_list);
        assert recyclerView != null;
        mRecyclerView = (RecyclerView) recyclerView;
        mRecyclerView.setAdapter(new SimpleItemRecyclerViewAdapter());

        if (findViewById(R.id.movie_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        mFavoriteHelper = new FavoritesHelper(this);
        FavoritesHelper.addListener(this);

        // Restore saved instance state if it exists
        if (savedInstanceState != null && !savedInstanceState.isEmpty()) {
            mCurrentLoader = savedInstanceState.getInt(SAVED_LOADER);
        }

        // Start loading the movies
        getSupportLoaderManager().initLoader(mCurrentLoader, null, this).forceLoad();

        // TODO: Load movies using a service
        FetchMovieTask task = new FetchMovieTask(this);
        task.execute(FetchMovieTask.SORT_ORDER_POPULAR);
        FetchMovieTask task2 = new FetchMovieTask(this);
        task2.execute(FetchMovieTask.SORT_ORDER_TOP_RATED);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_sort_popular:
                mCurrentLoader = MOVIE_POPULAR_LOADER;
                getSupportLoaderManager().restartLoader(MOVIE_POPULAR_LOADER, null, this);
                return true;
            case R.id.menu_sort_top_rated:
                mCurrentLoader = MOVIE_TOP_RATED_LOADER;
                getSupportLoaderManager().restartLoader(MOVIE_TOP_RATED_LOADER, null, this);
                return true;
            case R.id.menu_sort_favorites:
                mCurrentLoader = MOVIE_FAVORITE_LOADER;
                getSupportLoaderManager().restartLoader(MOVIE_FAVORITE_LOADER, null, this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case MOVIE_POPULAR_LOADER:
                return new CursorLoader(
                        this,
                        MovieContract.MovieEntry.CONTENT_URI.buildUpon()
                                .appendPath(Movie.Context.POPULAR.toString().toLowerCase()).build(),
                        null,
                        null,
                        null,
                        null
                );
            case MOVIE_TOP_RATED_LOADER:
                return new CursorLoader(
                        this,
                        MovieContract.MovieEntry.CONTENT_URI.buildUpon()
                                .appendPath(Movie.Context.TOP_RATED.toString().toLowerCase())
                                .build(),
                        null,
                        null,
                        null,
                        null
                );
            case MOVIE_FAVORITE_LOADER:
                List<Long> favorites = mFavoriteHelper.getFavorites();

                // Create a selection in the form of " id IN (?, ?, ?, ...)"
                String selection = MovieContract.MovieEntry.COLUMN_ID + " IN (?";
                for (int i = 1; i < favorites.size(); i++) selection += ", ?";
                selection += ")";

                // Convert the list of favorites to a string array of favorites
                String[] selectionArgs = TextUtils.join(",", favorites).split(",");
                return new CursorLoader(
                        this,
                        MovieContract.MovieEntry.CONTENT_URI,
                        null,
                        selection,
                        selectionArgs,
                        null
                );
            default:
                return null;
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        ((SimpleItemRecyclerViewAdapter) mRecyclerView.getAdapter()).swapCursor(data);
        mRecyclerView.scrollToPosition(0);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        ((SimpleItemRecyclerViewAdapter) mRecyclerView.getAdapter()).swapCursor(null);
    }

    @Override
    public void onFavoriteChange() {
        if (mCurrentLoader == MOVIE_FAVORITE_LOADER)
            getSupportLoaderManager().restartLoader(MOVIE_FAVORITE_LOADER, null, this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save the currently selected sort order / loader
        outState.putInt(SAVED_LOADER, mCurrentLoader);

        super.onSaveInstanceState(outState);
    }

    public class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        Cursor dataCursor;

        public SimpleItemRecyclerViewAdapter() {
        }

        public Cursor swapCursor(Cursor cursor) {
            if (dataCursor == cursor) return null;

            Cursor oldCursor = dataCursor;
            dataCursor = cursor;
            if (cursor != null) notifyDataSetChanged();

            return oldCursor;
        }

        public void changeCursor(Cursor cursor) {
            Cursor oldCursor = swapCursor(cursor);
            if (oldCursor != null) oldCursor.close();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.movie_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final Movie movie = getMovie(position);

            if (movie == null) return;

            Picasso.with(MovieListActivity.this).load(movie.getImageUrl())
                    .error(R.drawable.loading).into(holder.mImageView);

            holder.mView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mTwoPane) {
                        Bundle arguments = new Bundle();
                        arguments.putParcelable(MovieDetailFragment.ARG_MOVIE_URI,
                                MovieContract.MovieEntry.buildMovieUri(movie.getId()));
                        MovieDetailFragment fragment = new MovieDetailFragment();
                        fragment.setArguments(arguments);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.movie_detail_container, fragment)
                                .commit();
                    } else {
                        Context context = v.getContext();
                        Intent intent = new Intent(context, MovieDetailActivity.class);
                        intent.putExtra(MovieDetailFragment.ARG_MOVIE_URI,
                                MovieContract.MovieEntry.buildMovieUri(movie.getId()));

                        context.startActivity(intent);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return (dataCursor == null) ? 0 : dataCursor.getCount();
        }

        private Movie getMovie(int position) {
            dataCursor.moveToPosition(position);
            return Movie.fromCursor(dataCursor);
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public final ImageView mImageView;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mImageView = (ImageView) view.findViewById(R.id.item_image_view);
            }
        }
    }
}
