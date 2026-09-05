package com.freepets.domain.pet.entity;

public enum BreedSize {
    SMALL("소형견"),
    MEDIUM("중형견"),
    LARGE("대형견");

    private final String label;

    BreedSize(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
