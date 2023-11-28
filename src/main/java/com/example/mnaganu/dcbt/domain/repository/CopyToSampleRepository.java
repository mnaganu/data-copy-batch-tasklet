package com.example.mnaganu.dcbt.domain.repository;

import com.example.mnaganu.dcbt.domain.model.SampleModel;
import com.example.mnaganu.dcbt.domain.model.SelectModel;

import java.util.Optional;

public interface CopyToSampleRepository {
    void truncate();
    Optional<SampleModel> selectById(int id);
    SelectModel<SampleModel> select(int offset, int limit);
    int insert(SampleModel model);
}
