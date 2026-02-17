package com.otel.sample.batch.config;

import com.otel.sample.batch.entity.Transaction;
import com.otel.sample.batch.processor.TransactionProcessor;
import com.otel.sample.batch.reader.TransactionReader;
import com.otel.sample.batch.writer.TransactionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Batch job configuration demonstrating:
 * - Chunk-oriented processing
 * - Item reader, processor, writer pattern
 */
@Configuration
@EnableBatchProcessing
public class BatchConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchConfig.class);

    private final TransactionReader transactionReader;
    private final TransactionProcessor transactionProcessor;
    private final TransactionWriter transactionWriter;

    public BatchConfig(TransactionReader transactionReader,
                       TransactionProcessor transactionProcessor,
                       TransactionWriter transactionWriter) {
        this.transactionReader = transactionReader;
        this.transactionProcessor = transactionProcessor;
        this.transactionWriter = transactionWriter;
    }

    @Bean
    public Job transactionProcessingJob(JobRepository jobRepository, Step transactionStep) {
        log.info("Configuring transaction processing job");
        return new JobBuilder("transactionProcessingJob", jobRepository)
                .start(transactionStep)
                .build();
    }

    @Bean
    public Step transactionStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager) {
        log.info("Configuring transaction step");
        return new StepBuilder("transactionStep", jobRepository)
                .<Transaction, Transaction>chunk(5, transactionManager)
                .reader(transactionReader)
                .processor(transactionProcessor)
                .writer(transactionWriter)
                .build();
    }
}
