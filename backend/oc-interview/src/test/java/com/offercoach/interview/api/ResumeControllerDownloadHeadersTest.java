package com.offercoach.interview.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offercoach.identity.security.CurrentUser;
import com.offercoach.interview.resume.application.ResumeService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 简历原件是 PII：下载响应必须禁止任何中间层缓存。
 *
 * <p>Spring Security 默认头本来就带 no-store；这条用例钉的是"端点自己显式声明了它"——
 * 将来有人关掉全局 headers 或改坏这行，测试会红。</p>
 */
class ResumeControllerDownloadHeadersTest {

    private static final long USER_ID = 7L;
    private static final long RESUME_ID = 1L;

    private ResumeService resumeService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        resumeService = mock(ResumeService.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        when(currentUser.userId()).thenReturn(USER_ID);
        mvc = MockMvcBuilders.standaloneSetup(new ResumeController(resumeService, currentUser))
                .build();
    }

    @Test
    void resumeFileIsNotCacheable() throws Exception {
        when(resumeService.file(USER_ID, RESUME_ID))
                .thenReturn(new ResumeService.ResumeFile(
                        "我的简历.pdf".getBytes(StandardCharsets.UTF_8), "我的简历.pdf", "application/pdf"));

        mvc.perform(get("/api/v1/resumes/{id}/file", RESUME_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @Test
    void downloadUrlResponseIsNotCacheable() throws Exception {
        when(resumeService.downloadUrl(eq(USER_ID), eq(RESUME_ID)))
                .thenReturn(new ResumeService.DownloadUrl("/api/v1/resumes/1/file", 900));

        mvc.perform(get("/api/v1/resumes/{id}/download-url", RESUME_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }
}
