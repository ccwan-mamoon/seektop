package main

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

func checkedURL(raw string) (*url.URL, error) {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || u.Scheme != "https" || u.Hostname() == "" || u.User != nil {
		return nil, fmt.Errorf("HTTPS URL required (no credentials)")
	}
	return u, nil
}
func newHTTP() *http.Client {
	transport := http.DefaultTransport.(*http.Transport).Clone()
	transport.DisableCompression = true
	transport.Proxy = nil
	return &http.Client{Transport: transport, Timeout: 8 * time.Second, CheckRedirect: func(req *http.Request, via []*http.Request) error {
		if len(via) >= 5 {
			return fmt.Errorf("redirect limit")
		}
		_, err := checkedURL(req.URL.String())
		return err
	}}
}
func classifyResponse(status int, header http.Header, body string) text {
	lower := strings.ToLower(body)
	if header.Get("cf-mitigated") == "challenge" || strings.Contains(lower, "/cdn-cgi/challenge-platform/") || strings.Contains(lower, "cf-chl-") {
		return tr("需要验证", "Verification required")
	}
	if strings.Contains(lower, "unsupported_country_region_territory") || strings.Contains(lower, "not available in your country") {
		return tr("地区受限", "Region restricted")
	}
	if status == 401 {
		return tr("可达，需要登录", "Reachable, sign-in required")
	}
	if status == 403 || status == 451 {
		return tr("访问受限", "Access restricted")
	}
	if status == 429 {
		return tr("请求限流", "Rate limited")
	}
	if status >= 200 && status < 400 {
		return tr("可访问", "Reachable")
	}
	if status >= 500 {
		return tr("服务异常", "Service error")
	}
	return tr("HTTP 异常", "HTTP error")
}
func connectivity(ctx context.Context, p map[string]string) error {
	targets := []string{"https://www.nodeseek.com/", "https://github.com/", "https://www.google.com/generate_204", "https://chatgpt.com/", "https://claude.ai/", "https://www.youtube.com/", "https://www.netflix.com/", "https://www.cloudflare.com/cdn-cgi/trace"}
	if p["targets"] == "custom" {
		targets = strings.Fields(p["url"])
		if len(targets) == 0 || len(targets) > 8 {
			return fmt.Errorf("enter 1–8 HTTPS URLs")
		}
	}
	for _, raw := range targets {
		if _, err := checkedURL(raw); err != nil {
			return err
		}
	}
	client := newHTTP()
	defer client.CloseIdleConnections()
	rows := []row{}
	for _, raw := range targets {
		if err := ctx.Err(); err != nil {
			return err
		}
		start := time.Now()
		req, _ := http.NewRequestWithContext(ctx, "GET", raw, nil)
		req.Header.Set("User-Agent", "SeekTop-NetworkTools/1.0")
		resp, err := client.Do(req)
		r := row{Target: raw}
		if err != nil {
			r.Status = tr("连接失败", "Connection failed")
			r.Detail = err.Error()
		} else {
			body, readErr := io.ReadAll(io.LimitReader(resp.Body, 64*1024))
			resp.Body.Close()
			if readErr != nil {
				r.Status = tr("读取失败", "Read failed")
				r.Detail = readErr.Error()
			} else {
				r.Status = classifyResponse(resp.StatusCode, resp.Header, string(body))
				r.Detail = fmt.Sprintf("HTTP %d · %.0f ms", resp.StatusCode, time.Since(start).Seconds()*1000)
				if strings.Contains(raw, "/cdn-cgi/trace") {
					for _, line := range strings.Split(string(body), "\n") {
						if strings.HasPrefix(line, "loc=") || strings.HasPrefix(line, "colo=") || strings.HasPrefix(line, "ip=") {
							r.Detail += " · " + line
						}
					}
				}
			}
		}
		rows = append(rows, r)
		emit(event{Phase: tr("检测中", "Checking"), Rows: rows})
	}
	emit(event{Phase: tr("检测完成；可访问不代表已获得账号或地区使用权限", "Finished; reachability does not confirm account or regional access"), Rows: rows, Done: true})
	return nil
}
