package com.drift.media;

/** Snapshot of one media session as exposed by the OS. Unknown values are null/-1 rather than invented. */
public record MediaSession(String id, String app, String title, String artist, String album, Status status,
                           double positionSeconds, double durationSeconds, boolean canPlayPause, boolean canNext,
                           boolean canPrevious, boolean hasArtwork) {
    public enum Status { PLAYING, PAUSED, STOPPED, OTHER }

    public String display() {
        String t = title == null || title.isBlank() ? "Unknown title" : title;
        return artist == null || artist.isBlank() ? t : t + " - " + artist;
    }
}
