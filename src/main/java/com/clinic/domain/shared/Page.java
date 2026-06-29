package com.clinic.domain.shared;

import java.util.List;

public class Page<T> {

    public final List<T> items;
    public final String nextCursor;

    public Page(List<T> items, String nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
