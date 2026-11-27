package com.threatdetection.intelligence.exception;

public class FeedNotPollableException extends RuntimeException {

    private final String feedName;

    public FeedNotPollableException(String feedName, String message) {
        super(message);
        this.feedName = feedName;
    }

    public String getFeedName() {
        return feedName;
    }
}
