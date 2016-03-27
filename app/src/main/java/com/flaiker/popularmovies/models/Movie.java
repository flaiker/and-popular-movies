package com.flaiker.popularmovies.models;

public class Movie {
    private final String id;
    private final String name;

    public Movie(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
