package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
public class HelloController {

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.region:ap-south-1}")
    private String region;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String hello() {
        String now = LocalDateTime.now(ZoneOffset.UTC).format(TIME_FORMAT);
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <title>DevOps Pipeline Demo</title>
                  <style>
                    body {
                      margin: 0;
                      min-height: 100vh;
                      display: flex;
                      align-items: center;
                      justify-content: center;
                      background: linear-gradient(135deg, #1e293b, #0f172a);
                      font-family: -apple-system, Segoe UI, Roboto, sans-serif;
                      color: #e2e8f0;
                    }
                    .card {
                      background: #1e293b;
                      border: 1px solid #334155;
                      border-radius: 12px;
                      padding: 2.5rem 3rem;
                      box-shadow: 0 10px 30px rgba(0,0,0,0.4);
                      max-width: 480px;
                    }
                    h1 {
                      margin: 0 0 0.5rem;
                      font-size: 1.6rem;
                      color: #38bdf8;
                    }
                    p.subtitle {
                      margin: 0 0 1.5rem;
                      color: #94a3b8;
                    }
                    table {
                      width: 100%%;
                      border-collapse: collapse;
                    }
                    td {
                      padding: 0.5rem 0;
                      border-top: 1px solid #334155;
                      font-size: 0.9rem;
                    }
                    td.label {
                      color: #94a3b8;
                      width: 40%%;
                    }
                    td.value {
                      color: #f1f5f9;
                      font-family: monospace;
                    }
                    .badge {
                      display: inline-block;
                      background: #14532d;
                      color: #4ade80;
                      border-radius: 999px;
                      padding: 0.15rem 0.6rem;
                      font-size: 0.75rem;
                      font-weight: 600;
                    }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>DevOps Pipeline Demo</h1>
                    <p class="subtitle">Jenkins &rarr; ECR &rarr; ArgoCD &rarr; EKS</p>
                    <table>
                      <tr><td class="label">Status</td><td class="value"><span class="badge">RUNNING</span></td></tr>
                      <tr><td class="label">Build version</td><td class="value">%s</td></tr>
                      <tr><td class="label">Serving pod</td><td class="value">%s</td></tr>
                      <tr><td class="label">Region</td><td class="value">%s</td></tr>
                      <tr><td class="label">Server time</td><td class="value">%s</td></tr>
                    </table>
                  </div>
                </body>
                </html>
                """.formatted(appVersion, hostname(), region, now);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
