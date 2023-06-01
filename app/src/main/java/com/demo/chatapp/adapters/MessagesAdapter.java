package com.demo.chatapp.adapters;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.demo.chatapp.pojo.Message;
import com.demo.chatapp.R;
import com.demo.chatapp.screens.image.ImageActivity;
import com.demo.chatapp.screens.messages.MessagesListPresenter;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessagesViewHolder> {

    private static final int TYPE_MY_MESSAGE = 0;
    private static final int TYPE_OTHER_MESSAGE = 1;

    private List<Message> messages;
    private Context context;
    private OnMessageClickListener onMessageClickListener;
    private OnReachEndListener onReachEndListener;
    private MessagesListPresenter presenter;

    public interface OnMessageClickListener {
        void onMessageClick(int position);

        void onMessageLongClick(int position);
    }

    public interface OnReachEndListener {
        void onReachEnd();

        void onReachNotEnd();
    }

    public MessagesAdapter(Context context, MessagesListPresenter presenter) {
        this.messages = new ArrayList<>();
        this.context = context;
        this.presenter = presenter;
    }

    public void clearMessages() {
        if (messages != null) {
            messages.clear();
        }
    }

    public void setOnMessageClickListener(OnMessageClickListener onMessageClickListener) {
        this.onMessageClickListener = onMessageClickListener;
    }

    public void setOnReachEndListener(OnReachEndListener onReachEndListener) {
        this.onReachEndListener = onReachEndListener;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessagesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == TYPE_MY_MESSAGE) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_message, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_other_message, parent, false);
        }
        return new MessagesViewHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messages.get(position);
        String author = message.getAuthor();
        if (author != null && author.equals(PreferenceManager.getDefaultSharedPreferences(context).getString("author", "Anonym"))) {
            return TYPE_MY_MESSAGE;
        } else {
            return TYPE_OTHER_MESSAGE;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessagesViewHolder holder, int position) {
        Message message = messages.get(position);
        String author = message.getAuthor();
        String textOfMessage = message.getTextOfMessage();
        String urlToImage = message.getImageUrl();
        holder.textViewAuthor.setText(author);
        if (textOfMessage != null && !textOfMessage.isEmpty()) {
            holder.textViewTextOfMessage.setVisibility(View.VISIBLE);
            holder.textViewTextOfMessage.setText(textOfMessage);
            holder.imageViewImage.setVisibility(View.GONE);
        }
        if (urlToImage != null && !urlToImage.isEmpty()) {
            holder.textViewTextOfMessage.setVisibility(View.GONE);
            holder.imageViewImage.setVisibility(View.VISIBLE);
            Picasso.get().load(urlToImage).into(holder.imageViewImage);
        }

        if (onReachEndListener != null) {
            onReachEndListener.onReachNotEnd();
            if (position == getItemCount() - 1) {
                onReachEndListener.onReachEnd();
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class MessagesViewHolder extends RecyclerView.ViewHolder {
        private TextView textViewAuthor;
        private TextView textViewTextOfMessage;
        private ImageView imageViewImage;

        public MessagesViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewAuthor = itemView.findViewById(R.id.textViewAuthor);
            textViewTextOfMessage = itemView.findViewById(R.id.textViewTextOfMessage);
            imageViewImage = itemView.findViewById(R.id.imageViewImage);
            itemView.setOnClickListener(view -> {
                if (onMessageClickListener != null) {
                    onMessageClickListener.onMessageClick(getAdapterPosition());
                }
                String uri = messages.get(getAdapterPosition()).getImageUrl();
                if (uri != null && !uri.isEmpty()) {
                    Intent intent = new Intent(context, ImageActivity.class);
                    intent.putExtra("uriImage", uri);
                    ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation((Activity) context, imageViewImage, "message");
                    context.startActivity(intent, options.toBundle());
                }
            });
            itemView.setOnLongClickListener(view -> {
                if (onMessageClickListener != null) {
                    onMessageClickListener.onMessageLongClick(getAdapterPosition());
                }
                createPopupMenu();
                return true;
            });
        }

        private void createPopupMenu() {
            PopupMenu popupMenu = new PopupMenu(itemView.getContext(), itemView);
            popupMenu.inflate(R.menu.popupmenu);
            popupMenu
                    .setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            switch (item.getItemId()) {
                                case R.id.itemShare:
                                    Message message = messages.get(getAdapterPosition());
                                    if (message != null) {
                                        String imageUrl = message.getImageUrl();
                                        String textOfMessage = message.getTextOfMessage();
                                        String result;
                                        if (imageUrl != null) {
                                            result = imageUrl;
                                        } else {
                                            result = textOfMessage;
                                        }
                                        Intent intent = new Intent(Intent.ACTION_SEND);
                                        intent.setType("text/plain");
                                        intent.putExtra(Intent.EXTRA_TEXT, result);
                                        context.startActivity(intent);
                                        return true;
                                    }
                                case R.id.itemDelete:
                                    presenter.deleteMessage(getAdapterPosition());
                                    return true;
                                default:
                                    return false;
                            }
                        }
                    });

            popupMenu.setOnDismissListener(new PopupMenu.OnDismissListener() {
                @Override
                public void onDismiss(PopupMenu menu) {
                }
            });
            popupMenu.show();
        }
    }
}