package com.example.mnaganu.dcbt;

import com.example.mnaganu.dcbt.domain.model.SampleModel;
import com.example.mnaganu.dcbt.domain.model.SelectModel;
import com.example.mnaganu.dcbt.domain.repository.CopySourceSampleRepository;
import com.example.mnaganu.dcbt.domain.repository.CopyToSampleRepository;
import com.example.mnaganu.dcbt.domain.service.DataCopyService;
import com.example.mnaganu.dcbt.infrastructure.mapper.SampleRowMapper;
import com.example.mnaganu.dcbt.infrastructure.repository.CopyToSampleRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;

@SpringBootTest
@SpringBatchTest
public class TaskletsJobMockTest {

    private final JobLauncherTestUtils jobLauncherTestUtils;
    private DataSource copySourceDataSource;
    private DataSource copyToDataSource;

    @MockBean
    CopySourceSampleRepository mockCopySourceSampleRepository;

    @Autowired
    public TaskletsJobMockTest(
            JobLauncherTestUtils jobLauncherTestUtils,
            @Qualifier("copySourceDataSource") DataSource copySourceDataSource,
            @Qualifier("copyToDataSource") DataSource copyToDataSource) {
        this.jobLauncherTestUtils = jobLauncherTestUtils;
        this.copySourceDataSource = copySourceDataSource;
        this.copyToDataSource = copyToDataSource;
    }


    @Test
    void copy_コピー先のテーブル更新に失敗() {
        //CopySourceSampleRepository のモックの設定
        //id が重複したリストを作成する
        List<SampleModel> copySourceList = new ArrayList<>();
        copySourceList.add(
                SampleModel.builder()
                        .id(1)
                        .name("name1")
                        .build());
        copySourceList.add(
                SampleModel.builder()
                        .id(1)
                        .name("name2")
                        .build());

        //id が重複したリストを持つ SelectModel を作成
        SelectModel<SampleModel> selectModel =
                SelectModel.<SampleModel>builder()
                        .list(copySourceList)
                        .offset(0)
                        .limit(1000)
                        .total(2)
                        .build();

        //select メソッドが呼ばれたら、キーが重複したデータを返すようにする
        Mockito.when(mockCopySourceSampleRepository.select(anyInt(), anyInt())).thenReturn(selectModel);

        //コピー先のテーブルデータが0件であることの確認
        createTable(copyToDataSource);
        List<SampleModel> copyToList = getTableData(copyToDataSource);
        assertThat(copyToList).isEmpty();

        //Job実行
        try {
            JobExecution jobExecution = jobLauncherTestUtils.launchJob();
            assertThat(ExitStatus.FAILED.getExitCode()).isEqualTo(jobExecution.getExitStatus().getExitCode());
            assertThat(jobExecution.getExitStatus().getExitDescription().contains("コピー先の sample テーブルの insert に失敗しました。")).isTrue();
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    private void dropTable(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String sql = "DROP TABLE IF EXISTS `sample`";
        jdbcTemplate.execute(sql);
    }

    private void createTable(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String sql = "DROP TABLE IF EXISTS `sample`";
        jdbcTemplate.execute(sql);

        sql = "CREATE TABLE `sample` (" +
                "`id` int UNIQUE NOT NULL," +
                "`name` text" +
                ");";
        jdbcTemplate.execute(sql);
    }

    private void createTestData(DataSource dataSource, String name, int count) {
        if (count < 1) {
            return;
        }
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (int i=0; i< count; i++) {
            String sql = String.format("INSERT INTO sample VALUES( %d, '%s%d');", i, name, i);
            jdbcTemplate.execute(sql);
        }
    }

    private List<SampleModel> getTableData(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        String sql = "SELECT * FROM sample";
        SampleRowMapper sampleRowMapper = new SampleRowMapper();
        return jdbcTemplate.query(sql, sampleRowMapper);
    }

}
