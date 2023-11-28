package com.example.mnaganu.dcbt.domain.tasklets;

import com.example.mnaganu.dcbt.domain.service.DataCopyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DataCopyTasklet implements Tasklet, StepExecutionListener {
    private final Logger logger = LoggerFactory.getLogger(DataCopyTasklet.class);

    private final DataCopyService dataCopyService;

    @Autowired
    public DataCopyTasklet(DataCopyService dataCopyService) {
        this.dataCopyService = dataCopyService;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        logger.info("execute call");
        dataCopyService.copy();
        return RepeatStatus.FINISHED;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        logger.info("beforeStep call");
        StepExecutionListener.super.beforeStep(stepExecution);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        logger.info("afterStep call");
        return StepExecutionListener.super.afterStep(stepExecution);
    }
}
