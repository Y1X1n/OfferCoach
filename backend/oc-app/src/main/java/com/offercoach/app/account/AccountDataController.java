package com.offercoach.app.account;

import com.offercoach.identity.security.CurrentUser;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人数据导出端点；仅返回当前登录用户自己的数据。
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class AccountDataController {

    private final AccountDataExportService exportService;
    private final CurrentUser currentUser;

    public AccountDataController(AccountDataExportService exportService, CurrentUser currentUser) {
        this.exportService = exportService;
        this.currentUser = currentUser;
    }

    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export() {
        long userId = currentUser.userId();
        Map<String, Object> body = exportService.export(userId);
        String fileName = "offercoach-export-" + userId + "-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
