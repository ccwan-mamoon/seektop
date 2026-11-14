package main

import (
	"context"
	"fmt"
	iperf3 "github.com/appshubcc/go-iperf3"
	"math"
	"net"
	"strconv"
	"strings"
	"time"
)

func iperf(ctx context.Context, p map[string]string) error {
	port, err := strconv.Atoi(p["port"])
	if err != nil || port < 1 || port > 65535 {
		return fmt.Errorf("invalid port")
	}
	duration, err := strconv.Atoi(p["duration"])
	if err != nil || duration < 1 || duration > 60 {
		return fmt.Errorf("duration must be 1–60 seconds")
	}
	if p["mode"] == "server" {
		address := strings.TrimSpace(p["host"])
		if net.ParseIP(address) == nil {
			return fmt.Errorf("server bind address must be an IP, e.g. 127.0.0.1 or 0.0.0.0")
		}
		server, err := iperf3.Listen(iperf3.ServerConfig{Addr: net.JoinHostPort(address, strconv.Itoa(port)), OnUpdate: emitIperf})
		if err != nil {
			return err
		}
		defer server.Close()
		emit(event{Phase: tr("监听中（到时自动停止）", "Listening (stops automatically)"), Metrics: []metric{{tr("监听地址", "Listen address"), net.JoinHostPort(address, strconv.Itoa(server.Port()))}, {tr("最长等待", "Maximum wait"), fmt.Sprintf("%d s", duration)}}})
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(time.Duration(duration) * time.Second):
		}
		emit(event{Phase: tr("服务端已停止", "Server stopped"), Done: true})
		return nil
	}
	host := strings.TrimSpace(p["host"])
	if host == "" || strings.ContainsAny(host, " /\\\r\n\t") {
		return fmt.Errorf("enter a server hostname or IP")
	}
	parallel, err := strconv.Atoi(p["parallel"])
	if err != nil || parallel < 1 || parallel > 16 {
		return fmt.Errorf("parallel must be 1–16")
	}
	bandwidth, err := strconv.ParseFloat(p["bandwidth"], 64)
	if err != nil || math.IsNaN(bandwidth) || math.IsInf(bandwidth, 0) || bandwidth < 1 || bandwidth > 10000 {
		return fmt.Errorf("rate limit must be 1–10000 Mbps")
	}
	if p["protocol"] != "tcp" && p["protocol"] != "udp" {
		return fmt.Errorf("invalid protocol")
	}
	if p["direction"] != "upload" && p["direction"] != "download" && p["direction"] != "bidir" {
		return fmt.Errorf("invalid direction")
	}
	state := iperf3.RunClient(ctx, iperf3.ClientConfig{Hosts: []string{host}, Port: port, Protocol: iperf3.Protocol(p["protocol"]), Direction: iperf3.Direction(p["direction"]), Parallel: parallel, Duration: time.Duration(duration) * time.Second, Bandwidth: bandwidth * 1e6, OnUpdate: emitIperf})
	emitIperf(state)
	return nil
}
func emitIperf(s iperf3.State) {
	metrics := []metric{{tr("上传", "Upload"), fmt.Sprintf("%.2f Mbps", s.Upload/1e6)}, {tr("下载", "Download"), fmt.Sprintf("%.2f Mbps", s.Download/1e6)}, {tr("已用时间", "Elapsed"), fmt.Sprintf("%.1f s", s.Elapsed)}}
	for _, item := range []struct {
		label text
		flow  *iperf3.Flow
	}{{tr("上传结果", "Upload result"), s.UploadResult}, {tr("下载结果", "Download result"), s.DownloadResult}} {
		if item.flow != nil {
			f := item.flow
			metrics = append(metrics, metric{item.label, fmt.Sprintf("%.2f Mbps · %.1f MiB", f.Bitrate/1e6, f.Bytes/(1024*1024))})
			if s.Protocol == iperf3.UDP {
				metrics = append(metrics, metric{tr("丢包 / 抖动", "Loss / jitter"), fmt.Sprintf("%d/%d · %.2f ms", f.Lost, f.Packets, f.Jitter)})
			}
		}
	}
	phase := tr("测试中", "Testing")
	if s.Phase == iperf3.PhaseDone {
		phase = tr("测试完成", "Finished")
	}
	if s.Error != "" {
		phase = tr("测试失败："+s.Error, "Test failed: "+s.Error)
	}
	// A server session ending is not the end of its listening lifetime.
	emit(event{Phase: phase, Metrics: metrics, Done: s.Phase == iperf3.PhaseDone && s.Role == iperf3.RoleClient, Error: s.Error != ""})
}
