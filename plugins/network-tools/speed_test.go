package main

import (
	"bytes"
	"context"
	"io"
	"net/http"
	"testing"
	"time"
)

type transportFunc func(*http.Request) (*http.Response, error)

func (f transportFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }
func TestHTTPTransferIsCappedAndCountsBytesInsteadOfAssumingResponseSize(t *testing.T) {
	client := &http.Client{Transport: transportFunc(func(r *http.Request) (*http.Response, error) {
		return &http.Response{StatusCode: 200, Body: io.NopCloser(bytes.NewReader(make([]byte, 128*1024))), Header: make(http.Header)}, nil
	})}
	rate, n, err := measureHTTP(context.Background(), client, false, time.Second, 4, func(float64, int64) {})
	// 128 requests reserve 1 MiB each, but this server really returns only 128 KiB.
	if err != nil || n != 16*1024*1024 || rate <= 0 {
		t.Fatalf("rate=%v bytes=%d error=%v", rate, n, err)
	}
}
func TestFailedHTTPStatusIsNotReportedAsAValidSpeed(t *testing.T) {
	client := &http.Client{Transport: transportFunc(func(r *http.Request) (*http.Response, error) {
		return &http.Response{StatusCode: 403, Body: io.NopCloser(bytes.NewReader([]byte("blocked"))), Header: make(http.Header)}, nil
	})}
	_, n, err := measureHTTP(context.Background(), client, false, time.Second, 2, func(float64, int64) {})
	if err == nil || n != 0 {
		t.Fatalf("HTTP 403 became a speed result: bytes=%d error=%v", n, err)
	}
}
