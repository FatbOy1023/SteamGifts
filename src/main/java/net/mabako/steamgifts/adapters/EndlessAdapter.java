package net.mabako.steamgifts.adapters;

import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.mabako.steamgifts.R;

import java.util.ArrayList;
import java.util.List;

/**
 * An adapter for loading pseudo-endless lists of giveaways, discussions, games, comments and so forth.
 */
public abstract class EndlessAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = EndlessAdapter.class.getSimpleName();

    /**
     * First page to ever be seen on a list.
     */
    public static final int FIRST_PAGE = 1;

    /**
     * Last page we should reasonably expect.
     */
    public static final int LAST_PAGE = 11223344;

    /**
     * View ID for "Loading..."
     */
    private static final int PROGRESS_VIEW = -1;

    /**
     * View ID for "This is the end."
     */
    private static final int END_VIEW = -2;

    /**
     * Sticky items, for example when using cards.
     */
    private IEndlessAdaptable stickyItem = null;

    /**
     * The list of items this adapter holds.
     */
    private final List<IEndlessAdaptable> items = new ArrayList<>();

    /**
     * Are we currently loading?
     */
    private boolean loading = false;

    /**
     * Upon reaching the end of the current list, we'd want to execute the listener.
     */
    private OnLoadListener loadListener;

    /**
     * Are we at the end of the list yet?
     */
    private boolean reachedTheEnd;

    /**
     * What page are we currently on?
     */
    private int page = FIRST_PAGE;

    /**
     * If set to true, we start from the bottom instead of the top.
     */
    private boolean viewInReverse = false;

    /**
     * If set to true, you've reached the end if all loaded items have appeared on a previous page.
     */
    protected boolean alternativeEnd = false;

    private RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (layoutManager == null)
                throw new IllegalStateException("Can't handle scrolling without a LayoutManager");

            int lastVisibleItem = layoutManager.findLastVisibleItemPosition();

            if (!loading && layoutManager.getItemCount() <= (lastVisibleItem + 5)) {
                startLoading();
            }
        }
    };

    public EndlessAdapter(@NonNull OnLoadListener listener) {
        loadListener = listener;
    }

    /**
     * Start loading by insert a progress bar item.
     */
    private void startLoading() {
        if (reachedTheEnd)
            return;

        loading = true;

        // Insert bogus item for the progress bar.
        items.add(null);
        notifyItemInserted(getItemCount() - 1);

        Log.d(TAG, "Starting to load more content on page " + page);
        loadListener.onLoad(page);
    }

    public void finishLoading(List<IEndlessAdaptable> addedItems) {
        Log.d(TAG, "Finished loading - " + loading);
        if (loading) {
            // remove loading item for the progress bar
            if (items.size() > 0) {
                items.remove(items.size() - 1);
                notifyItemRemoved(getItemCount());
            }

            loading = false;
            addAll(addedItems);
        } else {
            addAll(addedItems);
        }

        // Have we reached the last page yet?
        if (viewInReverse) {
            --page;
            if (page < FIRST_PAGE && !reachedTheEnd)
                reachedTheEnd();
        } else
            ++page;
    }

    public void cancelLoading() {
        loading = false;
    }

    public void reachedTheEnd() {
        Log.d(TAG, "Reached the end");

        // Make sure we're not loading anymore...
        if (loading)
            finishLoading(new ArrayList<IEndlessAdaptable>());

        reachedTheEnd = true;

        items.add(null);

        notifyItemInserted(getItemCount() - 1);
    }

    protected List<IEndlessAdaptable> getItems() {
        return items;
    }

    /**
     * How many items do we currently show?
     *
     * @return
     */
    public int getItemCount() {
        int itemCount = items.size();
        if (stickyItem != null)
            itemCount++;
        return itemCount;
    }

    /**
     * Add a whole range of items to this adapter, and check if we've reached the end.
     *
     * @param items items to add.
     */
    private void addAll(List<IEndlessAdaptable> items) {
        if (items.size() > 0) {
            boolean enoughItems = hasEnoughItems(items);
            // remove all things we already have
            items.removeAll(this.items);

            this.items.addAll(items);

            notifyItemRangeInserted(getItemCount() - items.size(), items.size());

            if (enoughItems && items.size() == 0 && alternativeEnd) {
                enoughItems = false;
            }

            if (viewInReverse && page > FIRST_PAGE) {
                enoughItems = true;
            }

            // Did we have enough items and have not reached the end?
            if (!enoughItems && !reachedTheEnd)
                reachedTheEnd();
        } else {
            reachedTheEnd();
        }
    }

    public void clear() {
        Log.d(TAG, "Clearing list");

        items.clear();
        reachedTheEnd = false;
        page = viewInReverse ? LAST_PAGE : FIRST_PAGE;

        notifyDataSetChanged();
    }

    public IEndlessAdaptable getItem(int position) {
        if (stickyItem != null) {
            return position == 0 ? stickyItem : items.get(position - 1);
        } else {
            return items.get(position);
        }
    }

    public IEndlessAdaptable getStickyItem() {
        return stickyItem;
    }

    public void setStickyItem(IEndlessAdaptable stickyItem) {
        if (this.stickyItem == null) {
            this.stickyItem = stickyItem;
            notifyItemInserted(0);
        } else {
            this.stickyItem = stickyItem;
            notifyItemChanged(0);
        }
    }

    /**
     * Return the view type for the item at a specific position.
     *
     * @param position position of the item
     * @return the layout if it's an actual item, {@link #PROGRESS_VIEW} or {@link #END_VIEW} if it's a progress spinner or the end.
     */
    public int getItemViewType(int position) {
        return position < getItemCount() && getItem(position) != null ? getItem(position).getLayout() : reachedTheEnd ? END_VIEW : PROGRESS_VIEW;
    }

    /**
     * Create the ViewHolder for the item
     *
     * @param parent
     * @param viewType type of the view
     * @return
     */
    public final RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == PROGRESS_VIEW || viewType == END_VIEW) {
            View view = LayoutInflater.from(parent.getContext()).inflate(viewType == PROGRESS_VIEW ? R.layout.endless_progress_bar : R.layout.endless_scroll_end, parent, false);
            return new EmptyViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);

            RecyclerView.ViewHolder holder = onCreateActualViewHolder(view, viewType);
            if (holder == null)
                throw new IllegalStateException("Got no giveaway holder for " + viewType);
            return holder;
        }
    }

    /**
     * Proxy binding a viewholder to the item. In particular, if this is not a custom item, but a progress/end view, nothing will be called upon.
     *
     * @param holder   view holder instance
     * @param position position of the item
     */
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder != null && !(holder instanceof EmptyViewHolder)) {
            onBindActualViewHolder(holder, position);
        }
    }

    /**
     * Create a view holder for item.
     *
     * @param view     the instantiated view
     * @param viewType the view's layout id
     * @return viewholder for the view
     */
    protected abstract RecyclerView.ViewHolder onCreateActualViewHolder(View view, int viewType);

    /**
     * Bind a viewholder to a particular item
     *
     * @param holder   view holder instance
     * @param position position of the item
     */
    protected abstract void onBindActualViewHolder(RecyclerView.ViewHolder holder, int position);

    /**
     * Check whether or not we have enough items to load more (i.e. page is full)
     *
     * @param items
     * @return {@code true} if more items can be loaded, {@code false} otherwise
     */
    protected abstract boolean hasEnoughItems(List<IEndlessAdaptable> items);


    protected void removeItem(int position) {
        items.remove(position);
        notifyItemRemoved(position);
    }

    /**
     * Clear the list of elements if you're on the first page if {@link #viewInReverse} is not set, or the last page if {@link #viewInReverse} is set.
     *
     * @param page     current page
     * @param lastPage is this page the last page?
     */
    public void notifyPage(int page, boolean lastPage) {
        Log.d(TAG, "asdf " + page + ", " + this.page + ", " + lastPage + ", " + viewInReverse);
        if (viewInReverse && lastPage) {
            clear();
            this.page = page;
        } else if (!viewInReverse && page == 1)
            clear();
    }

    /**
     * Start from the last page, instead of the first.
     */
    public void setViewInReverse() {
        if (!alternativeEnd)
            throw new UnsupportedOperationException("could not reverse an endless adapter without alternativeEnd set [will have no content on the last pages]");

        viewInReverse = true;
        page = LAST_PAGE;
    }

    /**
     * Is this list viewed in reverse?
     *
     * @return true if the list is viewed in reverse, false otherwise
     */
    public boolean isViewInReverse() {
        return viewInReverse;
    }

    /**
     * Get the scroll listener associated with this adapter.
     *
     * @return scroll listener to bind the view to
     */
    public RecyclerView.OnScrollListener getScrollListener() {
        return scrollListener;
    }

    /**
     * Does this item have any items loaded yet?
     *
     * @return true if any items are loaded, false otherwise
     */
    public boolean isEmpty() {
        return stickyItem == null && items.isEmpty();
    }

    /**
     * View holder with no interactions.
     * <p/>
     * This is the case for the progress bar and the "You've reached the end" text.
     */
    public static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(View v) {
            super(v);
        }
    }

    /**
     * Listener called upon scrolling down to load "more" items.
     */
    public interface OnLoadListener {
        void onLoad(int page);
    }
}