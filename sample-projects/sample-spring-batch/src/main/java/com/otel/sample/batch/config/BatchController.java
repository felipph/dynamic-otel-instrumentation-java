package com.otel.sample.batch.config;

import com.otel.sample.batch.reader.TransactionReader;
import com.otel.sample.batch.writer.TransactionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller to trigger and monitor batch jobs.
 */
@RestController
@RequestMapping("/api/batch")
public class BatchController {

    private static final Logger log = LoggerFactory.getLogger(BatchController.class);

    private final JobLauncher jobLauncher;
    private final Job transactionProcessingJob;
    private final TransactionReader transactionReader;
    private final TransactionWriter transactionWriter;
    private final JobRepository jobRepository;

    public BatchController(JobLauncher jobLauncher,
                           Job transactionProcessingJob,
                           TransactionReader transactionReader,
                           TransactionWriter transactionWriter,
                           JobRepository jobRepository) {
        this.jobLauncher = jobLauncher;
        this.transactionProcessingJob = transactionProcessingJob;
        this.transactionReader = transactionReader;
        this.transactionWriter = transactionWriter;
        this.jobRepository = jobRepository;
    }

    @PostMapping("/run")
    public Map<String, Object> runJob() throws Exception {
        log.info("REST request to run batch job");

        // Reset reader for new run
        transactionReader.reset();

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncher.run(transactionProcessingJob, jobParameters);

        Map<String, Object> result = new HashMap<>();
        result.put("jobId", execution.getJobId());
        result.put("status", execution.getStatus().name());
        result.put("startTime", execution.getStartTime());
        result.put("processedCount", transactionWriter.getCount());

        return result;
    }

    @GetMapping("/status/{jobId}")
    public Map<String, Object> getJobStatus(@PathVariable Long jobId) {
        log.info("REST request to get job status: {}", jobId);

        JobExecution execution = jobRepository.getLastJobExecution(
                "transactionProcessingJob",
                new JobParametersBuilder()
                        .addLong("job.id", jobId)
                        .toJobParameters()
        );

        Map<String, Object> result = new HashMap<>();
        if (execution != null) {
            result.put("jobId", execution.getJobId());
            result.put("status", execution.getStatus().name());
            result.put("startTime", execution.getStartTime());
            result.put("endTime", execution.getEndTime());
            result.put("exitStatus", execution.getExitStatus().getExitCode());
        } else {
            result.put("error", "Job not found");
        }

        return result;
    }

    @GetMapping("/processed")
    public Map<String, Object> getProcessedTransactions() {
        log.info("REST request to get processed transactions");
        Map<String, Object> result = new HashMap<>();
        result.put("count", transactionWriter.getCount());
        result.put("transactions", transactionWriter.getProcessedTransactions());
        return result;
    }

    @DeleteMapping("/processed")
    public Map<String, Object> clearProcessedTransactions() {
        log.info("REST request to clear processed transactions");
        int count = transactionWriter.getCount();
        transactionWriter.clear();
        Map<String, Object> result = new HashMap<>();
        result.put("cleared", count);
        return result;
    }
}
