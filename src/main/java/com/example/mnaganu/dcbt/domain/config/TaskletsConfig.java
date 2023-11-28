package com.example.mnaganu.dcbt.domain.config;

import com.example.mnaganu.dcbt.domain.tasklets.DataCopyTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TaskletsConfig {

    private final DataCopyTasklet dataCopyTasklet;

    @Autowired
    public TaskletsConfig (DataCopyTasklet dataCopyTasklet) {
        this.dataCopyTasklet = dataCopyTasklet;
    }

    //@Bean
    //DataCopyTasklet getDataCopyTasklet() {
    //    return this.dataCopyTasklet;
    //}

    @Bean
    protected Step dataCopyStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("dataCopyStep", jobRepository)
                .tasklet(this.dataCopyTasklet, transactionManager)
                .build();
    }

    @Bean
    public Job job(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        /*
          JobのIDはDB（メタデータ）に登録される。
          JobのIDはプライマリーキーになっているので、重複しないようにインクリメントする必要がある。
         */
        return new JobBuilder("dataCopyJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(dataCopyStep(jobRepository, transactionManager))
                .build();
    }

}