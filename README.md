# data-copy-batch-tasklet

## 概要
２つのDBに接続し、test_db1 の Sample テーブルから test_db2 の Sample テーブルへデータをコピーするプログラム。  
[data-copy-batch](https://github.com/mnaganu/data-copy-batch)　を Spring Batch の tasklet を利用するように修正を行った。

## Spring batch
Spring Batchとは、Spring Frameworkを中心としたSpringプロジェクトのひとつで、バッチ開発用のフレームワークです。

## Job と step

### job
Spring Batchにおけるバッチアプリケーションの一連の処理をまとめた1実行単位。

### step
Jobを構成する処理の単位。1つのJobに1～N個のStepをもたせることが可能。  
1つのJobを複数のStepに分割して処理することにより、処理の再利用、並列化、条件分岐が可能になる。  
Stepは、チャンクまたはタスクレットのいずれかで実装する。

## タスクレット(Tasklet)とチャンク(Chunk)

### タスクレット(Tasklet)
データ読み書きのタイミングを開発者が自由に決定できる。  
タスクレット(Tasklet)は、`execute` という 1 つのメソッドを持つ単純なインターフェースです。  
`execute` は、`RepeatStatus.FINISHED` を返すか、例外をスローして失敗を通知するまで、TaskletStep によって繰り返し呼び出されます。

### チャンク(Chunk)
コミットインターバルの設定することで、自動で設定した処理件数ごとにコミットを行う。  
チャンク(Chunk)は、データを一度に1つずつ読み取りトランザクション境界内に書き込まれる「チャンク」を作成すること。  
1 つのアイテムが ItemReader から読み込まれ、ItemProcessor に渡されて集約されます。  
読み込まれたアイテムの数がコミット間隔に等しくなると、ItemWriter によってチャンク全体が書き出され、トランザクションがコミットされます。

## 複数データソース
Spring Batchは、内部でメタテーブルを持っている。  
ジョブの実行時に、メタテーブルに書き込みながら実行する。  
タスクレットモデルでも、チャンクモデルでも、メタテーブルに書き込みながら実行する。  
テーブルのDDLは、各プラットフォームに合わせたsqlファイルがSpring Batchのjarに内包されている。  
バッチ処理で利用するDBとバッチ内部で利用するメタテーブルを分ける。
[Metadata Schema](https://spring.pleiades.io/spring-batch/docs/current/reference/html/schema-appendix.html)

### application.yml
`application.yml` に接続したいDBの情報を記載しておく。  
内部で利用するメタテーブルの情報を`batch`に、コピー元のDBの情報を`copy-source`に、コピー先のDBの情報を`copy-to`に記載する。  
メタテーブルを保存したくない場合は、メタテーブルの保存先を H2DB にすることで、バッチ実行終了後削除される。  
メタテーブルの初期化を常に行うようにしている。
```yml
spring:
  datasource:
    batch:
      # Spring Batch メタデータをMySQLに保存する場合の設定
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:63306/test_db3?userCursorFetch=true
      username: test
      password: testpass
      # Spring Batch メタデータを　H2DB に保存する場合の設定
    # driver-class-name: org.h2.Driver
    # url: jdbc:h2:mem:batch_db:Mode=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false
    # username: test
    # password: testpass
    copy-source:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:63306/test_db1?userCursorFetch=true
      username: test
      password: testpass
      fetchsize: 1000
    copy-to:
      driver-class-name: com.mysql.cj.jdbc.Driver
      url: jdbc:mysql://localhost:63306/test_db2?userCursorFetch=true
      username: test
      password: testpass
      fetchsize: 1000
  batch:
    jdbc:
      # メタテーブルの初期化を常に行うように設定
      initialize-schema: always
```
### Configuration
`application.yml` に記載した情報を取得する`BatchDataSourceConfiguration`クラスを作成する。  
メタデータ格納先DBには`@BatchDataSource`を付与する。  
ただし、メタデータ格納先ではない方のDBに`@Primary`を付与しない正常に動かないので    
コピー元のDBの情報を取得する`CopySourceConfiguration`と  
コピー先の情報を取得する`CopyToConfiguration`のどちらか一方に`@Primary`をつける必要がある。  
今回は、コピー元のDBの情報を取得する`CopySourceConfiguration`に`@Primary`を付与した。
```Java
@lombok.Getter
@lombok.Setter
@Component
@ConfigurationProperties(prefix = "spring.datasource.batch")
public class BatchDataSourceConfiguration {
    private String driverClassName;
    private String url;
    private String username;
    private String password;

    @BatchDataSource
    @Bean(name = "batchDataSource")
    public DataSource createDataSource() {
        return DataSourceBuilder.create()
                .driverClassName(driverClassName)
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

}
```
```Java
@lombok.Getter
@lombok.Setter
@Component
@ConfigurationProperties(prefix = "spring.datasource.copy-source")
public class CopySourceDataSourceConfiguration {
    private String driverClassName;
    private String url;
    private String username;
    private String password;
    private int fetchsize;

    @Primary
    @Bean(name = "copySourceDataSource")
    public DataSource createDataSource() {
        return DataSourceBuilder.create()
                .driverClassName(driverClassName)
                .url(url)
                .username(username)
                .password(password)
                .build();
    }

    @Primary
    @Bean(name = "copySourceNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate createNamedParameterJdbcTemplate(
            @Qualifier("copySourceDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Primary
    @Bean(name = "copySourceFetchSize")
    public Integer createFetchSize() {
        return Integer.valueOf(fetchsize);
    }
    
}
```

# DataCopyTasklet
バッチ処理の step (Tasklet) を実装する。  
step (Tasklet) クラス に `Tasklet`を implements し、`execute`メソッドを実装する。  
今回は、`DataCopyService`の`copy`メソッドを実行するように実装する。  
また、`StepExecutionListener`を implements することで  
実行前と実行後を検出することができるので、事前の処理や事後処理を書くことが可能。  

```Java
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
```

# TaskletsConfig
step の実装が終わったら、`Config`を書いて、job と step の Bean 定義を行う。
step の Bean 作成は、`StepBuilder`を使って行う。    
job の Bean 作成は、`JobBuilder`を使って行う。

```Java
@Configuration
public class TaskletsConfig {

    private final DataCopyTasklet dataCopyTasklet;

    @Autowired
    public TaskletsConfig (DataCopyTasklet dataCopyTasklet) {
        this.dataCopyTasklet = dataCopyTasklet;
    }

    @Bean
    DataCopyTasklet getDataCopyTasklet() {
        return this.dataCopyTasklet;
    }

    @Bean
    protected Step dataCopyStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("dataCopyStep", jobRepository) //指定された名前とジョブリポジトリを使用して、ステップビルダーを初期化
                .tasklet(getDataCopyTasklet(), transactionManager) //taskletインタフェースを実装したインスタンスと tasklet で利用する transactionManager を指定してステップを作成
                .build();
    }

    @Bean
    public Job dataCopyJob(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new JobBuilder("dataCopyJob", jobRepository) //指定された名前とジョブリポジトリを使用して、ジョブビルダーを初期化
                .start(dataCopyStep(jobRepository, transactionManager)) //ステップまたはステップのシーケンスを実行する新しいジョブを作成
                .build();
    }

}
```

# Test
`@SpringBootTest`のアノテーションをつけるとテスト実行時に登録されているジョブが全て実行されてしまうので  
テストで利用する `application.yml` に job が実行されないように設定を追加しておく。  

```yml
spring:
  datasource:
    batch:
      driver-class-name: org.h2.Driver
      url: jdbc:h2:mem:batch_db:Mode=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false
      username: test
      password: testpass
    copy-source:
      driver-class-name: org.h2.Driver
      url: jdbc:h2:mem:test_db1:Mode=MySQL;DB_CLOSE_DELAY=-1
      username: test
      password: testpass
      fetchsize: 1000
    copy-to:
      driver-class-name: org.h2.Driver
      url: jdbc:h2:mem:test_db2:Mode=MySQL;DB_CLOSE_DELAY=-1
      username: test
      password: testpass
      fetchsize: 1000
  # @SpringBootTest で job が実行されないようにするための設定
  batch:
    job:
      enabled: false
```

`@SpringBatchTest`のアノテーションをつけることで`JobLauncherTestUtils`が利用できるようになるので  
`jobLauncherTestUtils.launchJob()`でジョブ実行できる。  
特定の`step`だけを実行したい場合は、`jobLauncherTestUtils.launchStep("step名")`で実行できる。
