package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"sync"
	"sync/atomic"
	"time"
)

const phaseByteLimit int64 = 128 * 1024 * 1024

func speed(ctx context.Context, p map[string]string) error {
	duration, err := strconv.Atoi(p["duration"])
	if err != nil || duration < 3 || duration > 10 {
		return fmt.Errorf("duration must be 3–10 seconds")
	}
	streams, err := strconv.Atoi(p["streams"])
	if err != nil || streams < 1 || streams > 4 {
		return fmt.Errorf("streams must be 1–4")
	}
	client := newHTTP()
	defer client.CloseIdleConnections()
	var latency float64
	for i := 0; i < 5; i++ {
		start := time.Now()
		req, _ := http.NewRequestWithContext(ctx, "GET", "https://speed.cloudflare.com/__down?bytes=0", nil)
		resp, err := client.Do(req)
		if err != nil {
			return err
		}
		_, err = io.Copy(io.Discard, resp.Body)
		resp.Body.Close()
		if err != nil {
			return err
		}
		if resp.StatusCode != 200 {
			return fmt.Errorf("speed server HTTP %d", resp.StatusCode)
		}
		latency += time.Since(start).Seconds() * 1000
	}
	latency /= 5
	metrics := []metric{{tr("HTTP 平均延迟", "Mean HTTP latency"), fmt.Sprintf("%.1f ms", latency)}}
	var total int64
	for _, upload := range []bool{false, true} {
		label := tr("下载测速", "Download test")
		if upload {
			label = tr("上传测速", "Upload test")
		}
		rate, used, err := measureHTTP(ctx, client, upload, time.Duration(duration)*time.Second, streams, func(bps float64, n int64) {
			emit(event{Phase: label, Metrics: append(append([]metric{}, metrics...), metric{tr("实时速率", "Live rate"), fmt.Sprintf("%.2f Mbps", bps/1e6)}, metric{tr("已传输", "Transferred"), fmt.Sprintf("%.1f MiB", float64(total+n)/(1024*1024))})})
		})
		if err != nil {
			return err
		}
		total += used
		metrics = append(metrics, metric{label, fmt.Sprintf("%.2f Mbps", rate/1e6)})
	}
	metrics = append(metrics, metric{tr("总流量", "Total traffic"), fmt.Sprintf("%.1f MiB", float64(total)/(1024*1024))})
	emit(event{Phase: tr("测速完成（HTTP 传输估算）", "Finished (HTTP transfer estimate)"), Metrics: metrics, Done: true})
	return nil
}

// Transfer bytes are measured while streaming; payloads and responses are not retained in memory.
func measureHTTP(parent context.Context, client *http.Client, upload bool, duration time.Duration, streams int, push func(float64, int64)) (float64, int64, error) {
	ctx, cancel := context.WithTimeout(parent, duration)
	defer cancel()
	var count atomic.Int64
	var completed atomic.Int64
	var reserved atomic.Int64
	var wg sync.WaitGroup
	start := time.Now()
	for i := 0; i < streams; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			payload := make([]byte, 1024*1024)
			if upload {
				_, _ = rand.Read(payload)
			}
			for ctx.Err() == nil {
				size := int64(1024 * 1024)
				if reserved.Add(size) > phaseByteLimit {
					return
				}
				address := "https://speed.cloudflare.com/__down?bytes=1048576"
				method := "GET"
				var body io.Reader
				if upload {
					address = "https://speed.cloudflare.com/__up"
					method = "POST"
					body = &countingReader{reader: bytes.NewReader(payload), count: &count}
				}
				req, _ := http.NewRequestWithContext(ctx, method, address, body)
				if upload {
					req.ContentLength = size
					req.Header.Set("Content-Type", "application/octet-stream")
				}
				resp, err := client.Do(req)
				if err != nil {
					return
				}
				if resp.StatusCode != 200 {
					resp.Body.Close()
					return
				}
				if upload {
					_, err = io.Copy(io.Discard, io.LimitReader(resp.Body, 4096))
				} else {
					_, err = io.Copy(&countingWriter{count: &count}, io.LimitReader(resp.Body, size))
				}
				resp.Body.Close()
				if err != nil {
					return
				}
				completed.Add(1)
			}
		}()
	}
	done := make(chan struct{})
	go func() { wg.Wait(); close(done) }()
	ticker := time.NewTicker(250 * time.Millisecond)
	defer ticker.Stop()
	for {
		select {
		case <-done:
			elapsed := time.Since(start).Seconds()
			if parent.Err() != nil {
				return 0, count.Load(), parent.Err()
			}
			if completed.Load() == 0 {
				return 0, count.Load(), fmt.Errorf("no successful transfer; server or network unavailable")
			}
			return float64(count.Load()) * 8 / elapsed, count.Load(), nil
		case <-ticker.C:
			push(float64(count.Load())*8/time.Since(start).Seconds(), count.Load())
		}
	}
}

type countingReader struct {
	reader io.Reader
	count  *atomic.Int64
}

func (c *countingReader) Read(p []byte) (int, error) {
	n, err := c.reader.Read(p)
	c.count.Add(int64(n))
	return n, err
}

type countingWriter struct{ count *atomic.Int64 }

func (c *countingWriter) Write(p []byte) (int, error) { c.count.Add(int64(len(p))); return len(p), nil }
