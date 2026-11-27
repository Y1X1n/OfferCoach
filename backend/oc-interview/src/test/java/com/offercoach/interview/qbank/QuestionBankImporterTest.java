package com.offercoach.interview.qbank;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.offercoach.interview.support.JsonCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * 一行脏数据不能让 ApplicationRunner 抛出、把整个应用带崩。
 */
class QuestionBankImporterTest {

    @TempDir
    Path tempDir;

    private QuestionBankImporter importer(Path jsonl) {
        QuestionBankMapper questionBankMapper = mock(QuestionBankMapper.class);
        when(questionBankMapper.insertImported(
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                        any()))
                .thenReturn(1);
        QuestionImportBatchMapper batchMapper = mock(QuestionImportBatchMapper.class);
        when(batchMapper.insertBatch(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyString()))
                .thenReturn(1);
        return new QuestionBankImporter(
                questionBankMapper,
                batchMapper,
                JsonMapper.builder().build(),
                new JsonCodec(JsonMapper.builder().build()),
                jsonl.toString());
    }

    /**
     * Jackson 3 的无参 asText() 在对象/数组节点上抛 JsonNodeException（Jackson 2 返回 ""），
     * 而 run() 外层只 catch (IOException)——脏字段会一路冒到 ApplicationRunner。
     */
    @Test
    void nonScalarFieldsDoNotAbortImport() throws IOException {
        Path jsonl = tempDir.resolve("qbank.jsonl");
        Files.writeString(
                jsonl,
                """
                {"sourceRepo":{"nested":1},"dimension":"DEPTH","difficulty":"EASY","stem":"请描述一次线上故障的排查过程"}
                {"sourceRepo":"repo","dimension":["DEPTH"],"difficulty":"EASY","stem":"请描述一次线上故障的排查过程"}
                """,
                StandardCharsets.UTF_8);

        assertThatNoException().isThrownBy(() -> importer(jsonl).run(null));
    }

    @Test
    void malformedJsonLineIsCountedNotThrown() throws IOException {
        Path jsonl = tempDir.resolve("qbank.jsonl");
        Files.writeString(jsonl, "{not json}\n", StandardCharsets.UTF_8);

        assertThatNoException().isThrownBy(() -> importer(jsonl).run(null));
    }
}
