package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"sync"
	"time"
)

// Set by the packaging script. A package exposes exactly one tool.
var toolKind = "connectivity"

type text struct {
	Zh string `json:"zh"`
	En string `json:"en"`
	Tw string `json:"tw"`
}

func tr(zh, en string) text { return text{zh, en, traditional.Replace(zh)} }

type metric struct {
	Label text   `json:"label"`
	Value string `json:"value"`
}
type row struct {
	Target string `json:"target"`
	Status text   `json:"status"`
	Detail string `json:"detail"`
}
type event struct {
	Phase   text     `json:"phase"`
	Metrics []metric `json:"metrics"`
	Rows    []row    `json:"rows"`
	Done    bool     `json:"done"`
	Error   bool     `json:"error"`
}

var traditional = strings.NewReplacer("测试", "測試", "检测", "檢測", "可访问", "可存取", "可达", "可達", "登录", "登入", "验证", "驗證", "地区", "地區", "请求", "請求", "服务", "服務", "异常", "異常", "连接", "連線", "读取", "讀取", "传输", "傳輸", "下载", "下載", "上传", "上傳", "延迟", "延遲", "实时", "即時", "总流量", "總流量", "监听", "監聽", "到时", "到時", "自动", "自動", "最长", "最長", "已用时间", "已用時間", "结果", "結果", "丢包", "丟包", "账号", "帳號", "获得", "獲得", "权限", "權限")

var outputMu sync.Mutex

func emit(e event) {
	outputMu.Lock()
	defer outputMu.Unlock()
	if e.Metrics == nil {
		e.Metrics = []metric{}
	}
	if e.Rows == nil {
		e.Rows = []row{}
	}
	_ = json.NewEncoder(os.Stdout).Encode(e)
}
func fail(err error) {
	emit(event{Phase: tr("测试失败："+err.Error(), "Test failed: "+err.Error()), Done: true, Error: true})
}
func main() {
	reader := bufio.NewScanner(os.Stdin)
	reader.Buffer(make([]byte, 4096), 32768)
	if !reader.Scan() {
		return
	}
	params := map[string]string{}
	if err := json.Unmarshal(reader.Bytes(), &params); err != nil {
		fail(err)
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 110*time.Second)
	defer cancel()
	go func() {
		for reader.Scan() {
			if reader.Text() == "cancel" {
				cancel()
				return
			}
		}
		cancel()
	}()
	var err error
	switch toolKind {
	case "speed":
		err = speed(ctx, params)
	case "connectivity":
		err = connectivity(ctx, params)
	case "iperf":
		err = iperf(ctx, params)
	default:
		err = fmt.Errorf("unknown tool")
	}
	if err != nil {
		fail(err)
	}
}
