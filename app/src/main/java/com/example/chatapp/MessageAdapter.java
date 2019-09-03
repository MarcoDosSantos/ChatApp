package com.example.chatapp;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private ArrayList<Message> messageList;
    private Context context;

    public MessageAdapter(ArrayList<Message> messageList, Context context) {
        this.messageList = messageList;
        this.context = context;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_message, viewGroup, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder messageViewHolder, int i) {
        Message currentMessage = messageList.get(i);
        String st_name = currentMessage.getName();
        String st_text = currentMessage.getText();
        String st_photo_url = currentMessage.getPhotoUrl();

        messageViewHolder.messageTextView.setText(st_text);
        messageViewHolder.nameTextView.setText(st_name);
        Glide.with(context).load(st_photo_url).into(messageViewHolder.photoImageView);
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public class MessageViewHolder extends RecyclerView.ViewHolder {
        ImageView photoImageView;
        TextView messageTextView, nameTextView;
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            photoImageView = itemView.findViewById(R.id.photoImageView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
        }
    }
}
