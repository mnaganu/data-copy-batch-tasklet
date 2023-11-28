package com.example.mnaganu.dcbt.domain.model;

import java.util.Optional;

@lombok.Value
@lombok.Builder
public class SampleModel {
    @lombok.NonNull
    private final Integer id;
    private final String name;

    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }
}
