package com.sherry.schedule.data;

public final class Memo {
    public long id;
    public String title = "";
    public String content = "";
    public boolean pinned;
    public boolean completed;
    public boolean archived;
    public long linkedEventId = -1L;
    public long createdAt;
    public long updatedAt;
}
