package com.harishun.hidinjector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import java.util.List;

public class ShortcutAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_ITEM = 0;
    private static final int TYPE_ADD = 1;

    public interface OnShortcutClickListener {
        void onShortcutClick(ShortcutItem item);
        void onShortcutDoublePress(ShortcutItem item);
        void onAddShortcutClick();
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    private final Context context;
    private final List<ShortcutItem> items;
    private final ShortcutManagerHelper helper;
    private final OnShortcutClickListener listener;

    public ShortcutAdapter(Context context, List<ShortcutItem> items, ShortcutManagerHelper helper, OnShortcutClickListener listener) {
        this.context = context;
        this.items = items;
        this.helper = helper;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == items.size()) {
            return TYPE_ADD;
        }
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_ADD) {
            View view = inflater.inflate(R.layout.item_shortcut_add, parent, false);
            return new AddViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_shortcut, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_ITEM) {
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            ShortcutItem item = items.get(position);
            itemHolder.tvName.setText(item.name);
            itemHolder.ivIcon.setImageResource(helper.getIconResourceId(item.iconName));

            GestureDetector detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    listener.onShortcutClick(item);
                    return true;
                }
                @Override
                public void onLongPress(MotionEvent e) {
                    listener.onShortcutDoublePress(item);
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    listener.onShortcutDoublePress(item);
                    return true;
                }
            });

            itemHolder.itemView.setOnTouchListener((v, event) -> {
                boolean handled = detector.onTouchEvent(event);
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.performClick();
                }
                return handled;
            });
        } else {
            AddViewHolder addHolder = (AddViewHolder) holder;
            addHolder.itemView.setOnClickListener(v -> listener.onAddShortcutClick());
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition >= items.size() || toPosition >= items.size()) {
            return;
        }
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(items, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(items, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
        helper.saveShortcuts(items);
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvName;
        public final ImageView ivIcon;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_shortcut_name);
            ivIcon = itemView.findViewById(R.id.iv_shortcut_icon);
        }
    }

    public static class AddViewHolder extends RecyclerView.ViewHolder {
        public AddViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
