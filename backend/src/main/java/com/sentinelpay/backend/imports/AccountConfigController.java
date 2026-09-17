package com.sentinelpay.backend.imports;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/** Local Vite preview only needs public configuration; the hosted dashboard has its own configuration endpoint. */
@RestController
@Profile("uploads")
public class AccountConfigController {
    @GetMapping("/api/account-config")
    public Map<String, Object> config(@Value("${uploads.supabase-url}") String url,
            @Value("${SUPABASE_PUBLISHABLE_KEY:}") String key) {
        return key.startsWith("sb_publishable_")
                ? Map.of("enabled",true,"supabaseUrl",url,"publishableKey",key,"apiBase","")
                : Map.of("enabled",false);
    }
}
