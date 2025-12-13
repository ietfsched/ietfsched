package org.ietf.ietfsched;

import androidx.test.espresso.IdlingResource;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

/**
 * IdlingResource that waits for a ListFragment's query to complete
 * 
 * This resource monitors a view hierarchy and becomes idle when either:
 * - The ListView (android.R.id.list) is visible, OR
 * - The empty view (android.R.id.empty) is visible
 * 
 * This indicates that the async query has completed and the fragment has
 * updated its UI accordingly.
 */
public class ListQueryIdlingResource implements IdlingResource {
    private static final String TAG = "ListQueryIdlingResource";
    
    private ResourceCallback resourceCallback;
    private View rootView;
    
    /**
     * Set the root view to monitor (typically the fragment's root view or activity's content view)
     */
    public void setRootView(View rootView) {
        this.rootView = rootView;
        Log.d(TAG, "setRootView: Monitoring view hierarchy for list/empty view");
    }
    
    @Override
    public String getName() {
        return "ListQueryIdlingResource";
    }
    
    private long startTime = 0;
    private int checkCount = 0;
    
    @Override
    public boolean isIdleNow() {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        checkCount++;
        long elapsed = System.currentTimeMillis() - startTime;
        
        // Always log first 5 checks, then every 20th check
        boolean shouldLog = checkCount <= 5 || checkCount % 20 == 0;
        
        if (rootView == null) {
            if (shouldLog) {
                Log.d(TAG, String.format("[%dms] check#%d: Root view not set yet, waiting...", elapsed, checkCount));
            }
            return false; // Not idle until root view is set
        }
        
        if (shouldLog && checkCount <= 10) {
            Log.d(TAG, String.format("[%dms] check#%d: Root view is set, checking hierarchy...", elapsed, checkCount));
        }
        
        // Find TabHost's tabcontent, which contains the fragment container
        View tabContent = findViewInHierarchy(rootView, android.R.id.tabcontent);
        if (tabContent == null) {
            // Log every 2 seconds
            if (checkCount % 20 == 0 || elapsed < 2000) {
                Log.d(TAG, String.format("[%dms] isIdleNow: tabContent not found", elapsed));
            }
            return false;
        }
        
        // Check if fragment container exists (R.id.fragment_sessions)
        // We'll search for any FrameLayout in tabcontent that might be the fragment container
        View fragmentContainer = findFragmentContainer(tabContent);
        if (fragmentContainer == null || !(fragmentContainer instanceof ViewGroup)) {
            // Log every 2 seconds
            if (checkCount % 20 == 0 || elapsed < 2000) {
                Log.d(TAG, String.format("[%dms] isIdleNow: fragmentContainer not found (tabContent children=%d)", 
                      elapsed, tabContent instanceof ViewGroup ? ((ViewGroup)tabContent).getChildCount() : 0));
                if (tabContent instanceof ViewGroup) {
                    dumpViewHierarchy((ViewGroup)tabContent, 0);
                }
            }
            return false;
        }
        
        ViewGroup container = (ViewGroup) fragmentContainer;
        if (container.getChildCount() == 0) {
            // Log every 2 seconds
            if (checkCount % 20 == 0 || elapsed < 2000) {
                Log.d(TAG, String.format("[%dms] isIdleNow: fragmentContainer has no children", elapsed));
            }
            return false;
        }
        
        // Search for ListView within the fragment container first
        View listView = findViewInHierarchy(container, android.R.id.list);
        
        // Also check root for ListView as fallback
        if (listView == null) {
            listView = findViewInHierarchy(rootView, android.R.id.list);
        }
        
        // Check if ListView has an adapter set (indicates query completed)
        boolean adapterReady = false;
        int adapterCount = -1;
        View emptyView = null;
        if (listView != null && listView instanceof android.widget.ListView) {
            android.widget.ListView lv = (android.widget.ListView) listView;
            android.widget.ListAdapter adapter = lv.getAdapter();
            if (adapter != null) {
                adapterReady = true;
                adapterCount = adapter.getCount();
            }
            // Get empty view directly from ListView (setEmptyText() sets it here)
            emptyView = lv.getEmptyView();
        }
        
        // Also search hierarchy for empty view as fallback
        if (emptyView == null) {
            emptyView = findViewInHierarchy(container, android.R.id.empty);
            if (emptyView == null) {
                emptyView = findViewInHierarchy(rootView, android.R.id.empty);
            }
        }
        
        boolean listVisible = isViewVisible(listView);
        boolean emptyVisible = isViewVisible(emptyView);
        
        // Query is complete if:
        // 1. ListView is visible (has items), OR
        // 2. Empty view is visible (no items), OR
        // 3. Adapter is ready with 0 items AND ListView exists (query completed, UI will update)
        // Note: When adapter has 0 items, ListView automatically shows empty view, but we need
        // to wait for it to become visible. However, if adapter is ready with 0 items, the query
        // is complete and the UI should update soon.
        boolean queryComplete = listVisible || emptyVisible || (adapterReady && adapterCount == 0 && listView != null);
        
        // Log every 2 seconds or first few checks
        if (checkCount % 20 == 0 || elapsed < 3000) {
            Log.d(TAG, String.format("[%dms] isIdleNow: fragmentContainer=%s, childCount=%d, listView=%s, emptyView=%s, listVisible=%s, emptyVisible=%s, adapterReady=%s, adapterCount=%d, queryComplete=%s",
                  elapsed, fragmentContainer != null, 
                  fragmentContainer instanceof ViewGroup ? ((ViewGroup)fragmentContainer).getChildCount() : 0,
                  listView != null, emptyView != null, listVisible, emptyVisible, adapterReady, adapterCount, queryComplete));
            if (listView != null) {
                Log.d(TAG, String.format("  listView: id=%d, visibility=%s", listView.getId(), getVisibilityString(listView.getVisibility())));
            }
            if (emptyView != null) {
                Log.d(TAG, String.format("  emptyView: id=%d, visibility=%s", emptyView.getId(), getVisibilityString(emptyView.getVisibility())));
            }
        }
        
        if (queryComplete) {
            Log.d(TAG, String.format("[%dms] isIdleNow: Query complete - listVisible=%s, emptyVisible=%s, adapterReady=%s, adapterCount=%d", 
                  elapsed, listVisible, emptyVisible, adapterReady, adapterCount));
            if (resourceCallback != null) {
                resourceCallback.onTransitionToIdle();
            }
            return true;
        } else {
            return false;
        }
    }
    
    private void dumpViewHierarchy(ViewGroup parent, int depth) {
        try {
            String indent = new String(new char[depth * 2]).replace('\0', ' ');
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                String idStr = "NO_ID";
                try {
                    if (child.getId() != -1 && child.getResources() != null) {
                        idStr = child.getResources().getResourceName(child.getId());
                    } else {
                        idStr = String.valueOf(child.getId());
                    }
                } catch (Exception e) {
                    idStr = "id=" + child.getId();
                }
                Log.d(TAG, String.format("%s[%d] %s (id=%s, visibility=%s)", 
                      indent, i, child.getClass().getSimpleName(), idStr, getVisibilityString(child.getVisibility())));
                if (child instanceof ViewGroup && depth < 3) {
                    dumpViewHierarchy((ViewGroup)child, depth + 1);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error dumping view hierarchy", e);
        }
    }
    
    /**
     * Find the fragment container (FrameLayout) inside tabcontent
     */
    private View findFragmentContainer(View tabContent) {
        if (!(tabContent instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) tabContent;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            // Fragment container is a FrameLayout
            if (child instanceof android.widget.FrameLayout) {
                return child;
            }
        }
        return null;
    }
    
    private String getVisibilityString(int visibility) {
        switch (visibility) {
            case View.VISIBLE: return "VISIBLE";
            case View.INVISIBLE: return "INVISIBLE";
            case View.GONE: return "GONE";
            default: return "UNKNOWN";
        }
    }
    
    /**
     * Recursively search for a view with the given ID in the view hierarchy
     */
    private View findViewInHierarchy(View root, int id) {
        if (root == null) {
            return null;
        }
        
        if (root.getId() == id) {
            return root;
        }
        
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findViewInHierarchy(group.getChildAt(i), id);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if a view is effectively visible (not GONE and has a visible rect)
     */
    private boolean isViewVisible(View view) {
        if (view == null) {
            return false;
        }
        
        if (view.getVisibility() != View.VISIBLE) {
            return false;
        }
        
        // Check if view has a visible rect
        android.graphics.Rect rect = new android.graphics.Rect();
        boolean hasVisibleRect = view.getGlobalVisibleRect(rect);
        return hasVisibleRect && !rect.isEmpty();
    }
    
    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.resourceCallback = callback;
    }
    
    /**
     * Reset the monitoring state
     */
    public void reset() {
        rootView = null;
        Log.d(TAG, "reset: Reset monitoring state");
    }
}

